package com.freirelts.araripe_invest_api.application.recommendations;

import com.freirelts.araripe_invest_api.application.recommendations.PositionRecommendationService.RecommendationSummary;
import com.freirelts.araripe_invest_api.application.risk.RiskAllocationService;
import com.freirelts.araripe_invest_api.domain.ai.AiContextAnalysis;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.marketdata.DailyCandle;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationEventType;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesis;
import com.freirelts.araripe_invest_api.domain.recommendations.RecommendationType;
import com.freirelts.araripe_invest_api.domain.recommendations.Severity;
import com.freirelts.araripe_invest_api.domain.thesis.AllocationPlan;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AiContextAnalysisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AllocationPlanRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DailyCandleRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.NotificationEventRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionRecommendationRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@Import({ PositionRecommendationService.class, RiskAllocationService.class })
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PositionRecommendationServiceTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 7);

	@Autowired
	private PositionRecommendationService service;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private CustomerPositionRepository positionRepository;

	@Autowired
	private CustomerPositionThesisRepository positionThesisRepository;

	@Autowired
	private PositionThesisRepository thesisRepository;

	@Autowired
	private DailyCandleRepository dailyCandleRepository;

	@Autowired
	private AllocationPlanRepository allocationPlanRepository;

	@Autowired
	private AiContextAnalysisRepository aiContextAnalysisRepository;

	@Autowired
	private PositionRecommendationRepository recommendationRepository;

	@Autowired
	private NotificationEventRepository notificationEventRepository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void stopReachedGeneratesExecuteStopAndNotification() {
		Scenario scenario = scenario("stop@araripe.test", "WEGE3", "38.40", "33.00", "48.00", "32.90",
				ThesisStatus.OPORTUNIDADE, 82);

		RecommendationSummary summary = service.recommendPosition(scenario.position().getId(), REFERENCE_DATE);

		assertThat(summary.recommendationType()).isEqualTo(RecommendationType.EXECUTAR_STOP);
		assertThat(summary.severity()).isEqualTo(Severity.CRITICAL);
		assertThat(summary.customerPositionThesisId()).isEqualTo(scenario.association().getId());
		assertThat(summary.currentThesisId()).isEqualTo(scenario.currentThesis().getId());
		assertThat(notificationEventRepository.findAll())
				.singleElement()
				.satisfies(notification -> assertThat(notification.getEventType())
						.isEqualTo(NotificationEventType.STOP_TRIGGERED));
	}

	@Test
	void targetReachedGeneratesRealizeTargetAndNotification() {
		Scenario scenario = scenario("target@araripe.test", "ITUB4", "38.40", "33.00", "48.00", "48.20",
				ThesisStatus.OPORTUNIDADE, 82);

		RecommendationSummary summary = service.recommendPosition(scenario.position().getId(), REFERENCE_DATE);

		assertThat(summary.recommendationType()).isEqualTo(RecommendationType.REALIZAR_OBJETIVO);
		assertThat(summary.currentPrice()).isEqualByComparingTo("48.200000");
		assertThat(notificationEventRepository.findAll())
				.singleElement()
				.satisfies(notification -> assertThat(notification.getEventType())
						.isEqualTo(NotificationEventType.TARGET_REACHED));
	}

	@Test
	void positionWithoutActiveMainThesisNeverGeneratesIncrease() {
		User user = saveCustomer("no-thesis@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("PETR4", "Petrobras PN", "Energia"));
		CustomerPosition position = savePosition(user, asset, "100", "37.00", "31.00", "48.00");
		saveCandle(asset, "37.50", DataQualityStatus.VALID);

		RecommendationSummary summary = service.recommendPosition(position.getId(), REFERENCE_DATE);

		assertThat(summary.recommendationType()).isEqualTo(RecommendationType.REAVALIAR);
		assertThat(summary.customerPositionThesisId()).isNull();
		assertThat(summary.finalMessage()).contains("sem tese principal ativa");
		assertThat(notificationEventRepository.findAll()).isEmpty();
	}

	@Test
	void insufficientPriceDataGeneratesReassessmentAndNeverFalseRecommendation() {
		User user = saveCustomer("bad-data@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("VALE3", "Vale S.A.", "Materiais Basicos"));
		CustomerPosition position = savePosition(user, asset, "100", "60.00", "51.00", "75.00");
		PositionThesis currentThesis = saveThesis(asset, ThesisStatus.OPORTUNIDADE, 88);
		positionThesisRepository.saveAndFlush(new CustomerPositionThesis(user, position, currentThesis,
				position.getAveragePrice()));
		saveCandle(asset, "62.00", DataQualityStatus.INCOMPLETE);

		RecommendationSummary summary = service.recommendPosition(position.getId(), REFERENCE_DATE);

		assertThat(summary.recommendationType()).isEqualTo(RecommendationType.REAVALIAR);
		assertThat(summary.finalMessage()).contains("qualidade valida");
		assertThat(notificationEventRepository.findAll())
				.singleElement()
				.satisfies(notification -> assertThat(notification.getEventType())
						.isEqualTo(NotificationEventType.REASSESSMENT_REQUIRED));
	}

	@Test
	void validThesisAllocationAndValuationCanGenerateIncrease() {
		Scenario scenario = scenario("increase@araripe.test", "EGIE3", "40.00", "34.00", "55.00", "41.00",
				ThesisStatus.APORTE_PLANEJADO, 84);
		saveAllocationPlan(scenario.currentThesis(), 50);

		RecommendationSummary summary = service.recommendPosition(scenario.position().getId(), REFERENCE_DATE);

		assertThat(summary.recommendationType()).isEqualTo(RecommendationType.AUMENTAR_POSICAO);
		assertThat(summary.finalMessage()).contains("aumento planejado");
		assertThat(notificationEventRepository.findAll()).isEmpty();
	}

	@Test
	void increaseIsBlockedWhenCustomerCurrentExposureAlreadyExceedsAssetLimit() {
		Scenario scenario = scenario("asset-limit@araripe.test", "CPFE3", "40.00", "34.00", "55.00", "41.00",
				ThesisStatus.APORTE_PLANEJADO, 84, "30");
		saveAllocationPlan(scenario.currentThesis(), 50);

		RecommendationSummary summary = service.recommendPosition(scenario.position().getId(), REFERENCE_DATE);

		assertThat(summary.recommendationType()).isEqualTo(RecommendationType.MANTER);
		assertThat(summary.finalMessage()).contains("sem gatilho deterministico");
		assertThat(notificationEventRepository.findAll()).isEmpty();
	}

	@Test
	void validAiContextIsAttachedAndConflictIsAuditedWithoutChangingDeterministicDecision() {
		Scenario scenario = scenario("ai-conflict@araripe.test", "TAEE11", "35.00", "30.00", "45.00", "36.00",
				ThesisStatus.OPORTUNIDADE, 82);
		saveValidAiContext(scenario.currentThesis(), true);

		RecommendationSummary summary = service.recommendPosition(scenario.position().getId(), REFERENCE_DATE);

		assertThat(summary.recommendationType()).isEqualTo(RecommendationType.MANTER);
		assertThat(summary.aiContextAvailable()).isTrue();
		assertThat(summary.finalMessage()).contains("Divergencia registrada");
	}

	@Test
	void unavailableAiDoesNotBlockDeterministicRecommendation() {
		Scenario scenario = scenario("ai-failed@araripe.test", "BBAS3", "28.00", "24.00", "35.00", "35.10",
				ThesisStatus.OPORTUNIDADE, 82);
		saveUnavailableAiContext(scenario.currentThesis());

		RecommendationSummary summary = service.recommendPosition(scenario.position().getId(), REFERENCE_DATE);

		assertThat(summary.recommendationType()).isEqualTo(RecommendationType.REALIZAR_OBJETIVO);
		assertThat(summary.aiContextAvailable()).isFalse();
		assertThat(notificationEventRepository.findAll()).hasSize(1);
	}

	@Test
	void recommendationIsIdempotentByUserPositionDateAndRuleVersion() {
		Scenario scenario = scenario("idempotent@araripe.test", "VIVT3", "48.00", "42.00", "60.00", "41.90",
				ThesisStatus.OPORTUNIDADE, 82);

		RecommendationSummary first = service.recommendPosition(scenario.position().getId(), REFERENCE_DATE);
		RecommendationSummary second = service.recommendPosition(scenario.position().getId(), REFERENCE_DATE);

		assertThat(second.id()).isEqualTo(first.id());
		assertThat(recommendationRepository.findAll()).hasSize(1);
		assertThat(notificationEventRepository.findAll()).hasSize(1);
	}

	private Scenario scenario(String email, String symbol, String averagePrice, String stopPrice, String targetPrice,
			String closePrice, ThesisStatus thesisStatus, int score) {
		return scenario(email, symbol, averagePrice, stopPrice, targetPrice, closePrice, thesisStatus, score, "10");
	}

	private Scenario scenario(String email, String symbol, String averagePrice, String stopPrice, String targetPrice,
			String closePrice, ThesisStatus thesisStatus, int score, String quantity) {
		User user = saveCustomer(email);
		Asset asset = assetRepository.saveAndFlush(new Asset(symbol, symbol + " S.A.", "Financeiro"));
		CustomerPosition position = savePosition(user, asset, quantity, averagePrice, stopPrice, targetPrice);
		PositionThesis acceptedThesis = saveThesis(asset, REFERENCE_DATE.minusDays(1), ThesisStatus.OPORTUNIDADE, 80);
		PositionThesis currentThesis = saveThesis(asset, REFERENCE_DATE, thesisStatus, score);
		CustomerPositionThesis association = positionThesisRepository.saveAndFlush(new CustomerPositionThesis(user,
				position, acceptedThesis, position.getAveragePrice()));
		saveCandle(asset, closePrice, DataQualityStatus.VALID);
		return new Scenario(user, asset, position, association, currentThesis);
	}

	private User saveCustomer(String email) {
		User user = new User("Cliente", email, "{bcrypt}hash", SubscriptionStatus.ACTIVE);
		user.addRole(UserRoleType.CUSTOMER);
		return userRepository.saveAndFlush(user);
	}

	private CustomerPosition savePosition(User user, Asset asset, String quantity, String averagePrice, String stopPrice,
			String targetPrice) {
		CustomerPosition position = new CustomerPosition(user, asset, new BigDecimal(quantity),
				new BigDecimal(averagePrice), LocalDate.of(2026, 1, 10));
		position.setStopPrice(new BigDecimal(stopPrice));
		position.setTargetPrice(new BigDecimal(targetPrice));
		return positionRepository.saveAndFlush(position);
	}

	private PositionThesis saveThesis(Asset asset, ThesisStatus status, int score) {
		return saveThesis(asset, REFERENCE_DATE, status, score);
	}

	private PositionThesis saveThesis(Asset asset, LocalDate referenceDate, ThesisStatus status, int score) {
		PositionThesis thesis = new PositionThesis(asset, referenceDate, ThesisType.QUALITY_REASONABLE_PRICE, status,
				score, "rules-v1");
		thesis.setPriceCeiling(new BigDecimal("42.000000"));
		thesis.setFairPriceEstimate(new BigDecimal("52.000000"));
		thesis.setSafetyMarginPercent(new BigDecimal("0.190000"));
		thesis.setStopPrice(new BigDecimal("34.000000"));
		thesis.setTargetPrice(new BigDecimal("55.000000"));
		return thesisRepository.saveAndFlush(thesis);
	}

	private DailyCandle saveCandle(Asset asset, String closePrice, DataQualityStatus qualityStatus) {
		DailyCandle candle = new DailyCandle(asset, REFERENCE_DATE, new BigDecimal(closePrice),
				new BigDecimal(closePrice), new BigDecimal(closePrice), new BigDecimal(closePrice), "brapi");
		candle.setQualityStatus(qualityStatus);
		return dailyCandleRepository.saveAndFlush(candle);
	}

	private AllocationPlan saveAllocationPlan(PositionThesis thesis, int suggestedQuantity) {
		AllocationPlan plan = new AllocationPlan(thesis);
		plan.setCapitalBase(new BigDecimal("10000.00"));
		plan.setTargetAllocationPercent(new BigDecimal("10.000000"));
		plan.setMaxAllocationPerAssetPercent(new BigDecimal("10.000000"));
		plan.setMaxPositionValue(new BigDecimal("1000.00"));
		plan.setCurrentPrice(new BigDecimal("41.000000"));
		plan.setPriceCeiling(new BigDecimal("42.000000"));
		plan.setSuggestedQuantity(suggestedQuantity);
		plan.setRecommendedAction("APORTE_PLANEJADO");
		plan.setValid(true);
		return allocationPlanRepository.saveAndFlush(plan);
	}

	private AiContextAnalysis saveValidAiContext(PositionThesis thesis, boolean conflict) {
		AiContextAnalysis analysis = new AiContextAnalysis(thesis.getAsset(), REFERENCE_DATE, "mock-ai", "mock-model",
				"macro-sector-context-v1", "hash-" + thesis.getAsset().getSymbol());
		analysis.setThesis(thesis);
		analysis.setInputSummaryJson("{}");
		analysis.setSourcesJson("[{\"name\":\"Banco Central SGS\"}]");
		analysis.setOutputJson("{\"contextSummary\":\"Contexto neutro\",\"conflictsWithDeterministicRecommendation\":"
				+ conflict + "}");
		analysis.setValidationStatus(AiValidationStatus.VALID);
		return aiContextAnalysisRepository.saveAndFlush(analysis);
	}

	private AiContextAnalysis saveUnavailableAiContext(PositionThesis thesis) {
		AiContextAnalysis analysis = new AiContextAnalysis(thesis.getAsset(), REFERENCE_DATE, "mock-ai", "mock-model",
				"macro-sector-context-v1", "failed-" + thesis.getAsset().getSymbol());
		analysis.setThesis(thesis);
		analysis.setInputSummaryJson("{}");
		analysis.setSourcesJson("[]");
		analysis.setValidationStatus(AiValidationStatus.UNAVAILABLE);
		analysis.setErrorMessage("OpenAI indisponivel.");
		return aiContextAnalysisRepository.saveAndFlush(analysis);
	}

	private record Scenario(
			User user,
			Asset asset,
			CustomerPosition position,
			CustomerPositionThesis association,
			PositionThesis currentThesis) {
	}

}
