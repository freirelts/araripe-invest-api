package com.freirelts.araripe_invest_api.application.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.ai.AiAssetContext;
import com.freirelts.araripe_invest_api.application.ai.AiContextSource;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiRequest;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAnalysisService;
import com.freirelts.araripe_invest_api.application.indicators.IndicatorCalculationService;
import com.freirelts.araripe_invest_api.application.marketdata.MarketDataCollectionService;
import com.freirelts.araripe_invest_api.application.marketdata.MarketDataCollectionSummary;
import com.freirelts.araripe_invest_api.application.recommendations.PositionRecommendationService;
import com.freirelts.araripe_invest_api.application.screening.AssetScreeningService;
import com.freirelts.araripe_invest_api.application.thesis.PositionThesisGenerationService;
import com.freirelts.araripe_invest_api.domain.jobs.JobName;
import com.freirelts.araripe_invest_api.domain.jobs.JobRun;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunStatus;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunTrigger;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationStatus;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.infrastructure.persistence.JobRunRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.NotificationEventRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class OperationalJobService {

	private static final Logger log = LoggerFactory.getLogger(OperationalJobService.class);
	private static final List<String> DEFAULT_MACRO_SLUGS = List.of("selic", "ipca", "usdbrl");
	private static final String AI_SOURCE_NAME = "Araripe Invest deterministic engine";
	private static final String WEB_SEARCH_SOURCE_NAME = "OpenAI Web Search";
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final JobRunRepository jobRunRepository;
	private final UserRepository userRepository;
	private final PositionThesisRepository positionThesisRepository;
	private final NotificationEventRepository notificationEventRepository;
	private final MarketDataCollectionService marketDataCollectionService;
	private final IndicatorCalculationService indicatorCalculationService;
	private final AssetScreeningService assetScreeningService;
	private final PositionThesisGenerationService thesisGenerationService;
	private final EconomicContextAnalysisService economicContextAnalysisService;
	private final PositionRecommendationService positionRecommendationService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public OperationalJobService(JobRunRepository jobRunRepository, UserRepository userRepository,
			PositionThesisRepository positionThesisRepository, NotificationEventRepository notificationEventRepository,
			MarketDataCollectionService marketDataCollectionService,
			IndicatorCalculationService indicatorCalculationService, AssetScreeningService assetScreeningService,
			PositionThesisGenerationService thesisGenerationService,
			EconomicContextAnalysisService economicContextAnalysisService,
			PositionRecommendationService positionRecommendationService) {
		this.jobRunRepository = jobRunRepository;
		this.userRepository = userRepository;
		this.positionThesisRepository = positionThesisRepository;
		this.notificationEventRepository = notificationEventRepository;
		this.marketDataCollectionService = marketDataCollectionService;
		this.indicatorCalculationService = indicatorCalculationService;
		this.assetScreeningService = assetScreeningService;
		this.thesisGenerationService = thesisGenerationService;
		this.economicContextAnalysisService = economicContextAnalysisService;
		this.positionRecommendationService = positionRecommendationService;
	}

	@Transactional
	public JobRunResult execute(JobName jobName, LocalDate referenceDate, JobRunTrigger trigger, UUID requestedByUserId) {
		LocalDate effectiveDate = referenceDate == null ? LocalDate.now() : referenceDate;
		long startedAtNanos = System.nanoTime();
		User requester = requestedByUserId == null ? null
				: userRepository.findById(requestedByUserId)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requester not found."));
		ensureNoConflictingRun(jobName, effectiveDate);

		JobRun run = jobRunRepository.saveAndFlush(new JobRun(jobName, effectiveDate, trigger, requester,
				json(Map.of("referenceDate", effectiveDate.toString()))));
		log.info("Job {} started for referenceDate={} trigger={} runId={} requestedByUserId={}", jobName,
				effectiveDate, trigger, run.getId(), requestedByUserId);
		try {
			Map<String, Object> summary = executeJob(jobName, effectiveDate, trigger, requestedByUserId);
			finish(run, statusFromSummary(summary), summary, null);
			log.info("Job {} finished with status={} referenceDate={} runId={} durationMs={} summary={}", jobName,
					run.getStatus(), effectiveDate, run.getId(), elapsedMs(startedAtNanos), summary);
		}
		catch (RuntimeException ex) {
			finish(run, JobRunStatus.FAILED, Map.of("failed", true), summarize(ex));
			log.warn("Job {} failed for referenceDate={} runId={} durationMs={} error={}", jobName, effectiveDate,
					run.getId(), elapsedMs(startedAtNanos), summarize(ex));
		}
		return toResult(run);
	}

	public List<JobRunResult> listRuns(LocalDate referenceDate) {
		return jobRunRepository.findByReferenceDateOrderByStartedAtDesc(referenceDate).stream()
				.map(this::toResult)
				.toList();
	}

	private Map<String, Object> executeJob(JobName jobName, LocalDate referenceDate, JobRunTrigger trigger,
			UUID requestedByUserId) {
		return switch (jobName) {
			case DAILY_MARKET_DATA_COLLECTION, FUNDAMENTAL_DATA_COLLECTION -> collectMarketAndFundamentalData();
			case INDICATOR_CALCULATION -> count("indicatorResults",
					() -> indicatorCalculationService.calculateForActiveAssets(referenceDate).size());
			case FILTERS_AND_THESES -> filtersAndTheses(referenceDate);
			case RANKING -> ranking(referenceDate);
			case AI_CONTEXT_ENRICHMENT -> enrichAiContext(referenceDate);
			case PORTFOLIO_SCAN -> count("recommendations",
					() -> positionRecommendationService.recommendOpenPositions(referenceDate).size());
			case DAILY_NOTIFICATION_DIGEST -> notificationDigest(referenceDate);
			case DAILY_OPERATIONAL_FLOW -> dailyOperationalFlow(referenceDate, trigger, requestedByUserId);
		};
	}

	private Map<String, Object> dailyOperationalFlow(LocalDate referenceDate, JobRunTrigger trigger,
			UUID requestedByUserId) {
		List<Map<String, Object>> steps = new ArrayList<>();
		boolean failed = false;
		// A ordem do fluxo preserva a cadeia financeira: dados brutos antes de indicadores, filtros antes de teses,
		// contexto de IA apenas depois da tese deterministica e varredura de carteira antes de qualquer notificacao.
		List<JobName> orderedSteps = List.of(JobName.DAILY_MARKET_DATA_COLLECTION, JobName.INDICATOR_CALCULATION,
				JobName.FILTERS_AND_THESES, JobName.RANKING, JobName.AI_CONTEXT_ENRICHMENT, JobName.PORTFOLIO_SCAN,
				JobName.DAILY_NOTIFICATION_DIGEST);
		log.info("Daily operational flow started for referenceDate={} steps={}", referenceDate, orderedSteps.size());
		for (JobName step : orderedSteps) {
			log.info("Daily operational flow executing step={} referenceDate={}", step, referenceDate);
			JobRunResult result = execute(step, referenceDate, trigger, requestedByUserId);
			steps.add(Map.of("runId", result.runId().toString(), "jobName", result.jobName().name(), "status",
					result.status().name(), "summary", result.summary()));
			failed = failed || result.status() == JobRunStatus.FAILED;
			log.info("Daily operational flow step={} finished with status={} referenceDate={} runId={}", step,
					result.status(), referenceDate, result.runId());
		}
		if (failed) {
			log.warn("Daily operational flow finished with failed steps for referenceDate={}", referenceDate);
		}
		else {
			log.info("Daily operational flow finished successfully for referenceDate={}", referenceDate);
		}
		return Map.of("steps", steps, "failed", failed);
	}

	private Map<String, Object> collectMarketAndFundamentalData() {
		log.info("Collecting market, fundamental and macro data macroSlugs={}", DEFAULT_MACRO_SLUGS);
		MarketDataCollectionSummary summary = marketDataCollectionService.collectActiveAssetData(DEFAULT_MACRO_SLUGS);
		log.info(
				"Data collection finished candlesPersisted={} fundamentalSnapshotsPersisted={} financialStatementsPersisted={} dividendEventsPersisted={} macroSnapshotsPersisted={} collectionRecordsPersisted={} warnings={}",
				summary.candlesPersisted(), summary.fundamentalSnapshotsPersisted(),
				summary.financialStatementsPersisted(), summary.dividendEventsPersisted(),
				summary.macroSnapshotsPersisted(), summary.collectionRecordsPersisted(), summary.warnings());
		return Map.of("candlesPersisted", summary.candlesPersisted(), "fundamentalSnapshotsPersisted",
				summary.fundamentalSnapshotsPersisted(), "financialStatementsPersisted",
				summary.financialStatementsPersisted(), "dividendEventsPersisted", summary.dividendEventsPersisted(),
				"macroSnapshotsPersisted", summary.macroSnapshotsPersisted(), "collectionRecordsPersisted",
				summary.collectionRecordsPersisted(), "warnings", summary.warnings());
	}

	private Map<String, Object> filtersAndTheses(LocalDate referenceDate) {
		log.info("Running screening filters for referenceDate={}", referenceDate);
		int diagnostics = assetScreeningService.screenActiveAssets(referenceDate).size();
		log.info("Screening filters finished for referenceDate={} diagnostics={}", referenceDate, diagnostics);
		log.info("Generating position trade theses for referenceDate={}", referenceDate);
		int theses = thesisGenerationService.generateForActiveAssets(referenceDate).size();
		log.info("Position trade theses generated for referenceDate={} thesesGenerated={}", referenceDate, theses);
		return Map.of("screeningDiagnostics", diagnostics, "thesesGenerated", theses);
	}

	private Map<String, Object> ranking(LocalDate referenceDate) {
		log.info("Building thesis ranking for referenceDate={} ruleVersion={}", referenceDate,
				PositionThesisGenerationService.RULE_VERSION);
		List<PositionThesis> ranking = positionThesisRepository
				.findByReferenceDateAndRuleVersionOrderByScoreDesc(referenceDate,
						PositionThesisGenerationService.RULE_VERSION);
		List<String> topSymbols = ranking.stream()
				.limit(10)
				.map(thesis -> thesis.getAsset().getSymbol())
				.toList();
		log.info("Thesis ranking finished for referenceDate={} rankedTheses={} topSymbols={}", referenceDate,
				ranking.size(), topSymbols);
		return Map.of("rankedTheses", ranking.size(), "topSymbols", topSymbols);
	}

	private Map<String, Object> enrichAiContext(LocalDate referenceDate) {
		List<PositionThesis> theses = positionThesisRepository
				.findByReferenceDateAndRuleVersionOrderByScoreDesc(referenceDate,
						PositionThesisGenerationService.RULE_VERSION)
				.stream()
				.filter(this::eligibleForAiContext)
				.toList();
		log.info("AI context enrichment started for referenceDate={} candidateTheses={}", referenceDate,
				theses.size());
		int persisted = 0;
		for (PositionThesis thesis : theses) {
			economicContextAnalysisService.analyzeAndPersist(thesis.getAsset(), aiRequest(thesis));
			persisted++;
			if (persisted % 10 == 0 || persisted == theses.size()) {
				log.info("AI context enrichment progress referenceDate={} persisted={} total={}", referenceDate,
						persisted, theses.size());
			}
		}
		log.info("AI context enrichment finished for referenceDate={} aiAnalysesPersisted={}", referenceDate,
				persisted);
		return Map.of("candidateTheses", theses.size(), "aiAnalysesPersisted", persisted);
	}

	private boolean eligibleForAiContext(PositionThesis thesis) {
		return thesis.getStatus() == ThesisStatus.MONITORAR || thesis.getStatus() == ThesisStatus.OPORTUNIDADE
				|| thesis.getStatus() == ThesisStatus.APORTE_PLANEJADO || thesis.getStatus() == ThesisStatus.REAVALIAR
				|| thesis.getStatus() == ThesisStatus.REDUZIR_EXPOSICAO || thesis.getStatus() == ThesisStatus.SAIR_DA_TESE;
	}

	private EconomicContextAiRequest aiRequest(PositionThesis thesis) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("status", thesis.getStatus().name());
		data.put("score", thesis.getScore());
		data.put("priceCeiling", thesis.getPriceCeiling());
		data.put("fairPriceEstimate", thesis.getFairPriceEstimate());
		data.put("safetyMarginPercent", thesis.getSafetyMarginPercent());
		data.put("stopPrice", thesis.getStopPrice());
		data.put("targetPrice", thesis.getTargetPrice());
		return new EconomicContextAiRequest(AiAssetContext.from(thesis.getAsset()), thesis.getReferenceDate(),
				thesis.getThesisType(), null, thesis.getScore(), data,
				List.of(new AiContextSource(AI_SOURCE_NAME, "internal://position-theses/" + thesis.getId(),
						"Tese deterministica, score, valuation, stop, objetivo e margem de seguranca."),
						new AiContextSource(WEB_SEARCH_SOURCE_NAME, "openai://web_search",
								"Busca web obrigatoria por noticias economicas, institucionais e setoriais recentes.")),
				List.of("IA nao aprova ativo bloqueado por filtro deterministico.",
						"IA nao altera preco teto, stop, objetivo ou alocacao."));
	}

	private Map<String, Object> notificationDigest(LocalDate referenceDate) {
		int pendingEvents = notificationEventRepository
				.findByReferenceDateAndChannelAndStatus(referenceDate, NotificationChannel.EMAIL_SNS,
						NotificationStatus.PENDING)
				.size();
		log.info("Daily notification digest checked referenceDate={} pendingActionableEvents={}", referenceDate,
				pendingEvents);
		// O job de Fase 9 nao envia e-mail: ele preserva eventos acionaveis pendentes para a integracao SNS da Fase 12,
		// evitando marcar alerta como entregue sem provider externo auditado.
		if (pendingEvents == 0) {
			log.info("Daily notification digest skipped for referenceDate={} because there are no pending events",
					referenceDate);
			return Map.of("pendingActionableEvents", 0, "snsPublishReady", false, "skipped", true);
		}
		log.info("Daily notification digest found pending events for referenceDate={} pendingActionableEvents={}",
				referenceDate, pendingEvents);
		return Map.of("pendingActionableEvents", pendingEvents, "snsPublishReady", false, "partial", true,
				"reason", "SNS publication is implemented in phase 12; events remain pending and idempotent.");
	}

	private Map<String, Object> count(String key, Supplier<Integer> supplier) {
		int count = supplier.get();
		log.info("Job counter calculated key={} count={}", key, count);
		return Map.of(key, count);
	}

	private JobRunStatus statusFromSummary(Map<String, Object> summary) {
		if (Boolean.TRUE.equals(summary.get("failed"))) {
			return JobRunStatus.FAILED;
		}
		if (Boolean.TRUE.equals(summary.get("partial"))) {
			return JobRunStatus.PARTIAL_SUCCESS;
		}
		if (Boolean.TRUE.equals(summary.get("skipped"))) {
			return JobRunStatus.SKIPPED;
		}
		return JobRunStatus.SUCCESS;
	}

	private void ensureNoConflictingRun(JobName jobName, LocalDate referenceDate) {
		jobRunRepository.findByJobNameAndReferenceDateAndStatus(jobName, referenceDate, JobRunStatus.RUNNING)
				.ifPresent(run -> {
					throw new ResponseStatusException(HttpStatus.CONFLICT,
							"Job already running for this reference date.");
				});
	}

	private void finish(JobRun run, JobRunStatus status, Map<String, Object> summary, String errorMessage) {
		run.setStatus(status);
		run.setResultSummaryJson(json(summary));
		run.setErrorMessage(errorMessage);
		run.setCompletedAt(Instant.now());
		jobRunRepository.saveAndFlush(run);
	}

	private JobRunResult toResult(JobRun run) {
		return new JobRunResult(run.getId(), run.getJobName(), run.getReferenceDate(), run.getStatus(), run.getTrigger(),
				run.getRequestedByUser() == null ? null : run.getRequestedByUser().getId(), map(run.getResultSummaryJson()),
				run.getErrorMessage(), run.getStartedAt(), run.getCompletedAt());
	}

	private String summarize(RuntimeException ex) {
		String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
		return message.length() <= 1000 ? message : message.substring(0, 1000);
	}

	private long elapsedMs(long startedAtNanos) {
		return (System.nanoTime() - startedAtNanos) / 1_000_000;
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not serialize job run audit payload.", ex);
		}
	}

	private Map<String, Object> map(String json) {
		try {
			return objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, MAP_TYPE);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not deserialize job run audit payload.", ex);
		}
	}
}
