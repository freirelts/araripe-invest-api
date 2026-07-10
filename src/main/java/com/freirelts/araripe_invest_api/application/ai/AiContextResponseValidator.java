package com.freirelts.araripe_invest_api.application.ai;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class AiContextResponseValidator {

	public AiContextValidationResult validate(EconomicContextAiRequest request, AiContextStructuredOutput output) {
		if (output == null) {
			return AiContextValidationResult.invalid("AI response body is missing.");
		}
		if (isBlank(output.contextSummary())) {
			return AiContextValidationResult.invalid("AI response must include a context summary.");
		}
		if (isBlank(output.thesisImpact())) {
			return AiContextValidationResult.invalid("AI response must include thesis impact.");
		}
		if (isBlank(output.recommendationExplanation())) {
			return AiContextValidationResult.invalid("AI response must explain the deterministic recommendation.");
		}
		if (output.sources() == null || output.sources().isEmpty()) {
			return AiContextValidationResult.invalid("AI response must include at least one configured source.");
		}
		if (!referencesConfiguredSource(request, output)) {
			return AiContextValidationResult.invalid("AI response cites no configured source.");
		}
		return AiContextValidationResult.valid();
	}

	private boolean referencesConfiguredSource(EconomicContextAiRequest request, AiContextStructuredOutput output) {
		Set<String> configuredSources = new HashSet<>();
		for (AiContextSource source : request.sources()) {
			if (!isBlank(source.name())) {
				configuredSources.add(source.name().toLowerCase(Locale.ROOT));
			}
		}
		for (String source : output.sources()) {
			if (source != null && configuredSources.contains(source.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
