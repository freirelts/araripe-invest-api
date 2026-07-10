package com.freirelts.araripe_invest_api.adapters.outbound.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiProvider;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiRequest;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiResult;

class DeterministicFallbackEconomicContextAiProvider implements EconomicContextAiProvider {

	static final String PROVIDER = "deterministic-fallback";

	private final OpenAiProperties properties;
	private final ObjectMapper objectMapper;

	DeterministicFallbackEconomicContextAiProvider(OpenAiProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Override
	public EconomicContextAiResult analyze(EconomicContextAiRequest request) {
		String inputSummaryJson = json(request);
		String sourcesJson = json(request.sources());
		return EconomicContextAiResult.unavailable(PROVIDER, properties.model(), properties.promptVersion(),
				"openai-disabled", inputSummaryJson, sourcesJson,
				"OpenAI chat model is not configured; deterministic recommendation remains unchanged.");
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			return "{}";
		}
	}
}
