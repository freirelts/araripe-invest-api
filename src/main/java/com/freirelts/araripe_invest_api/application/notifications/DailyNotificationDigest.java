package com.freirelts.araripe_invest_api.application.notifications;

import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailyNotificationDigest(
		UUID userId,
		String recipientName,
		String recipientEmail,
		LocalDate referenceDate,
		NotificationChannel channel,
		String ruleVersion,
		String subject,
		List<DailyNotificationDigestItem> items) {
}
