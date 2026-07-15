package com.freirelts.araripe_invest_api.adapters.outbound.notifications;

import com.freirelts.araripe_invest_api.application.notifications.DailyNotificationDigest;
import com.freirelts.araripe_invest_api.application.notifications.NotificationProvider;
import com.freirelts.araripe_invest_api.application.notifications.NotificationPublishResult;

class UnavailableNotificationProvider implements NotificationProvider {

	private static final String PROVIDER_NAME = "email-provider-unavailable";

	@Override
	public String providerName() {
		return PROVIDER_NAME;
	}

	@Override
	public NotificationPublishResult publish(DailyNotificationDigest digest) {
		throw new IllegalStateException("Email provider unavailable. Configure Spring Mail SMTP before publishing digests.");
	}
}
