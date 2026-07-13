package com.freirelts.araripe_invest_api.adapters.outbound.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.ai.AiContextResponseValidator;
import com.freirelts.araripe_invest_api.application.ai.AiContextStructuredOutput;
import com.freirelts.araripe_invest_api.application.ai.AiContextValidationResult;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiProvider;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiRequest;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiResult;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

@Component
public class OpenAiWebSearchEconomicContextAiProvider implements EconomicContextAiProvider {

	static final String PROVIDER = "openai-responses-web-search";

	private final OpenAiProperties properties;
	private final AiContextResponseValidator validator;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	@Autowired
	public OpenAiWebSearchEconomicContextAiProvider(OpenAiProperties properties, AiContextResponseValidator validator,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider) {
		this(properties, validator, new ObjectMapper(),
				restClientBuilderProvider.getIfAvailable(RestClient::builder));
	}

	OpenAiWebSearchEconomicContextAiProvider(OpenAiProperties properties, AiContextResponseValidator validator,
			ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
		this.properties = properties;
		this.validator = validator;
		this.objectMapper = objectMapper.findAndRegisterModules();
		this.restClient = restClientBuilder.baseUrl(trimTrailingSlash(properties.baseUrl()))
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}

	@Override
	public EconomicContextAiResult analyze(EconomicContextAiRequest request) {
		String inputSummaryJson = json(request);
		String systemPrompt = systemPrompt();
		String userPrompt = userPrompt(inputSummaryJson);
		String promptHash = sha256(systemPrompt + "\n" + userPrompt + "\n" + properties.webSearchModel());
		Instant startedAt = Instant.now();

		if (!properties.enabled()) {
			return unavailable(promptHash, inputSummaryJson, "OpenAI integration is disabled.");
		}
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			return unavailable(promptHash, inputSummaryJson,
					"OpenAI API key is not configured; deterministic recommendation remains unchanged.");
		}

