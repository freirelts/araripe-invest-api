package com.freirelts.araripe_invest_api.adapters.outbound.brapi;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "araripe.integrations.brapi")
public record BrapiProperties(
		@NotBlank
		String baseUrl,
		String token,
		@Min(1)
		int timeoutSeconds,
		@Min(1)
		int retryMaxAttempts) {

	boolean hasToken() {
		return token != null && !token.isBlank();
	}
}
