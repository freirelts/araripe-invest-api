package com.freirelts.araripe_invest_api.application.notifications;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.alerts.InformationalAlert;
import com.freirelts.araripe_invest_api.domain.alerts.InformationalEventType;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationStatus;
import com.freirelts.araripe_invest_api.domain.alerts.Severity;
import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.InformationalAlertRepository;
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
	private static final String RULE_VERSION = "informational-events-v1";

	@Autowired
	private NotificationDigestService service;

	@Autowired
	private RecordingNotificationProvider provider;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private InformationalAlertRepository alertRepository;

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
	void customerWithInformationalAlertsReceivesOneConsolidatedEmail() {
		User customer = saveCustomer("digest@araripe.test");
		savePendingAlert(customer, "WEGE3", InformationalEventType.PRICE_THRESHOLD_REACHED,
				"Limiar superior de preco atingido.");
		savePendingAlert(customer, "PETR4", InformationalEventType.QUALITY_DATA_BLOCKED,
				"Dado de preco indisponivel para ativo acompanhado.");

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
					assertThat(digest.items().getFirst().evidence())
							.containsEntry("source", "test")
							.containsEntry("referenceDate", "2026-07-07");
				});
		assertThat(alertRepository.findAll())
				.allSatisfy(event -> {
					assertThat(event.getNotificationStatus()).isEqualTo(NotificationStatus.SENT);
					assertThat(event.getNotificationProvider()).isEqualTo("test-mail");
					assertThat(event.getNotificationProviderMessageId()).isEqualTo("message-1");
					assertThat(event.getNotificationAttemptCount()).isEqualTo(1);
					assertThat(event.getNotificationSentAt()).isNotNull();
				});
	}

	@Test
	void customerWithoutInformationalAlertsDoesNotReceiveEmail() {
		saveCustomer("no-events@araripe.test");

		DailyNotificationDigestSummary summary = service.publishDailyDigest(REFERENCE_DATE);

		assertThat(summary.skipped()).isTrue();
		assertThat(summary.pendingEvents()).isZero();
		assertThat(provider.digests).isEmpty();
	}

	@Test
	void secondRunDoesNotDuplicateDailyEmailAfterSuccessfulSend() {
		User customer = saveCustomer("idempotent-digest@araripe.test");
		savePendingAlert(customer, "VALE3", InformationalEventType.STUDY_ASSUMPTION_CHANGED,
				"Premissas do estudo alteradas.");

		DailyNotificationDigestSummary first = service.publishDailyDigest(REFERENCE_DATE);
		provider.reset();
		DailyNotificationDigestSummary second = service.publishDailyDigest(REFERENCE_DATE);

		assertThat(first.digestsSent()).isEqualTo(1);
		assertThat(second.skipped()).isTrue();
		assertThat(second.pendingEvents()).isZero();
		assertThat(provider.digests).isEmpty();
		assertThat(alertRepository.findAll()).singleElement()
				.satisfies(event -> assertThat(event.getNotificationStatus()).isEqualTo(NotificationStatus.SENT));
	}

	@Test
	void providerFailureDoesNotRemovePersistedAlert() {
		User customer = saveCustomer("failure@araripe.test");
		savePendingAlert(customer, "ITUB4", InformationalEventType.INDICATOR_THRESHOLD_REACHED,
				"Indicador observado fora do intervalo do estudo.");
		provider.fail = true;

		DailyNotificationDigestSummary summary = service.publishDailyDigest(REFERENCE_DATE);

		assertThat(summary.partial()).isTrue();
		assertThat(summary.digestsFailed()).isEqualTo(1);
		assertThat(summary.eventsFailed()).isEqualTo(1);
		assertThat(alertRepository.findAll()).singleElement()
				.satisfies(event -> {
					assertThat(event.getNotificationStatus()).isEqualTo(NotificationStatus.FAILED);
					assertThat(event.getNotificationAttemptCount()).isEqualTo(1);
					assertThat(event.getNotificationLastError()).contains("forced test failure");
					assertThat(event.getNotificationSentAt()).isNull();
				});
	}

	private InformationalAlert savePendingAlert(User user, String symbol, InformationalEventType eventType,
			String summary) {
		Asset asset = assetRepository.saveAndFlush(new Asset(symbol, symbol + " S.A.", "Financeiro"));
		InformationalAlert alert = new InformationalAlert(user, asset, REFERENCE_DATE, eventType, Severity.HIGH,
				summary, summary, RULE_VERSION);
		alert.setEvidenceJson("{\"source\":\"test\",\"referenceDate\":\"2026-07-07\"}");
		return alertRepository.saveAndFlush(alert);
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
