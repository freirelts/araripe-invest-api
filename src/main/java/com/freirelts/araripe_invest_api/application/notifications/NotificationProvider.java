package com.freirelts.araripe_invest_api.application.notifications;

public interface NotificationProvider {

	String providerName();

	NotificationPublishResult publish(DailyNotificationDigest digest);
}
