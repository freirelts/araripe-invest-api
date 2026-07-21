package com.freirelts.araripe_invest_api.adapters.outbound.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.ai.AiAssetContext;
import com.freirelts.araripe_invest_api.application.ai.AiContextResponseValidator;
import com.freirelts.araripe_invest_api.application.ai.AiContextSource;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiRequest;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiResult;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiWebSearchEconomicContextAiProviderTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void callsResponsesApiWithRequiredWebSearchAndPersistsCitationUrls() throws Exception {
		StubOpenAiHttpTransport transport = new StubOpenAiHttpTransport(openAiResponse());
		OpenAiWebSearchEconomicContextAiProvider provider = new OpenAiWebSearchEconomicContextAiProvider(
				properties("test-key"), new AiContextResponseValidator(), objectMapper, restClient(transport));

		EconomicContextAiResult result = provider.analyze(request());

		JsonNode requestBody = objectMapper.readTree(transport.requestBody());
		assertThat(transport.method()).isEqualTo(HttpMethod.POST);
		assertThat(transport.uri()).isEqualTo(URI.create("https://unit-test.invalid/v1/responses"));
		assertThat(transport.authorization()).isEqualTo("Bearer test-key");
		assertThat(requestBody.path("model").asText()).isEqualTo("gpt-5.5");
		assertThat(requestBody.path("reasoning").path("effort").asText()).isEqualTo("high");
		assertThat(requestBody.path("tools").path(0).path("type").asText()).isEqualTo("web_search");
		assertThat(requestBody.path("tool_choice").asText()).isEqualTo("required");
		assertThat(requestBody.path("max_output_tokens").asInt()).isEqualTo(16000);
		assertThat(requestBody.path("instructions").asText())
				.contains("nao substitua Selic")
				.contains("sourceReferenceDates")
				.contains("nao oriente compra, venda");
		assertThat(requestBody.path("input").asText()).contains("Banco Central/SGS");
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
		assertThat(transport.callCount()).isEqualTo(1);
	}

	@Test
	void returnsFailedWithResponseAuditWhenResponseIsIncomplete() throws Exception {
		StubOpenAiHttpTransport transport = new StubOpenAiHttpTransport(incompleteOpenAiResponse());
		OpenAiWebSearchEconomicContextAiProvider provider = new OpenAiWebSearchEconomicContextAiProvider(
				properties("test-key"), new AiContextResponseValidator(), objectMapper, restClient(transport));

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
		assertThat(transport.callCount()).isEqualTo(1);
	}

	@Test
	void returnsUnavailableWhenApiKeyIsMissing() {
		StubOpenAiHttpTransport transport = new StubOpenAiHttpTransport("{}");
		OpenAiWebSearchEconomicContextAiProvider provider = new OpenAiWebSearchEconomicContextAiProvider(
				properties(""), new AiContextResponseValidator(), objectMapper, restClient(transport));

		EconomicContextAiResult result = provider.analyze(request());

		assertThat(result.validationStatus()).isEqualTo(AiValidationStatus.UNAVAILABLE);
		assertThat(result.errorMessage()).contains("API key is not configured");
		assertThat(transport.callCount()).isZero();
	}

	private RestClient restClient(StubOpenAiHttpTransport transport) {
		return RestClient.builder()
				.baseUrl("https://unit-test.invalid/v1")
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.requestFactory(transport)
				.build();
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

	private static final class StubOpenAiHttpTransport implements ClientHttpRequestFactory {

		private final String responseBody;
		private MockClientHttpRequest request;
		private int callCount;

		private StubOpenAiHttpTransport(String responseBody) {
			this.responseBody = responseBody;
		}

		@Override
		public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
			callCount++;
			request = new MockClientHttpRequest(httpMethod, uri);
			request.setResponse(new MockClientHttpResponse(responseBody.getBytes(StandardCharsets.UTF_8),
					HttpStatus.OK));
			return request;
		}

		private URI uri() {
			return request.getURI();
		}

		private HttpMethod method() {
			return request.getMethod();
		}

		private String authorization() {
			return request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		}

		private String requestBody() {
			return request.getBodyAsString(StandardCharsets.UTF_8);
		}

		private int callCount() {
			return callCount;
		}
	}
}
