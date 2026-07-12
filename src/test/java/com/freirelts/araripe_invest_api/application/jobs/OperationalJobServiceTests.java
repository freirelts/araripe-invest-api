package com.freirelts.araripe_invest_api.application.jobs;

import com.freirelts.araripe_invest_api.application.ai.EconomicContextAnalysisService;
import com.freirelts.araripe_invest_api.application.indicators.IndicatorCalculationService;
import com.freirelts.araripe_invest_api.application.marketdata.MarketDataCollectionService;
import com.freirelts.araripe_invest_api.application.recommendations.PositionRecommendationService;
import com.freirelts.araripe_invest_api.application.screening.AssetScreeningService;
import com.freirelts.araripe_invest_api.application.thesis.PositionThesisGenerationService;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.jobs.JobName;
import com.freirelts.araripe_invest_api.domain.jobs.JobRun;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunStatus;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunTrigger;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.JobRunRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
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
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@Testcontainers
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ OperationalJobService.class, OperationalJobServiceTests.MockedServices.class })
class OperationalJobServiceTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 7);

	@Autowired
	private OperationalJobService service;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private PositionThesisRepository thesisRepository;

	@Autowired
	private JobRunRepository jobRunRepository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void rankingJobIsAuditedAndCanBeRerunWithoutDuplicatingFinancialRecords() {
		User admin = saveAdmin();
		Asset asset = assetRepository.saveAndFlush(new Asset("WEGE3", "WEG ON", "Industrial"));
		thesisRepository.saveAndFlush(new PositionThesis(asset, REFERENCE_DATE,
				ThesisType.QUALITY_REASONABLE_PRICE, ThesisStatus.OPORTUNIDADE, 82,
				PositionThesisGenerationService.RULE_VERSION));

		JobRunResult first = service.execute(JobName.RANKING, REFERENCE_DATE, JobRunTrigger.MANUAL, admin.getId());
		JobRunResult second = service.execute(JobName.RANKING, REFERENCE_DATE, JobRunTrigger.MANUAL, admin.getId());

		assertThat(first.status()).isEqualTo(JobRunStatus.SUCCESS);
		assertThat(second.status()).isEqualTo(JobRunStatus.SUCCESS);
		assertThat(second.runId()).isNotEqualTo(first.runId());
		assertThat(second.summary()).containsEntry("rankedTheses", 1);
		assertThat(thesisRepository.findAll()).hasSize(1);
		assertThat(jobRunRepository.findByReferenceDateOrderByStartedAtDesc(REFERENCE_DATE)).hasSize(2);
	}

	@Test
	void runningJobForSameNameAndDateBlocksConflictingExecution() {
		User admin = saveAdmin();
		jobRunRepository.saveAndFlush(new JobRun(JobName.RANKING, REFERENCE_DATE, JobRunTrigger.MANUAL, admin,
				"{\"referenceDate\":\"2026-07-07\"}"));

		assertThatThrownBy(() -> service.execute(JobName.RANKING, REFERENCE_DATE, JobRunTrigger.MANUAL, admin.getId()))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("409 CONFLICT");
	}

	@Test
	void notificationDigestSkipsWhenThereAreNoActionableEvents() {
		User admin = saveAdmin();

		JobRunResult result = service.execute(JobName.DAILY_NOTIFICATION_DIGEST, REFERENCE_DATE,
				JobRunTrigger.MANUAL, admin.getId());

		assertThat(result.status()).isEqualTo(JobRunStatus.SKIPPED);
		assertThat(result.summary()).containsAllEntriesOf(Map.of("pendingActionableEvents", 0, "skipped", true));
	}

	private User saveAdmin() {
		User user = new User("Admin Jobs", "admin-jobs@araripe.test", "hashed", SubscriptionStatus.NONE);
		user.addRole(UserRoleType.ADMIN);
		return userRepository.saveAndFlush(user);
	}

	@TestConfiguration
	static class MockedServices {

		@Bean
		MarketDataCollectionService marketDataCollectionService() {
			return mock(MarketDataCollectionService.class);
		}

		@Bean
		IndicatorCalculationService indicatorCalculationService() {
			return mock(IndicatorCalculationService.class);
		}

		@Bean
		AssetScreeningService assetScreeningService() {
			return mock(AssetScreeningService.class);
		}

		@Bean
		PositionThesisGenerationService positionThesisGenerationService() {
			return mock(PositionThesisGenerationService.class);
		}

		@Bean
		EconomicContextAnalysisService economicContextAnalysisService() {
			return mock(EconomicContextAnalysisService.class);
		}

		@Bean
		PositionRecommendationService positionRecommendationService() {
			return mock(PositionRecommendationService.class);
		}
	}
}
