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
	private final AiContextAnalysisRepository aiContextAnalysisRepository;
	private final PositionRecommendationRepository recommendationRepository;
	private final NotificationEventRepository notificationEventRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public PositionRecommendationService(CustomerPositionRepository positionRepository,
			CustomerPositionThesisRepository positionThesisRepository, PositionThesisRepository thesisRepository,
			DailyCandleRepository dailyCandleRepository, AllocationPlanRepository allocationPlanRepository,
			AiContextAnalysisRepository aiContextAnalysisRepository,
			PositionRecommendationRepository recommendationRepository,
			NotificationEventRepository notificationEventRepository) {
		this.positionRepository = positionRepository;
		this.positionThesisRepository = positionThesisRepository;
		this.thesisRepository = thesisRepository;
		this.dailyCandleRepository = dailyCandleRepository;
		this.allocationPlanRepository = allocationPlanRepository;
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
				latestCandle.orElse(null), aiContext.orElse(null));

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
		recommendation.setStopPrice(position.getStopPrice());
		recommendation.setTargetPrice(position.getTargetPrice());
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
			DailyCandle latestCandle, AiContextAnalysis aiContext) {
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

		if (currentThesis.getStatus() == ThesisStatus.SAIR_DA_TESE || currentThesis.getStatus() == ThesisStatus.IGNORAR) {
			reasons.add("Tese diaria atual invalida a tese principal acompanhada pela posicao.");
			return withAiContext(decision(RecommendationType.SAIR_DA_TESE, Severity.CRITICAL, currentPrice, reasons,
					NotificationEventType.EXIT_THESIS), aiContext);
		}
		if (currentThesis.getStatus() == ThesisStatus.REDUZIR_EXPOSICAO || currentThesis.getScore() < 60) {
			reasons.add("Score ou status atual indicam risco elevado e reducao de exposicao.");
			return withAiContext(decision(RecommendationType.REDUZIR_POSICAO, Severity.HIGH, currentPrice, reasons,
					NotificationEventType.REDUCE_EXPOSURE), aiContext);
		}
		if (currentThesis.getStatus() == ThesisStatus.REAVALIAR) {
			reasons.add("Tese diaria atual entrou em reavaliacao por regra deterministica.");
			return withAiContext(decision(RecommendationType.REAVALIAR, Severity.HIGH, currentPrice, reasons,
					NotificationEventType.REASSESSMENT_REQUIRED), aiContext);
		}

		if (positive(currentThesis.getPriceCeiling()) && currentPrice.compareTo(currentThesis.getPriceCeiling()) > 0) {
			reasons.add("Preco atual acima do preco teto; novo aporte bloqueado por valuation, mantendo acompanhamento.");
			return withAiContext(decision(RecommendationType.MANTER, Severity.LOW, currentPrice, reasons, null),
					aiContext);
		}

		if (currentThesis.getScore() >= 75 && allowsIncrease(currentThesis)
				&& allocationAllowsIncrease(position, currentThesis)) {
			reasons.add("Tese principal segue valida, score atual e valuation permitem aumento planejado da posicao.");
			return withAiContext(decision(RecommendationType.AUMENTAR_POSICAO, Severity.MEDIUM, currentPrice, reasons,
					null), aiContext);
		}

		reasons.add("Tese principal segue acompanhavel, mas sem gatilho deterministico para nova acao operacional.");
		return withAiContext(decision(RecommendationType.MANTER, Severity.LOW, currentPrice, reasons, null), aiContext);
	}

	private boolean allocationAllowsIncrease(CustomerPosition position, PositionThesis currentThesis) {
		return allocationPlanRepository.findByThesisId(currentThesis.getId())
				.filter(AllocationPlan::isValid)
				.filter(plan -> plan.getSuggestedQuantity() > 0)
				.map(plan -> BigDecimal.valueOf(plan.getSuggestedQuantity()).compareTo(position.getQuantity()) > 0)
				.orElse(false);
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
				decision.notificationEventType().orElse(null));
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
		return new Decision(type, severity, currentPrice, List.copyOf(reasons), Optional.ofNullable(eventType));
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
			Optional<NotificationEventType> notificationEventType) {
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
