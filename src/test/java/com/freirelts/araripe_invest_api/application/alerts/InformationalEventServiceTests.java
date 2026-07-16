package com.freirelts.araripe_invest_api.application.alerts;

import com.freirelts.araripe_invest_api.application.alerts.InformationalEventService.InformationalEventSummary;
import com.freirelts.araripe_invest_api.domain.alerts.InformationalEventType;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.marketdata.DailyCandle;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesis;
import com.freirelts.araripe_invest_api.domain.recommendations.Severity;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DailyCandleRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.InformationalAlertRepository;
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
@Import(InformationalEventService.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InformationalEventServiceTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 7);
	private static final String OPERATIONAL_TERMS =
			"(?i).*(compr|vend|mant[eé]m|manter|aument|reduz|encerr|sair|execut|realiz|aporte|aloca|quantidade|recomend).*";

	@Autowired
	private InformationalEventService service;

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
	private InformationalAlertRepository alertRepository;

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
	void priceThresholdReachedCreatesOnlyFactualInformationalAlert() {
		Scenario scenario = scenario("threshold@araripe.test", "WEGE3", "38.40", "33.00", "48.00", "32.90",
				DataQualityStatus.VALID, ThesisStatus.OPORTUNIDADE, 82);

		InformationalEventSummary summary = service.scanPosition(scenario.position().getId(), REFERENCE_DATE)
				.orElseThrow();

		assertThat(summary.eventType()).isEqualTo(InformationalEventType.PRICE_THRESHOLD_REACHED);
		assertThat(summary.severity()).isEqualTo(Severity.HIGH);
		assertThat(summary.title()).contains("Limiar inferior");
		assertNoOperationalTermsWerePersisted();
		assertThat(recommendationRepository.findAll()).isEmpty();
		assertThat(notificationEventRepository.findAll()).isEmpty();
	}

	@Test
	void invalidExternalPriceDataCreatesQualityBlockOnly() {
		Scenario scenario = scenario("bad-source@araripe.test", "VALE3", "60.00", "51.00", "75.00", "62.00",
				DataQualityStatus.INCOMPLETE, ThesisStatus.OPORTUNIDADE, 88);

		InformationalEventSummary summary = service.scanPosition(scenario.position().getId(), REFERENCE_DATE)
				.orElseThrow();

		assertThat(summary.eventType()).isEqualTo(InformationalEventType.QUALITY_DATA_BLOCKED);
		assertThat(summary.summary()).contains("qualidade valida");
		assertNoOperationalTermsWerePersisted();
		assertThat(recommendationRepository.findAll()).isEmpty();
		assertThat(notificationEventRepository.findAll()).isEmpty();
	}

	@Test
	void staleStudyModelCreatesDataStaleEvent() {
		User user = saveCustomer("stale-study@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("KLBN11", "Klabin", "Materiais Basicos"));
		CustomerPosition position = savePosition(user, asset, "10", "20.00", "10.00", "100.00");
		PositionThesis oldThesis = saveThesis(asset, REFERENCE_DATE.minusDays(10), ThesisStatus.OPORTUNIDADE, 84);
		positionThesisRepository.saveAndFlush(new CustomerPositionThesis(user, position, oldThesis,
				position.getAveragePrice()));
		saveCandle(asset, "21.00", DataQualityStatus.VALID);

		InformationalEventSummary summary = service.scanPosition(position.getId(), REFERENCE_DATE).orElseThrow();

		assertThat(summary.eventType()).isEqualTo(InformationalEventType.DATA_STALE);
		assertThat(summary.summary()).contains("fora da tolerancia operacional");
		assertNoOperationalTermsWerePersisted();
	}

	@Test
	void changedStudyIndicatorsCreateAssumptionChangedEventWithoutOperationalText() {
		Scenario scenario = scenario("assumption@araripe.test", "TAEE11", "35.00", "30.00", "45.00", "36.00",
				DataQualityStatus.VALID, ThesisStatus.REAVALIAR, 58);

		InformationalEventSummary summary = service.scanPosition(scenario.position().getId(), REFERENCE_DATE)
				.orElseThrow();

		assertThat(summary.eventType()).isEqualTo(InformationalEventType.STUDY_ASSUMPTION_CHANGED);
		assertThat(summary.currentStudyModelSnapshotId()).isEqualTo(scenario.currentThesis().getId());
		assertNoOperationalTermsWerePersisted();
	}

	@Test
	void informationalAlertIsIdempotentByUserAssetPositionDateEventTypeRuleVersionAndSource() {
		Scenario scenario = scenario("idempotent@araripe.test", "VIVT3", "48.00", "42.00", "60.00", "41.90",
				DataQualityStatus.VALID, ThesisStatus.OPORTUNIDADE, 82);

		InformationalEventSummary first = service.scanPosition(scenario.position().getId(), REFERENCE_DATE)
				.orElseThrow();
		InformationalEventSummary second = service.scanPosition(scenario.position().getId(), REFERENCE_DATE)
				.orElseThrow();

		assertThat(second.id()).isEqualTo(first.id());
		assertThat(alertRepository.findAll()).hasSize(1);
	}

	private void assertNoOperationalTermsWerePersisted() {
		assertThat(alertRepository.findAll()).isNotEmpty();
		alertRepository.findAll().forEach(alert -> {
			assertThat(alert.getTitle()).doesNotMatch(OPERATIONAL_TERMS);
			assertThat(alert.getSummary()).doesNotMatch(OPERATIONAL_TERMS);
			assertThat(alert.getEvidenceJson()).doesNotMatch(OPERATIONAL_TERMS);
		});
	}

	private Scenario scenario(String email, String symbol, String averagePrice, String lowerThreshold,
			String upperThreshold, String closePrice, DataQualityStatus qualityStatus, ThesisStatus thesisStatus,
			int score) {
		User user = saveCustomer(email);
		Asset asset = assetRepository.saveAndFlush(new Asset(symbol, symbol + " S.A.", "Financeiro"));
		CustomerPosition position = savePosition(user, asset, "10", averagePrice, lowerThreshold, upperThreshold);
		PositionThesis acceptedThesis = saveThesis(asset, REFERENCE_DATE.minusDays(1), ThesisStatus.OPORTUNIDADE, 82);
		PositionThesis currentThesis = saveThesis(asset, REFERENCE_DATE, thesisStatus, score);
		CustomerPositionThesis association = positionThesisRepository.saveAndFlush(new CustomerPositionThesis(user,
				position, acceptedThesis, position.getAveragePrice()));
		saveCandle(asset, closePrice, qualityStatus);
		return new Scenario(user, asset, position, association, currentThesis);
	}

	private User saveCustomer(String email) {
		User user = new User("Cliente", email, "{bcrypt}hash", SubscriptionStatus.ACTIVE);
		user.addRole(UserRoleType.CUSTOMER);
		return userRepository.saveAndFlush(user);
	}

	private CustomerPosition savePosition(User user, Asset asset, String quantity, String averagePrice,
			String lowerThreshold, String upperThreshold) {
		CustomerPosition position = new CustomerPosition(user, asset, new BigDecimal(quantity),
				new BigDecimal(averagePrice), LocalDate.of(2026, 1, 10));
		if (lowerThreshold != null) {
			position.setStopPrice(new BigDecimal(lowerThreshold));
		}
		if (upperThreshold != null) {
			position.setTargetPrice(new BigDecimal(upperThreshold));
		}
		return positionRepository.saveAndFlush(position);
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

	private record Scenario(
			User user,
			Asset asset,
			CustomerPosition position,
			CustomerPositionThesis association,
			PositionThesis currentThesis) {
	}
}
