package com.freirelts.araripe_invest_api.adapters.outbound.openai;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "araripe.integrations.openai")
public record OpenAiProperties(
		boolean enabled,
		@NotBlank
		String model,
		String apiKey,
		@NotBlank
		String baseUrl,
		@Min(1)
		int timeoutSeconds,
		@Min(1)
		int maxTokens,
		@NotBlank
		String promptVersion,
		@NotBlank
		String webSearchModel,
		@NotBlank
		@Pattern(regexp = "none|low|medium|high|xhigh")
		String reasoningEffort,
		@NotBlank
		@Pattern(regexp = "low|medium|high")
		String webSearchContextSize) {
}
