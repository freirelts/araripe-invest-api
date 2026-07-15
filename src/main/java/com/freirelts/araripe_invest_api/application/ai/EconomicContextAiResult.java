package com.freirelts.araripe_invest_api.application.ai;

import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;

public record EconomicContextAiResult(
		String provider,
		String model,
		String promptVersion,
		String promptHash,
		String inputSummaryJson,
		String outputJson,
		String sourcesJson,
		AiValidationStatus validationStatus,
		Long latencyMs,
		String errorMessage,
		EconomicContextAiTokenUsage tokenUsage) {

	public EconomicContextAiResult(String provider, String model, String promptVersion, String promptHash,
			String inputSummaryJson, String outputJson, String sourcesJson, AiValidationStatus validationStatus,
			Long latencyMs, String errorMessage) {
		this(provider, model, promptVersion, promptHash, inputSummaryJson, outputJson, sourcesJson, validationStatus,
				latencyMs, errorMessage, null);
	}

	public static EconomicContextAiResult unavailable(String provider, String model, String promptVersion,
			String promptHash, String inputSummaryJson, String sourcesJson, String errorMessage) {
		return new EconomicContextAiResult(provider, model, promptVersion, promptHash, inputSummaryJson, null,
				sourcesJson, AiValidationStatus.UNAVAILABLE, 0L, errorMessage);
	}

	public static EconomicContextAiResult failed(String provider, String model, String promptVersion, String promptHash,
			String inputSummaryJson, String sourcesJson, Long latencyMs, String errorMessage) {
		return new EconomicContextAiResult(provider, model, promptVersion, promptHash, inputSummaryJson, null,
				sourcesJson, AiValidationStatus.FAILED, latencyMs, errorMessage);
	}
}
