package com.freirelts.araripe_invest_api.application.alerts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.screening.DataFreshnessPolicy;
import com.freirelts.araripe_invest_api.domain.alerts.AssetWatchItem;
import com.freirelts.araripe_invest_api.domain.alerts.AssetWatchStatus;
import com.freirelts.araripe_invest_api.domain.alerts.InformationalAlert;
import com.freirelts.araripe_invest_api.domain.alerts.InformationalEventType;
import com.freirelts.araripe_invest_api.domain.marketdata.DailyCandle;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesis;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesisStatus;
import com.freirelts.araripe_invest_api.domain.portfolio.PositionStatus;
import com.freirelts.araripe_invest_api.domain.alerts.Severity;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetWatchItemRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DailyCandleRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.InformationalAlertRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class InformationalEventService {

	public static final String RULE_VERSION = "informational-events-v1";
	private static final String SOURCE = "araripe-rules";

	private final CustomerPositionRepository positionRepository;
	private final AssetWatchItemRepository watchItemRepository;
	private final CustomerPositionThesisRepository positionThesisRepository;
	private final PositionThesisRepository thesisRepository;
	private final DailyCandleRepository dailyCandleRepository;
	private final InformationalAlertRepository alertRepository;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public InformationalEventService(CustomerPositionRepository positionRepository,
			AssetWatchItemRepository watchItemRepository, CustomerPositionThesisRepository positionThesisRepository,
			PositionThesisRepository thesisRepository, DailyCandleRepository dailyCandleRepository,
			InformationalAlertRepository alertRepository) {
		this.positionRepository = positionRepository;
		this.watchItemRepository = watchItemRepository;
		this.positionThesisRepository = positionThesisRepository;
		this.thesisRepository = thesisRepository;
		this.dailyCandleRepository = dailyCandleRepository;
		this.alertRepository = alertRepository;
	}

	@Transactional
	public List<InformationalEventSummary> scanOpenPositions(LocalDate referenceDate) {
		List<InformationalEventSummary> events = new ArrayList<>(positionRepository
				.findByStatusOrderByCreatedAtAsc(PositionStatus.OPEN).stream()
				.filter(CustomerPosition::isOpenAndValidForDailyScan)
				.map(position -> scanPosition(position.getId(), referenceDate))
				.flatMap(Optional::stream)
				.toList());
		events.addAll(scanActiveWatchedAssets(referenceDate, false));
		return events;
	}

	@Transactional
	public List<InformationalEventSummary> scanActiveWatchedAssets(LocalDate referenceDate) {
		return scanActiveWatchedAssets(referenceDate, true);
	}

	private List<InformationalEventSummary> scanActiveWatchedAssets(LocalDate referenceDate,
			boolean includePositionBackedItems) {
		return watchItemRepository.findByStatusOrderByCreatedAtAsc(AssetWatchStatus.ACTIVE).stream()
				.filter(item -> includePositionBackedItems || item.getSourcePosition() == null)
				.map(item -> scanWatchItem(item.getId(), referenceDate))
				.flatMap(Optional::stream)
				.toList();
	}

	@Transactional
	public Optional<InformationalEventSummary> scanPosition(UUID positionId, LocalDate referenceDate) {
		CustomerPosition position = positionRepository.findById(positionId)
				.orElseThrow(() -> new IllegalArgumentException("Position not found."));
		Optional<EventDecision> decision = decision(position, referenceDate);
		if (decision.isEmpty()) {
			return Optional.empty();
		}
		EventDecision event = decision.get();
		return alertRepository
				.findByUserIdAndAssetIdAndSourcePositionIdAndReferenceDateAndEventTypeAndRuleVersionAndSource(
						position.getUser().getId(), position.getAsset().getId(), position.getId(), referenceDate,
						event.eventType(), RULE_VERSION, SOURCE)
				.map(alert -> Optional.of(summary(alert)))
				.orElseGet(() -> Optional.of(createAlert(position, referenceDate, event)));
	}

	@Transactional
	public Optional<InformationalEventSummary> scanWatchItem(UUID watchItemId, LocalDate referenceDate) {
		AssetWatchItem item = watchItemRepository.findById(watchItemId)
				.orElseThrow(() -> new IllegalArgumentException("Watched asset not found."));
		Optional<EventDecision> decision = decision(item, referenceDate);
		if (decision.isEmpty()) {
			return Optional.empty();
		}
		EventDecision event = decision.get();
		return alertRepository
				.findByUserIdAndAssetIdAndWatchItemIdAndReferenceDateAndEventTypeAndRuleVersionAndSource(
						item.getUser().getId(), item.getAsset().getId(), item.getId(), referenceDate, event.eventType(),
						RULE_VERSION, SOURCE)
				.map(alert -> Optional.of(summary(alert)))
				.orElseGet(() -> Optional.of(createAlert(item, referenceDate, event)));
	}

	private InformationalEventSummary createAlert(CustomerPosition position, LocalDate referenceDate, EventDecision event) {
		InformationalAlert alert = new InformationalAlert(position.getUser(), position.getAsset(), referenceDate,
				event.eventType(), event.severity(), event.title(), event.summary(), RULE_VERSION);
		alert.setSourcePosition(position);
		alert.setStudyModel(event.association());
		alert.setCurrentStudyModelSnapshot(event.currentStudyModelSnapshot());
		alert.setSource(SOURCE);
		alert.setEvidenceJson(json(event.evidence()));
		return summary(alertRepository.saveAndFlush(alert));
	}

	private InformationalEventSummary createAlert(AssetWatchItem item, LocalDate referenceDate, EventDecision event) {
		InformationalAlert alert = new InformationalAlert(item.getUser(), item.getAsset(), referenceDate,
				event.eventType(), event.severity(), event.title(), event.summary(), RULE_VERSION);
		alert.setWatchItem(item);
		alert.setSourcePosition(item.getSourcePosition());
		alert.setStudyModel(event.association());
		alert.setCurrentStudyModelSnapshot(event.currentStudyModelSnapshot());
		alert.setSource(SOURCE);
		alert.setEvidenceJson(json(event.evidence()));
		return summary(alertRepository.saveAndFlush(alert));
	}

	private Optional<EventDecision> decision(CustomerPosition position, LocalDate referenceDate) {
		Optional<CustomerPositionThesis> activeAssociation = positionThesisRepository
				.findByPositionIdAndStatus(position.getId(), CustomerPositionThesisStatus.ACTIVE);
		Optional<DailyCandle> latestCandle = dailyCandleRepository
				.findTopByAssetIdAndTradeDateLessThanEqualOrderByTradeDateDescCollectedAtDesc(position.getAsset().getId(),
						referenceDate);
		Optional<PositionThesis> currentStudyModel = activeAssociation
				.flatMap(association -> thesisRepository
						.findTopByAssetIdAndThesisTypeAndReferenceDateLessThanEqualOrderByReferenceDateDescCreatedAtDesc(
								position.getAsset().getId(), association.getThesisType(), referenceDate));
		BigDecimal currentPrice = latestCandle.map(DailyCandle::getClosePrice).orElse(null);
		Map<String, Object> evidence = baseEvidence(position, latestCandle.orElse(null), currentStudyModel.orElse(null));

		if (!position.isOpenAndValidForDailyScan()) {
			return Optional.of(event(InformationalEventType.QUALITY_DATA_BLOCKED, Severity.HIGH,
					"Dados do ativo acompanhado bloqueiam o estudo",
					"O cadastro do ativo acompanhado esta incompleto para a varredura informativa diaria.", evidence,
					activeAssociation.orElse(null), currentStudyModel.orElse(null)));
		}
		if (latestCandle.isEmpty() || latestCandle.get().getQualityStatus() != DataQualityStatus.VALID
				|| !positive(currentPrice)) {
			return Optional.of(event(InformationalEventType.QUALITY_DATA_BLOCKED, Severity.HIGH,
					"Dado de preco indisponivel para estudo",
					"O fechamento mais recente esta ausente, inconsistente ou sem qualidade valida.", evidence,
					activeAssociation.orElse(null), currentStudyModel.orElse(null)));
		}
		if (positive(position.getUserLowerPriceThreshold()) && currentPrice.compareTo(position.getUserLowerPriceThreshold()) <= 0) {
			evidence.put("matchedThreshold", "USER_LOWER_PRICE_THRESHOLD");
			return Optional.of(event(InformationalEventType.PRICE_THRESHOLD_REACHED, Severity.HIGH,
					"Limiar inferior de preco atingido",
					"O fechamento mais recente cruzou o limiar inferior cadastrado pelo usuario para acompanhamento informativo.",
					evidence, activeAssociation.orElse(null), currentStudyModel.orElse(null)));
		}
		if (positive(position.getUserUpperPriceThreshold()) && currentPrice.compareTo(position.getUserUpperPriceThreshold()) >= 0) {
			evidence.put("matchedThreshold", "USER_UPPER_PRICE_THRESHOLD");
			return Optional.of(event(InformationalEventType.PRICE_THRESHOLD_REACHED, Severity.MEDIUM,
					"Limiar superior de preco atingido",
					"O fechamento mais recente cruzou o limiar superior cadastrado pelo usuario para acompanhamento informativo.",
					evidence, activeAssociation.orElse(null), currentStudyModel.orElse(null)));
		}
		if (activeAssociation.isEmpty()) {
			return Optional.of(event(InformationalEventType.DATA_STALE, Severity.MEDIUM,
					"Modelo de estudo acompanhado ausente",
					"Nao ha modelo de estudo acompanhado ativo para comparar as premissas do ativo.", evidence, null,
					currentStudyModel.orElse(null)));
		}
		if (currentStudyModel.isEmpty()
				|| DataFreshnessPolicy.marketDataStale(referenceDate, currentStudyModel.get().getReferenceDate())) {
			return Optional.of(event(InformationalEventType.DATA_STALE, Severity.HIGH,
					"Modelo de estudo desatualizado",
					"O modelo de estudo mais recente esta fora da tolerancia operacional definida para a varredura.",
					evidence, activeAssociation.get(), currentStudyModel.orElse(null)));
		}
		PositionThesis thesis = currentStudyModel.get();
		if (studyAssumptionChanged(activeAssociation.get(), thesis)) {
			addStudyAssumptionEvidence(evidence, activeAssociation.get(), thesis);
			return Optional.of(event(InformationalEventType.STUDY_ASSUMPTION_CHANGED, Severity.HIGH,
					"Premissas do modelo de estudo alteradas",
					"Indicadores ou criterios do modelo acompanhado mudaram em relacao ao registro aceito pelo usuario.",
					evidence, activeAssociation.get(), thesis));
		}
		if (positive(thesis.getPriceCeiling()) && currentPrice.compareTo(thesis.getPriceCeiling()) > 0) {
			evidence.put("matchedThreshold", "STUDY_PRICE_REFERENCE");
			return Optional.of(event(InformationalEventType.INDICATOR_THRESHOLD_REACHED, Severity.LOW,
					"Preco acima da referencia do estudo",
					"O fechamento mais recente ficou acima do preco de referencia usado no modelo de estudo.", evidence,
					activeAssociation.get(), thesis));
		}
		return Optional.empty();
	}

	private Optional<EventDecision> decision(AssetWatchItem item, LocalDate referenceDate) {
		Optional<CustomerPositionThesis> activeAssociation = Optional.ofNullable(item.getAccompaniedStudyModel());
		Optional<DailyCandle> latestCandle = dailyCandleRepository
				.findTopByAssetIdAndTradeDateLessThanEqualOrderByTradeDateDescCollectedAtDesc(item.getAsset().getId(),
						referenceDate);
		Optional<PositionThesis> currentStudyModel = activeAssociation
				.flatMap(association -> thesisRepository
						.findTopByAssetIdAndThesisTypeAndReferenceDateLessThanEqualOrderByReferenceDateDescCreatedAtDesc(
								item.getAsset().getId(), association.getThesisType(), referenceDate));
		BigDecimal currentPrice = latestCandle.map(DailyCandle::getClosePrice).orElse(null);
		Map<String, Object> evidence = baseEvidence(item, latestCandle.orElse(null), currentStudyModel.orElse(null));

		if (item.getStatus() != AssetWatchStatus.ACTIVE) {
			return Optional.empty();
		}
		if (latestCandle.isEmpty() || latestCandle.get().getQualityStatus() != DataQualityStatus.VALID
				|| !positive(currentPrice)) {
			return Optional.of(event(InformationalEventType.QUALITY_DATA_BLOCKED, Severity.HIGH,
					"Dado de preco indisponivel para ativo acompanhado",
					"O fechamento mais recente esta ausente, inconsistente ou sem qualidade valida.", evidence,
					activeAssociation.orElse(null), currentStudyModel.orElse(null)));
		}
		if (positive(item.getUserLowerPriceThreshold())
				&& currentPrice.compareTo(item.getUserLowerPriceThreshold()) <= 0) {
			evidence.put("matchedThreshold", "USER_LOWER_PRICE_THRESHOLD");
			return Optional.of(event(InformationalEventType.PRICE_THRESHOLD_REACHED, Severity.HIGH,
					"Limiar inferior de preco atingido",
					"O fechamento mais recente cruzou o limiar inferior cadastrado pelo usuario para acompanhamento informativo.",
					evidence, activeAssociation.orElse(null), currentStudyModel.orElse(null)));
		}
		if (positive(item.getUserUpperPriceThreshold())
				&& currentPrice.compareTo(item.getUserUpperPriceThreshold()) >= 0) {
			evidence.put("matchedThreshold", "USER_UPPER_PRICE_THRESHOLD");
			return Optional.of(event(InformationalEventType.PRICE_THRESHOLD_REACHED, Severity.MEDIUM,
					"Limiar superior de preco atingido",
					"O fechamento mais recente cruzou o limiar superior cadastrado pelo usuario para acompanhamento informativo.",
					evidence, activeAssociation.orElse(null), currentStudyModel.orElse(null)));
		}
		if (activeAssociation.isEmpty()) {
			return Optional.empty();
		}
		if (currentStudyModel.isEmpty()
				|| DataFreshnessPolicy.marketDataStale(referenceDate, currentStudyModel.get().getReferenceDate())) {
			return Optional.of(event(InformationalEventType.DATA_STALE, Severity.HIGH,
					"Modelo de estudo desatualizado",
					"O modelo de estudo mais recente esta fora da tolerancia operacional definida para a varredura.",
					evidence, activeAssociation.get(), currentStudyModel.orElse(null)));
		}
		PositionThesis thesis = currentStudyModel.get();
		if (studyAssumptionChanged(activeAssociation.get(), thesis)) {
			addStudyAssumptionEvidence(evidence, activeAssociation.get(), thesis);
			return Optional.of(event(InformationalEventType.STUDY_ASSUMPTION_CHANGED, Severity.HIGH,
					"Premissas do modelo de estudo alteradas",
					"Indicadores ou criterios do modelo acompanhado mudaram em relacao ao registro aceito pelo usuario.",
					evidence, activeAssociation.get(), thesis));
		}
		if (positive(thesis.getPriceCeiling()) && currentPrice.compareTo(thesis.getPriceCeiling()) > 0) {
			evidence.put("matchedThreshold", "STUDY_PRICE_REFERENCE");
			return Optional.of(event(InformationalEventType.INDICATOR_THRESHOLD_REACHED, Severity.LOW,
					"Preco acima da referencia do estudo",
					"O fechamento mais recente ficou acima do preco de referencia usado no modelo de estudo.", evidence,
					activeAssociation.get(), thesis));
		}
		return Optional.empty();
	}

	private boolean studyAssumptionChanged(CustomerPositionThesis association, PositionThesis thesis) {
		if (thesis.getStatus() == ThesisStatus.PREMISSAS_ALTERADAS
				|| thesis.getStatus() == ThesisStatus.DADOS_DESATUALIZADOS
				|| thesis.getStatus() == ThesisStatus.DADOS_INSUFICIENTES) {
			return true;
		}
		return thesis.getScore() < 60 || association.getAcceptedScore() - thesis.getScore() >= 20;
	}

	private Map<String, Object> baseEvidence(CustomerPosition position, DailyCandle latestCandle, PositionThesis thesis) {
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("assetSymbol", position.getAsset().getSymbol());
		evidence.put("sourcePositionId", position.getId());
		if (latestCandle != null) {
			evidence.put("priceReferenceDate", latestCandle.getTradeDate());
			evidence.put("currentPrice", latestCandle.getClosePrice());
			evidence.put("priceQualityStatus", latestCandle.getQualityStatus());
		}
		if (positive(position.getUserLowerPriceThreshold())) {
			evidence.put("userLowerPriceThreshold", position.getUserLowerPriceThreshold());
		}
		if (positive(position.getUserUpperPriceThreshold())) {
			evidence.put("userUpperPriceThreshold", position.getUserUpperPriceThreshold());
		}
		if (thesis != null) {
			evidence.put("studyReferenceDate", thesis.getReferenceDate());
			evidence.put("studyScore", thesis.getScore());
			evidence.put("studyPriceReference", thesis.getPriceCeiling());
		}
		return evidence;
	}

	private Map<String, Object> baseEvidence(AssetWatchItem item, DailyCandle latestCandle, PositionThesis thesis) {
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("assetSymbol", item.getAsset().getSymbol());
		evidence.put("watchItemId", item.getId());
		if (item.getSourcePosition() != null) {
			evidence.put("sourcePositionId", item.getSourcePosition().getId());
		}
		if (latestCandle != null) {
			evidence.put("priceReferenceDate", latestCandle.getTradeDate());
			evidence.put("currentPrice", latestCandle.getClosePrice());
			evidence.put("priceQualityStatus", latestCandle.getQualityStatus());
		}
		if (positive(item.getUserLowerPriceThreshold())) {
			evidence.put("userLowerPriceThreshold", item.getUserLowerPriceThreshold());
		}
		if (positive(item.getUserUpperPriceThreshold())) {
			evidence.put("userUpperPriceThreshold", item.getUserUpperPriceThreshold());
		}
		if (thesis != null) {
			evidence.put("studyReferenceDate", thesis.getReferenceDate());
			evidence.put("studyScore", thesis.getScore());
			evidence.put("studyPriceReference", thesis.getPriceCeiling());
		}
		return evidence;
	}

	private void addStudyAssumptionEvidence(Map<String, Object> evidence, CustomerPositionThesis association,
			PositionThesis thesis) {
		PositionThesis acceptedThesis = association.getAcceptedThesis();
		evidence.put("acceptedStudyReferenceDate", acceptedThesis.getReferenceDate());
		evidence.put("acceptedStudyStatus", acceptedThesis.getStatus());
		evidence.put("acceptedScore", association.getAcceptedScore());
		if (association.getAcceptedPriceCeiling() != null) {
			evidence.put("acceptedStudyPriceReference", association.getAcceptedPriceCeiling());
		}
		if (association.getAcceptedSafetyMarginPercent() != null) {
			evidence.put("acceptedSafetyMarginPercent", association.getAcceptedSafetyMarginPercent());
		}
		evidence.put("currentStudyStatus", thesis.getStatus());
		evidence.put("currentScore", thesis.getScore());
		evidence.put("scoreDelta", thesis.getScore() - association.getAcceptedScore());
		evidence.put("currentStudyRuleVersion", thesis.getRuleVersion());
		putJsonEvidence(evidence, "currentFailedFilters", thesis.getFailedFiltersJson());
		putJsonEvidence(evidence, "currentReviewPoints", thesis.getReviewPointsJson());
		putJsonEvidence(evidence, "currentReasons", thesis.getReasonsJson());
	}

	private void putJsonEvidence(Map<String, Object> evidence, String key, String json) {
		if (json == null || json.isBlank()) {
			return;
		}
		Object value = jsonValue(json);
		if (value instanceof List<?> list && list.isEmpty()) {
			return;
		}
		if (value instanceof Map<?, ?> map && map.isEmpty()) {
			return;
		}
		if (value != null) {
			evidence.put(key, value);
		}
	}

	private EventDecision event(InformationalEventType eventType, Severity severity, String title, String summary,
			Map<String, Object> evidence, CustomerPositionThesis association, PositionThesis currentStudyModelSnapshot) {
		return new EventDecision(eventType, severity, title, summary, Map.copyOf(evidence), association,
				currentStudyModelSnapshot);
	}

	private String json(Map<String, Object> evidence) {
		try {
			return objectMapper.writeValueAsString(evidence);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not serialize informational event evidence.", ex);
		}
	}

	private Object jsonValue(String json) {
		try {
			return objectMapper.readValue(json, Object.class);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not deserialize informational event evidence details.", ex);
		}
	}

	private boolean positive(BigDecimal value) {
		return value != null && value.signum() > 0;
	}

	private InformationalEventSummary summary(InformationalAlert alert) {
		return new InformationalEventSummary(alert.getId(),
				alert.getWatchItem() == null ? null : alert.getWatchItem().getId(),
				alert.getSourcePosition() == null ? null : alert.getSourcePosition().getId(), alert.getAsset().getSymbol(),
				alert.getStudyModel() == null ? null : alert.getStudyModel().getId(),
				alert.getCurrentStudyModelSnapshot() == null ? null : alert.getCurrentStudyModelSnapshot().getId(),
				alert.getReferenceDate(), alert.getEventType(), alert.getSeverity(), alert.getTitle(),
				alert.getSummary());
	}

	private record EventDecision(
			InformationalEventType eventType,
			Severity severity,
			String title,
			String summary,
			Map<String, Object> evidence,
			CustomerPositionThesis association,
			PositionThesis currentStudyModelSnapshot) {
	}

	public record InformationalEventSummary(
			UUID id,
			UUID watchItemId,
			UUID positionId,
			String symbol,
			UUID customerPositionThesisId,
			UUID currentStudyModelSnapshotId,
			LocalDate referenceDate,
			InformationalEventType eventType,
			Severity severity,
			String title,
			String summary) {
	}
}
