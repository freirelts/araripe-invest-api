package com.freirelts.araripe_invest_api.domain;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.alerts.AssetWatchItem;
import com.freirelts.araripe_invest_api.domain.alerts.AssetWatchStatus;
import com.freirelts.araripe_invest_api.domain.alerts.InformationalAlert;
import com.freirelts.araripe_invest_api.domain.alerts.InformationalEventType;
import com.freirelts.araripe_invest_api.domain.marketdata.DailyCandle;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationEvent;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationEventType;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesis;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesisStatus;
import com.freirelts.araripe_invest_api.domain.recommendations.PositionRecommendation;
import com.freirelts.araripe_invest_api.domain.recommendations.RecommendationType;
import com.freirelts.araripe_invest_api.domain.recommendations.Severity;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetWatchItemRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DailyCandleRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.NotificationEventRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.InformationalAlertRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionRecommendationRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DomainPersistenceTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private AssetWatchItemRepository assetWatchItemRepository;

	@Autowired
	private DailyCandleRepository dailyCandleRepository;

	@Autowired
	private FundamentalSnapshotRepository fundamentalSnapshotRepository;

	@Autowired
	private CustomerPositionRepository customerPositionRepository;

	@Autowired
	private CustomerPositionThesisRepository customerPositionThesisRepository;

	@Autowired
	private PositionThesisRepository positionThesisRepository;

	@Autowired
	private PositionRecommendationRepository recommendationRepository;

	@Autowired
	private NotificationEventRepository notificationEventRepository;

	@Autowired
	private InformationalAlertRepository informationalAlertRepository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void customerNeedsValidSubscriptionWhileAdminDoesNot() {
		User customer = new User("Cliente", "cliente@araripe.test", "{bcrypt}hash", SubscriptionStatus.ACTIVE);
		customer.addRole(UserRoleType.CUSTOMER);

		User admin = new User("Admin", "admin@araripe.test", "{bcrypt}hash", SubscriptionStatus.NONE);
		admin.addRole(UserRoleType.ADMIN);

		userRepository.saveAndFlush(customer);
		userRepository.saveAndFlush(admin);

		assertThat(customer.hasRole(UserRoleType.CUSTOMER)).isTrue();
		assertThat(customer.hasValidAuthenticatedAccess()).isTrue();
		assertThat(admin.hasRole(UserRoleType.ADMIN)).isTrue();
		assertThat(admin.hasValidAuthenticatedAccess()).isTrue();
	}

	@Test
	void customerPositionCanBePersistedAndClosedWithHistory() {
		User user = saveCustomer("position@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("WEGE3", "WEG S.A.", "Bens Industriais"));

		CustomerPosition position = new CustomerPosition(user, asset, new BigDecimal("10"), new BigDecimal("38.40"),
				LocalDate.of(2026, 7, 7));
		position.setStopPrice(new BigDecimal("33.00"));
		position.setTargetPrice(new BigDecimal("48.00"));

		customerPositionRepository.saveAndFlush(position);

		assertThat(position.isOpenAndValidForDailyScan()).isTrue();

		position.close();
		customerPositionRepository.saveAndFlush(position);

		assertThat(position.isOpenAndValidForDailyScan()).isFalse();
		assertThat(position.getClosedAt()).isNotNull();
	}

	@Test
	void customerPositionCanHaveActiveMainThesisWithAcceptedSnapshot() {
		User user = saveCustomer("thesis-association@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("WEGE3", "WEG S.A.", "Bens Industriais"));
		CustomerPosition position = customerPositionRepository.saveAndFlush(new CustomerPosition(user, asset,
				new BigDecimal("10"), new BigDecimal("38.40"), LocalDate.of(2026, 7, 7)));
		PositionThesis acceptedThesis = positionThesisRepository.saveAndFlush(thesis(asset, LocalDate.of(2026, 7, 7),
				ThesisType.QUALITY_REASONABLE_PRICE, 82, "rules-v1"));

		CustomerPositionThesis association = customerPositionThesisRepository.saveAndFlush(new CustomerPositionThesis(
				user, position, acceptedThesis, new BigDecimal("38.40")));

		assertThat(association.isActive()).isTrue();
		assertThat(association.getAsset()).isEqualTo(asset);
		assertThat(association.getThesisType()).isEqualTo(ThesisType.QUALITY_REASONABLE_PRICE);
		assertThat(association.getAcceptedScore()).isEqualTo(82);
		assertThat(association.getAcceptedPriceCeiling()).isEqualByComparingTo("42.00");
		assertThat(association.getAcceptedSafetyMarginPercent()).isEqualByComparingTo("0.150000");
		assertThat(association.getRuleVersion()).isEqualTo("rules-v1");

		assertThat(customerPositionThesisRepository.findByPositionIdAndStatus(position.getId(),
				CustomerPositionThesisStatus.ACTIVE)).contains(association);
	}

	@Test
	void onlyOneActiveMainThesisPerCustomerPositionIsAllowed() {
		User user = saveCustomer("single-active-thesis@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("EGIE3", "Engie Brasil", "Utilidade Pública"));
		CustomerPosition position = customerPositionRepository.saveAndFlush(new CustomerPosition(user, asset,
				new BigDecimal("20"), new BigDecimal("40.00"), LocalDate.of(2026, 7, 7)));
		PositionThesis firstThesis = positionThesisRepository.saveAndFlush(thesis(asset, LocalDate.of(2026, 7, 7),
				ThesisType.SUSTAINABLE_DIVIDENDS, 78, "rules-v1"));
		PositionThesis secondThesis = positionThesisRepository.saveAndFlush(thesis(asset, LocalDate.of(2026, 7, 8),
				ThesisType.SUSTAINABLE_DIVIDENDS, 80, "rules-v1"));

		customerPositionThesisRepository.saveAndFlush(new CustomerPositionThesis(user, position, firstThesis,
				new BigDecimal("40.00")));

		assertThatThrownBy(() -> customerPositionThesisRepository.saveAndFlush(new CustomerPositionThesis(user,
				position, secondThesis, new BigDecimal("39.50")))).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void closedMainThesisAllowsANewActiveAssociation() {
		User user = saveCustomer("replace-thesis@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("TAEE11", "Taesa UNT", "Utilidade Pública"));
		CustomerPosition position = customerPositionRepository.saveAndFlush(new CustomerPosition(user, asset,
				new BigDecimal("30"), new BigDecimal("35.00"), LocalDate.of(2026, 7, 7)));
		PositionThesis firstThesis = positionThesisRepository.saveAndFlush(thesis(asset, LocalDate.of(2026, 7, 7),
				ThesisType.SUSTAINABLE_DIVIDENDS, 75, "rules-v1"));
		PositionThesis secondThesis = positionThesisRepository.saveAndFlush(thesis(asset, LocalDate.of(2026, 7, 8),
				ThesisType.SUSTAINABLE_DIVIDENDS, 79, "rules-v1"));

		CustomerPositionThesis firstAssociation = customerPositionThesisRepository.saveAndFlush(
				new CustomerPositionThesis(user, position, firstThesis, new BigDecimal("35.00")));
		firstAssociation.close("Cliente trocou a tese principal.");
		customerPositionThesisRepository.saveAndFlush(firstAssociation);

		CustomerPositionThesis secondAssociation = customerPositionThesisRepository.saveAndFlush(
				new CustomerPositionThesis(user, position, secondThesis, new BigDecimal("34.50")));

		assertThat(firstAssociation.getStatus()).isEqualTo(CustomerPositionThesisStatus.CLOSED);
		assertThat(secondAssociation.getStatus()).isEqualTo(CustomerPositionThesisStatus.ACTIVE);
	}

	@Test
	void latestDailyThesisCanBeFoundByAssetAndThesisType() {
		Asset asset = assetRepository.saveAndFlush(new Asset("BBAS3", "Banco do Brasil ON", "Financeiro"));
		positionThesisRepository.saveAndFlush(thesis(asset, LocalDate.of(2026, 7, 7),
				ThesisType.QUALITY_REASONABLE_PRICE, 72, "rules-v1"));
		PositionThesis latest = positionThesisRepository.saveAndFlush(thesis(asset, LocalDate.of(2026, 7, 8),
				ThesisType.QUALITY_REASONABLE_PRICE, 81, "rules-v1"));

		assertThat(positionThesisRepository.findTopByAssetIdAndThesisTypeOrderByReferenceDateDescCreatedAtDesc(
				asset.getId(), ThesisType.QUALITY_REASONABLE_PRICE)).contains(latest);
	}

	@Test
	void duplicateDailyCandleForSameAssetDateAndSourceIsRejected() {
		Asset asset = assetRepository.saveAndFlush(new Asset("PETR4", "Petrobras PN", "Energia"));
		LocalDate tradeDate = LocalDate.of(2026, 7, 7);

		dailyCandleRepository.saveAndFlush(candle(asset, tradeDate, "brapi"));

		assertThatThrownBy(() -> dailyCandleRepository.saveAndFlush(candle(asset, tradeDate, "brapi")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void duplicateFundamentalSnapshotForSameAssetPeriodAndSourceIsRejected() {
		Asset asset = assetRepository.saveAndFlush(new Asset("VALE3", "Vale S.A.", "Materiais Básicos"));
		LocalDate referenceDate = LocalDate.of(2026, 6, 30);

		fundamentalSnapshotRepository.saveAndFlush(new FundamentalSnapshot(asset, referenceDate, PeriodType.QUARTERLY,
				"brapi"));

		assertThatThrownBy(() -> fundamentalSnapshotRepository.saveAndFlush(new FundamentalSnapshot(asset, referenceDate,
				PeriodType.QUARTERLY, "brapi"))).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void recommendationIdempotencyKeyIsEnforced() {
		User user = saveCustomer("digest@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("ITUB4", "Itaú Unibanco PN", "Financeiro"));
		CustomerPosition position = customerPositionRepository.saveAndFlush(new CustomerPosition(user, asset,
				new BigDecimal("100"), new BigDecimal("28.00"), LocalDate.of(2026, 1, 10)));

		PositionRecommendation recommendation = recommendation(user, asset, position);
		recommendationRepository.saveAndFlush(recommendation);

		assertThatThrownBy(() -> recommendationRepository.saveAndFlush(recommendation(user, asset, position)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void notificationIdempotencyKeyIsEnforced() {
		User user = saveCustomer("notification@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("BBDC4", "Bradesco PN", "Financeiro"));
		CustomerPosition position = customerPositionRepository.saveAndFlush(new CustomerPosition(user, asset,
				new BigDecimal("100"), new BigDecimal("14.00"), LocalDate.of(2026, 1, 10)));
		PositionRecommendation recommendation = recommendationRepository.saveAndFlush(recommendation(user, asset,
				position));
		NotificationEvent notification = notification(user, asset, position, recommendation);
		notificationEventRepository.saveAndFlush(notification);

		assertThatThrownBy(() -> notificationEventRepository.saveAndFlush(notification(user, asset, position,
				recommendation))).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void recommendationCanReferenceAcceptedAndCurrentThesis() {
		User user = saveCustomer("recommendation-thesis@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("VIVT3", "Telefônica Brasil ON", "Comunicações"));
		CustomerPosition position = customerPositionRepository.saveAndFlush(new CustomerPosition(user, asset,
				new BigDecimal("100"), new BigDecimal("48.00"), LocalDate.of(2026, 1, 10)));
		PositionThesis acceptedThesis = positionThesisRepository.saveAndFlush(thesis(asset, LocalDate.of(2026, 7, 7),
				ThesisType.SUSTAINABLE_DIVIDENDS, 76, "rules-v1"));
		PositionThesis currentThesis = positionThesisRepository.saveAndFlush(thesis(asset, LocalDate.of(2026, 7, 8),
				ThesisType.SUSTAINABLE_DIVIDENDS, 84, "rules-v1"));
		CustomerPositionThesis association = customerPositionThesisRepository.saveAndFlush(new CustomerPositionThesis(
				user, position, acceptedThesis, new BigDecimal("48.00")));

		PositionRecommendation recommendation = recommendation(user, asset, position);
		recommendation.setCustomerPositionThesis(association);
		recommendation.setCurrentThesis(currentThesis);
		recommendation.setThesisType(ThesisType.SUSTAINABLE_DIVIDENDS);

		PositionRecommendation persisted = recommendationRepository.saveAndFlush(recommendation);

		assertThat(persisted.getCustomerPositionThesis()).isEqualTo(association);
		assertThat(persisted.getCurrentThesis()).isEqualTo(currentThesis);
		assertThat(persisted.getThesisType()).isEqualTo(ThesisType.SUSTAINABLE_DIVIDENDS);
	}

	@Test
	void assetWatchItemCanAccompanyAssetWithoutInvestmentCommand() {
		User user = saveCustomer("watch-item@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("BBSE3", "BB Seguridade ON", "Financeiro"));
		CustomerPosition position = customerPositionRepository.saveAndFlush(new CustomerPosition(user, asset,
				new BigDecimal("50"), new BigDecimal("34.00"), LocalDate.of(2026, 7, 7)));
		PositionThesis studyModel = positionThesisRepository.saveAndFlush(thesis(asset, LocalDate.of(2026, 7, 7),
				ThesisType.SUSTAINABLE_DIVIDENDS, 80, "rules-v1"));
		CustomerPositionThesis association = customerPositionThesisRepository.saveAndFlush(new CustomerPositionThesis(
				user, position, studyModel, new BigDecimal("34.00")));

		AssetWatchItem watchItem = new AssetWatchItem(user, asset);
		watchItem.setSourcePosition(position);
		watchItem.setAccompaniedStudyModel(association);
		watchItem.setUserLowerPriceThreshold(new BigDecimal("30.00"));
		watchItem.setUserUpperPriceThreshold(new BigDecimal("42.00"));
		assetWatchItemRepository.saveAndFlush(watchItem);

		assertThat(assetWatchItemRepository.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(),
				AssetWatchStatus.ACTIVE)).singleElement().satisfies(persisted -> {
					assertThat(persisted.getAsset()).isEqualTo(asset);
					assertThat(persisted.getAccompaniedStudyModel()).isEqualTo(association);
				});
	}

	@Test
	void informationalAlertUsesOnlyNeutralEventTypes() {
		assertThat(Arrays.stream(InformationalEventType.values()).map(Enum::name)).containsExactly(
				"PRICE_THRESHOLD_REACHED",
				"DATA_UPDATED",
				"DATA_STALE",
				"INDICATOR_THRESHOLD_REACHED",
				"STUDY_ASSUMPTION_CHANGED",
				"QUALITY_DATA_BLOCKED");

		User user = saveCustomer("informational-alert@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("SAPR11", "Sanepar UNT", "Utilidade Pública"));
		AssetWatchItem watchItem = assetWatchItemRepository.saveAndFlush(new AssetWatchItem(user, asset));

		InformationalAlert alert = new InformationalAlert(user, asset, LocalDate.of(2026, 7, 7),
				InformationalEventType.PRICE_THRESHOLD_REACHED, Severity.MEDIUM,
				"Limiar de preço atingido",
				"Preço observado cruzou limiar informativo definido para acompanhamento.",
				"rules-v1");
		alert.setWatchItem(watchItem);
		alert.setEvidenceJson("{\"source\":\"daily_candles\",\"threshold\":\"42.00\"}");

		InformationalAlert persisted = informationalAlertRepository.saveAndFlush(alert);

		assertThat(persisted.getEventType()).isEqualTo(InformationalEventType.PRICE_THRESHOLD_REACHED);
		assertThat(persisted.getSummary()).doesNotContain("compr", "vend", "manten", "aument", "reduz");
	}

	private User saveCustomer(String email) {
		User user = new User("Cliente", email, "{bcrypt}hash", SubscriptionStatus.ACTIVE);
		user.addRole(UserRoleType.CUSTOMER);
		return userRepository.saveAndFlush(user);
	}

	private DailyCandle candle(Asset asset, LocalDate tradeDate, String source) {
		return new DailyCandle(asset, tradeDate, new BigDecimal("37.95"), new BigDecimal("38.02"),
				new BigDecimal("37.61"), new BigDecimal("37.77"), source);
	}

	private PositionRecommendation recommendation(User user, Asset asset, CustomerPosition position) {
		PositionRecommendation recommendation = new PositionRecommendation();
		recommendation.setUser(user);
		recommendation.setAsset(asset);
		recommendation.setPosition(position);
		recommendation.setReferenceDate(LocalDate.of(2026, 7, 7));
		recommendation.setRecommendationType(RecommendationType.REALIZAR_OBJETIVO);
		recommendation.setSeverity(Severity.HIGH);
		recommendation.setCurrentPrice(new BigDecimal("48.20"));
		recommendation.setAveragePrice(new BigDecimal("28.00"));
		recommendation.setTargetPrice(new BigDecimal("48.00"));
		recommendation.setFinalMessage("Objetivo atingido conforme plano da posição.");
		recommendation.setRuleVersion("rules-v1");
		return recommendation;
	}

	private PositionThesis thesis(Asset asset, LocalDate referenceDate, ThesisType thesisType, int score,
			String ruleVersion) {
		PositionThesis thesis = new PositionThesis(asset, referenceDate, thesisType, ThesisStatus.CRITERIOS_ATENDIDOS, score,
				ruleVersion);
		thesis.setPriceCeiling(new BigDecimal("42.00"));
		thesis.setFairPriceEstimate(new BigDecimal("49.40"));
		thesis.setSafetyMarginPercent(new BigDecimal("0.150000"));
		thesis.setStopPrice(new BigDecimal("34.00"));
		thesis.setTargetPrice(new BigDecimal("52.00"));
		return thesis;
	}

	private NotificationEvent notification(User user, Asset asset, CustomerPosition position,
			PositionRecommendation recommendation) {
		NotificationEvent notification = new NotificationEvent();
		notification.setUser(user);
		notification.setAsset(asset);
		notification.setPosition(position);
		notification.setRecommendation(recommendation);
		notification.setReferenceDate(LocalDate.of(2026, 7, 7));
		notification.setChannel(NotificationChannel.EMAIL);
		notification.setEventType(NotificationEventType.TARGET_REACHED);
		notification.setRecommendationType(RecommendationType.REALIZAR_OBJETIVO);
		notification.setSeverity(Severity.HIGH);
		notification.setSummary("Objetivo de preço atingido.");
		notification.setRuleVersion("rules-v1");
		return notification;
	}
}
