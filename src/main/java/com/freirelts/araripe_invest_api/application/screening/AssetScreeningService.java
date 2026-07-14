package com.freirelts.araripe_invest_api.application.screening;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.indicators.IndicatorCalculationService;
import com.freirelts.araripe_invest_api.application.thesis.PositionThesisGenerationService;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.marketdata.DailyCandle;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.marketdata.TechnicalIndicatorSnapshot;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.screening.AssetScreeningResult;
import com.freirelts.araripe_invest_api.domain.screening.ScreeningStatus;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetScreeningResultRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DailyCandleRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.TechnicalIndicatorSnapshotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class AssetScreeningService {

	public static final String RULE_VERSION = "phase5-v1";
	private static final String DERIVED_SOURCE = "araripe-indicators";

	private final AssetRepository assetRepository;
	private final DailyCandleRepository dailyCandleRepository;
	private final TechnicalIndicatorSnapshotRepository technicalIndicatorSnapshotRepository;
	private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
	private final AssetScreeningResultRepository assetScreeningResultRepository;
	private final PositionThesisRepository positionThesisRepository;
	private final EliminatoryFilterEvaluator evaluator;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public AssetScreeningService(AssetRepository assetRepository, DailyCandleRepository dailyCandleRepository,
			TechnicalIndicatorSnapshotRepository technicalIndicatorSnapshotRepository,
			FundamentalSnapshotRepository fundamentalSnapshotRepository,
			AssetScreeningResultRepository assetScreeningResultRepository,
			PositionThesisRepository positionThesisRepository,
			EliminatoryFilterEvaluator evaluator) {
		this.assetRepository = assetRepository;
		this.dailyCandleRepository = dailyCandleRepository;
		this.technicalIndicatorSnapshotRepository = technicalIndicatorSnapshotRepository;
		this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
		this.assetScreeningResultRepository = assetScreeningResultRepository;
		this.positionThesisRepository = positionThesisRepository;
		this.evaluator = evaluator;
	}

	@Transactional
	public List<AssetScreeningDiagnostic> screenActiveAssets(LocalDate referenceDate) {
		return assetRepository.findByActiveTrueOrderBySymbolAsc().stream()
				.map(asset -> screenAsset(asset, referenceDate))
				.toList();
	}

	@Transactional
	public AssetScreeningDiagnostic diagnoseAsset(String symbol, LocalDate referenceDate) {
		Asset asset = assetRepository.findBySymbolIgnoreCase(symbol.trim().toUpperCase(Locale.ROOT))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found."));
		return screenAsset(asset, referenceDate);
	}

	private AssetScreeningDiagnostic screenAsset(Asset asset, LocalDate referenceDate) {
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
		List<EliminatoryFilterReason> failedFilters = evaluator.evaluate(input);
		ScreeningStatus status = failedFilters.isEmpty() ? ScreeningStatus.ELIGIBLE : ScreeningStatus.ELIMINATED;

		AssetScreeningResult result = assetScreeningResultRepository
				.findByAssetIdAndReferenceDateAndRuleVersion(asset.getId(), referenceDate, RULE_VERSION)
				.orElseGet(() -> new AssetScreeningResult(asset, referenceDate, RULE_VERSION));
		result.setAsset(asset);
		result.setReferenceDate(referenceDate);
		result.setRuleVersion(RULE_VERSION);
		result.setStatus(status);
		result.setFailedFiltersJson(json(failedFilters));
		result.setUpdatedAt(Instant.now());
		assetScreeningResultRepository.save(result);

		return new AssetScreeningDiagnostic(asset.getId(), asset.getSymbol(), referenceDate, status, RULE_VERSION,
				failedFilters, thesisScores(asset, referenceDate));
	}

	private List<PositionThesisScoreDiagnostic> thesisScores(Asset asset, LocalDate referenceDate) {
		return positionThesisRepository
				.findByAssetIdAndReferenceDateAndRuleVersionOrderByScoreDesc(asset.getId(), referenceDate,
						PositionThesisGenerationService.RULE_VERSION)
				.stream()
				.map(this::toScoreDiagnostic)
				.toList();
	}

	private PositionThesisScoreDiagnostic toScoreDiagnostic(PositionThesis thesis) {
		return new PositionThesisScoreDiagnostic(thesis.getId(), thesis.getThesisType(), thesis.getStatus(),
				thesis.getScore(), thesis.getRuleVersion(), jsonNode(thesis.getScoreBreakdownJson()));
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
				.candleStale(candle != null
						&& DataFreshnessPolicy.marketDataStale(referenceDate, candle.getTradeDate()))
				.technicalStale(technical != null
						&& DataFreshnessPolicy.marketDataStale(referenceDate, technical.getTradeDate()))
				.fundamentalStale(fundamental != null
						&& DataFreshnessPolicy.fundamentalDataStale(referenceDate, fundamental.getReferenceDate()))
				.build();
	}

	private BigDecimal analysisClose(DailyCandle candle) {
		return candle.getAdjustedClosePrice() != null && candle.getAdjustedClosePrice().signum() > 0
				? candle.getAdjustedClosePrice() : candle.getClosePrice();
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not serialize screening filter audit payload.", ex);
		}
	}

	private JsonNode jsonNode(String value) {
		try {
			return objectMapper.readTree(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not deserialize thesis score audit payload.", ex);
		}
	}
}