		try {
			String responseBody = restClient.post()
					.uri("/responses")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
					.body(requestBody(systemPrompt, userPrompt))
					.retrieve()
					.body(String.class);
			JsonNode response;
			try {
				response = objectMapper.readTree(responseBody);
			}
			catch (JsonProcessingException ex) {
				return failed(promptHash, inputSummaryJson, "OpenAI web search returned invalid JSON response.",
						latencyMs(startedAt));
			}
			return resultFromResponse(request, response, promptHash, inputSummaryJson, startedAt);
		}
		catch (RuntimeException ex) {
			AiValidationStatus status = isTimeout(ex) ? AiValidationStatus.TIMEOUT : AiValidationStatus.FAILED;
			return new EconomicContextAiResult(PROVIDER, properties.webSearchModel(), properties.promptVersion(),
					promptHash, inputSummaryJson, null, json(request.sources()), status, latencyMs(startedAt),
					"OpenAI web search " + status.name().toLowerCase() + ": " + ex.getClass().getSimpleName() + ".");
		}
	}

	private Map<String, Object> requestBody(String systemPrompt, String userPrompt) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.webSearchModel());
		body.put("instructions", systemPrompt);
		body.put("input", userPrompt);
		body.put("tools", List.of(Map.of("type", "web_search", "search_context_size",
				properties.webSearchContextSize())));
		body.put("tool_choice", "required");
		body.put("include", List.of("web_search_call.action.sources"));
		body.put("max_output_tokens", properties.maxTokens());
		body.put("text", Map.of("format", Map.of("type", "json_schema", "name", "araripe_ai_context",
				"strict", true, "schema", outputSchema())));
		return body;
	}

	private Map<String, Object> outputSchema() {
		Map<String, Object> stringArray = Map.of("type", "array", "items", Map.of("type", "string"));
		Map<String, Object> propertiesSchema = new LinkedHashMap<>();
		propertiesSchema.put("contextSummary", Map.of("type", "string"));
		propertiesSchema.put("positiveFactors", stringArray);
		propertiesSchema.put("riskFactors", stringArray);
		propertiesSchema.put("thesisImpact", Map.of("type", "string"));
		propertiesSchema.put("confidenceLevel", Map.of("type", "string"));
		propertiesSchema.put("sources", stringArray);
		propertiesSchema.put("sourceUrls", stringArray);
		propertiesSchema.put("recommendationExplanation", Map.of("type", "string"));
		propertiesSchema.put("conflictsWithDeterministicRecommendation", Map.of("type", "boolean"));
		return Map.of("type", "object", "additionalProperties", false, "properties", propertiesSchema, "required",
				List.of("contextSummary", "positiveFactors", "riskFactors", "thesisImpact", "confidenceLevel",
						"sources", "sourceUrls", "recommendationExplanation",
						"conflictsWithDeterministicRecommendation"));
	}

	private EconomicContextAiResult resultFromResponse(EconomicContextAiRequest request, JsonNode response,
			String promptHash, String inputSummaryJson, Instant startedAt) {
		if (response == null) {
			return failed(promptHash, inputSummaryJson, "OpenAI web search response body is missing.",
					latencyMs(startedAt));
		}
		String status = response.path("status").asText();
		if (!status.isBlank() && !"completed".equals(status)) {
			return failed(promptHash, inputSummaryJson, "OpenAI web search response status is " + status + ".",
					latencyMs(startedAt));
		}

		String outputText = outputText(response);
		if (outputText == null || outputText.isBlank()) {
			return failed(promptHash, inputSummaryJson, "OpenAI web search response has no output text.",
					latencyMs(startedAt));
		}

		List<Map<String, String>> citations = citations(response);
		try {
			AiContextStructuredOutput output = objectMapper.readValue(outputText, AiContextStructuredOutput.class);
			output = mergeCitationUrls(output, citations);
			AiContextValidationResult validation = validator.validate(request, output);
			return new EconomicContextAiResult(PROVIDER, properties.webSearchModel(), properties.promptVersion(),
					promptHash, inputSummaryJson, json(output), sourcesJson(request, citations), validation.status(),
					latencyMs(startedAt), validation.errorMessage());
		}
		catch (JsonProcessingException ex) {
			return new EconomicContextAiResult(PROVIDER, properties.webSearchModel(), properties.promptVersion(),
					promptHash, inputSummaryJson, outputText, sourcesJson(request, citations),
					AiValidationStatus.INVALID, latencyMs(startedAt),
					"OpenAI web search returned non-structured JSON output.");
		}
	}

	private AiContextStructuredOutput mergeCitationUrls(AiContextStructuredOutput output,
			List<Map<String, String>> citations) {
		Set<String> urls = new HashSet<>();
		List<String> merged = new ArrayList<>();
		if (output.sourceUrls() != null) {
			for (String sourceUrl : output.sourceUrls()) {
				if (sourceUrl != null && urls.add(sourceUrl)) {
					merged.add(sourceUrl);
				}
			}
		}
		for (Map<String, String> citation : citations) {
			String url = citation.get("url");
			if (url != null && urls.add(url)) {
				merged.add(url);
			}
		}
		return new AiContextStructuredOutput(output.contextSummary(), output.positiveFactors(), output.riskFactors(),
				output.thesisImpact(), output.confidenceLevel(), output.sources(), merged,
				output.recommendationExplanation(), output.conflictsWithDeterministicRecommendation());
	}

	private String outputText(JsonNode response) {
		String outputText = response.path("output_text").asText(null);
		if (outputText != null && !outputText.isBlank()) {
			return outputText;
		}
		JsonNode output = response.path("output");
		if (!output.isArray()) {
			return null;
		}
		for (JsonNode item : output) {
			if (!"message".equals(item.path("type").asText())) {
				continue;
			}
			for (JsonNode content : item.path("content")) {
				if ("output_text".equals(content.path("type").asText())) {
					return content.path("text").asText(null);
				}
			}
		}
		return null;
	}

	private List<Map<String, String>> citations(JsonNode response) {
		List<Map<String, String>> citations = new ArrayList<>();
		JsonNode output = response.path("output");
		if (!output.isArray()) {
			return citations;
		}
		for (JsonNode item : output) {
			addWebSearchActionSources(citations, item.path("action").path("sources"));
			for (JsonNode content : item.path("content")) {
				for (JsonNode annotation : content.path("annotations")) {
					if ("url_citation".equals(annotation.path("type").asText())) {
						addCitation(citations, annotation.path("url").asText(null),
								annotation.path("title").asText(null));
					}
				}
			}
		}
		return citations;
	}

	private void addWebSearchActionSources(List<Map<String, String>> citations, JsonNode sources) {
		if (!sources.isArray()) {
			return;
		}
		for (JsonNode source : sources) {
			addCitation(citations, source.path("url").asText(null), source.path("title").asText(null));
		}
	}

	private void addCitation(List<Map<String, String>> citations, String url, String title) {
		if (url == null || url.isBlank()) {
			return;
		}
		Map<String, String> citation = new LinkedHashMap<>();
		citation.put("url", url);
		if (title != null && !title.isBlank()) {
			citation.put("title", title);
		}
		citations.add(citation);
	}

	private String sourcesJson(EconomicContextAiRequest request, List<Map<String, String>> citations) {
		Map<String, Object> sources = new LinkedHashMap<>();
		sources.put("configuredSources", request.sources());
		sources.put("webCitations", citations);
		return json(sources);
	}

	private EconomicContextAiResult unavailable(String promptHash, String inputSummaryJson, String message) {
		return EconomicContextAiResult.unavailable(PROVIDER, properties.webSearchModel(), properties.promptVersion(),
				promptHash, inputSummaryJson, json(List.of()), message);
	}

	private EconomicContextAiResult failed(String promptHash, String inputSummaryJson, String message, Long latencyMs) {
		return new EconomicContextAiResult(PROVIDER, properties.webSearchModel(), properties.promptVersion(),
				promptHash, inputSummaryJson, null, json(List.of()), AiValidationStatus.FAILED, latencyMs, message);
	}

	private String systemPrompt() {
		return """
				Voce enriquece contexto macroeconomico e setorial para recomendacoes de position trade do Araripe Invest.
				A analise da IA deve usar busca web real, noticias economicas recentes e fontes institucionais ou jornalisticas confiaveis.
				Responda somente no JSON estruturado solicitado.
				Regras obrigatorias:
				- use a ferramenta web_search antes de responder;
				- analise contexto economico, macro, setorial e noticias recentes relacionadas ao ativo, setor e Brasil;
				- inclua em sourceUrls as URLs externas efetivamente consultadas;
				- inclua em sources o item "OpenAI Web Search" e os nomes das fontes citadas;
				- nao aprove ativo bloqueado por regra deterministica;
				- nao altere stop, objetivo, preco teto, margem de seguranca, alocacao ou recomendacao base;
				- nao use promessas de lucro, certeza ou ausencia de risco;
				- explique divergencias como conflito, mantendo a recomendacao deterministica.
				""";
	}

	private String userPrompt(String inputSummaryJson) {
		return """
				Gere contexto macro/setorial estruturado para esta tese ou recomendacao.
				Use os dados internos abaixo apenas como base deterministica e busque noticias/fontes recentes na web.
				Priorize fontes institucionais, reguladores, empresas, B3/CVM/Banco Central e noticias economicas confiaveis.
				Nao inclua segredo, dado pessoal nem texto livre fora do JSON.

				Input:
				%s
				""".formatted(inputSummaryJson);
	}

	private Long latencyMs(Instant startedAt) {
		return Duration.between(startedAt, Instant.now()).toMillis();
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not serialize AI audit payload.", ex);
		}
	}

	private String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available.", ex);
		}
	}

	private boolean isTimeout(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof TimeoutException || current.getClass().getSimpleName().contains("Timeout")) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private String trimTrailingSlash(String value) {
		if (value == null || value.isBlank()) {
			return "https://api.openai.com/v1";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
