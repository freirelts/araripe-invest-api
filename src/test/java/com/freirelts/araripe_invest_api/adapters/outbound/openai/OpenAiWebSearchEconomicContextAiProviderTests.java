package com.freirelts.araripe_invest_api.adapters.outbound.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.ai.AiAssetContext;
import com.freirelts.araripe_invest_api.application.ai.AiContextResponseValidator;
import com.freirelts.araripe_invest_api.application.ai.AiContextSource;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiRequest;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiResult;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
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
import static org.hamcrest.Matchers.containsString;
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
				.andExpect(jsonPath("$.reasoning.effort").value("high"))
				.andExpect(jsonPath("$.tools[0].type").value("web_search"))
				.andExpect(jsonPath("$.tool_choice").value("required"))
				.andExpect(jsonPath("$.max_output_tokens").value(16000))
				.andExpect(jsonPath("$.instructions").value(containsString("nao substitua Selic")))
				.andExpect(jsonPath("$.instructions").value(containsString("sourceReferenceDates")))
				.andExpect(jsonPath("$.instructions").value(containsString("nao oriente compra, venda")))
				.andExpect(jsonPath("$.input").value(containsString("Banco Central/SGS")))
				.andRespond(withSuccess(openAiResponse(), MediaType.APPLICATION_JSON));

		EconomicContextAiResult result = provider.analyze(request());

		assertThat(result.provider()).isEqualTo(OpenAiWebSearchEconomicContextAiProvider.PROVIDER);
		assertThat(result.validationStatus()).isEqualTo(AiValidationStatus.VALID);
		assertThat(result.outputJson()).contains("https://valor.example/noticia-wege");
		assertThat(result.sourcesJson()).contains("webCitations");
		assertThat(result.sourcesJson()).contains("\"openAiResponse\"");
		assertThat(result.sourcesJson()).contains("\"status\":\"completed\"");
		assertThat(result.sourcesJson()).contains("\"outputTextLength\"");
		assertThat(result.tokenUsage()).isNotNull();
		assertThat(result.tokenUsage().inputTokens()).isEqualTo(1200L);
		assertThat(result.tokenUsage().outputTokens()).isEqualTo(450L);
		assertThat(result.tokenUsage().totalTokens()).isEqualTo(1650L);
		assertThat(result.tokenUsage().reasoningTokens()).isEqualTo(300L);
		server.verify();
	}

	@Test
	void returnsFailedWithResponseAuditWhenResponseIsIncomplete() throws Exception {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		OpenAiWebSearchEconomicContextAiProvider provider = new OpenAiWebSearchEconomicContextAiProvider(
				properties("test-key"), new AiContextResponseValidator(), objectMapper, builder);

		server.expect(requestTo("https://api.openai.test/v1/responses"))
				.andRespond(withSuccess(incompleteOpenAiResponse(), MediaType.APPLICATION_JSON));

		EconomicContextAiResult result = provider.analyze(request());

		assertThat(result.validationStatus()).isEqualTo(AiValidationStatus.FAILED);
		assertThat(result.errorMessage()).contains("status is incomplete").contains("max_output_tokens");
		assertThat(result.sourcesJson()).contains("\"status\":\"incomplete\"");
		assertThat(result.sourcesJson()).contains("\"incompleteDetails\":{\"reason\":\"max_output_tokens\"}");
		assertThat(result.sourcesJson()).contains("\"usage\"");
		assertThat(result.sourcesJson()).contains("\"outputTextLength\":7");
		assertThat(result.tokenUsage()).isNotNull();
		assertThat(result.tokenUsage().inputTokens()).isEqualTo(1400L);
		assertThat(result.tokenUsage().outputTokens()).isEqualTo(16000L);
		assertThat(result.tokenUsage().totalTokens()).isEqualTo(17400L);
		assertThat(result.tokenUsage().reasoningTokens()).isEqualTo(15993L);
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
				"sourceReferenceDates", List.of("2026-07-08")));
		return objectMapper.writeValueAsString(Map.of(
				"status", "completed",
				"usage", Map.of(
						"input_tokens", 1200,
						"output_tokens", 450,
						"total_tokens", 1650,
						"output_tokens_details", Map.of("reasoning_tokens", 300)),
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

	private String incompleteOpenAiResponse() throws Exception {
		return objectMapper.writeValueAsString(Map.of(
				"status", "incomplete",
				"incomplete_details", Map.of("reason", "max_output_tokens"),
				"usage", Map.of(
						"input_tokens", 1400,
						"output_tokens", 16000,
						"total_tokens", 17400,
						"output_tokens_details", Map.of("reasoning_tokens", 15993)),
				"output_text", "partial"));
	}

	private EconomicContextAiRequest request() {
		return new EconomicContextAiRequest(
				new AiAssetContext("WEGE3", "WEG S.A.", "Bens Industriais", "Motores"),
				LocalDate.of(2026, 7, 9),
				ThesisType.QUALITY_REASONABLE_PRICE,
				82,
				Map.of("priceCeiling", "42.00"),
				List.of(new AiContextSource("Araripe Invest deterministic engine", "internal://position-theses/1",
						"Tese deterministica."),
						new AiContextSource("OpenAI Web Search", "openai://web_search",
								"Busca web obrigatoria por noticias recentes.")),
				List.of("IA nao orienta compra, venda, manutencao, aumento, reducao, alocacao ou encerramento."));
	}

	private OpenAiProperties properties(String apiKey) {
		return new OpenAiProperties(true, "gpt-5.6-luna", apiKey, "https://api.openai.test/v1", 30, 16000,
				"macro-sector-context-v1", "gpt-5.5", "high", "medium");
	}
}
