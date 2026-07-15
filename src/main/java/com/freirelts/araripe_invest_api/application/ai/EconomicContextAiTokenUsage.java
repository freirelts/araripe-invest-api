package com.freirelts.araripe_invest_api.application.ai;

public record EconomicContextAiTokenUsage(
		Long inputTokens,
		Long outputTokens,
		Long totalTokens,
		Long reasoningTokens) {
}
