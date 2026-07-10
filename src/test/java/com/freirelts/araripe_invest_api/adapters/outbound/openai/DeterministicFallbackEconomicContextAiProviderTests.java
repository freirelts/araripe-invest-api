package com.freirelts.araripe_invest_api.adapters.outbound.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.ai.AiAssetContext;
import com.freirelts.araripe_invest_api.application.ai.AiContextSource;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiRequest;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiResult;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import com.freirelts.araripe_invest_api.domain.recommendations.RecommendationType;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicFallbackEconomicContextAiProviderTests {

	@Test
	void returnsUnavailableTraceWhenOpenAiIsNotConfigured() {
		DeterministicFallbackEconomicContextAiProvider provider = new DeterministicFallbackEconomicContextAiProvider(
				new OpenAiProperties(true, "gpt-4.1-mini", 30, 900, "macro-sector-context-v1"), new ObjectMapper());

		EconomicContextAiResult result = provider.analyze(new EconomicContextAiRequest(
				new AiAssetContext("WEGE3", "WEG S.A.", "Bens Industriais", "Motores"),
				LocalDate.of(2026, 7, 9),
				ThesisType.QUALITY_REASONABLE_PRICE,
				RecommendationType.MANTER,
				82,
				Map.of("score", 82),
				List.of(new AiContextSource("Banco Central SGS", "https://www3.bcb.gov.br/sgspub/",
						"Juros e inflacao de referencia.")),
				List.of("IA nao pode alterar recomendacao deterministica.")));

		assertThat(result.provider()).isEqualTo(DeterministicFallbackEconomicContextAiProvider.PROVIDER);
		assertThat(result.validationStatus()).isEqualTo(AiValidationStatus.UNAVAILABLE);
		assertThat(result.outputJson()).isNull();
		assertThat(result.errorMessage()).contains("deterministic recommendation remains unchanged");
	}
}
