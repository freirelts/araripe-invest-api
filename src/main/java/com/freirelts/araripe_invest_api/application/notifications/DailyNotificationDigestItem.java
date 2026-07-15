package com.freirelts.araripe_invest_api.application.notifications;

import com.freirelts.araripe_invest_api.domain.notifications.NotificationEventType;
import com.freirelts.araripe_invest_api.domain.recommendations.RecommendationType;
import com.freirelts.araripe_invest_api.domain.recommendations.Severity;

import java.util.UUID;

public record DailyNotificationDigestItem(
		UUID notificationId,
		UUID recommendationId,
		UUID positionId,
		String symbol,
		RecommendationType recommendationType,
		NotificationEventType eventType,
		Severity severity,
		String summary) {
}
