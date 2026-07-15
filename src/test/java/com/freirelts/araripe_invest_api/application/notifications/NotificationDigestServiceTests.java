package com.freirelts.araripe_invest_api.application.notifications;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationEvent;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationEventType;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationStatus;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.recommendations.PositionRecommendation;
import com.freirelts.araripe_invest_api.domain.recommendations.RecommendationType;
import com.freirelts.araripe_invest_api.domain.recommendations.Severity;
import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.NotificationEventRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionRecommendationRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@Import({ NotificationDigestService.class, NotificationDigestServiceTests.TestProviderConfig.class })
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationDigestServiceTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 7);
	private static final String RULE_VERSION = "position-recommendation-v1";

	@Autowired
	private NotificationDigestService service;

	@Autowired
	private RecordingNotificationProvider provider;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private CustomerPositionRepository positionRepository;

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

	@BeforeEach
	void resetProvider() {
		provider.reset();
	}

	@Test
	void customerWithActionableRecommendationsReceivesOneConsolidatedEmail() {
		User customer = saveCustomer("digest@araripe.test");
		savePendingNotification(customer, "WEGE3", RecommendationType.REALIZAR_OBJETIVO,
				NotificationEventType.TARGET_REACHED, "Objetivo atingido.");
		savePendingNotification(customer, "PETR4", RecommendationType.EXECUTAR_STOP,
				NotificationEventType.STOP_TRIGGERED, "Stop acionado.");

		DailyNotificationDigestSummary summary = service.publishDailyDigest(REFERENCE_DATE);

		assertThat(summary.pendingEvents()).isEqualTo(2);
		assertThat(summary.digestsCreated()).isEqualTo(1);
		assertThat(summary.digestsSent()).isEqualTo(1);
		assertThat(summary.eventsSent()).isEqualTo(2);
		assertThat(summary.partial()).isFalse();
		assertThat(provider.digests).singleElement()
				.satisfies(digest -> {
					assertThat(digest.recipientEmail()).isEqualTo("digest@araripe.test");
					assertThat(digest.items()).hasSize(2);
				});
		assertThat(notificationEventRepository.findAll())
				.allSatisfy(event -> {
					assertThat(event.getStatus()).isEqualTo(NotificationStatus.SENT);
					assertThat(event.getProvider()).isEqualTo("test-mail");
					assertThat(event.getProviderMessageId()).isEqualTo("message-1");
					assertThat(event.getAttemptCount()).isEqualTo(1);
					assertThat(event.getSentAt()).isNotNull();
				});
	}

	@Test
	void customerWithoutActionableRecommendationsDoesNotReceiveEmail() {
		saveCustomer("no-events@araripe.test");

		DailyNotificationDigestSummary summary = service.publishDailyDigest(REFERENCE_DATE);

		assertThat(summary.skipped()).isTrue();
		assertThat(summary.pendingEvents()).isZero();
		assertThat(provider.digests).isEmpty();
	}

	@Test
	void secondRunDoesNotDuplicateDailyEmailAfterSuccessfulSend() {
		User customer = saveCustomer("idempotent-digest@araripe.test");
		savePendingNotification(customer, "VALE3", RecommendationType.REAVALIAR,
				NotificationEventType.REASSESSMENT_REQUIRED, "Reavaliar posicao.");

		DailyNotificationDigestSummary first = service.publishDailyDigest(REFERENCE_DATE);
		provider.reset();
		DailyNotificationDigestSummary second = service.publishDailyDigest(REFERENCE_DATE);

		assertThat(first.digestsSent()).isEqualTo(1);
		assertThat(second.skipped()).isTrue();
		assertThat(second.pendingEvents()).isZero();
		assertThat(provider.digests).isEmpty();
		assertThat(notificationEventRepository.findAll()).singleElement()
				.satisfies(event -> assertThat(event.getStatus()).isEqualTo(NotificationStatus.SENT));
	}

	@Test
	void providerFailureDoesNotRemovePersistedRecommendation() {
		User customer = saveCustomer("failure@araripe.test");
		savePendingNotification(customer, "ITUB4", RecommendationType.REDUZIR_POSICAO,
				NotificationEventType.REDUCE_EXPOSURE, "Reducao por risco.");
		provider.fail = true;

		DailyNotificationDigestSummary summary = service.publishDailyDigest(REFERENCE_DATE);

		assertThat(summary.partial()).isTrue();
		assertThat(summary.digestsFailed()).isEqualTo(1);
		assertThat(summary.eventsFailed()).isEqualTo(1);
		assertThat(recommendationRepository.findAll()).hasSize(1);
		assertThat(notificationEventRepository.findAll()).singleElement()
				.satisfies(event -> {
					assertThat(event.getStatus()).isEqualTo(NotificationStatus.FAILED);
					assertThat(event.getAttemptCount()).isEqualTo(1);
					assertThat(event.getLastError()).contains("forced test failure");
					assertThat(event.getSentAt()).isNull();
				});
	}

	private NotificationEvent savePendingNotification(User user, String symbol, RecommendationType recommendationType,
			NotificationEventType eventType, String summary) {
		Asset asset = assetRepository.saveAndFlush(new Asset(symbol, symbol + " S.A.", "Financeiro"));
		CustomerPosition position = positionRepository.saveAndFlush(new CustomerPosition(user, asset,
				new BigDecimal("10"), new BigDecimal("30.00"), LocalDate.of(2026, 1, 10)));
		PositionRecommendation recommendation = new PositionRecommendation();
		recommendation.setUser(user);
		recommendation.setPosition(position);
		recommendation.setAsset(asset);
		recommendation.setReferenceDate(REFERENCE_DATE);
		recommendation.setRecommendationType(recommendationType);
		recommendation.setSeverity(Severity.HIGH);
		recommendation.setDeterministicReasonJson("[]");
		recommendation.setFinalMessage(summary);
		recommendation.setRuleVersion(RULE_VERSION);
		PositionRecommendation savedRecommendation = recommendationRepository.saveAndFlush(recommendation);

		NotificationEvent event = new NotificationEvent();
		event.setUser(user);
		event.setPosition(position);
		event.setRecommendation(savedRecommendation);
		event.setAsset(asset);
		event.setReferenceDate(REFERENCE_DATE);
		event.setChannel(NotificationChannel.EMAIL);
		event.setEventType(eventType);
		event.setRecommendationType(recommendationType);
		event.setSeverity(Severity.HIGH);
		event.setSummary(summary);
		event.setStatus(NotificationStatus.PENDING);
		event.setRuleVersion(RULE_VERSION);
		return notificationEventRepository.saveAndFlush(event);
	}

	private User saveCustomer(String email) {
		User user = new User("Cliente", email, "{bcrypt}hash", SubscriptionStatus.ACTIVE);
		user.addRole(UserRoleType.CUSTOMER);
		return userRepository.saveAndFlush(user);
	}

	@TestConfiguration
	static class TestProviderConfig {

		@Bean
		RecordingNotificationProvider recordingNotificationProvider() {
			return new RecordingNotificationProvider();
		}
	}

	static class RecordingNotificationProvider implements NotificationProvider {

		private final List<DailyNotificationDigest> digests = new ArrayList<>();
		private boolean fail;

		@Override
		public String providerName() {
			return "test-mail";
		}

		@Override
		public NotificationPublishResult publish(DailyNotificationDigest digest) {
			digests.add(digest);
			if (fail) {
				throw new IllegalStateException("forced test failure");
			}
			return new NotificationPublishResult(providerName(), "message-" + digests.size());
		}

		private void reset() {
			digests.clear();
			fail = false;
		}
	}
}
