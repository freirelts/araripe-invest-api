package com.freirelts.araripe_invest_api.domain;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.marketdata.DailyCandle;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationEvent;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationEventType;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.recommendations.PositionRecommendation;
import com.freirelts.araripe_invest_api.domain.recommendations.RecommendationType;
import com.freirelts.araripe_invest_api.domain.recommendations.Severity;
import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DailyCandleRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.NotificationEventRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionRecommendationRepository;
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
	private DailyCandleRepository dailyCandleRepository;

	@Autowired
	private FundamentalSnapshotRepository fundamentalSnapshotRepository;

	@Autowired
	private CustomerPositionRepository customerPositionRepository;

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

	private NotificationEvent notification(User user, Asset asset, CustomerPosition position,
			PositionRecommendation recommendation) {
		NotificationEvent notification = new NotificationEvent();
		notification.setUser(user);
		notification.setAsset(asset);
		notification.setPosition(position);
		notification.setRecommendation(recommendation);
		notification.setReferenceDate(LocalDate.of(2026, 7, 7));
		notification.setChannel(NotificationChannel.EMAIL_SNS);
		notification.setEventType(NotificationEventType.TARGET_REACHED);
		notification.setRecommendationType(RecommendationType.REALIZAR_OBJETIVO);
		notification.setSeverity(Severity.HIGH);
		notification.setSummary("Objetivo de preço atingido.");
		notification.setRuleVersion("rules-v1");
		return notification;
	}
}
