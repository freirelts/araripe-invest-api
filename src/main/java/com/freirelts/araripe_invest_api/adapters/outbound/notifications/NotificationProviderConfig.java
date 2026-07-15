package com.freirelts.araripe_invest_api.adapters.outbound.notifications;

import com.freirelts.araripe_invest_api.application.notifications.NotificationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
@EnableConfigurationProperties(EmailNotificationProperties.class)
class NotificationProviderConfig {

	@Bean
	@ConditionalOnMissingBean(NotificationProvider.class)
	NotificationProvider notificationProvider(ObjectProvider<JavaMailSender> mailSenderProvider,
			EmailNotificationProperties properties) {
		JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
		if (mailSender == null) {
			return new UnavailableNotificationProvider();
		}
		return new SpringMailNotificationProvider(mailSender, properties);
	}
}
