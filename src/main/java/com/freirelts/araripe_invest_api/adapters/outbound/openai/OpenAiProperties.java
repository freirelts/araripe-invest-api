package com.freirelts.araripe_invest_api.adapters.outbound.openai;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "araripe.integrations.openai")
public record OpenAiProperties(
		boolean enabled,
		@NotBlank
		String model,
		@Min(1)
		int timeoutSeconds,
		@Min(1)
		int maxTokens,
		@NotBlank
		String promptVersion) {
}
