package com.freirelts.araripe_invest_api.application.notifications;

import com.freirelts.araripe_invest_api.domain.alerts.InformationalEventType;
import com.freirelts.araripe_invest_api.domain.recommendations.Severity;

import java.time.LocalDate;
import java.util.UUID;

public record DailyNotificationDigestItem(
		UUID alertId,
		UUID watchItemId,
		UUID positionId,
		String symbol,
		LocalDate referenceDate,
		InformationalEventType eventType,
		Severity severity,
		String title,
		String source,
		String ruleVersion,
		String summary) {
}
