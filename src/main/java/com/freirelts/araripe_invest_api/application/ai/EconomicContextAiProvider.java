package com.freirelts.araripe_invest_api.application.ai;

public interface EconomicContextAiProvider {

	EconomicContextAiResult analyze(EconomicContextAiRequest request);

	default String provider() {
		return "unknown-ai-provider";
	}

	default String model() {
		return "unknown-model";
	}

	default String promptVersion() {
		return "unknown-prompt";
	}
}
