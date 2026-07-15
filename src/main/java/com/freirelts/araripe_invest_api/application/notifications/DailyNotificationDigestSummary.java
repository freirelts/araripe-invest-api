package com.freirelts.araripe_invest_api.application.notifications;

public record DailyNotificationDigestSummary(
		int pendingEvents,
		int digestsCreated,
		int digestsSent,
		int digestsFailed,
		int eventsSent,
		int eventsFailed,
		boolean skipped,
		boolean partial) {
}
