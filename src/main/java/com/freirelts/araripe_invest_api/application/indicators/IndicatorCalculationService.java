package com.freirelts.araripe_invest_api.application.indicators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.marketdata.DailyCandle;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.FinancialStatementSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.marketdata.StatementType;
import com.freirelts.araripe_invest_api.domain.marketdata.TechnicalIndicatorSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DailyCandleRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FinancialStatementSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.TechnicalIndicatorSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class IndicatorCalculationService {

	public static final String CALCULATION_VERSION = "phase4-v1";
	private static final String COLLECTOR_SOURCE = "brapi";
	private static final String COLLECTOR_CALCULATION_VERSION = "collector-v1";
	private static final String DERIVED_SOURCE = "araripe-indicators";
	private static final int SIX_MONTH_TRADING_DAYS = 126;
	private static final int TWELVE_MONTH_TRADING_DAYS = 252;
	private static final int MINIMUM_LONG_TREND_DAYS = 200;
	private static final int MINIMUM_VOLATILITY_DAYS = 60;
	private static final BigDecimal TWO = new BigDecimal("2");

	private final AssetRepository assetRepository;
	private final DailyCandleRepository dailyCandleRepository;
	private final TechnicalIndicatorSnapshotRepository technicalIndicatorSnapshotRepository;
	private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
	private final FinancialStatementSnapshotRepository financialStatementSnapshotRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public IndicatorCalculationService(AssetRepository assetRepository, DailyCandleRepository dailyCandleRepository,
			TechnicalIndicatorSnapshotRepository technicalIndicatorSnapshotRepository,
			FundamentalSnapshotRepository fundamentalSnapshotRepository,
			FinancialStatementSnapshotRepository financialStatementSnapshotRepository) {
		this.assetRepository = assetRepository;
		this.dailyCandleRepository = dailyCandleRepository;
		this.technicalIndicatorSnapshotRepository = technicalIndicatorSnapshotRepository;
		this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
		this.financialStatementSnapshotRepository = financialStatementSnapshotRepository;
	}

	@Transactional
	public List<IndicatorCalculationResult> calculateForActiveAssets(LocalDate referenceDate) {
		return assetRepository.findByActiveTrueOrderBySymbolAsc().stream()
				.map(asset -> calculateForAsset(asset, referenceDate))
				.toList();
	}

	@Transactional
	public IndicatorCalculationResult calculateForAsset(Asset asset, LocalDate referenceDate) {
		TechnicalIndicatorSnapshot technical = calculateTechnicalIndicators(asset, referenceDate);
		FundamentalSnapshot fundamental = calculateFundamentalIndicators(asset, referenceDate);
		return new IndicatorCalculationResult(asset.getId(), referenceDate,
				technical.getTrendStatus() != TrendStatus.INSUFFICIENT_DATA,
				fundamental.getQualityStatus() == DataQualityStatus.VALID);
	}

	private TechnicalIndicatorSnapshot calculateTechnicalIndicators(Asset asset, LocalDate referenceDate) {
		List<DailyCandle> candles = dailyCandleRepository
				.findByAssetIdAndTradeDateLessThanEqualAndQualityStatusOrderByTradeDateAsc(asset.getId(),
						referenceDate, DataQualityStatus.VALID);
		TechnicalIndicatorSnapshot snapshot = technicalIndicatorSnapshotRepository
				.findByAssetIdAndTradeDateAndCalculationVersion(asset.getId(), referenceDate, CALCULATION_VERSION)
				.orElseGet(() -> new TechnicalIndicatorSnapshot(asset, referenceDate, CALCULATION_VERSION));
		snapshot.setAsset(asset);
		snapshot.setTradeDate(referenceDate);
		snapshot.setCalculationVersion(CALCULATION_VERSION);

		if (candles.isEmpty()) {
			snapshot.setTrendStatus(TrendStatus.INSUFFICIENT_DATA);
			return technicalIndicatorSnapshotRepository.save(snapshot);
		}

		List<BigDecimal> closes = candles.stream().map(this::analysisClose).toList();
		snapshot.setSma50(sma(closes, 50).orElse(null));
		snapshot.setSma100(sma(closes, 100).orElse(null));
		snapshot.setSma200(sma(closes, 200).orElse(null));
		snapshot.setEma50(ema(closes, 50).orElse(null));
		snapshot.setEma100(ema(closes, 100).orElse(null));
		snapshot.setEma200(ema(closes, 200).orElse(null));
		snapshot.setReturn6m(periodReturn(closes, SIX_MONTH_TRADING_DAYS).orElse(null));
		snapshot.setReturn12m(periodReturn(closes, TWELVE_MONTH_TRADING_DAYS).orElse(null));
		snapshot.setAvgVolume60(averageFinancialVolume(candles, 60).orElse(null));
		snapshot.setHigh52w(maximumHigh(candles, TWELVE_MONTH_TRADING_DAYS).orElse(null));
		snapshot.setLow52w(minimumLow(candles, TWELVE_MONTH_TRADING_DAYS).orElse(null));
		snapshot.setHistoricalVolatility(historicalVolatility(closes).orElse(null));
		snapshot.setRecentDrawdown(recentDrawdown(closes).orElse(null));
		snapshot.setTrendStatus(trendStatus(closes, snapshot.getSma50(), snapshot.getSma200()));

		return technicalIndicatorSnapshotRepository.save(snapshot);
	}

	private FundamentalSnapshot calculateFundamentalIndicators(Asset asset, LocalDate referenceDate) {
		List<String> missingFields = new ArrayList<>();
		List<String> assumptions = new ArrayList<>();
		FundamentalSnapshot collector = fundamentalSnapshotRepository
				.findTopByAssetIdAndReferenceDateLessThanEqualAndPeriodTypeAndSourceAndCalculationVersionAndQualityStatusOrderByReferenceDateDescCreatedAtDesc(
						asset.getId(), referenceDate, PeriodType.TTM, COLLECTOR_SOURCE,
						COLLECTOR_CALCULATION_VERSION, DataQualityStatus.VALID)
				.orElse(null);
		FundamentalSnapshot derived = fundamentalSnapshotRepository
				.findByAssetIdAndReferenceDateAndPeriodTypeAndSourceAndCalculationVersion(asset.getId(), referenceDate,
						PeriodType.TTM, DERIVED_SOURCE, CALCULATION_VERSION)
				.orElseGet(() -> new FundamentalSnapshot(asset, referenceDate, PeriodType.TTM, DERIVED_SOURCE));
		derived.setAsset(asset);
		derived.setReferenceDate(referenceDate);
		derived.setPeriodType(PeriodType.TTM);
		derived.setSource(DERIVED_SOURCE);
		derived.setCalculationVersion(CALCULATION_VERSION);

		copyCollectorValuation(collector, derived);

		List<StatementValues> annualIncome = statements(asset, StatementType.INCOME_STATEMENT, PeriodType.ANNUAL,
				referenceDate);
		List<StatementValues> quarterlyIncome = statements(asset, StatementType.INCOME_STATEMENT, PeriodType.QUARTERLY,
				referenceDate);
		List<StatementValues> annualCash = statements(asset, StatementType.CASH_FLOW, PeriodType.ANNUAL, referenceDate);
		List<StatementValues> annualBalance = statements(asset, StatementType.BALANCE_SHEET, PeriodType.ANNUAL,
				referenceDate);

		StatementValues latestAnnualIncome = first(annualIncome);
		StatementValues previousAnnualIncome = second(annualIncome);
		StatementValues latestQuarterlyIncome = first(quarterlyIncome);
		StatementValues comparableQuarterlyIncome = sameQuarterPreviousYear(quarterlyIncome, latestQuarterlyIncome);
		StatementValues latestCash = first(annualCash);
		StatementValues latestBalance = first(annualBalance);
		derived.setMostRecentQuarter(latestAccountingPeriod(derived.getMostRecentQuarter(), latestAnnualIncome,
				latestQuarterlyIncome, latestCash, latestBalance));

		derived.setAnnualRevenueGrowth(growth(value(latestAnnualIncome, "revenue", missingFields),
				value(previousAnnualIncome, "revenue", missingFields)).orElse(derived.getAnnualRevenueGrowth()));
		derived.setQuarterlyRevenueGrowth(growth(value(latestQuarterlyIncome, "revenue", missingFields),
				value(comparableQuarterlyIncome, "revenue", missingFields)).orElse(derived.getQuarterlyRevenueGrowth()));
		derived.setAnnualEarningsGrowth(growth(value(latestAnnualIncome, "netIncome", missingFields),
				value(previousAnnualIncome, "netIncome", missingFields)).orElse(derived.getAnnualEarningsGrowth()));
		derived.setQuarterlyEarningsGrowth(growth(value(latestQuarterlyIncome, "netIncome", missingFields),
				value(comparableQuarterlyIncome, "netIncome", missingFields)).orElse(derived.getQuarterlyEarningsGrowth()));
		derived.setEbitdaGrowth(growth(value(latestAnnualIncome, "ebitda", missingFields),
				value(previousAnnualIncome, "ebitda", missingFields))
			.or(() -> growth(value(latestQuarterlyIncome, "ebitda", missingFields),
					value(comparableQuarterlyIncome, "ebitda", missingFields)))
			.orElse(derived.getEbitdaGrowth()));

		BigDecimal revenue = value(latestAnnualIncome, "revenue", missingFields);
		BigDecimal netIncome = value(latestAnnualIncome, "netIncome", missingFields);
		BigDecimal operatingCashflow = value(latestCash, "operatingCashflow", missingFields);

		assumptions.add("Campos current/TTM coletados da brapi sao preservados quando presentes; calculos internos por demonstrativo nao preenchem lacunas desses campos.");
		assumptions.add("Crescimento anual compara a ultima demonstracao anual valida com a anual imediatamente anterior.");
		assumptions.add("Crescimento trimestral compara o ultimo trimestre valido com o mesmo trimestre do ano anterior para reduzir ruido sazonal.");
		assumptions.add("Crescimento com base negativa nao usa divisao percentual tradicional; viradas de prejuizo para lucro nao contam como crescimento normal.");
		derived.setAssumptionsJson(json(assumptions));
		derived.setQualityStatus(fundamentalQuality(derived, revenue, netIncome, operatingCashflow, missingFields));
		derived.setMissingFieldsJson(json(distinctSorted(missingFields)));

		return fundamentalSnapshotRepository.save(derived);
	}

	private void copyCollectorValuation(FundamentalSnapshot source, FundamentalSnapshot target) {
		resetCollectorFields(target);
		if (source == null) {
			return;
		}
		target.setMarketCap(source.getMarketCap());
		target.setMostRecentQuarter(source.getMostRecentQuarter());
		target.setEnterpriseValue(source.getEnterpriseValue());
		target.setTrailingPe(source.getTrailingPe());
		target.setPriceToBook(source.getPriceToBook());
		target.setEnterpriseToRevenue(source.getEnterpriseToRevenue());
		target.setEnterpriseToEbitda(source.getEnterpriseToEbitda());
		target.setForwardPe(source.getForwardPe());
		target.setPegRatio(source.getPegRatio());
		target.setEarningsPerShare(source.getEarningsPerShare());
		target.setNetIncomeToCommon(source.getNetIncomeToCommon());
		target.setBookValue(source.getBookValue());
		target.setDividendYield(source.getDividendYield());
		target.setLastDividendValue(source.getLastDividendValue());
		target.setLastDividendDate(source.getLastDividendDate());
		target.setBeta(source.getBeta());
		target.setFloatShares(source.getFloatShares());
		target.setSharesOutstanding(source.getSharesOutstanding());
		target.setFiftyTwoWeekChange(source.getFiftyTwoWeekChange());
		target.setTotalCash(source.getTotalCash());
		target.setTotalCashPerShare(source.getTotalCashPerShare());
		target.setEbitda(source.getEbitda());
		target.setTotalDebt(source.getTotalDebt());
		target.setQuickRatio(source.getQuickRatio());
		target.setCurrentRatio(source.getCurrentRatio());
		target.setTotalRevenue(source.getTotalRevenue());
		target.setGrossProfits(source.getGrossProfits());
		target.setProfitMargin(source.getProfitMargin());
		target.setGrossMargin(source.getGrossMargin());
		target.setEbitdaMargin(source.getEbitdaMargin());
		target.setOperatingMargin(source.getOperatingMargin());
		target.setRoe(source.getRoe());
		target.setRoa(source.getRoa());
		target.setDebtToEquity(source.getDebtToEquity());
		target.setRevenueGrowth(source.getRevenueGrowth());
		target.setEarningsGrowth(source.getEarningsGrowth());
		target.setAnnualRevenueGrowth(source.getAnnualRevenueGrowth());
		target.setQuarterlyRevenueGrowth(source.getQuarterlyRevenueGrowth());
		target.setAnnualEarningsGrowth(source.getAnnualEarningsGrowth());
		target.setQuarterlyEarningsGrowth(source.getQuarterlyEarningsGrowth());
		target.setEbitdaGrowth(source.getEbitdaGrowth());
		target.setFreeCashflow(source.getFreeCashflow());
		target.setOperatingCashflow(source.getOperatingCashflow());
		target.setNetDebt(source.getNetDebt());
	}

	private void resetCollectorFields(FundamentalSnapshot target) {
		target.setMarketCap(null);
		target.setMostRecentQuarter(null);
		target.setEnterpriseValue(null);
		target.setTrailingPe(null);
		target.setPriceToBook(null);
		target.setEnterpriseToRevenue(null);
		target.setEnterpriseToEbitda(null);
		target.setForwardPe(null);
		target.setPegRatio(null);
		target.setEarningsPerShare(null);
		target.setNetIncomeToCommon(null);
		target.setBookValue(null);
		target.setDividendYield(null);
		target.setLastDividendValue(null);
		target.setLastDividendDate(null);
		target.setBeta(null);
		target.setFloatShares(null);
		target.setSharesOutstanding(null);
		target.setFiftyTwoWeekChange(null);
		target.setTotalCash(null);
		target.setTotalCashPerShare(null);
		target.setEbitda(null);
		target.setTotalDebt(null);
		target.setQuickRatio(null);
		target.setCurrentRatio(null);
		target.setTotalRevenue(null);
		target.setGrossProfits(null);
		target.setProfitMargin(null);
		target.setGrossMargin(null);
		target.setEbitdaMargin(null);
		target.setOperatingMargin(null);
		target.setRoe(null);
		target.setRoa(null);
		target.setDebtToEquity(null);
		target.setRevenueGrowth(null);
		target.setEarningsGrowth(null);
		target.setAnnualRevenueGrowth(null);
		target.setQuarterlyRevenueGrowth(null);
		target.setAnnualEarningsGrowth(null);
		target.setQuarterlyEarningsGrowth(null);
		target.setEbitdaGrowth(null);
		target.setFreeCashflow(null);
		target.setOperatingCashflow(null);
		target.setNetDebt(null);
	}

	private List<StatementValues> statements(Asset asset, StatementType statementType, PeriodType periodType,
			LocalDate referenceDate) {
		return financialStatementSnapshotRepository
				.findByAssetIdAndStatementTypeAndPeriodTypeAndEndDateLessThanEqualAndQualityStatusOrderByEndDateDesc(
						asset.getId(), statementType, periodType, referenceDate, DataQualityStatus.VALID)
				.stream()
				.map(this::toStatementValues)
				.toList();
	}

	private StatementValues toStatementValues(FinancialStatementSnapshot snapshot) {
		try {
			JsonNode root = objectMapper.readTree(snapshot.getPayloadJson());
			return new StatementValues(snapshot.getEndDate(),
					decimal(root, "totalRevenue", "revenue", "operatingRevenue", "netRevenue"),
					decimal(root, "netIncome", "cleanNetIncome", "netIncomeCommonStockholders",
							"netIncomeApplicableToCommonShares"),
					decimal(root, "cleanEbitda", "ebitda", "EBITDA"), decimal(root, "grossProfit"),
					decimal(root, "operatingIncome", "operatingProfit", "cleanEbit", "ebit"),
					decimal(root, "totalAssets"),
					decimal(root, "shareholdersEquity", "totalStockholderEquity", "stockholdersEquity",
							"shareholderEquity", "totalEquityGrossMinorityInterest",
							"controllerShareholdersEquity"),
					totalDebt(root),
					decimal(root, "cash", "cashAndCashEquivalents", "cashAndShortTermInvestments", "totalCash"),
					decimal(root, "operatingCashflow", "operatingCashFlow", "totalCashFromOperatingActivities"),
					decimal(root, "freeCashflow", "freeCashFlow"),
					decimal(root, "capitalExpenditures", "capitalExpenditure", "capex"));
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not parse financial statement snapshot.", ex);
		}
	}

	private BigDecimal totalDebt(JsonNode root) {
		BigDecimal directTotal = decimal(root, "totalDebt", "shortLongTermDebtTotal");
		if (directTotal != null) {
			return directTotal;
		}
		BigDecimal[] components = {
				decimal(root, "shortLongTermDebt", "shortTermDebt", "loansAndFinancing"),
				decimal(root, "longTermDebtAndFinanceLeaseObligation", "longTermDebt", "longTermLoansAndFinancing"),
				decimal(root, "debentures"), decimal(root, "longTermDebentures"), decimal(root, "leaseFinancing"),
				decimal(root, "longTermLeaseFinancing") };
		boolean hasDebtComponent = false;
		BigDecimal total = BigDecimal.ZERO;
		for (BigDecimal component : components) {
			if (component != null) {
				hasDebtComponent = true;
				total = total.add(component);
			}
		}
		return hasDebtComponent ? total : null;
	}

	private Optional<BigDecimal> sma(List<BigDecimal> values, int window) {
		if (values.size() < window) {
			return Optional.empty();
		}
		List<BigDecimal> tail = values.subList(values.size() - window, values.size());
		return Optional.of(scalePrice(tail.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
			.divide(BigDecimal.valueOf(window), 10, RoundingMode.HALF_UP)));
	}

	private Optional<BigDecimal> ema(List<BigDecimal> values, int window) {
		if (values.size() < window) {
			return Optional.empty();
		}

		// EMA suaviza preços recentes com peso alpha = 2 / (janela + 1), preservando memoria da tendencia longa.
		BigDecimal alpha = TWO.divide(BigDecimal.valueOf(window + 1L), 12, RoundingMode.HALF_UP);
		BigDecimal ema = sma(values.subList(0, window), window).orElseThrow();
		for (int i = window; i < values.size(); i++) {
			ema = values.get(i).multiply(alpha).add(ema.multiply(BigDecimal.ONE.subtract(alpha)));
		}
		return Optional.of(scalePrice(ema));
	}

	private Optional<BigDecimal> periodReturn(List<BigDecimal> closes, int tradingDays) {
		if (closes.size() <= tradingDays) {
			return Optional.empty();
		}
		BigDecimal current = closes.getLast();
		BigDecimal base = closes.get(closes.size() - tradingDays - 1);
		if (base.signum() == 0) {
			return Optional.empty();
		}
		// Retorno acumulado mede variacao percentual entre o fechamento atual e o fechamento da janela.
		return Optional.of(scaleRatio(current.divide(base, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)));
	}

	private Optional<BigDecimal> historicalVolatility(List<BigDecimal> closes) {
		if (closes.size() < MINIMUM_VOLATILITY_DAYS + 1) {
			return Optional.empty();
		}
		List<BigDecimal> tail = closes.subList(Math.max(0, closes.size() - TWELVE_MONTH_TRADING_DAYS - 1),
				closes.size());
		List<Double> logReturns = new ArrayList<>();
		for (int i = 1; i < tail.size(); i++) {
			logReturns.add(Math.log(tail.get(i).divide(tail.get(i - 1), 12, RoundingMode.HALF_UP).doubleValue()));
		}
		double average = logReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
		double variance = logReturns.stream().mapToDouble(value -> Math.pow(value - average, 2)).sum()
				/ Math.max(1, logReturns.size() - 1);
		// Volatilidade historica anualizada: desvio padrao dos retornos diarios multiplicado por sqrt(252 pregoes).
		return Optional.of(scaleRatio(BigDecimal.valueOf(Math.sqrt(variance) * Math.sqrt(TWELVE_MONTH_TRADING_DAYS))));
	}

	private Optional<BigDecimal> recentDrawdown(List<BigDecimal> closes) {
		if (closes.size() < 2) {
			return Optional.empty();
		}
		List<BigDecimal> tail = closes.subList(Math.max(0, closes.size() - TWELVE_MONTH_TRADING_DAYS), closes.size());
		BigDecimal peak = tail.stream().max(Comparator.naturalOrder()).orElse(null);
		BigDecimal current = tail.getLast();
		if (peak == null || peak.signum() == 0) {
			return Optional.empty();
		}
		// Drawdown recente mostra a queda percentual desde o maior fechamento da janela ate o preco atual.
		return Optional.of(scaleRatio(current.divide(peak, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)));
	}

	private Optional<BigDecimal> averageFinancialVolume(List<DailyCandle> candles, int window) {
		if (candles.size() < window) {
			return Optional.empty();
		}
		List<DailyCandle> tail = candles.subList(candles.size() - window, candles.size());
		BigDecimal total = BigDecimal.ZERO;
		for (DailyCandle candle : tail) {
			BigDecimal volume = candle.getVolumeFinancial();
			if (volume == null && candle.getVolumeQuantity() != null) {
				volume = candle.getVolumeQuantity().multiply(analysisClose(candle));
			}
			if (volume == null) {
				return Optional.empty();
			}
			total = total.add(volume);
		}
		return Optional.of(scaleMoney(total.divide(BigDecimal.valueOf(window), 10, RoundingMode.HALF_UP)));
	}

	private Optional<BigDecimal> maximumHigh(List<DailyCandle> candles, int window) {
		if (candles.size() < window) {
			return Optional.empty();
		}
		return candles.subList(candles.size() - window, candles.size()).stream()
				.map(DailyCandle::getHighPrice)
				.max(Comparator.naturalOrder())
				.map(this::scalePrice);
	}

	private Optional<BigDecimal> minimumLow(List<DailyCandle> candles, int window) {
		if (candles.size() < window) {
			return Optional.empty();
		}
		return candles.subList(candles.size() - window, candles.size()).stream()
				.map(DailyCandle::getLowPrice)
				.min(Comparator.naturalOrder())
				.map(this::scalePrice);
	}

	private TrendStatus trendStatus(List<BigDecimal> closes, BigDecimal sma50, BigDecimal sma200) {
		if (closes.size() < MINIMUM_LONG_TREND_DAYS || sma200 == null) {
			return TrendStatus.INSUFFICIENT_DATA;
		}
		BigDecimal current = closes.getLast();
		if (current.compareTo(sma200) >= 0 && sma50 != null && sma50.compareTo(sma200) >= 0) {
			return TrendStatus.HEALTHY;
		}
		if (current.compareTo(sma200) >= 0) {
			return TrendStatus.NEUTRAL;
		}
		if (sma50 != null && sma50.compareTo(sma200) >= 0) {
			return TrendStatus.DETERIORATING;
		}
		return TrendStatus.DOWN_TREND;
	}

	private Optional<BigDecimal> growth(BigDecimal current, BigDecimal previous) {
		if (current == null || previous == null) {
			return Optional.empty();
		}
		if (previous.signum() > 0) {
			// Crescimento percentual padrao so e valido quando a base anterior e positiva.
			return Optional.of(scaleRatio(current.divide(previous, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)));
		}
		if (previous.signum() == 0) {
			return current.signum() < 0 ? Optional.of(scaleRatio(BigDecimal.ONE.negate())) : Optional.empty();
		}
		if (current.compareTo(previous) < 0) {
			return Optional.of(scaleRatio(current.subtract(previous)
					.divide(previous.abs(), 12, RoundingMode.HALF_UP)));
		}
		// Melhora partindo de prejuizo ou virada para lucro e sinal qualitativo, nao crescimento percentual normal.
		return Optional.of(scaleRatio(BigDecimal.ZERO));
	}

	private DataQualityStatus fundamentalQuality(FundamentalSnapshot snapshot, BigDecimal revenue, BigDecimal netIncome,
			BigDecimal operatingCashflow, List<String> missingFields) {
		boolean hasRevenue = revenue != null || snapshot.getTotalRevenue() != null;
		boolean hasValuation = snapshot.getTrailingPe() != null || snapshot.getPriceToBook() != null
				|| snapshot.getEnterpriseToEbitda() != null;
		boolean hasProfitability = netIncome != null || snapshot.getEarningsPerShare() != null;
		boolean hasCash = operatingCashflow != null || snapshot.getOperatingCashflow() != null;
		if (!hasRevenue || !hasProfitability || !hasCash || !hasValuation) {
			missingFields.add("minimumFundamentalSet");
			return DataQualityStatus.INCOMPLETE;
		}
		return DataQualityStatus.VALID;
	}

	private BigDecimal value(StatementValues values, String field, List<String> missingFields) {
		if (values == null) {
			missingFields.add(field);
			return null;
		}
		BigDecimal value = values.value(field);
		if (value == null) {
			missingFields.add(field);
		}
		return value;
	}

	private BigDecimal analysisClose(DailyCandle candle) {
		return candle.getAdjustedClosePrice() != null && candle.getAdjustedClosePrice().signum() > 0
				? candle.getAdjustedClosePrice() : candle.getClosePrice();
	}

	private BigDecimal decimal(JsonNode root, String... aliases) {
		for (String alias : aliases) {
			BigDecimal value = findDecimal(root, alias.toLowerCase(Locale.ROOT));
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private BigDecimal findDecimal(JsonNode node, String fieldName) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isObject()) {
			var fields = node.fields();
			while (fields.hasNext()) {
				var entry = fields.next();
				if (entry.getKey().toLowerCase(Locale.ROOT).equals(fieldName) && entry.getValue().isNumber()) {
					return entry.getValue().decimalValue();
				}
				BigDecimal nested = findDecimal(entry.getValue(), fieldName);
				if (nested != null) {
					return nested;
				}
			}
		}
		if (node.isArray()) {
			for (JsonNode child : node) {
				BigDecimal nested = findDecimal(child, fieldName);
				if (nested != null) {
					return nested;
				}
			}
		}
		return null;
	}

	private StatementValues first(List<StatementValues> statements) {
		return statements.isEmpty() ? null : statements.getFirst();
	}

	private StatementValues second(List<StatementValues> statements) {
		return statements.size() < 2 ? null : statements.get(1);
	}

	private StatementValues sameQuarterPreviousYear(List<StatementValues> statements, StatementValues latest) {
		if (latest == null) {
			return null;
		}
		LocalDate targetEndDate = latest.endDate().minusYears(1);
		return statements.stream()
				.filter(statement -> targetEndDate.equals(statement.endDate()))
				.findFirst()
				.orElse(null);
	}

	private LocalDate latestAccountingPeriod(LocalDate collectorMostRecentQuarter, StatementValues... statements) {
		LocalDate latest = collectorMostRecentQuarter;
		for (StatementValues statement : statements) {
			if (statement != null && (latest == null || statement.endDate().isAfter(latest))) {
				latest = statement.endDate();
			}
		}
		return latest;
	}

	private List<String> distinctSorted(List<String> values) {
		return values.stream().distinct().sorted().toList();
	}

	private BigDecimal scalePrice(BigDecimal value) {
		return value.setScale(6, RoundingMode.HALF_UP);
	}

	private BigDecimal scaleMoney(BigDecimal value) {
		return value.setScale(6, RoundingMode.HALF_UP);
	}

	private BigDecimal scaleRatio(BigDecimal value) {
		return value.setScale(6, RoundingMode.HALF_UP);
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not serialize indicator audit payload.", ex);
		}
	}

	private record StatementValues(LocalDate endDate, BigDecimal revenue, BigDecimal netIncome, BigDecimal ebitda,
			BigDecimal grossProfit, BigDecimal operatingIncome, BigDecimal totalAssets, BigDecimal equity,
			BigDecimal totalDebt, BigDecimal cash, BigDecimal operatingCashflow, BigDecimal freeCashflow,
			BigDecimal capitalExpenditures) {

		private BigDecimal value(String field) {
			return switch (field) {
				case "revenue" -> revenue;
				case "netIncome" -> netIncome;
				case "ebitda" -> ebitda;
				case "grossProfit" -> grossProfit;
				case "operatingIncome" -> operatingIncome;
				case "totalAssets" -> totalAssets;
				case "equity" -> equity;
				case "totalDebt" -> totalDebt;
				case "cash" -> cash;
				case "operatingCashflow" -> operatingCashflow;
				case "freeCashflow" -> freeCashflow;
				case "capitalExpenditures" -> capitalExpenditures;
				default -> null;
			};
		}
	}
}
