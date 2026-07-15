package com.freirelts.araripe_invest_api.application.notifications;

import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationEvent;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationStatus;
import com.freirelts.araripe_invest_api.infrastructure.persistence.NotificationEventRepository;
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

	private final NotificationEventRepository notificationEventRepository;
	private final NotificationProvider notificationProvider;

	public NotificationDigestService(NotificationEventRepository notificationEventRepository,
			NotificationProvider notificationProvider) {
		this.notificationEventRepository = notificationEventRepository;
		this.notificationProvider = notificationProvider;
	}

	@Transactional
	public DailyNotificationDigestSummary publishDailyDigest(LocalDate referenceDate) {
		List<NotificationEvent> pendingEvents = notificationEventRepository.findDigestCandidates(referenceDate,
				NotificationChannel.EMAIL, NotificationStatus.PENDING);
		if (pendingEvents.isEmpty()) {
			log.info("Daily notification digest skipped referenceDate={} because there are no pending email events",
					referenceDate);
			return new DailyNotificationDigestSummary(0, 0, 0, 0, 0, 0, true, false);
		}

		Map<DigestKey, List<NotificationEvent>> groupedEvents = groupedByUserAndRuleVersion(pendingEvents);
		int digestsSent = 0;
		int digestsFailed = 0;
		int eventsSent = 0;
		int eventsFailed = 0;
		for (List<NotificationEvent> events : groupedEvents.values()) {
			DailyNotificationDigest digest = toDigest(referenceDate, events);
			try {
				NotificationPublishResult result = notificationProvider.publish(digest);
				markSent(events, result);
				digestsSent++;
				eventsSent += events.size();
				log.info("Daily notification digest sent referenceDate={} userId={} items={} provider={} messageId={}",
						referenceDate, digest.userId(), digest.items().size(), result.provider(),
						result.providerMessageId());
			}
			catch (RuntimeException ex) {
				markFailed(events, notificationProvider.providerName(), summarize(ex));
				digestsFailed++;
				eventsFailed += events.size();
				log.warn("Daily notification digest failed referenceDate={} userId={} items={} provider={} error={}",
						referenceDate, digest.userId(), digest.items().size(), notificationProvider.providerName(),
						summarize(ex));
			}
		}
		notificationEventRepository.saveAllAndFlush(pendingEvents);
		return new DailyNotificationDigestSummary(pendingEvents.size(), groupedEvents.size(), digestsSent,
				digestsFailed, eventsSent, eventsFailed, false, digestsFailed > 0);
	}

	private Map<DigestKey, List<NotificationEvent>> groupedByUserAndRuleVersion(List<NotificationEvent> events) {
		Map<DigestKey, List<NotificationEvent>> grouped = new LinkedHashMap<>();
		for (NotificationEvent event : events) {
			DigestKey key = new DigestKey(event.getUser().getId(), event.getRuleVersion());
			grouped.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(event);
		}
		return grouped;
	}

	private DailyNotificationDigest toDigest(LocalDate referenceDate, List<NotificationEvent> events) {
		NotificationEvent first = events.getFirst();
		return new DailyNotificationDigest(first.getUser().getId(), first.getUser().getName(), first.getUser().getEmail(),
				referenceDate, NotificationChannel.EMAIL, first.getRuleVersion(), subject(referenceDate),
				events.stream().map(this::toItem).toList());
	}

	private DailyNotificationDigestItem toItem(NotificationEvent event) {
		return new DailyNotificationDigestItem(event.getId(), event.getRecommendation().getId(),
				event.getPosition().getId(), event.getAsset().getSymbol(), event.getRecommendationType(),
				event.getEventType(), event.getSeverity(), event.getSummary());
	}

	private String subject(LocalDate referenceDate) {
		return "Araripe Invest - recomendacoes da sua carteira em " + referenceDate.format(SUBJECT_DATE_FORMAT);
	}

	private void markSent(List<NotificationEvent> events, NotificationPublishResult result) {
		Instant sentAt = Instant.now();
		String provider = result.provider() == null || result.provider().isBlank() ? notificationProvider.providerName()
				: result.provider();
		for (NotificationEvent event : events) {
			event.setStatus(NotificationStatus.SENT);
			event.setProvider(provider);
			event.setProviderMessageId(result.providerMessageId());
			event.setAttemptCount(event.getAttemptCount() + 1);
			event.setLastError(null);
			event.setSentAt(sentAt);
		}
	}

	private void markFailed(List<NotificationEvent> events, String provider, String error) {
		for (NotificationEvent event : events) {
			event.setStatus(NotificationStatus.FAILED);
			event.setProvider(provider);
			event.setAttemptCount(event.getAttemptCount() + 1);
			event.setLastError(error);
		}
	}

	private String summarize(RuntimeException ex) {
		String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
		return message.length() <= 1000 ? message : message.substring(0, 1000);
	}

	private record DigestKey(UUID userId, String ruleVersion) {
	}
}
