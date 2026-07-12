package com.freirelts.araripe_invest_api.application.thesis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.indicators.IndicatorCalculationService;
import com.freirelts.araripe_invest_api.application.screening.EliminatoryFilterCode;
import com.freirelts.araripe_invest_api.application.screening.EliminatoryFilterEvaluator;
import com.freirelts.araripe_invest_api.application.screening.EliminatoryFilterInput;
import com.freirelts.araripe_invest_api.application.screening.EliminatoryFilterReason;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.marketdata.DailyCandle;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.DividendEventType;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.marketdata.TechnicalIndicatorSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import com.freirelts.araripe_invest_api.domain.thesis.AllocationPlan;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AllocationPlanRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DailyCandleRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DividendEventRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.TechnicalIndicatorSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PositionThesisGenerationService {

	public static final String RULE_VERSION = "phase6-v1";
	private static final String DERIVED_SOURCE = "araripe-indicators";
	private static final BigDecimal CAPITAL_BASE = new BigDecimal("10000.00");
	private static final BigDecimal DEFAULT_TARGET_ALLOCATION_PERCENT = new BigDecimal("10.000000");
	private static final BigDecimal REDUCED_TARGET_ALLOCATION_PERCENT = new BigDecimal("6.000000");
	private static final BigDecimal MIN_SAFETY_MARGIN = new BigDecimal("0.150000");
	private static final BigDecimal PRICE_REVIEW_PREMIUM = new BigDecimal("1.150000");
	private static final BigDecimal STOP_PERCENT = new BigDecimal("0.150000");
	private static final BigDecimal TARGET_RETURN_FLOOR = new BigDecimal("0.250000");
	private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.000000");
	private static final List<DividendEventType> CASH_DIVIDEND_EVENTS = List.of(DividendEventType.DIVIDEND,
			DividendEventType.JCP);

	private final AssetRepository assetRepository;
	private final DailyCandleRepository dailyCandleRepository;
	private final TechnicalIndicatorSnapshotRepository technicalIndicatorSnapshotRepository;
	private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
	private final DividendEventRepository dividendEventRepository;
	private final PositionThesisRepository positionThesisRepository;
	private final AllocationPlanRepository allocationPlanRepository;
	private final EliminatoryFilterEvaluator eliminatoryFilterEvaluator;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public PositionThesisGenerationService(AssetRepository assetRepository, DailyCandleRepository dailyCandleRepository,
			TechnicalIndicatorSnapshotRepository technicalIndicatorSnapshotRepository,
			FundamentalSnapshotRepository fundamentalSnapshotRepository, DividendEventRepository dividendEventRepository,
			PositionThesisRepository positionThesisRepository, AllocationPlanRepository allocationPlanRepository,
			EliminatoryFilterEvaluator eliminatoryFilterEvaluator) {
		this.assetRepository = assetRepository;
		this.dailyCandleRepository = dailyCandleRepository;
		this.technicalIndicatorSnapshotRepository = technicalIndicatorSnapshotRepository;
		this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
		this.dividendEventRepository = dividendEventRepository;
		this.positionThesisRepository = positionThesisRepository;
		this.allocationPlanRepository = allocationPlanRepository;
		this.eliminatoryFilterEvaluator = eliminatoryFilterEvaluator;
	}

	@Transactional
	public List<PositionThesis> generateForActiveAssets(LocalDate referenceDate) {
		return assetRepository.findByActiveTrueOrderBySymbolAsc().stream()
				.flatMap(asset -> generateForAsset(asset, referenceDate).stream())
				.toList();
	}

	@Transactional
	public List<PositionThesis> generateForAsset(Asset asset, LocalDate referenceDate) {
		ThesisMarketContext context = loadContext(asset, referenceDate);
		return List.of(save(evaluateQualityReasonablePrice(context)), save(evaluateSustainableDividends(context)),
				save(evaluateProfitableGrowth(context)));
	}

	private ThesisDraft evaluateQualityReasonablePrice(ThesisMarketContext context) {
		List<ThesisReason> reasons = new ArrayList<>();
		int score = 0;

		BigDecimal eps = earningsPerShare(context);
		BigDecimal bookValue = bookValuePerShare(context);
		BigDecimal fairPrice = minimumPositive(multiply(eps, new BigDecimal("12.000000")),
				multiply(bookValue, new BigDecimal("2.200000")));

		// A tese de qualidade usa multiplos conservadores de P/L e P/VP para evitar aprovar empresa boa a preco caro.
		BigDecimal priceCeiling = priceCeiling(fairPrice);
		BigDecimal safetyMargin = safetyMargin(fairPrice, context.currentPrice());
		boolean valuationComplete = fairPrice != null && priceCeiling != null && safetyMargin != null;
		boolean profitable = positive(eps) && greaterOrEqual(context.profitMargin(), new BigDecimal("0.080000"));
		boolean marginsHealthy = greaterOrEqual(any(context.grossMargin(), context.ebitdaMargin(),
				context.operatingMargin(), context.profitMargin()), new BigDecimal("0.100000"));
		boolean returnsHealthy = greaterOrEqual(context.roe(), new BigDecimal("0.120000"))
				|| greaterOrEqual(context.roa(), new BigDecimal("0.060000"));
		boolean cashHealthy = positive(context.operatingCashflow()) && nonNegative(context.freeCashflow());
		boolean debtControlled = context.debtToEquity() == null
				|| lessOrEqual(context.debtToEquity(), new BigDecimal("1.500000"));
		boolean valuationReasonable = lessOrEqual(context.trailingPe(), new BigDecimal("18.000000"))
				&& (context.priceToBook() == null || lessOrEqual(context.priceToBook(), new BigDecimal("3.000000")))
				&& (context.enterpriseToEbitda() == null
						|| lessOrEqual(context.enterpriseToEbitda(), new BigDecimal("12.000000")));
		boolean trendAcceptable = context.trendStatus() == TrendStatus.HEALTHY
				|| context.trendStatus() == TrendStatus.NEUTRAL;

		score += addReason(reasons, profitable, 18, "fundamentos", "QUALITY_PROFITABLE",
				"Lucro por acao positivo e margem liquida adequada sustentam a tese de qualidade.");
		score += addReason(reasons, marginsHealthy, 14, "fundamentos", "QUALITY_MARGINS",
				"Margens operacionais ou liquidas indicam capacidade economica minima.");
		score += addReason(reasons, returnsHealthy, 14, "fundamentos", "QUALITY_RETURNS",
				"ROE ou ROA adequado sinaliza retorno sobre capital aceitavel.");
		score += addReason(reasons, cashHealthy, 18, "fundamentos", "QUALITY_CASH",
				"Caixa operacional positivo e fluxo de caixa livre nao negativo reduzem risco da tese.");
		score += addReason(reasons, debtControlled, 10, "risco", "QUALITY_DEBT",
				"Divida/patrimonio dentro do limite conservador da tese.");
		score += addReason(reasons, valuationReasonable && valuationComplete, 16, "valuation", "QUALITY_VALUATION",
				"Multiplos e preco teto indicam valuation razoavel para a qualidade observada.");
		score += addReason(reasons, trendAcceptable, 10, "tecnico", "QUALITY_TREND",
				"Tendencia longa saudavel ou neutra evita tese contra deterioracao estrutural.");

		boolean mandatory = profitable && cashHealthy && debtControlled && valuationComplete;
		ThesisStatus status = status(context, score, mandatory, priceCeiling, priceAttractive(context.currentPrice(),
				priceCeiling, safetyMargin));

		return draft(context, ThesisType.QUALITY_REASONABLE_PRICE, status, score, fairPrice, priceCeiling, safetyMargin,
				reasons);
	}

	private ThesisDraft evaluateSustainableDividends(ThesisMarketContext context) {
		List<ThesisReason> reasons = new ArrayList<>();
		int score = 0;
		BigDecimal eps = earningsPerShare(context);
		BigDecimal annualDividendPerShare = multiply(context.currentPrice(), context.dividendYield());
		BigDecimal payout = positive(eps) && annualDividendPerShare != null
				? annualDividendPerShare.divide(eps, 6, RoundingMode.HALF_UP) : null;
		BigDecimal fairPrice = annualDividendPerShare == null ? null
				: annualDividendPerShare.divide(new BigDecimal("0.060000"), 6, RoundingMode.HALF_UP);
		BigDecimal priceCeiling = priceCeiling(fairPrice);
		BigDecimal safetyMargin = safetyMargin(fairPrice, context.currentPrice());

		// Dividend yield so entra na tese quando ha recorrencia e cobertura por lucro/caixa; yield isolado pode ser
		// apenas efeito de queda de preco ou evento extraordinario.
		boolean attractiveYield = greaterOrEqual(context.dividendYield(), new BigDecimal("0.040000"))
				&& lessOrEqual(context.dividendYield(), new BigDecimal("0.150000"));
		boolean recurringDividends = context.cashDividendEventsLastThreeYears() >= 2;
		boolean payoutHealthy = payout != null && payout.signum() > 0
				&& payout.compareTo(new BigDecimal("0.850000")) <= 0;
		boolean profitAndCashCoverage = positive(eps) && positive(context.operatingCashflow())
				&& positive(context.freeCashflow());
		boolean debtControlled = context.debtToEquity() == null
				|| lessOrEqual(context.debtToEquity(), new BigDecimal("1.800000"));
		boolean noStrongDeterioration = greaterOrEqual(any(context.revenueGrowth(), context.annualRevenueGrowth(),
				context.quarterlyRevenueGrowth()), new BigDecimal("-0.050000"))
				&& greaterOrEqual(any(context.earningsGrowth(), context.annualEarningsGrowth(),
						context.quarterlyEarningsGrowth()), new BigDecimal("-0.100000"));
		boolean trendAcceptable = context.trendStatus() != TrendStatus.DOWN_TREND
				&& context.trendStatus() != TrendStatus.INSUFFICIENT_DATA;

		score += addReason(reasons, attractiveYield, 16, "dividendos", "DIVIDEND_YIELD",
				"Dividend yield atrativo sem sinal de extremo isolado.");
		score += addReason(reasons, recurringDividends, 18, "dividendos", "DIVIDEND_RECURRENCE",
				"Historico recente possui mais de um evento de dividendo ou JCP.");
		score += addReason(reasons, payoutHealthy, 18, "dividendos", "DIVIDEND_PAYOUT",
				"Payout estimado fica em faixa sustentavel pelo lucro por acao.");
		score += addReason(reasons, profitAndCashCoverage, 22, "fundamentos", "DIVIDEND_COVERAGE",
				"Lucro, caixa operacional e fluxo de caixa livre sustentam os proventos.");
		score += addReason(reasons, debtControlled, 10, "risco", "DIVIDEND_DEBT",
				"Endividamento controlado reduz risco de corte de dividendos por pressao financeira.");
		score += addReason(reasons, noStrongDeterioration, 8, "fundamentos", "DIVIDEND_STABILITY",
				"Receita e lucro nao mostram deterioracao forte para a tese de dividendos.");
		score += addReason(reasons, trendAcceptable, 8, "tecnico", "DIVIDEND_TREND",
				"Tendencia longa nao esta em queda estrutural.");

		boolean mandatory = attractiveYield && recurringDividends && payoutHealthy && profitAndCashCoverage
				&& priceCeiling != null && safetyMargin != null;
		ThesisStatus status = status(context, score, mandatory, priceCeiling, priceAttractive(context.currentPrice(),
				priceCeiling, safetyMargin));

		return draft(context, ThesisType.SUSTAINABLE_DIVIDENDS, status, score, fairPrice, priceCeiling, safetyMargin,
				reasons);
	}

	private ThesisDraft evaluateProfitableGrowth(ThesisMarketContext context) {
		List<ThesisReason> reasons = new ArrayList<>();
		int score = 0;
		BigDecimal eps = earningsPerShare(context);
		BigDecimal selectedGrowth = any(context.annualRevenueGrowth(), context.revenueGrowth(),
				context.quarterlyRevenueGrowth(), context.annualEarningsGrowth(), context.earningsGrowth(),
				context.quarterlyEarningsGrowth(), context.ebitdaGrowth());
		BigDecimal targetPe = greaterOrEqual(selectedGrowth, new BigDecimal("0.200000")) ? new BigDecimal("18.000000")
				: new BigDecimal("15.000000");
		BigDecimal fairPrice = multiply(eps, targetPe);
		BigDecimal priceCeiling = priceCeiling(fairPrice);
		BigDecimal safetyMargin = safetyMargin(fairPrice, context.currentPrice());

		// Crescimento so e aceito quando vem junto de rentabilidade, tendencia longa e valuation ainda compravel.
		boolean revenueGrowth = greaterOrEqual(any(context.annualRevenueGrowth(), context.revenueGrowth(),
				context.quarterlyRevenueGrowth()), new BigDecimal("0.080000"));
		boolean profitGrowth = greaterOrEqual(any(context.annualEarningsGrowth(), context.earningsGrowth(),
				context.quarterlyEarningsGrowth(), context.ebitdaGrowth()), new BigDecimal("0.080000"));
		boolean profitability = positive(eps) && greaterOrEqual(context.profitMargin(), new BigDecimal("0.080000"))
				&& greaterOrEqual(context.roe(), new BigDecimal("0.120000"));
		boolean trendHealthy = context.trendStatus() == TrendStatus.HEALTHY
				&& context.currentPrice() != null && context.sma200() != null
				&& context.currentPrice().compareTo(context.sma200()) >= 0;
		boolean valuationAcceptable = lessOrEqual(context.trailingPe(), new BigDecimal("30.000000"))
				&& (context.enterpriseToEbitda() == null
						|| lessOrEqual(context.enterpriseToEbitda(), new BigDecimal("18.000000")));
		boolean cashAndDebtCompatible = positive(context.operatingCashflow())
				&& (context.debtToEquity() == null || lessOrEqual(context.debtToEquity(), new BigDecimal("2.000000")));

		score += addReason(reasons, revenueGrowth, 18, "fundamentos", "GROWTH_REVENUE",
				"Receita cresce em janela anual, trimestral ou TTM.");
		score += addReason(reasons, profitGrowth, 18, "fundamentos", "GROWTH_PROFIT",
				"Lucro, EBITDA ou caixa acompanha o crescimento da receita.");
		score += addReason(reasons, profitability, 20, "fundamentos", "GROWTH_PROFITABILITY",
				"Crescimento vem com lucro, margem e retorno sobre patrimonio.");
		score += addReason(reasons, trendHealthy, 18, "tecnico", "GROWTH_TREND",
				"Preco acima da media de 200 periodos confirma tendencia longa saudavel.");
		score += addReason(reasons, valuationAcceptable && fairPrice != null, 16, "valuation", "GROWTH_VALUATION",
				"Valuation permanece aceitavel em relacao ao crescimento observado.");
		score += addReason(reasons, cashAndDebtCompatible, 10, "risco", "GROWTH_RISK",
				"Caixa operacional e endividamento sao compativeis com crescimento rentavel.");

		boolean mandatory = revenueGrowth && profitGrowth && profitability && trendHealthy && valuationAcceptable
				&& fairPrice != null && priceCeiling != null && safetyMargin != null;
		ThesisStatus status = status(context, score, mandatory, priceCeiling, priceAttractive(context.currentPrice(),
				priceCeiling, safetyMargin));

		return draft(context, ThesisType.PROFITABLE_GROWTH_HEALTHY_TREND, status, score, fairPrice, priceCeiling,
				safetyMargin, reasons);
	}

	private PositionThesis save(ThesisDraft draft) {
		PositionThesis thesis = positionThesisRepository
				.findByAssetIdAndReferenceDateAndThesisTypeAndRuleVersion(draft.context().asset().getId(),
						draft.context().referenceDate(), draft.thesisType(), RULE_VERSION)
				.orElseGet(() -> new PositionThesis(draft.context().asset(), draft.context().referenceDate(),
						draft.thesisType(), draft.status(), draft.score(), RULE_VERSION));
		thesis.setAsset(draft.context().asset());
		thesis.setReferenceDate(draft.context().referenceDate());
		thesis.setThesisType(draft.thesisType());
		thesis.setStatus(draft.status());
		thesis.setScore(draft.score());
		thesis.setScoreBreakdownJson(json(scoreBreakdown(draft)));
		thesis.setReasonsJson(json(draft.reasons()));
		thesis.setFailedFiltersJson(json(draft.context().failedFilters()));
		thesis.setFairPriceEstimate(draft.fairPrice());
		thesis.setPriceCeiling(draft.priceCeiling());
		thesis.setSafetyMarginPercent(draft.safetyMargin());
		thesis.setStopPrice(stopPrice(draft.context()));
		thesis.setTargetPrice(targetPrice(draft.context(), draft.fairPrice()));
		thesis.setReviewPointsJson(json(reviewPoints(draft)));
		thesis.setRuleVersion(RULE_VERSION);
		PositionThesis saved = positionThesisRepository.save(thesis);
		upsertAllocationPlan(saved, draft);
		return saved;
	}

	private void upsertAllocationPlan(PositionThesis thesis, ThesisDraft draft) {
		if (!positive(draft.context().currentPrice()) || !positive(draft.priceCeiling())) {
			return;
		}
		AllocationPlan plan = allocationPlanRepository.findByThesisId(thesis.getId())
				.orElseGet(() -> new AllocationPlan(thesis));
		BigDecimal targetAllocation = allocationPercent(draft.context());
		BigDecimal maxPositionValue = CAPITAL_BASE.multiply(targetAllocation)
				.divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
		boolean valid = draft.status() == ThesisStatus.APORTE_PLANEJADO || draft.status() == ThesisStatus.OPORTUNIDADE;
		int quantity = valid ? maxPositionValue.divide(draft.context().currentPrice(), 0, RoundingMode.DOWN).intValue()
				: 0;

		// Alocacao sugerida respeita teto por ativo e entrada parcelada; quando preco passa do teto, quantidade e zero.
		plan.setThesis(thesis);
		plan.setCapitalBase(CAPITAL_BASE);
		plan.setTargetAllocationPercent(targetAllocation);
		plan.setMaxAllocationPerAssetPercent(DEFAULT_TARGET_ALLOCATION_PERCENT);
		plan.setMaxPositionValue(maxPositionValue);
		plan.setCurrentPrice(scaleMoney(draft.context().currentPrice()));
		plan.setPriceCeiling(scaleMoney(draft.priceCeiling()));
		plan.setSuggestedQuantity(quantity);
		plan.setRecommendedAction(draft.status().name());
		plan.setFirstTrancheValue(valid ? maxPositionValue.multiply(new BigDecimal("0.500000"))
				.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
		plan.setRemainingPlannedValue(valid ? maxPositionValue.multiply(new BigDecimal("0.500000"))
				.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
		plan.setValid(valid);
		plan.setInvalidReason(valid ? null : "Sem aporte sugerido porque a tese, margem de seguranca ou preco teto nao foram atendidos.");
		allocationPlanRepository.save(plan);
	}

	private ThesisMarketContext loadContext(Asset asset, LocalDate referenceDate) {
		DailyCandle candle = dailyCandleRepository
				.findTopByAssetIdAndTradeDateLessThanEqualOrderByTradeDateDescCollectedAtDesc(asset.getId(),
						referenceDate)
				.orElse(null);
		TechnicalIndicatorSnapshot technical = technicalIndicatorSnapshotRepository
				.findTopByAssetIdAndTradeDateLessThanEqualAndCalculationVersionOrderByTradeDateDescCreatedAtDesc(
						asset.getId(), referenceDate, IndicatorCalculationService.CALCULATION_VERSION)
				.orElse(null);
		FundamentalSnapshot fundamental = fundamentalSnapshotRepository
				.findTopByAssetIdAndReferenceDateLessThanEqualAndPeriodTypeAndSourceAndCalculationVersionOrderByReferenceDateDescCreatedAtDesc(
						asset.getId(), referenceDate, PeriodType.TTM, DERIVED_SOURCE,
						IndicatorCalculationService.CALCULATION_VERSION)
				.orElse(null);
		List<BigDecimal> fcfHistory = fundamentalSnapshotRepository
				.findTop4ByAssetIdAndReferenceDateLessThanEqualAndPeriodTypeAndSourceAndCalculationVersionOrderByReferenceDateDescCreatedAtDesc(
						asset.getId(), referenceDate, PeriodType.TTM, DERIVED_SOURCE,
						IndicatorCalculationService.CALCULATION_VERSION)
				.stream()
				.map(FundamentalSnapshot::getFreeCashflow)
				.toList();
		EliminatoryFilterInput input = buildInput(referenceDate, candle, technical, fundamental, fcfHistory);
		long dividendEvents = dividendEventRepository.countByAssetIdAndEventTypeInAndLastDatePriorBetween(asset.getId(),
				CASH_DIVIDEND_EVENTS, referenceDate.minusYears(3), referenceDate);
		return new ThesisMarketContext(asset, referenceDate, analysisClose(candle), technical, fundamental,
				eliminatoryFilterEvaluator.evaluate(input), dividendEvents);
	}

	private EliminatoryFilterInput buildInput(LocalDate referenceDate, DailyCandle candle,
			TechnicalIndicatorSnapshot technical, FundamentalSnapshot fundamental, List<BigDecimal> fcfHistory) {
		return EliminatoryFilterInput.builder()
				.currentPrice(candle == null ? null : analysisClose(candle))
				.averageFinancialVolume60(technical == null ? null : technical.getAvgVolume60())
				.candleQualityStatus(candle == null ? null : candle.getQualityStatus())
				.fundamentalQualityStatus(fundamental == null ? null : fundamental.getQualityStatus())
				.trendStatus(technical == null ? null : technical.getTrendStatus())
				.sma200(technical == null ? null : technical.getSma200())
				.return12m(technical == null ? null : technical.getReturn12m())
				.historicalVolatility(technical == null ? null : technical.getHistoricalVolatility())
				.recentDrawdown(technical == null ? null : technical.getRecentDrawdown())
				.trailingPe(fundamental == null ? null : fundamental.getTrailingPe())
				.priceToBook(fundamental == null ? null : fundamental.getPriceToBook())
				.enterpriseToEbitda(fundamental == null ? null : fundamental.getEnterpriseToEbitda())
				.earningsPerShare(fundamental == null ? null : fundamental.getEarningsPerShare())
				.profitMargin(fundamental == null ? null : fundamental.getProfitMargin())
				.operatingCashflow(fundamental == null ? null : fundamental.getOperatingCashflow())
				.freeCashflow(fundamental == null ? null : fundamental.getFreeCashflow())
				.freeCashflowHistory(fcfHistory)
				.debtToEquity(fundamental == null ? null : fundamental.getDebtToEquity())
				.netDebt(fundamental == null ? null : fundamental.getNetDebt())
				.revenueGrowth(fundamental == null ? null : fundamental.getRevenueGrowth())
				.earningsGrowth(fundamental == null ? null : fundamental.getEarningsGrowth())
				.annualRevenueGrowth(fundamental == null ? null : fundamental.getAnnualRevenueGrowth())
				.quarterlyRevenueGrowth(fundamental == null ? null : fundamental.getQuarterlyRevenueGrowth())
				.annualEarningsGrowth(fundamental == null ? null : fundamental.getAnnualEarningsGrowth())
				.quarterlyEarningsGrowth(fundamental == null ? null : fundamental.getQuarterlyEarningsGrowth())
				.ebitdaGrowth(fundamental == null ? null : fundamental.getEbitdaGrowth())
				.candleMissing(candle == null)
				.technicalMissing(technical == null)
				.fundamentalMissing(fundamental == null)
				.candleStale(candle != null && !referenceDate.equals(candle.getTradeDate()))
				.technicalStale(technical != null && !referenceDate.equals(technical.getTradeDate()))
				.fundamentalStale(fundamental != null
						&& fundamental.getReferenceDate().isBefore(referenceDate.minusMonths(6)))
				.build();
	}

	private ThesisDraft draft(ThesisMarketContext context, ThesisType thesisType, ThesisStatus status, int score,
			BigDecimal fairPrice, BigDecimal priceCeiling, BigDecimal safetyMargin, List<ThesisReason> reasons) {
		if (!context.failedFilters().isEmpty()) {
			reasons.add(new ThesisReason("dados", "ELIMINATORY_FILTER_BLOCK",
					"Ativo bloqueado pelos filtros eliminatorios; tese nao pode virar recomendacao acionavel."));
			status = blockedStatus(context.failedFilters());
			score = 0;
		}
		if (priceCeiling != null && context.currentPrice() != null) {
			reasons.add(new ThesisReason("valuation", "ENTRY_ZONE",
					"Zona de entrada calculada ate o preco teto de R$ " + priceCeiling.setScale(2,
							RoundingMode.HALF_UP) + "."));
		}
		return new ThesisDraft(context, thesisType, status, Math.min(100, Math.max(0, score)), fairPrice, priceCeiling,
				safetyMargin, reasons);
	}

	private ThesisStatus status(ThesisMarketContext context, int score, boolean mandatory, BigDecimal priceCeiling,
			boolean priceAttractive) {
		if (!context.failedFilters().isEmpty()) {
			return blockedStatus(context.failedFilters());
		}
		if (!mandatory) {
			return score >= 60 ? ThesisStatus.MONITORAR : ThesisStatus.IGNORAR;
		}
		if (priceCeiling != null && context.currentPrice() != null
				&& context.currentPrice().compareTo(priceCeiling.multiply(PRICE_REVIEW_PREMIUM)) > 0) {
			return ThesisStatus.REAVALIAR;
		}
		if (priceAttractive && score >= 80) {
			return ThesisStatus.APORTE_PLANEJADO;
		}
		if (priceAttractive && score >= 70) {
			return ThesisStatus.OPORTUNIDADE;
		}
		return score >= 60 ? ThesisStatus.MONITORAR : ThesisStatus.IGNORAR;
	}

	private ThesisStatus blockedStatus(List<EliminatoryFilterReason> failedFilters) {
		boolean thesisInvalidated = failedFilters.stream()
				.map(EliminatoryFilterReason::code)
				.anyMatch(code -> code == EliminatoryFilterCode.RECURRING_LOSSES
						|| code == EliminatoryFilterCode.PERSISTENT_NEGATIVE_FREE_CASHFLOW
						|| code == EliminatoryFilterCode.STRONG_FUNDAMENTAL_DETERIORATION
						|| code == EliminatoryFilterCode.LONG_TREND_DETERIORATED);
		if (thesisInvalidated) {
			return ThesisStatus.SAIR_DA_TESE;
		}
		boolean exposureRisk = failedFilters.stream()
				.map(EliminatoryFilterReason::code)
				.anyMatch(code -> code == EliminatoryFilterCode.EXCESSIVE_DEBT
						|| code == EliminatoryFilterCode.EXTREME_VALUATION_WITHOUT_GROWTH
						|| code == EliminatoryFilterCode.EXTREME_VOLATILITY);
		if (exposureRisk) {
			return ThesisStatus.REDUZIR_EXPOSICAO;
		}
		return ThesisStatus.IGNORAR;
	}

	private boolean priceAttractive(BigDecimal currentPrice, BigDecimal priceCeiling, BigDecimal safetyMargin) {
		return positive(currentPrice) && positive(priceCeiling) && safetyMargin != null
				&& currentPrice.compareTo(priceCeiling) <= 0 && safetyMargin.compareTo(MIN_SAFETY_MARGIN) >= 0;
	}

	private int addReason(List<ThesisReason> reasons, boolean passed, int points, String category, String code,
			String message) {
		reasons.add(new ThesisReason(category, passed ? code : code + "_FAILED", message));
		return passed ? points : 0;
	}

	private List<ReviewPoint> reviewPoints(ThesisDraft draft) {
		BigDecimal priceReview = draft.priceCeiling() == null ? null
				: draft.priceCeiling().multiply(PRICE_REVIEW_PREMIUM).setScale(6, RoundingMode.HALF_UP);
		String fundamentals = switch (draft.thesisType()) {
			case QUALITY_REASONABLE_PRICE -> "Reavaliar se lucro, margem liquida, caixa operacional ou ROE perderem os limites da tese.";
			case SUSTAINABLE_DIVIDENDS -> "Reavaliar se payout estimado superar 85%, fluxo de caixa livre ficar negativo ou houver corte relevante de dividendos.";
			case PROFITABLE_GROWTH_HEALTHY_TREND -> "Reavaliar se crescimento de receita/lucro cair abaixo de 8% ou se a rentabilidade deixar de sustentar o crescimento.";
		};
		return List.of(new ReviewPoint("preco", priceReview == null ? "Preco teto indisponivel para reavaliacao."
				: "Reavaliar se o preco ficar acima de R$ " + priceReview.setScale(2, RoundingMode.HALF_UP)
						+ ", pois a margem de seguranca fica comprimida."),
				new ReviewPoint("fundamentos", fundamentals),
				new ReviewPoint("tendencia", "Reavaliar se perder a media de 200 periodos ou se a tendencia virar DOWN_TREND."));
	}

	private ScoreBreakdown scoreBreakdown(ThesisDraft draft) {
		return new ScoreBreakdown(draft.score(), draft.thesisType().name(), RULE_VERSION,
				"Score preliminar da Fase 6; a composicao completa sera refinada na Fase 7.");
	}

	private BigDecimal stopPrice(ThesisMarketContext context) {
		if (!positive(context.currentPrice())) {
			return null;
		}
		// Stop inicial usa 15% abaixo do preco atual para explicitar risco antes de haver preco medio do cliente.
		BigDecimal priceStop = context.currentPrice().multiply(BigDecimal.ONE.subtract(STOP_PERCENT));
		BigDecimal trendStop = context.sma200() == null ? null : context.sma200().multiply(new BigDecimal("0.950000"));
		return scaleMoney(maximumPositive(priceStop, trendStop));
	}

	private BigDecimal targetPrice(ThesisMarketContext context, BigDecimal fairPrice) {
		if (!positive(context.currentPrice())) {
			return null;
		}
		// Objetivo inicial usa o valor justo estimado; sem valuation disponivel, aplica retorno alvo conservador de 25%.
		BigDecimal floorTarget = context.currentPrice().multiply(BigDecimal.ONE.add(TARGET_RETURN_FLOOR));
		return scaleMoney(fairPrice == null ? floorTarget : fairPrice);
	}

	private BigDecimal allocationPercent(ThesisMarketContext context) {
		boolean higherRisk = greaterThan(context.historicalVolatility(), new BigDecimal("0.400000"))
				|| greaterThan(context.debtToEquity(), new BigDecimal("1.500000"));
		return higherRisk ? REDUCED_TARGET_ALLOCATION_PERCENT : DEFAULT_TARGET_ALLOCATION_PERCENT;
	}

	private BigDecimal analysisClose(DailyCandle candle) {
		if (candle == null) {
			return null;
		}
		return candle.getAdjustedClosePrice() != null && candle.getAdjustedClosePrice().signum() > 0
				? candle.getAdjustedClosePrice() : candle.getClosePrice();
	}

	private BigDecimal earningsPerShare(ThesisMarketContext context) {
		if (positive(context.earningsPerShare())) {
			return context.earningsPerShare();
		}
		return positive(context.currentPrice()) && positive(context.trailingPe())
				? context.currentPrice().divide(context.trailingPe(), 6, RoundingMode.HALF_UP) : null;
	}

	private BigDecimal bookValuePerShare(ThesisMarketContext context) {
		if (positive(context.bookValue())) {
			return context.bookValue();
		}
		return positive(context.currentPrice()) && positive(context.priceToBook())
				? context.currentPrice().divide(context.priceToBook(), 6, RoundingMode.HALF_UP) : null;
	}

	private BigDecimal priceCeiling(BigDecimal fairPrice) {
		return positive(fairPrice) ? fairPrice.multiply(BigDecimal.ONE.subtract(MIN_SAFETY_MARGIN))
				.setScale(6, RoundingMode.HALF_UP) : null;
	}

	private BigDecimal safetyMargin(BigDecimal fairPrice, BigDecimal currentPrice) {
		return positive(fairPrice) && positive(currentPrice)
				? fairPrice.subtract(currentPrice).divide(fairPrice, 6, RoundingMode.HALF_UP) : null;
	}

	private BigDecimal multiply(BigDecimal left, BigDecimal right) {
		return left == null || right == null ? null : left.multiply(right).setScale(6, RoundingMode.HALF_UP);
	}

	private BigDecimal minimumPositive(BigDecimal... values) {
		BigDecimal selected = null;
		for (BigDecimal value : values) {
			if (positive(value) && (selected == null || value.compareTo(selected) < 0)) {
				selected = value;
			}
		}
		return selected;
	}

	private BigDecimal maximumPositive(BigDecimal... values) {
		BigDecimal selected = null;
		for (BigDecimal value : values) {
			if (positive(value) && (selected == null || value.compareTo(selected) > 0)) {
				selected = value;
			}
		}
		return selected;
	}

	private BigDecimal any(BigDecimal... values) {
		for (BigDecimal value : values) {
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private boolean positive(BigDecimal value) {
		return value != null && value.signum() > 0;
	}

	private boolean nonNegative(BigDecimal value) {
		return value != null && value.signum() >= 0;
	}

	private boolean lessOrEqual(BigDecimal value, BigDecimal limit) {
		return value != null && value.compareTo(limit) <= 0;
	}

	private boolean greaterThan(BigDecimal value, BigDecimal limit) {
		return value != null && limit != null && value.compareTo(limit) > 0;
	}

	private boolean greaterOrEqual(BigDecimal value, BigDecimal limit) {
		return value != null && limit != null && value.compareTo(limit) >= 0;
	}

	private BigDecimal scaleMoney(BigDecimal value) {
		return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not serialize thesis audit payload.", ex);
		}
	}

	private record ThesisMarketContext(Asset asset, LocalDate referenceDate, BigDecimal currentPrice,
			TechnicalIndicatorSnapshot technical, FundamentalSnapshot fundamental,
			List<EliminatoryFilterReason> failedFilters, long cashDividendEventsLastThreeYears) {

		BigDecimal trailingPe() {
			return fundamental == null ? null : fundamental.getTrailingPe();
		}

		BigDecimal priceToBook() {
			return fundamental == null ? null : fundamental.getPriceToBook();
		}

		BigDecimal enterpriseToEbitda() {
			return fundamental == null ? null : fundamental.getEnterpriseToEbitda();
		}

		BigDecimal earningsPerShare() {
			return fundamental == null ? null : fundamental.getEarningsPerShare();
		}

		BigDecimal bookValue() {
			return fundamental == null ? null : fundamental.getBookValue();
		}

		BigDecimal dividendYield() {
			return fundamental == null ? null : fundamental.getDividendYield();
		}

		BigDecimal profitMargin() {
			return fundamental == null ? null : fundamental.getProfitMargin();
		}

		BigDecimal grossMargin() {
			return fundamental == null ? null : fundamental.getGrossMargin();
		}

		BigDecimal ebitdaMargin() {
			return fundamental == null ? null : fundamental.getEbitdaMargin();
		}

		BigDecimal operatingMargin() {
			return fundamental == null ? null : fundamental.getOperatingMargin();
		}

		BigDecimal roe() {
			return fundamental == null ? null : fundamental.getRoe();
		}

		BigDecimal roa() {
			return fundamental == null ? null : fundamental.getRoa();
		}

		BigDecimal debtToEquity() {
			return fundamental == null ? null : fundamental.getDebtToEquity();
		}

		BigDecimal revenueGrowth() {
			return fundamental == null ? null : fundamental.getRevenueGrowth();
		}

		BigDecimal earningsGrowth() {
			return fundamental == null ? null : fundamental.getEarningsGrowth();
		}

		BigDecimal annualRevenueGrowth() {
			return fundamental == null ? null : fundamental.getAnnualRevenueGrowth();
		}

		BigDecimal quarterlyRevenueGrowth() {
			return fundamental == null ? null : fundamental.getQuarterlyRevenueGrowth();
		}

		BigDecimal annualEarningsGrowth() {
			return fundamental == null ? null : fundamental.getAnnualEarningsGrowth();
		}

		BigDecimal quarterlyEarningsGrowth() {
			return fundamental == null ? null : fundamental.getQuarterlyEarningsGrowth();
		}

		BigDecimal ebitdaGrowth() {
			return fundamental == null ? null : fundamental.getEbitdaGrowth();
		}

		BigDecimal freeCashflow() {
			return fundamental == null ? null : fundamental.getFreeCashflow();
		}

		BigDecimal operatingCashflow() {
			return fundamental == null ? null : fundamental.getOperatingCashflow();
		}

		BigDecimal sma200() {
			return technical == null ? null : technical.getSma200();
		}

		BigDecimal historicalVolatility() {
			return technical == null ? null : technical.getHistoricalVolatility();
		}

		TrendStatus trendStatus() {
			return technical == null ? TrendStatus.INSUFFICIENT_DATA : technical.getTrendStatus();
		}

	}

	private record ThesisDraft(ThesisMarketContext context, ThesisType thesisType, ThesisStatus status, int score,
			BigDecimal fairPrice, BigDecimal priceCeiling, BigDecimal safetyMargin, List<ThesisReason> reasons) {
	}

	private record ThesisReason(String category, String code, String message) {
	}

	private record ReviewPoint(String type, String message) {
	}

	private record ScoreBreakdown(int preliminaryScore, String thesisType, String ruleVersion, String note) {
	}
}
