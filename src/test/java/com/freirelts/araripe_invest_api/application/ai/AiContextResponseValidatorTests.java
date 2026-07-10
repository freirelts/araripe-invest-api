package com.freirelts.araripe_invest_api.application.ai;

import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import com.freirelts.araripe_invest_api.domain.recommendations.RecommendationType;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiContextResponseValidatorTests {

	private final AiContextResponseValidator validator = new AiContextResponseValidator();

	@Test
	void acceptsStructuredResponseWithConfiguredSource() {
		AiContextValidationResult result = validator.validate(request(), new AiContextStructuredOutput(
				"Contexto macro neutro para a tese.",
				List.of("Juros sem deterioracao relevante."),
				List.of("Setor sensivel a credito."),
				"Impacto neutro sobre a tese principal.",
				"MEDIA",
				List.of("Banco Central SGS"),
				"Manter recomendacao deterministica sem alterar objetivo ou stop.",
				false));

		assertThat(result.status()).isEqualTo(AiValidationStatus.VALID);
		assertThat(result.errorMessage()).isNull();
	}

	@Test
	void rejectsResponseWithoutConfiguredSource() {
		AiContextValidationResult result = validator.validate(request(), new AiContextStructuredOutput(
				"Contexto macro neutro.",
				List.of(),
				List.of(),
				"Impacto neutro.",
				"BAIXA",
				List.of("Fonte nao configurada"),
				"Explicacao preserva recomendacao base.",
				false));

		assertThat(result.status()).isEqualTo(AiValidationStatus.INVALID);
		assertThat(result.errorMessage()).contains("configured source");
	}

	private EconomicContextAiRequest request() {
		return new EconomicContextAiRequest(
				new AiAssetContext("WEGE3", "WEG S.A.", "Bens Industriais", "Motores"),
				LocalDate.of(2026, 7, 9),
				ThesisType.QUALITY_REASONABLE_PRICE,
				RecommendationType.MANTER,
				82,
				Map.of("priceCeiling", "42.00"),
				List.of(new AiContextSource("Banco Central SGS", "https://www3.bcb.gov.br/sgspub/",
						"Juros e inflacao de referencia.")),
				List.of("IA nao pode alterar recomendacao deterministica."));
	}
}
