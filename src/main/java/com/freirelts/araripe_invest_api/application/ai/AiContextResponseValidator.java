package com.freirelts.araripe_invest_api.application.ai;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class AiContextResponseValidator {

	public AiContextValidationResult validate(EconomicContextAiRequest request, AiContextStructuredOutput output) {
		if (output == null) {
			return AiContextValidationResult.invalid("AI response body is missing.");
		}
		if (isBlank(output.contextSummary())) {
			return AiContextValidationResult.invalid("AI response must include a context summary.");
		}
		if (isBlank(output.thesisImpact())) {
			return AiContextValidationResult.invalid("AI response must include thesis impact.");
		}
		if (containsOperationalGuidance(output)) {
			return AiContextValidationResult.invalid("AI response cannot guide buy, sell, hold, allocation or position changes.");
		}
		if (output.sources() == null || output.sources().isEmpty()) {
			return AiContextValidationResult.invalid("AI response must include at least one configured source.");
		}
		if (!referencesConfiguredSource(request, output)) {
			return AiContextValidationResult.invalid("AI response cites no configured source.");
		}
		if (!hasExternalWebEvidence(output)) {
			return AiContextValidationResult.invalid("AI response must include at least one external web source URL.");
		}
		if (!hasValidSourceReferenceDate(output)) {
			return AiContextValidationResult.invalid("AI response must include at least one valid source reference date.");
		}
		return AiContextValidationResult.valid();
	}

	private boolean referencesConfiguredSource(EconomicContextAiRequest request, AiContextStructuredOutput output) {
		Set<String> configuredSources = new HashSet<>();
		for (AiContextSource source : request.sources()) {
			if (!isBlank(source.name())) {
				configuredSources.add(source.name().toLowerCase(Locale.ROOT));
			}
		}
		for (String source : output.sources()) {
			if (source != null && configuredSources.contains(source.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private boolean hasExternalWebEvidence(AiContextStructuredOutput output) {
		if (output.sourceUrls() != null) {
			for (String url : output.sourceUrls()) {
				if (isExternalUrl(url)) {
					return true;
				}
			}
		}
		for (String source : output.sources()) {
			if (isExternalUrl(source)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasValidSourceReferenceDate(AiContextStructuredOutput output) {
		if (output.sourceReferenceDates() == null || output.sourceReferenceDates().isEmpty()) {
			return false;
		}
		for (String referenceDate : output.sourceReferenceDates()) {
			if (isBlank(referenceDate)) {
				continue;
			}
			try {
				LocalDate.parse(referenceDate);
				return true;
			}
			catch (DateTimeParseException ignored) {
				return false;
			}
		}
		return false;
	}

	private boolean containsOperationalGuidance(AiContextStructuredOutput output) {
		String combined = String.join(" ",
				value(output.contextSummary()),
				value(output.thesisImpact()),
				value(output.confidenceLevel()),
				values(output.positiveFactors()),
				values(output.riskFactors()));
		String normalized = normalize(combined);
		return normalized.contains("recomenda compra")
				|| normalized.contains("recomendar compra")
				|| normalized.contains("orienta compra")
				|| normalized.contains("indica compra")
				|| normalized.contains("deve comprar")
				|| normalized.contains("pode comprar")
				|| normalized.contains("recomenda venda")
				|| normalized.contains("recomendar venda")
				|| normalized.contains("orienta venda")
				|| normalized.contains("indica venda")
				|| normalized.contains("deve vender")
				|| normalized.contains("pode vender")
				|| normalized.contains("manter posicao")
				|| normalized.contains("aumentar posicao")
				|| normalized.contains("reduzir posicao")
				|| normalized.contains("encerrar posicao")
				|| normalized.contains("alocar capital")
				|| normalized.contains("fazer aporte");
	}

	private String values(Collection<String> values) {
		if (values == null || values.isEmpty()) {
			return "";
		}
		return String.join(" ", values.stream().map(this::value).toList());
	}

	private String value(String value) {
		return value == null ? "" : value;
	}

	private String normalize(String value) {
		String withoutAccents = Normalizer.normalize(value(value), Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		return withoutAccents.toLowerCase(Locale.ROOT);
	}

	private boolean isExternalUrl(String value) {
		if (isBlank(value)) {
			return false;
		}
		String normalized = value.toLowerCase(Locale.ROOT);
		return normalized.startsWith("https://") || normalized.startsWith("http://");
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
