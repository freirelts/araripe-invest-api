package com.freirelts.araripe_invest_api.application.notifications;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.domain.alerts.InformationalAlert;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationStatus;
import com.freirelts.araripe_invest_api.infrastructure.persistence.InformationalAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationDigestService {

	private static final Logger log = LoggerFactory.getLogger(NotificationDigestService.class);
	private static final DateTimeFormatter SUBJECT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final TypeReference<Map<String, Object>> EVIDENCE_TYPE = new TypeReference<>() {
	};

	private final InformationalAlertRepository alertRepository;
	private final NotificationProvider notificationProvider;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public NotificationDigestService(InformationalAlertRepository alertRepository,
			NotificationProvider notificationProvider) {
		this.alertRepository = alertRepository;
		this.notificationProvider = notificationProvider;
	}

	@Transactional
	public DailyNotificationDigestSummary publishDailyDigest(LocalDate referenceDate) {
		List<InformationalAlert> pendingEvents = alertRepository.findDigestCandidates(referenceDate,
				NotificationChannel.EMAIL, NotificationStatus.PENDING);
		if (pendingEvents.isEmpty()) {
			log.info("Daily informational alert digest skipped referenceDate={} because there are no pending email alerts",
					referenceDate);
			return new DailyNotificationDigestSummary(0, 0, 0, 0, 0, 0, true, false);
		}

		Map<DigestKey, List<InformationalAlert>> groupedEvents = groupedByUserAndRuleVersion(pendingEvents);
		int digestsSent = 0;
		int digestsFailed = 0;
		int eventsSent = 0;
		int eventsFailed = 0;
		for (List<InformationalAlert> events : groupedEvents.values()) {
			DailyNotificationDigest digest = toDigest(referenceDate, events);
			try {
				NotificationPublishResult result = notificationProvider.publish(digest);
				markSent(events, result);
				digestsSent++;
				eventsSent += events.size();
				log.info("Daily informational alert digest sent referenceDate={} userId={} items={} provider={} messageId={}",
						referenceDate, digest.userId(), digest.items().size(), result.provider(),
						result.providerMessageId());
			}
			catch (RuntimeException ex) {
				markFailed(events, notificationProvider.providerName(), summarize(ex));
				digestsFailed++;
				eventsFailed += events.size();
				log.warn("Daily informational alert digest failed referenceDate={} userId={} items={} provider={} error={}",
						referenceDate, digest.userId(), digest.items().size(), notificationProvider.providerName(),
						summarize(ex), ex);
			}
		}
		alertRepository.saveAllAndFlush(pendingEvents);
		return new DailyNotificationDigestSummary(pendingEvents.size(), groupedEvents.size(), digestsSent,
				digestsFailed, eventsSent, eventsFailed, false, digestsFailed > 0);
	}

	private Map<DigestKey, List<InformationalAlert>> groupedByUserAndRuleVersion(List<InformationalAlert> events) {
		Map<DigestKey, List<InformationalAlert>> grouped = new LinkedHashMap<>();
		for (InformationalAlert event : events) {
			DigestKey key = new DigestKey(event.getUser().getId(), event.getRuleVersion());
			grouped.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(event);
		}
		return grouped;
	}

	private DailyNotificationDigest toDigest(LocalDate referenceDate, List<InformationalAlert> events) {
		InformationalAlert first = events.getFirst();
		return new DailyNotificationDigest(first.getUser().getId(), first.getUser().getName(), first.getUser().getEmail(),
				referenceDate, NotificationChannel.EMAIL, first.getRuleVersion(), subject(referenceDate),
				events.stream().map(this::toItem).toList());
	}

	private DailyNotificationDigestItem toItem(InformationalAlert event) {
		return new DailyNotificationDigestItem(event.getId(), event.getWatchItem() == null ? null : event.getWatchItem().getId(),
				event.getSourcePosition() == null ? null : event.getSourcePosition().getId(),
				event.getAsset().getSymbol(), event.getReferenceDate(), event.getEventType(), event.getSeverity(),
				event.getTitle(), event.getSource(), event.getRuleVersion(), event.getSummary(),
				evidence(event.getEvidenceJson()));
	}

	private Map<String, Object> evidence(String evidenceJson) {
		if (evidenceJson == null || evidenceJson.isBlank()) {
			return Map.of();
		}
		try {
			return objectMapper.readValue(evidenceJson, EVIDENCE_TYPE);
		}
		catch (Exception ex) {
			log.warn("Could not parse informational alert evidence for email digest.", ex);
			return Map.of();
		}
	}

	private String subject(LocalDate referenceDate) {
		return "Araripe Invest - alertas informativos dos ativos acompanhados em "
				+ referenceDate.format(SUBJECT_DATE_FORMAT);
	}

	private void markSent(List<InformationalAlert> events, NotificationPublishResult result) {
		Instant sentAt = Instant.now();
		String provider = result.provider() == null || result.provider().isBlank() ? notificationProvider.providerName()
				: result.provider();
		for (InformationalAlert event : events) {
			event.setNotificationStatus(NotificationStatus.SENT);
			event.setNotificationProvider(provider);
			event.setNotificationProviderMessageId(result.providerMessageId());
			event.setNotificationAttemptCount(event.getNotificationAttemptCount() + 1);
			event.setNotificationLastError(null);
			event.setNotificationSentAt(sentAt);
		}
	}

	private void markFailed(List<InformationalAlert> events, String provider, String error) {
		for (InformationalAlert event : events) {
			event.setNotificationStatus(NotificationStatus.FAILED);
			event.setNotificationProvider(provider);
			event.setNotificationAttemptCount(event.getNotificationAttemptCount() + 1);
			event.setNotificationLastError(error);
		}
	}

	private String summarize(RuntimeException ex) {
		String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
		return message.length() <= 1000 ? message : message.substring(0, 1000);
	}

	private record DigestKey(UUID userId, String ruleVersion) {
	}
}
