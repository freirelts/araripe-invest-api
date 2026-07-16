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
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiTokenUsage;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private static final Logger log = LoggerFactory.getLogger(OpenAiWebSearchEconomicContextAiProvider.class);

	private final OpenAiProperties properties;
	private final AiContextResponseValidator validator;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	@Autowired
	public OpenAiWebSearchEconomicContextAiProvider(OpenAiProperties properties, AiContextResponseValidator validator,
			@Qualifier("openAiRestClient") RestClient openAiRestClient) {
		this(properties, validator, new ObjectMapper(), openAiRestClient);
	}

	OpenAiWebSearchEconomicContextAiProvider(OpenAiProperties properties, AiContextResponseValidator validator,
			ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
		this(properties, validator, objectMapper, restClientBuilder.baseUrl(trimTrailingSlash(properties.baseUrl()))
				.defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				.build());
	}

	OpenAiWebSearchEconomicContextAiProvider(OpenAiProperties properties, AiContextResponseValidator validator,
			ObjectMapper objectMapper, RestClient restClient) {
		this.properties = properties;
		this.validator = validator;
		this.objectMapper = objectMapper.findAndRegisterModules();
		this.restClient = restClient;
	}

	@Override
	public EconomicContextAiResult analyze(EconomicContextAiRequest request) {
		String inputSummaryJson = json(request);
		String systemPrompt = systemPrompt();
		String userPrompt = userPrompt(inputSummaryJson);
		String promptHash = sha256(systemPrompt + "\n" + userPrompt + "\n" + properties.webSearchModel() + "\n"
				+ properties.reasoningEffort());
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
				log.warn("OpenAI web search provider returned invalid JSON model={} promptVersion={} latencyMs={}",
						properties.webSearchModel(), properties.promptVersion(), latencyMs(startedAt), ex);
				return failed(promptHash, inputSummaryJson, "OpenAI web search returned invalid JSON response.",
						latencyMs(startedAt));
			}
			return resultFromResponse(request, response, promptHash, inputSummaryJson, startedAt);
		}
		catch (RuntimeException ex) {
			AiValidationStatus status = isTimeout(ex) ? AiValidationStatus.TIMEOUT : AiValidationStatus.FAILED;
			log.warn("OpenAI web search provider failed status={} model={} promptVersion={} latencyMs={}",
					status, properties.webSearchModel(), properties.promptVersion(), latencyMs(startedAt), ex);
			return new EconomicContextAiResult(PROVIDER, properties.webSearchModel(), properties.promptVersion(),
					promptHash, inputSummaryJson, null, json(request.sources()), status, latencyMs(startedAt),
					"OpenAI web search " + status.name().toLowerCase() + ": " + ex.getClass().getSimpleName() + ".");
		}
	}

	@Override
	public String provider() {
		return PROVIDER;
	}

	@Override
	public String model() {
		return properties.webSearchModel();
	}

	@Override
	public String promptVersion() {
		return properties.promptVersion();
	}

	private Map<String, Object> requestBody(String systemPrompt, String userPrompt) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.webSearchModel());
		body.put("instructions", systemPrompt);
		body.put("input", userPrompt);
		body.put("reasoning", Map.of("effort", properties.reasoningEffort()));
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
		propertiesSchema.put("sourceReferenceDates", stringArray);
		return Map.of("type", "object", "additionalProperties", false, "properties", propertiesSchema, "required",
				List.of("contextSummary", "positiveFactors", "riskFactors", "thesisImpact", "confidenceLevel",
						"sources", "sourceUrls", "sourceReferenceDates"));
	}

	private EconomicContextAiResult resultFromResponse(EconomicContextAiRequest request, JsonNode response,
			String promptHash, String inputSummaryJson, Instant startedAt) {
		if (response == null) {
			return failed(promptHash, inputSummaryJson, "OpenAI web search response body is missing.",
					latencyMs(startedAt));
		}
		String outputText = outputText(response);
		Map<String, Object> responseAudit = responseAudit(response, outputText);
		logResponseAudit(responseAudit);

		String status = response.path("status").asText();
		if (!status.isBlank() && !"completed".equals(status)) {
			return failed(promptHash, inputSummaryJson, incompleteStatusMessage(status, response),
					latencyMs(startedAt), sourcesJson(request, List.of(), responseAudit), tokenUsage(response));
		}

		if (outputText == null || outputText.isBlank()) {
			return failed(promptHash, inputSummaryJson, "OpenAI web search response has no output text.",
					latencyMs(startedAt), sourcesJson(request, List.of(), responseAudit), tokenUsage(response));
		}

		List<Map<String, String>> citations = citations(response);
		try {
			AiContextStructuredOutput output = objectMapper.readValue(outputText, AiContextStructuredOutput.class);
			output = mergeCitationUrls(output, citations);
			AiContextValidationResult validation = validator.validate(request, output);
			return new EconomicContextAiResult(PROVIDER, properties.webSearchModel(), properties.promptVersion(),
					promptHash, inputSummaryJson, json(output), sourcesJson(request, citations, responseAudit),
					validation.status(), latencyMs(startedAt), validation.errorMessage(), tokenUsage(response));
		}
		catch (JsonProcessingException ex) {
			log.warn("OpenAI web search provider returned non-structured JSON output model={} promptVersion={} latencyMs={}",
					properties.webSearchModel(), properties.promptVersion(), latencyMs(startedAt), ex);
			return new EconomicContextAiResult(PROVIDER, properties.webSearchModel(), properties.promptVersion(),
					promptHash, inputSummaryJson, outputText, sourcesJson(request, citations, responseAudit),
					AiValidationStatus.INVALID, latencyMs(startedAt),
					"OpenAI web search returned non-structured JSON output.", tokenUsage(response));
		}
	}

	private String incompleteStatusMessage(String status, JsonNode response) {
		String reason = response.path("incomplete_details").path("reason").asText(null);
		if (reason == null || reason.isBlank()) {
			return "OpenAI web search response status is " + status + ".";
		}
		return "OpenAI web search response status is " + status + " (reason: " + reason + ").";
	}

	private Map<String, Object> responseAudit(JsonNode response, String outputText) {
		Map<String, Object> audit = new LinkedHashMap<>();
		audit.put("status", textOrNull(response.path("status")));
		audit.put("incompleteDetails", jsonNodeValue(response.path("incomplete_details")));
		audit.put("usage", jsonNodeValue(response.path("usage")));
		audit.put("outputTextLength", outputText == null ? 0 : outputText.length());
		return audit;
	}

	private void logResponseAudit(Map<String, Object> responseAudit) {
		Object status = responseAudit.get("status");
		if ("completed".equals(status)) {
			log.info("OpenAI response audit: status={}, incompleteDetails={}, usage={}, outputTextLength={}",
					status, responseAudit.get("incompleteDetails"), responseAudit.get("usage"),
					responseAudit.get("outputTextLength"));
			return;
		}
		log.warn("OpenAI response audit: status={}, incompleteDetails={}, usage={}, outputTextLength={}",
				status, responseAudit.get("incompleteDetails"), responseAudit.get("usage"),
				responseAudit.get("outputTextLength"));
	}

	private String textOrNull(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		String value = node.asText(null);
		return value == null || value.isBlank() ? null : value;
	}

	private Object jsonNodeValue(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		return objectMapper.convertValue(node, Object.class);
	}

	private EconomicContextAiTokenUsage tokenUsage(JsonNode response) {
		JsonNode usage = response.path("usage");
		if (!usage.isObject()) {
			return null;
		}
		return new EconomicContextAiTokenUsage(longValue(usage.path("input_tokens")),
				longValue(usage.path("output_tokens")),
				longValue(usage.path("total_tokens")),
				reasoningTokens(usage));
	}

	private Long reasoningTokens(JsonNode usage) {
		Long directValue = longValue(usage.path("reasoning_tokens"));
		if (directValue != null) {
			return directValue;
		}
		return longValue(usage.path("output_tokens_details").path("reasoning_tokens"));
	}

	private Long longValue(JsonNode node) {
		return node != null && node.canConvertToLong() ? node.asLong() : null;
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
				output.sourceReferenceDates());
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

	private String sourcesJson(EconomicContextAiRequest request, List<Map<String, String>> citations,
			Map<String, Object> responseAudit) {
		Map<String, Object> sources = new LinkedHashMap<>();
		sources.put("configuredSources", request.sources());
		sources.put("webCitations", citations);
		sources.put("openAiResponse", responseAudit);
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

	private EconomicContextAiResult failed(String promptHash, String inputSummaryJson, String message, Long latencyMs,
			String sourcesJson, EconomicContextAiTokenUsage tokenUsage) {
		return new EconomicContextAiResult(PROVIDER, properties.webSearchModel(), properties.promptVersion(),
				promptHash, inputSummaryJson, null, sourcesJson, AiValidationStatus.FAILED, latencyMs, message,
				tokenUsage);
	}

	private String systemPrompt() {
		return """
				Voce enriquece contexto macroeconomico e setorial para modelos de estudo educacionais do Araripe Invest.
				A analise da IA deve usar busca web real, noticias economicas recentes e fontes institucionais ou jornalisticas confiaveis.
				Responda somente no JSON estruturado solicitado.
				Regras obrigatorias:
				- use a ferramenta web_search antes de responder;
				- analise contexto economico, macro, setorial e noticias recentes relacionadas ao ativo, setor e Brasil;
				- trate dados internos como fonte deterministica; nao substitua Selic, IPCA, CDI, cambio, valuation, aderencia a criterios ou risco por memoria do modelo;
				- para qualquer numero macroeconomico citado, confirme em fonte oficial ou fonte externa confiavel e inclua a URL em sourceUrls;
				- inclua em sourceReferenceDates datas no formato ISO yyyy-MM-dd que indiquem a data de referencia ou publicacao das fontes externas usadas;
				- se uma fonte externa divergir dos dados internos, descreva a divergencia como incerteza factual e nao invente um valor conciliado;
				- se nao houver evidencia auditavel para um dado macro atual, diga que o dado nao foi confirmado e mantenha confidenceLevel baixo;
				- inclua em sourceUrls as URLs externas efetivamente consultadas;
				- inclua em sources o item "OpenAI Web Search" e os nomes das fontes citadas;
				- nao aprove ativo bloqueado por regra deterministica;
				- nao altere referencia de preco, limiares do usuario, margem de seguranca, alocacao ou alerta informativo;
				- nao use promessas de lucro, certeza ou ausencia de risco;
				- nao oriente compra, venda, manutencao, aumento, reducao, alocacao ou encerramento de posicao.
				""";
	}

	private String userPrompt(String inputSummaryJson) {
		return """
				Gere contexto macro/setorial estruturado para este modelo de estudo educacional.
				Use os dados internos abaixo apenas como base deterministica e busque noticias/fontes recentes na web.
				Priorize fontes institucionais, reguladores, empresas, B3/CVM/Banco Central e noticias economicas confiaveis.
				Quando citar Selic, IPCA, CDI ou cambio, confira o dado contra Banco Central/SGS, B3, fonte oficial equivalente ou dado macro interno informado no Input.
				Se o Input trouxer macroIndicators, use esses valores como referencia interna auditavel e cite a fonte/data de referencia.
				Nao use conhecimento memorizado para preencher indicador macro ausente ou desatualizado.
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

	private static String trimTrailingSlash(String value) {
		if (value == null || value.isBlank()) {
			return "https://api.openai.com/v1";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
