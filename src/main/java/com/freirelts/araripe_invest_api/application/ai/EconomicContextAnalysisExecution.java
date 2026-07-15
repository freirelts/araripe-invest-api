package com.freirelts.araripe_invest_api.application.ai;

import com.freirelts.araripe_invest_api.domain.ai.AiContextAnalysis;

public record EconomicContextAnalysisExecution(
		AiContextAnalysis analysis,
		EconomicContextAiTokenUsage tokenUsage) {
}
