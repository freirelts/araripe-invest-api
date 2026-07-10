package com.freirelts.araripe_invest_api.adapters.outbound.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.ai.AiContextResponseValidator;
import com.freirelts.araripe_invest_api.application.ai.AiContextStructuredOutput;
import com.freirelts.araripe_invest_api.application.ai.AiContextValidationResult;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiProvider;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiRequest;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAiResult;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.TimeoutException;

@Component
class OpenAiEconomicContextAiProvider implements EconomicContextAiProvider {

	static final String PROVIDER = "openai-spring-ai";

	private final ChatClient chatClient;
	private final OpenAiProperties properties;
	private final AiContextResponseValidator validator;
	private final ObjectMapper objectMapper = new ObjectMapper();

	OpenAiEconomicContextAiProvider(ObjectProvider<ChatModel> chatModelProvider, OpenAiProperties properties,
			AiContextResponseValidator validator) {
		ChatModel chatModel = chatModelProvider.getIfAvailable();
		this.chatClient = chatModel == null ? null : ChatClient.create(chatModel);
		this.properties = properties;
		this.validator = validator;
	}

	@Override
	public EconomicContextAiResult analyze(EconomicContextAiRequest request) {
		String inputSummaryJson = json(request);
		String sourcesJson = json(request.sources());
		String systemPrompt = systemPrompt();
		String userPrompt = userPrompt(inputSummaryJson);
		String promptHash = sha256(systemPrompt + "\n" + userPrompt);
		Instant startedAt = Instant.now();

		if (!properties.enabled() || chatClient == null) {
			return EconomicContextAiResult.unavailable(PROVIDER, properties.model(), properties.promptVersion(),
					promptHash, inputSummaryJson, sourcesJson,
					"OpenAI chat model is not configured; deterministic recommendation remains unchanged.");
		}

		try {
			AiContextStructuredOutput output = chatClient.prompt()
					.options(OpenAiChatOptions.builder()
							.model(properties.model())
							.timeout(Duration.ofSeconds(properties.timeoutSeconds()))
							.maxTokens(properties.maxTokens())
							.temperature(0.1))
					.system(systemPrompt)
					.user(userPrompt)
					.call()
					.entity(AiContextStructuredOutput.class);
			AiContextValidationResult validation = validator.validate(request, output);
			return new EconomicContextAiResult(PROVIDER, properties.model(), properties.promptVersion(), promptHash,
					inputSummaryJson, json(output), sourcesJson, validation.status(), latencyMs(startedAt),
					validation.errorMessage());
		}
		catch (RuntimeException ex) {
			AiValidationStatus status = isTimeout(ex) ? AiValidationStatus.TIMEOUT : AiValidationStatus.FAILED;
			return new EconomicContextAiResult(PROVIDER, properties.model(), properties.promptVersion(), promptHash,
					inputSummaryJson, null, sourcesJson, status, latencyMs(startedAt), errorMessage(ex, status));
		}
	}

	private String systemPrompt() {
		return """
				Voce enriquece contexto macroeconomico e setorial para recomendacoes de position trade do Araripe Invest.
				Responda somente no schema estruturado solicitado pelo Spring AI.
				Regras obrigatorias:
				- nao aprove ativo bloqueado por regra deterministica;
				- nao altere stop, objetivo, preco teto, margem de seguranca, alocacao ou recomendacao base;
				- nao use promessas de lucro, certeza ou ausencia de risco;
				- cite somente fontes configuradas no input;
				- explique divergencias como conflito, mantendo a recomendacao deterministica.
				""";
	}

	private String userPrompt(String inputSummaryJson) {
		return """
				Gere contexto macro/setorial estruturado para esta tese ou recomendacao.
				Use apenas os dados e fontes abaixo. Nao inclua segredo, dado pessoal nem texto livre fora do schema.

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

	private String errorMessage(RuntimeException ex, AiValidationStatus status) {
		return "OpenAI " + status.name().toLowerCase() + ": " + ex.getClass().getSimpleName() + ".";
	}
}
