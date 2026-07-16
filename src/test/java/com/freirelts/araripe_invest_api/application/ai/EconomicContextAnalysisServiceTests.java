package com.freirelts.araripe_invest_api.application.ai;

import com.freirelts.araripe_invest_api.application.indicators.IndicatorCalculationService;
import com.freirelts.araripe_invest_api.domain.ai.AiContextAnalysis;
import com.freirelts.araripe_invest_api.domain.ai.AiProcessingStatus;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.MacroIndicatorSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.marketdata.TechnicalIndicatorSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AiContextAnalysisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.MacroIndicatorSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.TechnicalIndicatorSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	private PositionThesisRepository thesisRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AiContextAnalysisRepository aiContextAnalysisRepository;

	@Autowired
	private FundamentalSnapshotRepository fundamentalRepository;

	@Autowired
	private TechnicalIndicatorSnapshotRepository technicalRepository;

	@Autowired
	private MacroIndicatorSnapshotRepository macroRepository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void persistsValidAiResponseAssociatedToThesisAndDate() {
		PositionThesis thesis = thesis("WEGE3", "WEG S.A.");
		User admin = admin();
		aiProvider.result = validResult("hash-valid", "Contexto macro neutro.");

		AiContextAnalysis analysis = service.analyzeThesis(thesis.getId(), admin.getId(), false);

		assertThat(analysis.getValidationStatus()).isEqualTo(AiValidationStatus.VALID);
		assertThat(analysis.getProcessingStatus()).isEqualTo(AiProcessingStatus.COMPLETED);
		assertThat(analysis.getThesis().getId()).isEqualTo(thesis.getId());
		assertThat(analysis.getAsset().getId()).isEqualTo(thesis.getAsset().getId());
		assertThat(analysis.getReferenceDate()).isEqualTo(LocalDate.of(2026, 7, 9));
		assertThat(analysis.getPromptHash()).isEqualTo("hash-valid");
		assertThat(analysis.getInputHash()).isNotBlank();
		assertThat(analysis.getOutputJson()).contains("Contexto macro neutro");
		assertThat(aiProvider.lastRequest.deterministicData()).containsKey("macroIndicators");
		assertThat((List<?>) aiProvider.lastRequest.deterministicData().get("macroIndicators"))
				.hasSize(2)
				.anySatisfy(indicator -> assertThat(((Map<?, ?>) indicator).get("slug")).isEqualTo("selic"));
		assertThat(aiProvider.lastRequest.deterministicData().toString())
				.contains("0.15000000")
				.doesNotContain("0.13000000");
		assertThat(aiContextAnalysisRepository.findAll()).hasSize(1);
	}

	@Test
	void persistsTraceableFailureWithoutOutput() {
		PositionThesis thesis = thesis("PETR4", "Petrobras PN");
		User admin = admin();
		aiProvider.result = new EconomicContextAiResult("mock-ai", "mock-model", "macro-sector-context-v1",
				"hash-failed", "{\"asset\":\"PETR4\"}", null, "[{\"name\":\"Banco Central SGS\"}]",
				AiValidationStatus.FAILED, 35L, "OpenAI failed: RuntimeException.");

		AiContextAnalysis analysis = service.analyzeThesis(thesis.getId(), admin.getId(), false);

		assertThat(analysis.getValidationStatus()).isEqualTo(AiValidationStatus.FAILED);
		assertThat(analysis.getProcessingStatus()).isEqualTo(AiProcessingStatus.FAILED);
		assertThat(analysis.getOutputJson()).isNull();
		assertThat(analysis.getErrorMessage()).contains("OpenAI failed");
		assertThat(analysis.getLatencyMs()).isEqualTo(35L);
	}

	@Test
	void reusesExistingAnalysisForSameInputWhenRefreshIsFalse() {
		PositionThesis thesis = thesis("VALE3", "Vale S.A.");
		User admin = admin();
		aiProvider.result = validResult("same-hash", "Primeira analise.");
		service.analyzeThesis(thesis.getId(), admin.getId(), false);

		aiProvider.result = validResult("same-hash", "Analise revisada.");
		AiContextAnalysis analysis = service.analyzeThesis(thesis.getId(), admin.getId(), false);

		assertThat(analysis.getOutputJson()).contains("Primeira analise");
		assertThat(aiContextAnalysisRepository.findAll()).hasSize(1);
	}

	@Test
	void rejectsThesisWithoutMinimumSnapshots() {
		Asset asset = assetRepository.saveAndFlush(new Asset("RADL3", "Raia Drogasil", "Saude"));
		PositionThesis thesis = new PositionThesis(asset, LocalDate.of(2026, 7, 9),
				ThesisType.QUALITY_REASONABLE_PRICE, ThesisStatus.CRITERIOS_ATENDIDOS, 82, "test-rule-v1");
		thesis.setPriceCeiling(new java.math.BigDecimal("24.00"));
		thesis.setFairPriceEstimate(new java.math.BigDecimal("28.00"));
		thesis.setSafetyMarginPercent(new java.math.BigDecimal("12.50"));
		thesis.setStopPrice(new java.math.BigDecimal("20.00"));
		thesis.setTargetPrice(new java.math.BigDecimal("30.00"));
		thesis = thesisRepository.saveAndFlush(thesis);
		var thesisId = thesis.getId();
		User admin = admin();

		assertThatThrownBy(() -> service.analyzeThesis(thesisId, admin.getId(), false))
				.hasMessageContaining("Valid fundamental and technical snapshots are required");
	}

	private PositionThesis thesis(String symbol, String name) {
		Asset asset = assetRepository.saveAndFlush(new Asset(symbol, name, "Bens Industriais"));
		PositionThesis thesis = new PositionThesis(asset, LocalDate.of(2026, 7, 9),
				ThesisType.QUALITY_REASONABLE_PRICE, ThesisStatus.CRITERIOS_ATENDIDOS, 82, "test-rule-v1");
		thesis.setPriceCeiling(new java.math.BigDecimal("42.00"));
		thesis.setFairPriceEstimate(new java.math.BigDecimal("48.00"));
		thesis.setSafetyMarginPercent(new java.math.BigDecimal("12.50"));
		thesis.setStopPrice(new java.math.BigDecimal("35.00"));
		thesis.setTargetPrice(new java.math.BigDecimal("52.00"));
		thesis = thesisRepository.saveAndFlush(thesis);
		saveFundamentals(asset, thesis.getReferenceDate());
		saveTechnical(asset, thesis.getReferenceDate());
		saveMacro(thesis.getReferenceDate());
		return thesis;
	}

	private void saveFundamentals(Asset asset, LocalDate referenceDate) {
		FundamentalSnapshot snapshot = new FundamentalSnapshot(asset, referenceDate, PeriodType.TTM,
				"araripe-indicators");
		snapshot.setCalculationVersion(IndicatorCalculationService.CALCULATION_VERSION);
		snapshot.setQualityStatus(DataQualityStatus.VALID);
		snapshot.setTrailingPe(new java.math.BigDecimal("10.00"));
		snapshot.setPriceToBook(new java.math.BigDecimal("2.10"));
		snapshot.setEnterpriseToEbitda(new java.math.BigDecimal("7.50"));
		snapshot.setRoe(new java.math.BigDecimal("0.180000"));
		snapshot.setFreeCashflow(new java.math.BigDecimal("1500000.00"));
		fundamentalRepository.saveAndFlush(snapshot);
	}

	private void saveTechnical(Asset asset, LocalDate referenceDate) {
		TechnicalIndicatorSnapshot snapshot = new TechnicalIndicatorSnapshot(asset, referenceDate,
				IndicatorCalculationService.CALCULATION_VERSION);
		snapshot.setSma200(new java.math.BigDecimal("36.00"));
		snapshot.setAvgVolume60(new java.math.BigDecimal("8000000.00"));
		snapshot.setTrendStatus(TrendStatus.HEALTHY);
		technicalRepository.saveAndFlush(snapshot);
	}

	private void saveMacro(LocalDate referenceDate) {
		MacroIndicatorSnapshot oldSelic = new MacroIndicatorSnapshot("selic", "Taxa Selic", referenceDate.minusDays(30),
				new java.math.BigDecimal("0.13000000"), "brapi");
		oldSelic.setUnit("percentual ao ano");
		macroRepository.saveAndFlush(oldSelic);

		MacroIndicatorSnapshot currentSelic = new MacroIndicatorSnapshot("selic", "Taxa Selic", referenceDate,
				new java.math.BigDecimal("0.15000000"), "brapi");
		currentSelic.setUnit("percentual ao ano");
		macroRepository.saveAndFlush(currentSelic);

		MacroIndicatorSnapshot ipca = new MacroIndicatorSnapshot("ipca12m", "IPCA 12 meses", referenceDate.minusDays(1),
				new java.math.BigDecimal("0.04500000"), "brapi");
		ipca.setUnit("percentual em 12 meses");
		macroRepository.saveAndFlush(ipca);
	}

	private User admin() {
		User user = new User("Admin", "admin-" + java.util.UUID.randomUUID() + "@araripe.test", "{noop}password",
				SubscriptionStatus.ACTIVE);
		user.addRole(UserRoleType.ADMIN);
		return userRepository.saveAndFlush(user);
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
		private EconomicContextAiRequest lastRequest;

		@Override
		public EconomicContextAiResult analyze(EconomicContextAiRequest request) {
			this.lastRequest = request;
			return result;
		}

		@Override
		public String provider() {
			return "mock-ai";
		}

		@Override
		public String model() {
			return "mock-model";
		}

		@Override
		public String promptVersion() {
			return "macro-sector-context-v1";
		}
	}
}
