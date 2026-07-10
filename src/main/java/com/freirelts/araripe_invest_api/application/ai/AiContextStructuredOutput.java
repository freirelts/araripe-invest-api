package com.freirelts.araripe_invest_api.application.ai;

import java.util.List;

public record AiContextStructuredOutput(
		String contextSummary,
		List<String> positiveFactors,
		List<String> riskFactors,
		String thesisImpact,
		String confidenceLevel,
		List<String> sources,
		String recommendationExplanation,
		boolean conflictsWithDeterministicRecommendation) {
}
