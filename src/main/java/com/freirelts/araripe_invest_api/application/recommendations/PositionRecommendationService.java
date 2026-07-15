package com.freirelts.araripe_invest_api.application.recommendations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.domain.ai.AiContextAnalysis;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.DailyCandle;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationEvent;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationEventType;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationStatus;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesis;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesisStatus;
import com.freirelts.araripe_invest_api.domain.portfolio.PositionStatus;
import com.freirelts.araripe_invest_api.domain.recommendations.PositionRecommendation;
import com.freirelts.araripe_invest_api.domain.recommendations.RecommendationType;
import com.freirelts.araripe_invest_api.domain.recommendations.Severity;
import com.freirelts.araripe_invest_api.application.screening.DataFreshnessPolicy;
import com.freirelts.araripe_invest_api.application.risk.RiskAllocationInput;
import com.freirelts.araripe_invest_api.application.risk.RiskAllocationResult;
import com.freirelts.araripe_invest_api.application.risk.RiskAllocationService;
import com.freirelts.araripe_invest_api.application.risk.RiskAllocationSettings;
import com.freirelts.araripe_invest_api.domain.risk.UserRiskAllocationSettings;
import com.freirelts.araripe_invest_api.domain.thesis.AllocationPlan;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AiContextAnalysisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AllocationPlanRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DailyCandleRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.NotificationEventRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionRecommendationRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRiskAllocationSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PositionRecommendationService {

	public static final String RULE_VERSION = "position-recommendation-v1";

	private final CustomerPositionRepository positionRepository;
	private final CustomerPositionThesisRepository positionThesisRepository;
	private final PositionThesisRepository thesisRepository;
	private final DailyCandleRepository dailyCandleRepository;
	private final AllocationPlanRepository allocationPlanRepository;
	private final UserRiskAllocationSettingsRepository riskSettingsRepository;
	private final RiskAllocationService riskAllocationService;
	private final AiContextAnalysisRepository aiContextAnalysisRepository;
	private final PositionRecommendationRepository recommendationRepository;
	private final NotificationEventRepository notificationEventRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public PositionRecommendationService(CustomerPositionRepository positionRepository,
				CustomerPositionThesisRepository positionThesisRepository, PositionThesisRepository thesisRepository,
				DailyCandleRepository dailyCandleRepository, AllocationPlanRepository allocationPlanRepository,
				UserRiskAllocationSettingsRepository riskSettingsRepository, RiskAllocationService riskAllocationService,
				AiContextAnalysisRepository aiContextAnalysisRepository,
				PositionRecommendationRepository recommendationRepository,
				NotificationEventRepository notificationEventRepository) {
		this.positionRepository = positionRepository;
		this.positionThesisRepository = positionThesisRepository;
		this.thesisRepository = thesisRepository;
		this.dailyCandleRepository = dailyCandleRepository;
		this.allocationPlanRepository = allocationPlanRepository;
		this.riskSettingsRepository = riskSettingsRepository;
		this.riskAllocationService = riskAllocationService;
		this.aiContextAnalysisRepository = aiContextAnalysisRepository;
		this.recommendationRepository = recommendationRepository;
		this.notificationEventRepository = notificationEventRepository;
	}

	@Transactional
	public List<RecommendationSummary> recommendOpenPositions(LocalDate referenceDate) {
		return positionRepository.findByStatusOrderByCreatedAtAsc(PositionStatus.OPEN).stream()
				.filter(CustomerPosition::isOpenAndValidForDailyScan)
				.map(position -> recommendPosition(position.getId(), referenceDate))
				.toList();
	}

	@Transactional
	public RecommendationSummary recommendPosition(UUID positionId, LocalDate referenceDate) {
		CustomerPosition position = positionRepository.findById(positionId)
				.orElseThrow(() -> new IllegalArgumentException("Position not found."));
		return recommendationRepository
				.findByUserIdAndPositionIdAndReferenceDateAndRuleVersion(position.getUser().getId(), position.getId(),
						referenceDate, RULE_VERSION)
				.map(this::summary)
				.orElseGet(() -> createRecommendation(position, referenceDate));
	}

	private RecommendationSummary createRecommendation(CustomerPosition position, LocalDate referenceDate) {
		Optional<CustomerPositionThesis> activeAssociation = positionThesisRepository
				.findByPositionIdAndStatus(position.getId(), CustomerPositionThesisStatus.ACTIVE);
		Optional<DailyCandle> latestCandle = dailyCandleRepository
				.findTopByAssetIdAndTradeDateLessThanEqualOrderByTradeDateDescCollectedAtDesc(position.getAsset().getId(),
						referenceDate);
		Optional<PositionThesis> currentThesis = activeAssociation
				.flatMap(association -> thesisRepository
						.findTopByAssetIdAndThesisTypeAndReferenceDateLessThanEqualOrderByReferenceDateDescCreatedAtDesc(
								position.getAsset().getId(), association.getThesisType(), referenceDate));
		Optional<AiContextAnalysis> aiContext = currentThesis
				.flatMap(thesis -> aiContextAnalysisRepository
						.findTopByThesisIdAndReferenceDateLessThanEqualAndValidationStatusOrderByReferenceDateDescCreatedAtDesc(
								thesis.getId(), referenceDate, AiValidationStatus.VALID));

		Decision decision = decide(position, activeAssociation.orElse(null), currentThesis.orElse(null),
				latestCandle.orElse(null), aiContext.orElse(null), referenceDate);

		PositionRecommendation recommendation = new PositionRecommendation();
		recommendation.setUser(position.getUser());
		recommendation.setPosition(position);
		recommendation.setAsset(position.getAsset());
		recommendation.setCustomerPositionThesis(activeAssociation.orElse(null));
		recommendation.setCurrentThesis(currentThesis.orElse(null));
		recommendation.setThesisType(activeAssociation.map(CustomerPositionThesis::getThesisType).orElse(null));
		recommendation.setReferenceDate(referenceDate);
		recommendation.setRecommendationType(decision.type());
		recommendation.setSeverity(decision.severity());
		recommendation.setCurrentPrice(decision.currentPrice());
		recommendation.setAveragePrice(position.getAveragePrice());
		recommendation.setStopPrice(effectiveStopPrice(position, decision.allocationResult().orElse(null),
				currentThesis.orElse(null)));
		recommendation.setTargetPrice(effectiveTargetPrice(position, decision.allocationResult().orElse(null),
				currentThesis.orElse(null)));
		applyRiskAudit(recommendation, decision.allocationResult().orElse(null));
		recommendation.setScore(currentThesis.map(PositionThesis::getScore).orElse(null));
		recommendation.setDeterministicReasonJson(json(decision.reasons()));
		recommendation.setAiContextAnalysis(aiContext.orElse(null));
		recommendation.setAiModel(aiContext.map(AiContextAnalysis::getModel).orElse(null));
		recommendation.setFinalMessage(limit(String.join(" ", decision.reasons()), 2000));
		recommendation.setRuleVersion(RULE_VERSION);

		PositionRecommendation saved = recommendationRepository.saveAndFlush(recommendation);
		decision.notificationEventType().ifPresent(eventType -> createNotification(saved, eventType));
		return summary(saved);
	}

	private Decision decide(CustomerPosition position, CustomerPositionThesis association, PositionThesis currentThesis,
			DailyCandle latestCandle, AiContextAnalysis aiContext, LocalDate referenceDate) {
		List<String> reasons = new ArrayList<>();
		BigDecimal currentPrice = latestCandle == null ? null : latestCandle.getClosePrice();

		if (!position.isOpenAndValidForDailyScan()) {
			reasons.add("Posicao aberta invalida ou incompleta; varredura diaria bloqueada.");
			return decision(RecommendationType.REAVALIAR, Severity.HIGH, currentPrice, reasons,
					NotificationEventType.REASSESSMENT_REQUIRED);
		}
		if (latestCandle == null || latestCandle.getQualityStatus() != DataQualityStatus.VALID || !positive(currentPrice)) {
			reasons.add("Preco atual indisponivel, inconsistente ou sem qualidade valida; recomendacao falsa bloqueada.");
			return decision(RecommendationType.REAVALIAR, Severity.HIGH, currentPrice, reasons,
					NotificationEventType.REASSESSMENT_REQUIRED);
		}
		// Stop e objetivo sao regras operacionais prioritarias: quando o preco cruza esses limites, a disciplina do plano
		// da posicao prevalece sobre score, valuation ou contexto de IA.
		if (positive(position.getStopPrice()) && currentPrice.compareTo(position.getStopPrice()) <= 0) {
			reasons.add("Stop cadastrado foi atingido ou perdido pelo preco de fechamento mais recente.");
			return withAiContext(decision(RecommendationType.EXECUTAR_STOP, Severity.CRITICAL, currentPrice, reasons,
					NotificationEventType.STOP_TRIGGERED), aiContext);
		}
		if (positive(position.getTargetPrice()) && currentPrice.compareTo(position.getTargetPrice()) >= 0) {
			reasons.add("Objetivo cadastrado foi atingido pelo preco de fechamento mais recente.");
			return withAiContext(decision(RecommendationType.REALIZAR_OBJETIVO, Severity.HIGH, currentPrice, reasons,
					NotificationEventType.TARGET_REACHED), aiContext);
		}
		if (association == null) {
			reasons.add("Posicao sem tese principal ativa; aumento de posicao bloqueado ate associacao de tese.");
			return withAiContext(decision(RecommendationType.REAVALIAR, Severity.MEDIUM, currentPrice, reasons, null),
					aiContext);
		}
		if (currentThesis == null) {
			reasons.add("Nao existe tese diaria recente para o mesmo ativo e tipo da tese principal associada.");
			return withAiContext(decision(RecommendationType.REAVALIAR, Severity.HIGH, currentPrice, reasons,
					NotificationEventType.REASSESSMENT_REQUIRED), aiContext);
		}
		if (DataFreshnessPolicy.marketDataStale(referenceDate, currentThesis.getReferenceDate())) {
			reasons.add("Tese diaria mais recente esta fora da tolerancia operacional; recomendacao acionavel bloqueada.");
			return withAiContext(decision(RecommendationType.REAVALIAR, Severity.HIGH, currentPrice, reasons,
					NotificationEventType.REASSESSMENT_REQUIRED), aiContext);
		}

		RiskAllocationResult allocation = allocationResult(position, currentThesis, currentPrice,
				latestCandle.getTradeDate());
		BigDecimal effectiveStop = effectiveStopPrice(position, allocation, currentThesis);
		BigDecimal effectiveTarget = effectiveTargetPrice(position, allocation, currentThesis);
		if (!positive(position.getStopPrice()) && positive(effectiveStop) && currentPrice.compareTo(effectiveStop) <= 0) {
			reasons.add("Stop calculado pela tese ou pelo motor de risco foi atingido ou perdido pelo preco de fechamento mais recente.");
			return withAiContext(decision(RecommendationType.EXECUTAR_STOP, Severity.CRITICAL, currentPrice, reasons,
					NotificationEventType.STOP_TRIGGERED, allocation), aiContext);
		}
		if (!positive(position.getTargetPrice()) && positive(effectiveTarget)
				&& currentPrice.compareTo(effectiveTarget) >= 0) {
			reasons.add("Objetivo calculado pela tese ou pelo motor de risco foi atingido pelo preco de fechamento mais recente.");
			return withAiContext(decision(RecommendationType.REALIZAR_OBJETIVO, Severity.HIGH, currentPrice, reasons,
					NotificationEventType.TARGET_REACHED, allocation), aiContext);
		}

		if (currentThesis.getStatus() == ThesisStatus.SAIR_DA_TESE || currentThesis.getStatus() == ThesisStatus.IGNORAR) {
			reasons.add("Tese diaria atual invalida a tese principal acompanhada pela posicao.");
			return withAiContext(decision(RecommendationType.SAIR_DA_TESE, Severity.CRITICAL, currentPrice, reasons,
					NotificationEventType.EXIT_THESIS, allocation), aiContext);
		}
		if (currentThesis.getStatus() == ThesisStatus.REDUZIR_EXPOSICAO || currentThesis.getScore() < 60) {
			reasons.add("Score ou status atual indicam risco elevado e reducao de exposicao.");
			return withAiContext(decision(RecommendationType.REDUZIR_POSICAO, Severity.HIGH, currentPrice, reasons,
					NotificationEventType.REDUCE_EXPOSURE, allocation), aiContext);
		}
		if (currentThesis.getStatus() == ThesisStatus.REAVALIAR) {
			reasons.add("Tese diaria atual entrou em reavaliacao por regra deterministica.");
			return withAiContext(decision(RecommendationType.REAVALIAR, Severity.HIGH, currentPrice, reasons,
					NotificationEventType.REASSESSMENT_REQUIRED, allocation), aiContext);
		}

		if (positive(currentThesis.getPriceCeiling()) && currentPrice.compareTo(currentThesis.getPriceCeiling()) > 0) {
			reasons.add("Preco atual acima do preco teto; novo aporte bloqueado por valuation, mantendo acompanhamento.");
			return withAiContext(decision(RecommendationType.MANTER, Severity.LOW, currentPrice, reasons, null,
					allocation), aiContext);
		}

		if (currentThesis.getScore() >= 75 && allowsIncrease(currentThesis)
				&& allocation.valid() && allocation.suggestedQuantity() > 0) {
			reasons.add("Tese principal segue valida, score atual e valuation permitem aumento planejado da posicao.");
			return withAiContext(decision(RecommendationType.AUMENTAR_POSICAO, Severity.MEDIUM, currentPrice, reasons,
					null, allocation), aiContext);
		}

		reasons.add("Tese principal segue acompanhavel, mas sem gatilho deterministico para nova acao operacional.");
		return withAiContext(decision(RecommendationType.MANTER, Severity.LOW, currentPrice, reasons, null,
				allocation), aiContext);
	}

	private RiskAllocationResult allocationResult(CustomerPosition position, PositionThesis currentThesis, BigDecimal currentPrice,
			LocalDate referenceDate) {
		AllocationPlan thesisPlan = allocationPlanRepository.findByThesisId(currentThesis.getId()).orElse(null);
		BigDecimal targetAllocationPercent = thesisPlan == null ? null : thesisPlan.getTargetAllocationPercent();
		return riskAllocationService.calculate(new RiskAllocationInput(
				riskSettings(position.getUser().getId()), currentThesis.getStatus(), currentPrice,
				currentThesis.getFairPriceEstimate(), currentThesis.getPriceCeiling(), currentThesis.getSafetyMarginPercent(),
				targetAllocationPercent, assetExposure(position, referenceDate), sectorExposure(position, referenceDate),
				totalExposure(position, referenceDate), position.getAveragePrice(), position.getStopPrice(),
				position.getTargetPrice(), null, null, false));
	}

	private RiskAllocationSettings riskSettings(UUID userId) {
		return riskSettingsRepository.findByUserId(userId)
				.map(this::toSettings)
				.orElseGet(RiskAllocationSettings::conservativeDefault);
	}

	private RiskAllocationSettings toSettings(UserRiskAllocationSettings settings) {
		return new RiskAllocationSettings(settings.getCapitalBase(), settings.getMaxAllocationPerAssetPercent(),
				settings.getMaxAllocationPerSectorPercent(), settings.getToleratedDrawdownPercent(),
				settings.getMinimumCashReservePercent(), settings.getMinimumSafetyMarginPercent(),
				settings.getFirstTranchePercent(), settings.getSecondTranchePercent(), settings.getThirdTranchePercent(),
				settings.getDefaultStopPercent(), settings.getDefaultTargetReturnPercent());
	}

	private BigDecimal assetExposure(CustomerPosition position, LocalDate referenceDate) {
		return positionRepository.findByUserIdOrderByCreatedAtDesc(position.getUser().getId()).stream()
				.filter(CustomerPosition::isOpenAndValidForDailyScan)
				.filter(openPosition -> openPosition.getAsset().getId().equals(position.getAsset().getId()))
				.map(openPosition -> exposureValue(openPosition, referenceDate))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal sectorExposure(CustomerPosition position, LocalDate referenceDate) {
		String sector = position.getAsset().getSector();
		return positionRepository.findByUserIdOrderByCreatedAtDesc(position.getUser().getId()).stream()
				.filter(CustomerPosition::isOpenAndValidForDailyScan)
				.filter(openPosition -> sameSector(sector, openPosition.getAsset().getSector()))
				.map(openPosition -> exposureValue(openPosition, referenceDate))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal totalExposure(CustomerPosition position, LocalDate referenceDate) {
		return positionRepository.findByUserIdOrderByCreatedAtDesc(position.getUser().getId()).stream()
				.filter(CustomerPosition::isOpenAndValidForDailyScan)
				.map(openPosition -> exposureValue(openPosition, referenceDate))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal exposureValue(CustomerPosition position, LocalDate referenceDate) {
		BigDecimal price = dailyCandleRepository
				.findTopByAssetIdAndTradeDateLessThanEqualOrderByTradeDateDescCollectedAtDesc(position.getAsset().getId(),
						referenceDate)
				.filter(candle -> candle.getQualityStatus() == DataQualityStatus.VALID)
				.map(DailyCandle::getClosePrice)
				.filter(this::positive)
				.orElse(position.getAveragePrice());
		return position.getQuantity().multiply(price);
	}

	private boolean sameSector(String left, String right) {
		return left == null ? right == null : left.equalsIgnoreCase(right);
	}

	private boolean allowsIncrease(PositionThesis currentThesis) {
		return currentThesis.getStatus() == ThesisStatus.OPORTUNIDADE
				|| currentThesis.getStatus() == ThesisStatus.APORTE_PLANEJADO;
	}

	private Decision withAiContext(Decision decision, AiContextAnalysis aiContext) {
		if (aiContext == null || aiContext.getOutputJson() == null || aiContext.getOutputJson().isBlank()) {
			return decision;
		}
		List<String> reasons = new ArrayList<>(decision.reasons());
		reasons.add("Contexto de IA validado incorporado como explicacao, sem alterar a regra deterministica.");
		if (aiConflictsWithDeterministicRule(aiContext)) {
			reasons.add("Divergencia registrada: IA validada sinalizou conflito, mas a decisao deterministica prevaleceu.");
		}
		return decision(decision.type(), decision.severity(), decision.currentPrice(), reasons,
				decision.notificationEventType().orElse(null), decision.allocationResult().orElse(null));
	}

	private boolean aiConflictsWithDeterministicRule(AiContextAnalysis aiContext) {
		try {
			JsonNode node = objectMapper.readTree(aiContext.getOutputJson());
			return node.path("conflictsWithDeterministicRecommendation").asBoolean(false);
		}
		catch (JsonProcessingException ex) {
			return false;
		}
	}

	private void createNotification(PositionRecommendation recommendation, NotificationEventType eventType) {
		notificationEventRepository
				.findByRecommendationIdAndChannelAndEventType(recommendation.getId(), NotificationChannel.EMAIL,
						eventType)
				.orElseGet(() -> {
					NotificationEvent notification = new NotificationEvent();
					notification.setUser(recommendation.getUser());
					notification.setPosition(recommendation.getPosition());
					notification.setRecommendation(recommendation);
					notification.setAsset(recommendation.getAsset());
					notification.setReferenceDate(recommendation.getReferenceDate());
					notification.setChannel(NotificationChannel.EMAIL);
					notification.setEventType(eventType);
					notification.setRecommendationType(recommendation.getRecommendationType());
					notification.setSeverity(recommendation.getSeverity());
					notification.setSummary(limit(recommendation.getFinalMessage(), 1000));
					notification.setStatus(NotificationStatus.PENDING);
					notification.setRuleVersion(recommendation.getRuleVersion());
					return notificationEventRepository.saveAndFlush(notification);
				});
	}

	private Decision decision(RecommendationType type, Severity severity, BigDecimal currentPrice, List<String> reasons,
			NotificationEventType eventType) {
		return decision(type, severity, currentPrice, reasons, eventType, null);
	}

	private Decision decision(RecommendationType type, Severity severity, BigDecimal currentPrice, List<String> reasons,
			NotificationEventType eventType, RiskAllocationResult allocationResult) {
		return new Decision(type, severity, currentPrice, List.copyOf(reasons), Optional.ofNullable(eventType),
				Optional.ofNullable(allocationResult));
	}

	private BigDecimal effectiveStopPrice(CustomerPosition position, RiskAllocationResult allocation, PositionThesis thesis) {
		if (positive(position.getStopPrice())) {
			return position.getStopPrice();
		}
		if (allocation != null && positive(allocation.stopPrice())) {
			return allocation.stopPrice();
		}
		return thesis == null ? null : thesis.getStopPrice();
	}

	private BigDecimal effectiveTargetPrice(CustomerPosition position, RiskAllocationResult allocation,
			PositionThesis thesis) {
		if (positive(position.getTargetPrice())) {
			return position.getTargetPrice();
		}
		if (allocation != null && positive(allocation.targetPrice())) {
			return allocation.targetPrice();
		}
		return thesis == null ? null : thesis.getTargetPrice();
	}

	private void applyRiskAudit(PositionRecommendation recommendation, RiskAllocationResult allocation) {
		if (allocation == null) {
			return;
		}
		recommendation.setPriceCeiling(allocation.priceCeiling());
		recommendation.setFairPriceEstimate(allocation.fairPriceEstimate());
		recommendation.setSafetyMarginPercent(allocation.safetyMarginPercent());
		recommendation.setEstimatedUpsidePercent(allocation.estimatedUpsidePercent());
		recommendation.setSuggestedQuantity(allocation.suggestedQuantity());
		recommendation.setCurrentAssetExposureValue(allocation.currentAssetExposureValue());
		recommendation.setCurrentSectorExposureValue(allocation.currentSectorExposureValue());
		recommendation.setCurrentTotalExposureValue(allocation.currentTotalExposureValue());
		recommendation.setAvailableForAsset(allocation.availableForAsset());
		recommendation.setAvailableForSector(allocation.availableForSector());
		recommendation.setAvailableForCash(allocation.availableForCash());
		recommendation.setAllocationValid(allocation.valid());
		recommendation.setAllocationInvalidReason(limit(allocation.invalidReason(), 1000));
	}

	private String json(List<String> reasons) {
		try {
			return objectMapper.writeValueAsString(reasons);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not serialize recommendation audit reasons.", ex);
		}
	}

	private boolean positive(BigDecimal value) {
		return value != null && value.signum() > 0;
	}

	private String limit(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	private RecommendationSummary summary(PositionRecommendation recommendation) {
		return new RecommendationSummary(recommendation.getId(), recommendation.getPosition().getId(),
				recommendation.getAsset().getSymbol(), recommendation.getCustomerPositionThesis() == null ? null
						: recommendation.getCustomerPositionThesis().getId(),
				recommendation.getCurrentThesis() == null ? null : recommendation.getCurrentThesis().getId(),
				recommendation.getThesisType(), recommendation.getReferenceDate(), recommendation.getRecommendationType(),
				recommendation.getSeverity(), recommendation.getCurrentPrice(), recommendation.getScore(),
				recommendation.getAiContextAnalysis() != null, recommendation.getFinalMessage());
	}

	private record Decision(
			RecommendationType type,
			Severity severity,
			BigDecimal currentPrice,
			List<String> reasons,
			Optional<NotificationEventType> notificationEventType,
			Optional<RiskAllocationResult> allocationResult) {
	}

	public record RecommendationSummary(
			UUID id,
			UUID positionId,
			String symbol,
			UUID customerPositionThesisId,
			UUID currentThesisId,
			com.freirelts.araripe_invest_api.domain.thesis.ThesisType thesisType,
			LocalDate referenceDate,
			RecommendationType recommendationType,
			Severity severity,
			BigDecimal currentPrice,
			Integer score,
			boolean aiContextAvailable,
			String finalMessage) {
	}
}
