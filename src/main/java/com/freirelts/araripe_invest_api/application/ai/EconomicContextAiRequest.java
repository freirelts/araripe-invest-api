package com.freirelts.araripe_invest_api.application.ai;

import com.freirelts.araripe_invest_api.domain.recommendations.RecommendationType;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record EconomicContextAiRequest(
		AiAssetContext asset,
		LocalDate referenceDate,
		ThesisType thesisType,
		RecommendationType deterministicRecommendation,
		Integer deterministicScore,
		Map<String, Object> deterministicData,
		List<AiContextSource> sources,
		List<String> nonViolationRules) {

	public EconomicContextAiRequest {
		deterministicData = deterministicData == null ? Map.of() : Map.copyOf(deterministicData);
		sources = sources == null ? List.of() : List.copyOf(sources);
		nonViolationRules = nonViolationRules == null ? List.of() : List.copyOf(nonViolationRules);
	}
}
