package com.freirelts.araripe_invest_api.adapters.inbound.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.indicators.IndicatorCalculationService;
import com.freirelts.araripe_invest_api.application.recommendations.PositionRecommendationService;
import com.freirelts.araripe_invest_api.application.thesis.PositionThesisGenerationService;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.jobs.JobName;
import com.freirelts.araripe_invest_api.domain.jobs.JobRun;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunStatus;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunTrigger;
import com.freirelts.araripe_invest_api.domain.marketdata.DataCollectionCategory;
import com.freirelts.araripe_invest_api.domain.marketdata.DataCollectionRecord;
import com.freirelts.araripe_invest_api.domain.marketdata.DataCollectionStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.DividendEvent;
import com.freirelts.araripe_invest_api.domain.marketdata.DividendEventType;
import com.freirelts.araripe_invest_api.domain.marketdata.FinancialStatementSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.marketdata.StatementType;
import com.freirelts.araripe_invest_api.domain.marketdata.TechnicalIndicatorSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationEvent;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationEventType;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationStatus;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesis;
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
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DataCollectionRecordRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DividendEventRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FinancialStatementSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.JobRunRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.NotificationEventRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionRecommendationRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.TechnicalIndicatorSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class Phase10ApiControllerTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private PositionThesisRepository thesisRepository;

	@Autowired
	private FundamentalSnapshotRepository fundamentalRepository;

	@Autowired
	private TechnicalIndicatorSnapshotRepository technicalRepository;

	@Autowired
	private FinancialStatementSnapshotRepository financialStatementRepository;

	@Autowired
	private DividendEventRepository dividendEventRepository;

	@Autowired
	private CustomerPositionRepository positionRepository;

	@Autowired
	private CustomerPositionThesisRepository positionThesisRepository;

	@Autowired
	private PositionRecommendationRepository recommendationRepository;

	@Autowired
	private NotificationEventRepository notificationEventRepository;

	@Autowired
	private DataCollectionRecordRepository dataCollectionRecordRepository;

	@Autowired
	private JobRunRepository jobRunRepository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void openApiIsAvailableWithoutJwt() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").value("3.0.3"))
				.andExpect(jsonPath("$.paths['/api/v1/recommendations'].get.summary").isString())
				.andExpect(jsonPath("$.paths['/api/v1/admin/jobs/{jobName}/runs'].post.summary").isString());
	}

	@Test
	void customerCanReadPhase10ContractsAndCannotReadOtherCustomerRecommendations() throws Exception {
		LocalDate referenceDate = LocalDate.of(2026, 7, 7);
		User customer = saveUser("Phase 10 Customer", "phase10-customer@araripe.test", "senha-phase10-123",
				SubscriptionStatus.ACTIVE, UserRoleType.CUSTOMER);
		User other = saveUser("Phase 10 Other", "phase10-other@araripe.test", "senha-phase10-other-123",
				SubscriptionStatus.ACTIVE, UserRoleType.CUSTOMER);
		Asset asset = assetRepository.saveAndFlush(new Asset("WEGE3", "WEG S.A.", "Bens Industriais"));
		PositionThesis thesis = thesisRepository.saveAndFlush(thesis(asset, referenceDate));
		saveFundamentals(asset, referenceDate);
		saveTechnical(asset, referenceDate);
		FinancialStatementSnapshot statement = new FinancialStatementSnapshot(asset, StatementType.BALANCE_SHEET,
				PeriodType.QUARTERLY, referenceDate, "brapi", "{\"cash\":1000}");
		statement.setQualityStatus(DataQualityStatus.VALID);
		financialStatementRepository.saveAndFlush(statement);
		dividendEventRepository.saveAndFlush(new DividendEvent(asset, DividendEventType.DIVIDEND,
				referenceDate.minusDays(10), referenceDate.plusDays(30), "brapi"));
		CustomerPosition position = positionRepository.saveAndFlush(new CustomerPosition(customer, asset,
				new BigDecimal("10"), new BigDecimal("38.00"), referenceDate));
		CustomerPositionThesis association = positionThesisRepository.saveAndFlush(new CustomerPositionThesis(customer,
				position, thesis, new BigDecimal("38.00")));
		PositionRecommendation recommendation = recommendationRepository.saveAndFlush(recommendation(customer, position,
				asset, association, thesis, referenceDate));
		NotificationEvent notification = notificationEventRepository.saveAndFlush(notification(customer, position, asset,
				recommendation, referenceDate));
		saveJobStatus(customer, referenceDate);

		String token = login(customer, "senha-phase10-123");
		String otherToken = login(other, "senha-phase10-other-123");

		mockMvc.perform(get("/api/v1/theses/ranking?date=2026-07-07"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/assets").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].symbol").value("WEGE3"));

		mockMvc.perform(get("/api/v1/theses/ranking?date=2026-07-07").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].score").value(82))
				.andExpect(jsonPath("$[0].priceCeiling").value(42.0));

		mockMvc.perform(get("/api/v1/theses/{thesisId}", thesis.getId()).header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.thesis.asset.symbol").value("WEGE3"))
				.andExpect(jsonPath("$.riskNotice").isString());

		mockMvc.perform(get("/api/v1/theses/history?symbol=WEGE3&from=2026-07-01&to=2026-07-12")
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(thesis.getId().toString()));

		mockMvc.perform(get("/api/v1/assets/WEGE3/fundamentals?date=2026-07-07")
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fundamentals.trailingPe").value(10.0))
				.andExpect(jsonPath("$.technicalIndicators.trendStatus").value("HEALTHY"))
				.andExpect(jsonPath("$.financialStatements[0].statementType").value("BALANCE_SHEET"));

		mockMvc.perform(get("/api/v1/allocation-settings").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.persisted").value(false));

		mockMvc.perform(put("/api/v1/allocation-settings").header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "capitalBase":15000.00,
								  "maxAllocationPerAssetPercent":10.000000,
								  "maxAllocationPerSectorPercent":25.000000,
								  "toleratedDrawdownPercent":25.000000,
								  "minimumCashReservePercent":10.000000,
								  "minimumSafetyMarginPercent":15.000000,
								  "firstTranchePercent":50.000000,
								  "secondTranchePercent":25.000000,
								  "thirdTranchePercent":25.000000,
								  "defaultStopPercent":15.000000,
								  "defaultTargetReturnPercent":25.000000
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.capitalBase").value(15000.0))
				.andExpect(jsonPath("$.persisted").value(true));

		mockMvc.perform(get("/api/v1/recommendations?date=2026-07-07").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(recommendation.getId().toString()))
				.andExpect(jsonPath("$[0].deterministicReasons[0]").value("Tese principal segue valida."));

		mockMvc.perform(get("/api/v1/recommendations/{recommendationId}", recommendation.getId())
						.header("Authorization", bearer(otherToken)))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/notifications?from=2026-07-01&to=2026-07-12")
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(notification.getId().toString()))
				.andExpect(jsonPath("$[0].readAt").doesNotExist());

		mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notification.getId())
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.readAt", notNullValue()));

		mockMvc.perform(get("/api/v1/jobs/status?date=2026-07-07").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.collectionRecords[0].category").value("DAILY_HISTORY"))
				.andExpect(jsonPath("$.jobRuns[0].jobName").value("RANKING"));
	}

	private String login(User user, String password) throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%s"}
								""".formatted(user.getEmail(), password)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode json = objectMapper.readTree(response);
		return json.get("accessToken").asText();
	}

	private User saveUser(String name, String email, String password, SubscriptionStatus subscriptionStatus,
			UserRoleType role) {
		User user = new User(name, email, passwordEncoder.encode(password), subscriptionStatus);
		user.addRole(role);
		return userRepository.saveAndFlush(user);
	}

	private PositionThesis thesis(Asset asset, LocalDate referenceDate) {
		PositionThesis thesis = new PositionThesis(asset, referenceDate, ThesisType.QUALITY_REASONABLE_PRICE,
				ThesisStatus.OPORTUNIDADE, 82, PositionThesisGenerationService.RULE_VERSION);
		thesis.setPriceCeiling(new BigDecimal("42.00"));
		thesis.setFairPriceEstimate(new BigDecimal("49.40"));
		thesis.setSafetyMarginPercent(new BigDecimal("0.150000"));
		thesis.setStopPrice(new BigDecimal("34.00"));
		thesis.setTargetPrice(new BigDecimal("52.00"));
		thesis.setReasonsJson("[\"Fundamentos consistentes e preco dentro da zona de entrada.\"]");
		thesis.setScoreBreakdownJson("{\"quality\":30,\"valuation\":18}");
		thesis.setReviewPointsJson("[\"Reavaliar se perder tendencia longa.\"]");
		return thesis;
	}

	private void saveFundamentals(Asset asset, LocalDate referenceDate) {
		FundamentalSnapshot snapshot = new FundamentalSnapshot(asset, referenceDate, PeriodType.TTM,
				"araripe-indicators");
		snapshot.setCalculationVersion(IndicatorCalculationService.CALCULATION_VERSION);
		snapshot.setQualityStatus(DataQualityStatus.VALID);
		snapshot.setTrailingPe(new BigDecimal("10.00"));
		snapshot.setPriceToBook(new BigDecimal("2.10"));
		snapshot.setEnterpriseToEbitda(new BigDecimal("7.50"));
		snapshot.setRoe(new BigDecimal("0.180000"));
		snapshot.setFreeCashflow(new BigDecimal("1500000.00"));
		fundamentalRepository.saveAndFlush(snapshot);
	}

	private void saveTechnical(Asset asset, LocalDate referenceDate) {
		TechnicalIndicatorSnapshot snapshot = new TechnicalIndicatorSnapshot(asset, referenceDate,
				IndicatorCalculationService.CALCULATION_VERSION);
		snapshot.setSma200(new BigDecimal("36.00"));
		snapshot.setAvgVolume60(new BigDecimal("8000000.00"));
		snapshot.setTrendStatus(TrendStatus.HEALTHY);
		technicalRepository.saveAndFlush(snapshot);
	}

	private PositionRecommendation recommendation(User user, CustomerPosition position, Asset asset,
			CustomerPositionThesis association, PositionThesis thesis, LocalDate referenceDate) {
		PositionRecommendation recommendation = new PositionRecommendation();
		recommendation.setUser(user);
		recommendation.setPosition(position);
		recommendation.setAsset(asset);
		recommendation.setCustomerPositionThesis(association);
		recommendation.setCurrentThesis(thesis);
		recommendation.setThesisType(thesis.getThesisType());
		recommendation.setReferenceDate(referenceDate);
		recommendation.setRecommendationType(RecommendationType.MANTER);
		recommendation.setSeverity(Severity.LOW);
		recommendation.setCurrentPrice(new BigDecimal("39.00"));
		recommendation.setAveragePrice(position.getAveragePrice());
		recommendation.setStopPrice(new BigDecimal("34.00"));
		recommendation.setTargetPrice(new BigDecimal("52.00"));
		recommendation.setScore(82);
		recommendation.setDeterministicReasonJson("[\"Tese principal segue valida.\"]");
		recommendation.setFinalMessage("Tese principal segue valida.");
		recommendation.setRuleVersion(PositionRecommendationService.RULE_VERSION);
		return recommendation;
	}

	private NotificationEvent notification(User user, CustomerPosition position, Asset asset,
			PositionRecommendation recommendation, LocalDate referenceDate) {
		NotificationEvent notification = new NotificationEvent();
		notification.setUser(user);
		notification.setPosition(position);
		notification.setAsset(asset);
		notification.setRecommendation(recommendation);
		notification.setReferenceDate(referenceDate);
		notification.setChannel(NotificationChannel.EMAIL_SNS);
		notification.setEventType(NotificationEventType.REASSESSMENT_REQUIRED);
		notification.setRecommendationType(RecommendationType.REAVALIAR);
		notification.setSeverity(Severity.HIGH);
		notification.setStatus(NotificationStatus.PENDING);
		notification.setSummary("Reavaliar posicao por evento critico.");
		notification.setRuleVersion(PositionRecommendationService.RULE_VERSION);
		return notification;
	}

	private void saveJobStatus(User requester, LocalDate referenceDate) {
		DataCollectionRecord record = new DataCollectionRecord(DataCollectionCategory.DAILY_HISTORY, "brapi",
				"/quote/list", referenceDate, Instant.now());
		record.setStatus(DataCollectionStatus.SUCCESS);
		dataCollectionRecordRepository.saveAndFlush(record);

		JobRun run = new JobRun(JobName.RANKING, referenceDate, JobRunTrigger.MANUAL, requester,
				"{\"referenceDate\":\"2026-07-07\"}");
		run.setStatus(JobRunStatus.SUCCESS);
		run.setCompletedAt(Instant.now());
		jobRunRepository.saveAndFlush(run);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}
}
