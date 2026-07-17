package com.freirelts.araripe_invest_api.application.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.indicators.IndicatorCalculationService;
import com.freirelts.araripe_invest_api.application.thesis.PositionThesisGenerationService;
import com.freirelts.araripe_invest_api.domain.ai.AiContextAnalysis;
import com.freirelts.araripe_invest_api.domain.ai.AiProcessingStatus;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import com.freirelts.araripe_invest_api.domain.alerts.InformationalAlert;
import com.freirelts.araripe_invest_api.domain.alerts.InformationalEventType;
import com.freirelts.araripe_invest_api.domain.alerts.Severity;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.assets.AssetType;
import com.freirelts.araripe_invest_api.domain.assets.Market;
import com.freirelts.araripe_invest_api.domain.jobs.JobName;
import com.freirelts.araripe_invest_api.domain.jobs.JobRun;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.DataCollectionCategory;
import com.freirelts.araripe_invest_api.domain.marketdata.DataCollectionRecord;
import com.freirelts.araripe_invest_api.domain.marketdata.DataCollectionStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.DividendEvent;
import com.freirelts.araripe_invest_api.domain.marketdata.FinancialStatementSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.marketdata.StatementType;
import com.freirelts.araripe_invest_api.domain.marketdata.TechnicalIndicatorSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationStatus;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AiContextAnalysisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DataCollectionRecordRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DividendEventRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FinancialStatementSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.InformationalAlertRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.JobRunRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.TechnicalIndicatorSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ApiQueryService {

	private static final String DERIVED_FUNDAMENTAL_SOURCE = "araripe-indicators";
	private static final String COLLECTOR_FUNDAMENTAL_SOURCE = "brapi";
	private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final AssetRepository assetRepository;
	private final PositionThesisRepository thesisRepository;
	private final TechnicalIndicatorSnapshotRepository technicalIndicatorRepository;
	private final FundamentalSnapshotRepository fundamentalRepository;
	private final FinancialStatementSnapshotRepository financialStatementRepository;
	private final DividendEventRepository dividendEventRepository;
	private final InformationalAlertRepository informationalAlertRepository;
	private final DataCollectionRecordRepository dataCollectionRecordRepository;
	private final JobRunRepository jobRunRepository;
	private final UserRepository userRepository;
	private final AiContextAnalysisRepository aiContextAnalysisRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public ApiQueryService(AssetRepository assetRepository, PositionThesisRepository thesisRepository,
			TechnicalIndicatorSnapshotRepository technicalIndicatorRepository,
			FundamentalSnapshotRepository fundamentalRepository,
			FinancialStatementSnapshotRepository financialStatementRepository,
			DividendEventRepository dividendEventRepository, InformationalAlertRepository informationalAlertRepository,
			DataCollectionRecordRepository dataCollectionRecordRepository, JobRunRepository jobRunRepository,
			UserRepository userRepository,
			AiContextAnalysisRepository aiContextAnalysisRepository) {
		this.assetRepository = assetRepository;
		this.thesisRepository = thesisRepository;
		this.technicalIndicatorRepository = technicalIndicatorRepository;
		this.fundamentalRepository = fundamentalRepository;
		this.financialStatementRepository = financialStatementRepository;
		this.dividendEventRepository = dividendEventRepository;
		this.informationalAlertRepository = informationalAlertRepository;
		this.dataCollectionRecordRepository = dataCollectionRecordRepository;
		this.jobRunRepository = jobRunRepository;
		this.userRepository = userRepository;
		this.aiContextAnalysisRepository = aiContextAnalysisRepository;
	}

	@Transactional(readOnly = true)
	public List<AssetResponse> listActiveAssets() {
		return assetRepository.findByActiveTrueOrderBySymbolAsc().stream()
				.map(AssetResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ThesisSummaryResponse> screener(LocalDate referenceDate, ScreenerSortBy sortBy,
			SortDirection direction) {
		ScreenerSortBy effectiveSort = sortBy == null ? ScreenerSortBy.ASSET_SYMBOL : sortBy;
		SortDirection effectiveDirection = direction == null ? SortDirection.ASC : direction;
		Comparator<PositionThesis> comparator = comparator(effectiveSort);
		if (effectiveDirection == SortDirection.DESC) {
			comparator = comparator.reversed();
		}
		LocalDate effectiveReferenceDate = effectiveScreenerDate(referenceDate);
		return thesisRepository
				.findByReferenceDateAndRuleVersion(effectiveReferenceDate, PositionThesisGenerationService.RULE_VERSION)
				.stream()
				.sorted(comparator.thenComparing(thesis -> thesis.getId().toString()))
				.map(this::thesisSummary)
				.toList();
	}

	@Transactional(readOnly = true)
	public ThesisDetailResponse thesisDetail(UUID thesisId) {
		PositionThesis thesis = thesisRepository.findById(thesisId)
				.orElseThrow(() -> notFound("Thesis not found."));
		TechnicalIndicatorSnapshot technical = technicalIndicatorRepository
				.findTopByAssetIdAndTradeDateLessThanEqualAndCalculationVersionOrderByTradeDateDescCreatedAtDesc(
						thesis.getAsset().getId(), thesis.getReferenceDate(), IndicatorCalculationService.CALCULATION_VERSION)
				.orElse(null);
		FundamentalSnapshot fundamental = latestFundamental(thesis.getAsset().getId(), thesis.getReferenceDate());
		AiContextAnalysis aiContext = aiContextAnalysisRepository
				.findTopByThesisIdAndReferenceDateLessThanEqualAndValidationStatusOrderByReferenceDateDescCreatedAtDesc(
						thesis.getId(), thesis.getReferenceDate(), AiValidationStatus.VALID)
				.orElse(null);
		return new ThesisDetailResponse(thesisSummary(thesis), map(thesis.getScoreBreakdownJson()),
				list(thesis.getReasonsJson()), list(thesis.getFailedFiltersJson()), list(thesis.getReviewPointsJson()),
				technical == null ? null : TechnicalIndicatorResponse.from(technical),
				fundamental == null ? null : FundamentalResponse.from(fundamental),
				aiContext == null ? null : AiContextAnalysisSummaryResponse.from(aiContext, this::jsonValue),
				"Conteudo educacional e informativo; nao indica compra, venda, manutencao, aumento, reducao, alocacao ou encerramento de posicao.");
	}

	@Transactional(readOnly = true)
	public List<ThesisSummaryResponse> thesisHistory(String symbol, LocalDate from, LocalDate to) {
		Asset asset = findAsset(symbol);
		LocalDate effectiveTo = effectiveDate(to);
		LocalDate effectiveFrom = from == null ? effectiveTo.minusMonths(6) : from;
		return thesisRepository.findByAssetIdAndReferenceDateBetweenOrderByReferenceDateDescScoreDesc(asset.getId(),
						effectiveFrom, effectiveTo)
				.stream()
				.map(this::thesisSummary)
				.toList();
	}

	@Transactional(readOnly = true)
	public AssetFundamentalsResponse assetFundamentals(String symbol, LocalDate date) {
		Asset asset = findAsset(symbol);
		LocalDate referenceDate = effectiveDate(date);
		FundamentalSnapshot fundamental = latestFundamental(asset.getId(), referenceDate);
		TechnicalIndicatorSnapshot technical = technicalIndicatorRepository
				.findTopByAssetIdAndTradeDateLessThanEqualAndCalculationVersionOrderByTradeDateDescCreatedAtDesc(
						asset.getId(), referenceDate, IndicatorCalculationService.CALCULATION_VERSION)
				.orElse(null);
		List<FinancialStatementResponse> statements = List.of(StatementType.BALANCE_SHEET, StatementType.INCOME_STATEMENT,
						StatementType.CASH_FLOW)
				.stream()
				.flatMap(type -> financialStatementRepository
						.findByAssetIdAndStatementTypeAndPeriodTypeAndEndDateLessThanEqualAndQualityStatusOrderByEndDateDesc(
								asset.getId(), type, PeriodType.QUARTERLY, referenceDate, DataQualityStatus.VALID)
						.stream()
						.limit(1))
				.map(statement -> new FinancialStatementResponse(statement.getId(), statement.getStatementType(),
						statement.getPeriodType(), statement.getEndDate(), statement.getSource(),
						statement.getQualityStatus(), map(statement.getPayloadJson())))
				.toList();
		return new AssetFundamentalsResponse(AssetResponse.from(asset),
				fundamental == null ? null : FundamentalResponse.from(fundamental),
				technical == null ? null : TechnicalIndicatorResponse.from(technical),
				statements, dividendEventRepository.findTop20ByAssetIdOrderByLastDatePriorDescPaymentDateDesc(asset.getId())
						.stream()
						.map(DividendResponse::from)
						.toList());
	}

	@Transactional(readOnly = true)
	public List<InformationalAlertResponse> alerts(UUID userId, LocalDate from, LocalDate to) {
		requireCustomer(userId);
		LocalDate effectiveTo = effectiveAlertDate(to);
		LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(30) : from;
		return informationalAlertRepository
				.findByUserIdAndReferenceDateBetweenOrderByReferenceDateDescCreatedAtDesc(userId, effectiveFrom,
						effectiveTo)
				.stream()
				.map(alert -> InformationalAlertResponse.from(alert, this::map))
				.toList();
	}

	@Transactional
	public InformationalAlertResponse alert(UUID userId, UUID alertId) {
		requireCustomer(userId);
		InformationalAlert alert = informationalAlertRepository.findByIdAndUserId(alertId, userId)
				.orElseThrow(() -> notFound("Alert not found."));
		return InformationalAlertResponse.from(alert, this::map);
	}

	@Transactional
	public InformationalAlertResponse markAlertRead(UUID userId, UUID alertId) {
		requireCustomer(userId);
		InformationalAlert event = informationalAlertRepository.findByIdAndUserId(alertId, userId)
				.orElseThrow(() -> notFound("Alert not found."));
		if (event.getReadAt() == null) {
			event.setReadAt(Instant.now());
		}
		return InformationalAlertResponse.from(informationalAlertRepository.saveAndFlush(event), this::map);
	}

	@Transactional(readOnly = true)
	public JobStatusResponse jobStatus(LocalDate date) {
		LocalDate referenceDate = effectiveJobStatusDate(date);
		List<DataCollectionRecordResponse> records = dataCollectionRecordRepository
				.findByReferenceDateOrderByCreatedAtDesc(referenceDate)
				.stream()
				.map(DataCollectionRecordResponse::from)
				.toList();
		List<JobRunStatusResponse> runs = jobRunRepository.findByReferenceDateOrderByStartedAtDesc(referenceDate)
				.stream()
				.map(run -> new JobRunStatusResponse(run.getId(), run.getJobName().name(), run.getStatus(),
						run.getStartedAt(), run.getCompletedAt(), run.getErrorMessage()))
				.toList();
		return new JobStatusResponse(referenceDate, records, runs);
	}

	private ThesisSummaryResponse thesisSummary(PositionThesis thesis) {
		Asset asset = thesis.getAsset();
		return new ThesisSummaryResponse(thesis.getId(), AssetResponse.from(asset), thesis.getReferenceDate(),
				thesis.getThesisType(), thesis.getStatus(), thesis.getScore(), "Aderencia a criterios do estudo",
				thesis.getPriceCeiling(),
				thesis.getFairPriceEstimate(), percent(thesis.getSafetyMarginPercent()), list(thesis.getReasonsJson()),
				methodology(), sources(), thesis.getReferenceDate(), limitations(), thesis.getRuleVersion(),
				thesis.getCreatedAt());
	}

	private Comparator<PositionThesis> comparator(ScreenerSortBy sortBy) {
		return switch (sortBy) {
			case ASSET_SYMBOL -> Comparator.comparing(thesis -> thesis.getAsset().getSymbol(),
					String.CASE_INSENSITIVE_ORDER);
			case UPDATED_AT -> Comparator.comparing(PositionThesis::getCreatedAt);
			case CRITERIA_ADHERENCE_SCORE -> Comparator.comparingInt(PositionThesis::getScore);
			case SAFETY_MARGIN -> Comparator.comparing(PositionThesis::getSafetyMarginPercent,
					Comparator.nullsLast(BigDecimal::compareTo));
			case STATUS -> Comparator.comparing(thesis -> thesis.getStatus().name());
			case STUDY_TYPE -> Comparator.comparing(thesis -> thesis.getThesisType().name());
		};
	}

	private String methodology() {
		return "Pontuacao deterministica de 0 a 100 que mede aderencia aos criterios do modelo de estudo, "
				+ "com filtros obrigatorios, valuation educacional, risco analitico e trilha de auditoria.";
	}

	private List<String> sources() {
		return List.of(COLLECTOR_FUNDAMENTAL_SOURCE, DERIVED_FUNDAMENTAL_SOURCE, "araripe-rules");
	}

	private List<String> limitations() {
		return List.of("Conteudo educacional e informativo.",
				"Nao recomenda compra, venda, manutencao, aumento, reducao, alocacao ou encerramento de posicao.",
				"Dados incompletos, desatualizados ou inconsistentes bloqueiam modelos de estudo e alertas informativos.");
	}

	private FundamentalSnapshot latestFundamental(UUID assetId, LocalDate referenceDate) {
		return fundamentalRepository
				.findTopByAssetIdAndReferenceDateLessThanEqualAndPeriodTypeAndSourceAndCalculationVersionOrderByReferenceDateDescCreatedAtDesc(
						assetId, referenceDate, PeriodType.TTM, DERIVED_FUNDAMENTAL_SOURCE,
						IndicatorCalculationService.CALCULATION_VERSION)
				.or(() -> fundamentalRepository
						.findTopByAssetIdAndReferenceDateLessThanEqualAndPeriodTypeAndSourceOrderByReferenceDateDescCreatedAtDesc(
								assetId, referenceDate, PeriodType.TTM, COLLECTOR_FUNDAMENTAL_SOURCE))
				.orElse(null);
	}

	private User requireCustomer(UUID userId) {
		return userRepository.findById(userId)
				.filter(user -> user.hasValidAuthenticatedAccess() && user.hasRole(UserRoleType.CUSTOMER))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user."));
	}

	private Asset findAsset(String symbol) {
		return assetRepository.findBySymbolIgnoreCase(symbol)
				.orElseThrow(() -> notFound("Asset not found."));
	}

	private BigDecimal percent(BigDecimal ratio) {
		return ratio == null ? null : ratio.multiply(new BigDecimal("100.000000")).setScale(6, RoundingMode.HALF_UP);
	}

	private LocalDate effectiveDate(LocalDate date) {
		return date == null ? LocalDate.now() : date;
	}

	private LocalDate effectiveScreenerDate(LocalDate date) {
		return latestSuccessfulReferenceDate(date, List.of(JobName.SCREENER, JobName.DAILY_OPERATIONAL_FLOW));
	}

	private LocalDate effectiveAlertDate(LocalDate date) {
		return latestSuccessfulReferenceDate(date, List.of(JobName.PORTFOLIO_SCAN, JobName.DAILY_OPERATIONAL_FLOW));
	}

	private LocalDate effectiveJobStatusDate(LocalDate date) {
		return latestSuccessfulReferenceDate(date, List.of(JobName.DAILY_OPERATIONAL_FLOW, JobName.SCREENER,
				JobName.PORTFOLIO_SCAN, JobName.INDICATOR_CALCULATION, JobName.DAILY_MARKET_DATA_COLLECTION));
	}

	private LocalDate latestSuccessfulReferenceDate(LocalDate explicitDate, List<JobName> jobNames) {
		if (explicitDate != null) {
			return explicitDate;
		}
		return jobRunRepository
				.findTopByJobNameInAndStatusInOrderByReferenceDateDescCompletedAtDescStartedAtDesc(jobNames,
						List.of(JobRunStatus.SUCCESS))
				.map(JobRun::getReferenceDate)
				.orElseGet(LocalDate::now);
	}

	private ResponseStatusException notFound(String message) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
	}

	private List<Object> list(String json) {
		try {
			return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json, LIST_TYPE);
		}
		catch (JsonProcessingException ex) {
			return List.of(json);
		}
	}

	private Map<String, Object> map(String json) {
		try {
			return objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, MAP_TYPE);
		}
		catch (JsonProcessingException ex) {
			return Map.of("raw", json);
		}
	}

	private Object jsonValue(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readValue(json, Object.class);
		}
		catch (JsonProcessingException ex) {
			return json;
		}
	}

	public record AssetResponse(UUID id, String symbol, String name, String sector, String industry, Market market,
			AssetType assetType, boolean active, String monitoringReason) {
		static AssetResponse from(Asset asset) {
			return new AssetResponse(asset.getId(), asset.getSymbol(), asset.getName(), asset.getSector(),
					asset.getIndustry(), asset.getMarket(), asset.getAssetType(), asset.isActive(),
					asset.getMonitoringReason());
		}
	}

	public enum ScreenerSortBy {
		ASSET_SYMBOL,
		UPDATED_AT,
		CRITERIA_ADHERENCE_SCORE,
		SAFETY_MARGIN,
		STATUS,
		STUDY_TYPE
	}

	public enum SortDirection {
		ASC,
		DESC
	}

	public record ThesisSummaryResponse(UUID id, AssetResponse asset, LocalDate referenceDate, ThesisType thesisType,
			ThesisStatus status, int criteriaAdherenceScore, String scoreLabel, BigDecimal studyPriceReference,
			BigDecimal fairPriceEstimate, BigDecimal safetyMarginPercent, List<Object> reasons, String methodology,
			List<String> sources, LocalDate dataReferenceDate, List<String> limitations, String ruleVersion,
			Instant createdAt) {
	}

	public record ThesisDetailResponse(ThesisSummaryResponse thesis, Map<String, Object> scoreBreakdown,
			List<Object> reasons, List<Object> failedFilters, List<Object> reviewPoints,
			TechnicalIndicatorResponse technicalIndicators, FundamentalResponse fundamentals,
			AiContextAnalysisSummaryResponse aiContext, String riskNotice) {
	}

	public record AiContextAnalysisSummaryResponse(UUID analysisId, String model, String promptVersion,
			Object output, Object sources, AiValidationStatus validationStatus, AiProcessingStatus processingStatus,
			Instant createdAt, Instant finishedAt) {
		static AiContextAnalysisSummaryResponse from(AiContextAnalysis analysis,
				java.util.function.Function<String, Object> jsonReader) {
			return new AiContextAnalysisSummaryResponse(analysis.getId(), analysis.getModel(),
					analysis.getPromptVersion(), jsonReader.apply(analysis.getOutputJson()),
					jsonReader.apply(analysis.getSourcesJson()), analysis.getValidationStatus(),
					analysis.getProcessingStatus(), analysis.getCreatedAt(), analysis.getFinishedAt());
		}
	}

	public record TechnicalIndicatorResponse(LocalDate tradeDate, BigDecimal sma50, BigDecimal sma100, BigDecimal sma200,
			BigDecimal ema50, BigDecimal ema100, BigDecimal ema200, BigDecimal return6m, BigDecimal return12m,
			BigDecimal avgVolume60, BigDecimal high52w, BigDecimal low52w, BigDecimal historicalVolatility,
			BigDecimal recentDrawdown, TrendStatus trendStatus, String calculationVersion) {
		static TechnicalIndicatorResponse from(TechnicalIndicatorSnapshot snapshot) {
			return new TechnicalIndicatorResponse(snapshot.getTradeDate(), snapshot.getSma50(), snapshot.getSma100(),
					snapshot.getSma200(), snapshot.getEma50(), snapshot.getEma100(), snapshot.getEma200(),
					snapshot.getReturn6m(), snapshot.getReturn12m(), snapshot.getAvgVolume60(), snapshot.getHigh52w(),
					snapshot.getLow52w(), snapshot.getHistoricalVolatility(), snapshot.getRecentDrawdown(),
					snapshot.getTrendStatus(), snapshot.getCalculationVersion());
		}
	}

	public record FundamentalResponse(UUID id, LocalDate referenceDate, LocalDate mostRecentQuarter,
			PeriodType periodType, String source, BigDecimal marketCap, BigDecimal enterpriseValue,
			BigDecimal trailingPe, BigDecimal priceToBook, BigDecimal enterpriseToRevenue,
			BigDecimal enterpriseToEbitda, BigDecimal forwardPe, BigDecimal pegRatio,
			BigDecimal earningsPerShare, BigDecimal netIncomeToCommon, BigDecimal bookValue,
			BigDecimal dividendYield, BigDecimal lastDividendValue, LocalDate lastDividendDate,
			BigDecimal beta, BigDecimal floatShares, BigDecimal sharesOutstanding,
			BigDecimal fiftyTwoWeekChange, BigDecimal totalCash, BigDecimal totalCashPerShare,
			BigDecimal ebitda, BigDecimal totalDebt, BigDecimal quickRatio, BigDecimal currentRatio,
			BigDecimal totalRevenue, BigDecimal grossProfits, BigDecimal profitMargin, BigDecimal grossMargin,
			BigDecimal ebitdaMargin, BigDecimal operatingMargin, BigDecimal roe, BigDecimal roa,
			BigDecimal debtToEquity, BigDecimal revenueGrowth, BigDecimal earningsGrowth,
			BigDecimal annualRevenueGrowth, BigDecimal quarterlyRevenueGrowth, BigDecimal annualEarningsGrowth,
			BigDecimal quarterlyEarningsGrowth, BigDecimal ebitdaGrowth, BigDecimal freeCashflow,
			BigDecimal operatingCashflow, BigDecimal netDebt, DataQualityStatus qualityStatus,
			String calculationVersion) {
		static FundamentalResponse from(FundamentalSnapshot snapshot) {
			return new FundamentalResponse(snapshot.getId(), snapshot.getReferenceDate(),
					snapshot.getMostRecentQuarter(), snapshot.getPeriodType(), snapshot.getSource(),
					snapshot.getMarketCap(), snapshot.getEnterpriseValue(), snapshot.getTrailingPe(),
					snapshot.getPriceToBook(), snapshot.getEnterpriseToRevenue(), snapshot.getEnterpriseToEbitda(),
					snapshot.getForwardPe(), snapshot.getPegRatio(), snapshot.getEarningsPerShare(),
					snapshot.getNetIncomeToCommon(), snapshot.getBookValue(), snapshot.getDividendYield(),
					snapshot.getLastDividendValue(), snapshot.getLastDividendDate(), snapshot.getBeta(),
					snapshot.getFloatShares(), snapshot.getSharesOutstanding(), snapshot.getFiftyTwoWeekChange(),
					snapshot.getTotalCash(), snapshot.getTotalCashPerShare(), snapshot.getEbitda(),
					snapshot.getTotalDebt(), snapshot.getQuickRatio(), snapshot.getCurrentRatio(),
					snapshot.getTotalRevenue(), snapshot.getGrossProfits(), snapshot.getProfitMargin(),
					snapshot.getGrossMargin(), snapshot.getEbitdaMargin(), snapshot.getOperatingMargin(),
					snapshot.getRoe(), snapshot.getRoa(), snapshot.getDebtToEquity(), snapshot.getRevenueGrowth(),
					snapshot.getEarningsGrowth(), snapshot.getAnnualRevenueGrowth(), snapshot.getQuarterlyRevenueGrowth(),
					snapshot.getAnnualEarningsGrowth(), snapshot.getQuarterlyEarningsGrowth(),
					snapshot.getEbitdaGrowth(), snapshot.getFreeCashflow(), snapshot.getOperatingCashflow(),
					snapshot.getNetDebt(), snapshot.getQualityStatus(), snapshot.getCalculationVersion());
		}
	}

	public record AssetFundamentalsResponse(AssetResponse asset, FundamentalResponse fundamentals,
			TechnicalIndicatorResponse technicalIndicators, List<FinancialStatementResponse> financialStatements,
			List<DividendResponse> dividends) {
	}

	public record FinancialStatementResponse(UUID id, StatementType statementType, PeriodType periodType,
			LocalDate endDate, String source, DataQualityStatus qualityStatus, Map<String, Object> payload) {
	}

	public record DividendResponse(UUID id, String eventType, LocalDate lastDatePrior, LocalDate paymentDate,
			LocalDate approvedOn, BigDecimal rate, BigDecimal factor, String label, String source) {
		static DividendResponse from(DividendEvent event) {
			return new DividendResponse(event.getId(), event.getEventType().name(), event.getLastDatePrior(),
					event.getPaymentDate(), event.getApprovedOn(), event.getRate(), event.getFactor(), event.getLabel(),
					event.getSource());
		}
	}

	public record InformationalAlertResponse(UUID id, UUID watchItemId, UUID positionId, AssetResponse asset,
			UUID studyModelId, UUID currentStudyModelSnapshotId, LocalDate referenceDate,
			NotificationChannel notificationChannel, InformationalEventType eventType, Severity severity, String title,
			String summary, Map<String, Object> evidence, String source, NotificationStatus notificationStatus,
			String notificationProvider, String notificationProviderMessageId, int notificationAttemptCount,
			String notificationLastError, String ruleVersion, Instant createdAt, Instant notificationSentAt,
			Instant readAt, String regulatoryNotice) {
		static InformationalAlertResponse from(InformationalAlert alert,
				java.util.function.Function<String, Map<String, Object>> jsonReader) {
			return new InformationalAlertResponse(alert.getId(),
					alert.getWatchItem() == null ? null : alert.getWatchItem().getId(),
					alert.getSourcePosition() == null ? null : alert.getSourcePosition().getId(),
					AssetResponse.from(alert.getAsset()), alert.getStudyModel() == null ? null : alert.getStudyModel().getId(),
					alert.getCurrentStudyModelSnapshot() == null ? null : alert.getCurrentStudyModelSnapshot().getId(),
					alert.getReferenceDate(), alert.getNotificationChannel(), alert.getEventType(), alert.getSeverity(),
					alert.getTitle(), alert.getSummary(), jsonReader.apply(alert.getEvidenceJson()), alert.getSource(),
					alert.getNotificationStatus(), alert.getNotificationProvider(),
					alert.getNotificationProviderMessageId(), alert.getNotificationAttemptCount(),
					alert.getNotificationLastError(), alert.getRuleVersion(), alert.getCreatedAt(),
					alert.getNotificationSentAt(), alert.getReadAt(),
					"Alerta informativo factual; nao recomenda compra, venda, manutencao, aumento, reducao, alocacao ou encerramento.");
		}
	}

	public record JobStatusResponse(LocalDate referenceDate, List<DataCollectionRecordResponse> collectionRecords,
			List<JobRunStatusResponse> jobRuns) {
	}

	public record DataCollectionRecordResponse(UUID id, DataCollectionCategory category, String provider,
			String endpoint, LocalDate referenceDate, DataCollectionStatus status, String errorCode, String errorMessage,
			Instant requestedAt, Instant completedAt, long tookMillis) {
		static DataCollectionRecordResponse from(DataCollectionRecord record) {
			return new DataCollectionRecordResponse(record.getId(), record.getCategory(), record.getProvider(),
					record.getEndpoint(), record.getReferenceDate(), record.getStatus(), record.getErrorCode(),
					record.getErrorMessage(), record.getRequestedAt(), record.getCompletedAt(), record.getTookMillis());
		}
	}

	public record JobRunStatusResponse(UUID id, String jobName, JobRunStatus status, Instant startedAt,
			Instant completedAt, String errorMessage) {
	}
}
