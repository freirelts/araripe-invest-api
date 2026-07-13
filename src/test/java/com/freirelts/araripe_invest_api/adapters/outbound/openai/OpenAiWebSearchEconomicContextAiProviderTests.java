package com.freirelts.araripe_invest_api.adapters.outbound.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.ai.AiAssetContext;
import com.freirelts.araripe_invest_api.application.ai.AiContextResponseValidator;
import com.freirelts.araripe_invest_api.application.ai.AiContextSource;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiRequest;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiResult;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import com.freirelts.araripe_invest_api.domain.recommendations.RecommendationType;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiWebSearchEconomicContextAiProviderTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void callsResponsesApiWithRequiredWebSearchAndPersistsCitationUrls() throws Exception {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		OpenAiWebSearchEconomicContextAiProvider provider = new OpenAiWebSearchEconomicContextAiProvider(
				properties("test-key"), new AiContextResponseValidator(), objectMapper, builder);

		server.expect(requestTo("https://api.openai.test/v1/responses"))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
				.andExpect(jsonPath("$.model").value("gpt-5.5"))
				.andExpect(jsonPath("$.tools[0].type").value("web_search"))
				.andExpect(jsonPath("$.tool_choice").value("required"))
				.andRespond(withSuccess(openAiResponse(), MediaType.APPLICATION_JSON));

		EconomicContextAiResult result = provider.analyze(request());

		assertThat(result.provider()).isEqualTo(OpenAiWebSearchEconomicContextAiProvider.PROVIDER);
		assertThat(result.validationStatus()).isEqualTo(AiValidationStatus.VALID);
		assertThat(result.outputJson()).contains("https://valor.example/noticia-wege");
		assertThat(result.sourcesJson()).contains("webCitations");
		server.verify();
	}

	@Test
	void returnsUnavailableWhenApiKeyIsMissing() {
		OpenAiWebSearchEconomicContextAiProvider provider = new OpenAiWebSearchEconomicContextAiProvider(
				properties(""), new AiContextResponseValidator(), objectMapper, RestClient.builder());

		EconomicContextAiResult result = provider.analyze(request());

		assertThat(result.validationStatus()).isEqualTo(AiValidationStatus.UNAVAILABLE);
		assertThat(result.errorMessage()).contains("API key is not configured");
	}

	private String openAiResponse() throws Exception {
		String outputText = objectMapper.writeValueAsString(Map.of(
				"contextSummary", "Noticias recentes indicam demanda industrial estavel no Brasil.",
				"positiveFactors", List.of("Ciclo de juros menos restritivo ajuda bens de capital."),
				"riskFactors", List.of("Cambio pode pressionar custos importados."),
				"thesisImpact", "Impacto neutro a levemente positivo sobre a tese.",
				"confidenceLevel", "MEDIA",
				"sources", List.of("OpenAI Web Search", "Valor Economico"),
				"sourceUrls", List.of("https://valor.example/noticia-wege"),
				"recommendationExplanation", "Contexto apenas explica a recomendacao deterministica.",
				"conflictsWithDeterministicRecommendation", false));
		return objectMapper.writeValueAsString(Map.of(
				"status", "completed",
				"output", List.of(
						Map.of("type", "web_search_call", "status", "completed", "action",
								Map.of("type", "search", "query", "WEGE3 noticias economia Brasil", "sources",
										List.of(Map.of("url", "https://valor.example/noticia-wege", "title",
												"WEG e contexto industrial")))),
						Map.of("type", "message", "content",
								List.of(Map.of("type", "output_text", "text", outputText, "annotations",
										List.of(Map.of("type", "url_citation", "url",
												"https://valor.example/noticia-wege", "title",
												"WEG e contexto industrial"))))))));
	}

	private EconomicContextAiRequest request() {
		return new EconomicContextAiRequest(
				new AiAssetContext("WEGE3", "WEG S.A.", "Bens Industriais", "Motores"),
				LocalDate.of(2026, 7, 9),
				ThesisType.QUALITY_REASONABLE_PRICE,
				RecommendationType.MANTER,
				82,
				Map.of("priceCeiling", "42.00"),
				List.of(new AiContextSource("Araripe Invest deterministic engine", "internal://position-theses/1",
						"Tese deterministica."),
						new AiContextSource("OpenAI Web Search", "openai://web_search",
								"Busca web obrigatoria por noticias recentes.")),
				List.of("IA nao pode alterar recomendacao deterministica."));
	}

	private OpenAiProperties properties(String apiKey) {
		return new OpenAiProperties(true, "gpt-5.6-luna", apiKey, "https://api.openai.test/v1", 30, 900,
				"macro-sector-context-v1", "gpt-5.5", "medium");
	}
}
