package com.freirelts.araripe_invest_api.application.ai;

import com.freirelts.araripe_invest_api.domain.ai.AiContextAnalysis;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.recommendations.RecommendationType;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AiContextAnalysisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ EconomicContextAnalysisService.class, EconomicContextAnalysisServiceTests.AiProviderConfig.class })
class EconomicContextAnalysisServiceTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@Autowired
	private EconomicContextAnalysisService service;

	@Autowired
	private MutableAiProvider aiProvider;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private AiContextAnalysisRepository aiContextAnalysisRepository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void persistsValidAiResponseAssociatedToAssetAndDate() {
		Asset asset = assetRepository.saveAndFlush(new Asset("WEGE3", "WEG S.A.", "Bens Industriais"));
		aiProvider.result = validResult("hash-valid", "Contexto macro neutro.");

		AiContextAnalysis analysis = service.analyzeAndPersist(asset, request(asset));

		assertThat(analysis.getValidationStatus()).isEqualTo(AiValidationStatus.VALID);
		assertThat(analysis.getAsset()).isEqualTo(asset);
		assertThat(analysis.getReferenceDate()).isEqualTo(LocalDate.of(2026, 7, 9));
		assertThat(analysis.getPromptHash()).isEqualTo("hash-valid");
		assertThat(analysis.getOutputJson()).contains("Contexto macro neutro");
		assertThat(aiContextAnalysisRepository.findAll()).hasSize(1);
	}

	@Test
	void persistsTraceableFailureWithoutOutput() {
		Asset asset = assetRepository.saveAndFlush(new Asset("PETR4", "Petrobras PN", "Energia"));
		aiProvider.result = new EconomicContextAiResult("mock-ai", "mock-model", "macro-sector-context-v1",
				"hash-failed", "{\"asset\":\"PETR4\"}", null, "[{\"name\":\"Banco Central SGS\"}]",
				AiValidationStatus.FAILED, 35L, "OpenAI failed: RuntimeException.");

		AiContextAnalysis analysis = service.analyzeAndPersist(asset, request(asset));

		assertThat(analysis.getValidationStatus()).isEqualTo(AiValidationStatus.FAILED);
		assertThat(analysis.getOutputJson()).isNull();
		assertThat(analysis.getErrorMessage()).contains("OpenAI failed");
		assertThat(analysis.getLatencyMs()).isEqualTo(35L);
	}

	@Test
	void updatesExistingAnalysisForSameNaturalKey() {
		Asset asset = assetRepository.saveAndFlush(new Asset("VALE3", "Vale S.A.", "Materiais Basicos"));
		aiProvider.result = validResult("same-hash", "Primeira analise.");
		service.analyzeAndPersist(asset, request(asset));

		aiProvider.result = validResult("same-hash", "Analise revisada.");
		AiContextAnalysis analysis = service.analyzeAndPersist(asset, request(asset));

		assertThat(analysis.getOutputJson()).contains("Analise revisada");
		assertThat(aiContextAnalysisRepository.findAll()).hasSize(1);
	}

	private EconomicContextAiRequest request(Asset asset) {
		return new EconomicContextAiRequest(
				AiAssetContext.from(asset),
				LocalDate.of(2026, 7, 9),
				ThesisType.QUALITY_REASONABLE_PRICE,
				RecommendationType.MANTER,
				82,
				Map.of("priceCeiling", "42.00"),
				List.of(new AiContextSource("Banco Central SGS", "https://www3.bcb.gov.br/sgspub/",
						"Juros e inflacao de referencia.")),
				List.of("IA nao pode alterar recomendacao deterministica."));
	}

	private EconomicContextAiResult validResult(String promptHash, String summary) {
		return new EconomicContextAiResult("mock-ai", "mock-model", "macro-sector-context-v1", promptHash,
				"{\"asset\":\"WEGE3\"}",
				"{\"contextSummary\":\"" + summary + "\",\"sources\":[\"Banco Central SGS\"]}",
				"[{\"name\":\"Banco Central SGS\"}]", AiValidationStatus.VALID, 20L, null);
	}

	@TestConfiguration
	static class AiProviderConfig {

		@Bean
		MutableAiProvider mutableAiProvider() {
			return new MutableAiProvider();
		}
	}

	static class MutableAiProvider implements EconomicContextAiProvider {

		private EconomicContextAiResult result;

		@Override
		public EconomicContextAiResult analyze(EconomicContextAiRequest request) {
			return result;
		}
	}
}
