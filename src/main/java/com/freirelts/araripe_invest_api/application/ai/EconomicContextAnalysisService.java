package com.freirelts.araripe_invest_api.application.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.indicators.IndicatorCalculationService;
import com.freirelts.araripe_invest_api.domain.ai.AiContextAnalysis;
import com.freirelts.araripe_invest_api.domain.ai.AiProcessingStatus;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.MacroIndicatorSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.marketdata.TechnicalIndicatorSnapshot;
import com.freirelts.araripe_invest_api.domain.thesis.AllocationPlan;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AiContextAnalysisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AllocationPlanRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.MacroIndicatorSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.TechnicalIndicatorSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EconomicContextAnalysisService {

	private static final String DERIVED_FUNDAMENTAL_SOURCE = "araripe-indicators";
	private static final String COLLECTOR_FUNDAMENTAL_SOURCE = "brapi";
	private static final String AI_SOURCE_NAME = "Araripe Invest deterministic engine";
	private static final String WEB_SEARCH_SOURCE_NAME = "OpenAI Web Search";

	private final EconomicContextAiProvider aiProvider;
	private final AiContextAnalysisRepository aiContextAnalysisRepository;
	private final PositionThesisRepository thesisRepository;
	private final UserRepository userRepository;
	private final AllocationPlanRepository allocationPlanRepository;
	private final FundamentalSnapshotRepository fundamentalRepository;
	private final TechnicalIndicatorSnapshotRepository technicalIndicatorRepository;
	private final MacroIndicatorSnapshotRepository macroIndicatorSnapshotRepository;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public EconomicContextAnalysisService(EconomicContextAiProvider aiProvider,
			AiContextAnalysisRepository aiContextAnalysisRepository, PositionThesisRepository thesisRepository,
			UserRepository userRepository, AllocationPlanRepository allocationPlanRepository,
			FundamentalSnapshotRepository fundamentalRepository,
			TechnicalIndicatorSnapshotRepository technicalIndicatorRepository,
			MacroIndicatorSnapshotRepository macroIndicatorSnapshotRepository) {
		this.aiProvider = aiProvider;
		this.aiContextAnalysisRepository = aiContextAnalysisRepository;
		this.thesisRepository = thesisRepository;
		this.userRepository = userRepository;
		this.allocationPlanRepository = allocationPlanRepository;
		this.fundamentalRepository = fundamentalRepository;
		this.technicalIndicatorRepository = technicalIndicatorRepository;
		this.macroIndicatorSnapshotRepository = macroIndicatorSnapshotRepository;
	}

	@Transactional
	public AiContextAnalysis analyzeThesis(UUID thesisId, UUID requestedByUserId, boolean forceRefresh) {
		return analyzeThesisWithExecution(thesisId, requestedByUserId, forceRefresh).analysis();
	}

	@Transactional
	public EconomicContextAnalysisExecution analyzeThesisWithExecution(UUID thesisId, UUID requestedByUserId,
			boolean forceRefresh) {
		PositionThesis thesis = thesisRepository.findById(thesisId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thesis not found."));
		User requester = userRepository.findById(requestedByUserId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requester not found."));
		validateEligible(thesis);

		EconomicContextAiRequest request = requestFor(thesis);
		String inputSummaryJson = json(request);
		String inputHash = sha256(inputSummaryJson);

		if (!forceRefresh) {
			var existing = aiContextAnalysisRepository
					.findByThesisIdAndReferenceDateAndProviderAndModelAndPromptVersionAndInputHash(thesis.getId(),
							thesis.getReferenceDate(), aiProvider.provider(), aiProvider.model(), aiProvider.promptVersion(),
							inputHash);
			if (existing.isPresent()) {
				return new EconomicContextAnalysisExecution(existing.get(), null);
			}
		}


		AiContextAnalysis analysis = new AiContextAnalysis(thesis.getAsset(), thesis.getReferenceDate(),
				aiProvider.provider(), aiProvider.model(), aiProvider.promptVersion(), "pending", inputHash);
		analysis.setThesis(thesis);
		analysis.setRequestedByUser(requester);
		analysis.setInputSummaryJson(inputSummaryJson);
		analysis.setSourcesJson(json(request.sources()));
		analysis.setValidationStatus(AiValidationStatus.PENDING);
		analysis.setProcessingStatus(AiProcessingStatus.PROCESSING);
		analysis.setStartedAt(Instant.now());
		analysis = aiContextAnalysisRepository.saveAndFlush(analysis);

		EconomicContextAiResult result;
		try {
			result = aiProvider.analyze(request);
		}
		catch (RuntimeException ex) {
			result = EconomicContextAiResult.failed("unhandled-ai-provider", "unknown", "unknown", "unavailable",
					inputSummaryJson, "[]", 0L, "AI provider failed before returning a traceable result: "
							+ ex.getClass().getSimpleName() + ".");
		}

		analysis.setProvider(result.provider());
		analysis.setModel(result.model());
		analysis.setPromptVersion(result.promptVersion());
		analysis.setPromptHash(defaultText(result.promptHash(), "unavailable"));
		analysis.setInputHash(inputHash);
		analysis.setInputSummaryJson(defaultJsonObject(result.inputSummaryJson()));
		analysis.setOutputJson(result.outputJson());
		analysis.setSourcesJson(defaultJsonArray(result.sourcesJson()));
		analysis.setValidationStatus(result.validationStatus() == null ? AiValidationStatus.FAILED
				: result.validationStatus());
		analysis.setLatencyMs(result.latencyMs());
		analysis.setErrorMessage(limit(result.errorMessage()));
		analysis.setProcessingStatus(processingStatus(analysis.getValidationStatus()));
		analysis.setFinishedAt(Instant.now());
		return new EconomicContextAnalysisExecution(aiContextAnalysisRepository.saveAndFlush(analysis),
				result.tokenUsage());
	}

	private EconomicContextAiRequest requestFor(PositionThesis thesis) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("thesisId", thesis.getId());
		data.put("status", thesis.getStatus().name());
		data.put("criteriaAdherenceScore", thesis.getScore());
		data.put("criteriaAdherenceBreakdown", jsonValue(thesis.getScoreBreakdownJson()));
		data.put("reasons", jsonValue(thesis.getReasonsJson()));
		data.put("failedFilters", jsonValue(thesis.getFailedFiltersJson()));
		data.put("studyPriceReference", thesis.getPriceCeiling());
		data.put("fairPriceEstimate", thesis.getFairPriceEstimate());
		data.put("safetyMarginPercent", percent(thesis.getSafetyMarginPercent()));
		data.put("lowerUserPriceThreshold", thesis.getStopPrice());
		data.put("upperUserPriceThreshold", thesis.getTargetPrice());
		allocationPlanRepository.findByThesisId(thesis.getId()).map(this::allocationData)
				.ifPresent(value -> data.put("allocationPlan", value));
		latestFundamental(thesis).map(this::fundamentalData).ifPresent(value -> data.put("fundamentals", value));
		latestTechnical(thesis).map(this::technicalData).ifPresent(value -> data.put("technicalIndicators", value));
		List<Map<String, Object>> macroIndicators = macroIndicatorSnapshotRepository
				.findLatestByReferenceDateLessThanEqual(thesis.getReferenceDate())
				.stream()
				.map(this::macroIndicatorData)
				.toList();
		if (!macroIndicators.isEmpty()) {
			data.put("macroIndicators", macroIndicators);
		}
		return new EconomicContextAiRequest(AiAssetContext.from(thesis.getAsset()), thesis.getReferenceDate(),
				thesis.getThesisType(), null, thesis.getScore(), data,
				List.of(new AiContextSource(AI_SOURCE_NAME, "internal://position-theses/" + thesis.getId(),
						"Modelo de estudo deterministico, aderencia a criterios, valuation educacional e margem de seguranca."),
						new AiContextSource(WEB_SEARCH_SOURCE_NAME, "openai://web_search",
								"Busca web obrigatoria por noticias economicas, institucionais e setoriais recentes.")),
				List.of("IA nao aprova ativo bloqueado por filtro deterministico.",
						"IA nao altera referencia de preco, limiares do usuario, margem de seguranca ou alocacao.",
						"IA nao orienta compra, venda, manutencao, aumento, reducao, alocacao ou encerramento."));
	}

	private void validateEligible(PositionThesis thesis) {
		if (thesis.getStatus() == ThesisStatus.DADOS_INSUFICIENTES
				|| thesis.getStatus() == ThesisStatus.DADOS_DESATUALIZADOS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Study model blocked by data rules cannot be analyzed with AI.");
		}
		if (thesis.getAsset() == null || thesis.getAsset().getId() == null || thesis.getReferenceDate() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thesis has insufficient data for AI analysis.");
		}
		if (thesis.getPriceCeiling() == null || thesis.getFairPriceEstimate() == null
				|| thesis.getSafetyMarginPercent() == null || thesis.getStopPrice() == null
				|| thesis.getTargetPrice() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Thesis valuation and risk data are required for AI analysis.");
		}
		if (latestFundamental(thesis).isEmpty() || latestTechnical(thesis).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Valid fundamental and technical snapshots are required for AI analysis.");
		}
	}

	private Map<String, Object> macroIndicatorData(MacroIndicatorSnapshot snapshot) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("slug", snapshot.getSlug());
		data.put("name", snapshot.getName());
		data.put("referenceDate", snapshot.getReferenceDate());
		data.put("value", snapshot.getValue());
		data.put("source", snapshot.getSource());
		if (snapshot.getUnit() != null) {
			data.put("unit", snapshot.getUnit());
		}
		if (snapshot.getFrequency() != null) {
			data.put("frequency", snapshot.getFrequency());
		}
		return data;
	}

	private BigDecimal percent(BigDecimal ratio) {
		return ratio == null ? null : ratio.multiply(new BigDecimal("100.000000")).setScale(6, RoundingMode.HALF_UP);
	}

	private java.util.Optional<FundamentalSnapshot> latestFundamental(PositionThesis thesis) {
		return fundamentalRepository
				.findTopByAssetIdAndReferenceDateLessThanEqualAndPeriodTypeAndSourceAndCalculationVersionOrderByReferenceDateDescCreatedAtDesc(
						thesis.getAsset().getId(), thesis.getReferenceDate(), PeriodType.TTM,
						DERIVED_FUNDAMENTAL_SOURCE, IndicatorCalculationService.CALCULATION_VERSION)
				.or(() -> fundamentalRepository
						.findTopByAssetIdAndReferenceDateLessThanEqualAndPeriodTypeAndSourceOrderByReferenceDateDescCreatedAtDesc(
								thesis.getAsset().getId(), thesis.getReferenceDate(), PeriodType.TTM,
								COLLECTOR_FUNDAMENTAL_SOURCE))
				.filter(snapshot -> snapshot.getQualityStatus() == DataQualityStatus.VALID);
	}

	private java.util.Optional<TechnicalIndicatorSnapshot> latestTechnical(PositionThesis thesis) {
		return technicalIndicatorRepository
				.findTopByAssetIdAndTradeDateLessThanEqualAndCalculationVersionOrderByTradeDateDescCreatedAtDesc(
						thesis.getAsset().getId(), thesis.getReferenceDate(), IndicatorCalculationService.CALCULATION_VERSION);
	}

	private Map<String, Object> allocationData(AllocationPlan plan) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("targetAllocationPercent", plan.getTargetAllocationPercent());
		data.put("suggestedQuantity", plan.getSuggestedQuantity());
		data.put("valid", plan.isValid());
		data.put("invalidReason", plan.getInvalidReason());
		return data;
	}

	private Map<String, Object> fundamentalData(FundamentalSnapshot snapshot) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("referenceDate", snapshot.getReferenceDate());
		data.put("periodType", snapshot.getPeriodType());
		data.put("trailingPe", snapshot.getTrailingPe());
		data.put("priceToBook", snapshot.getPriceToBook());
		data.put("enterpriseToEbitda", snapshot.getEnterpriseToEbitda());
		data.put("dividendYield", snapshot.getDividendYield());
		data.put("profitMargin", snapshot.getProfitMargin());
		data.put("roe", snapshot.getRoe());
		data.put("debtToEquity", snapshot.getDebtToEquity());
		data.put("revenueGrowth", snapshot.getRevenueGrowth());
		data.put("earningsGrowth", snapshot.getEarningsGrowth());
		data.put("freeCashflow", snapshot.getFreeCashflow());
		data.put("qualityStatus", snapshot.getQualityStatus());
		return data;
	}

	private Map<String, Object> technicalData(TechnicalIndicatorSnapshot snapshot) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("tradeDate", snapshot.getTradeDate());
		data.put("sma200", snapshot.getSma200());
		data.put("return6m", snapshot.getReturn6m());
		data.put("return12m", snapshot.getReturn12m());
		data.put("avgVolume60", snapshot.getAvgVolume60());
		data.put("historicalVolatility", snapshot.getHistoricalVolatility());
		data.put("recentDrawdown", snapshot.getRecentDrawdown());
		data.put("trendStatus", snapshot.getTrendStatus());
		return data;
	}

	private Object jsonValue(String json) {
		try {
			return objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, Object.class);
		}
		catch (JsonProcessingException ex) {
			return json;
		}
	}

	private AiProcessingStatus processingStatus(AiValidationStatus validationStatus) {
		return validationStatus == AiValidationStatus.VALID || validationStatus == AiValidationStatus.INVALID
				? AiProcessingStatus.COMPLETED : AiProcessingStatus.FAILED;
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not serialize AI audit payload.", ex);
		}
	}

	private String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available.", ex);
		}
	}

	private String defaultText(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private String defaultJsonObject(String value) {
		return value == null || value.isBlank() ? "{}" : value;
	}

	private String defaultJsonArray(String value) {
		return value == null || value.isBlank() ? "[]" : value;
	}

	private String limit(String value) {
		if (value == null || value.length() <= 1000) {
			return value;
		}
		return value.substring(0, 1000);
	}
}
