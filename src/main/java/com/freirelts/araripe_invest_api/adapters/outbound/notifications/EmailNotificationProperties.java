package com.freirelts.araripe_invest_api.adapters.outbound.notifications;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "araripe.notifications.email")
public record EmailNotificationProperties(
		@NotBlank
		String from,
		String replyTo) {
}
