package com.freirelts.araripe_invest_api.adapters.inbound.rest.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
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
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class PortfolioPositionControllerTests {

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
	private CustomerPositionRepository positionRepository;

	@Autowired
	private PositionThesisRepository thesisRepository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void customerCanCreateListAssociateAndClosePosition() throws Exception {
		User customer = saveUser("Portfolio Customer", "portfolio-rest@araripe.test", "senha-portfolio-123",
				SubscriptionStatus.ACTIVE, UserRoleType.CUSTOMER);
		Asset asset = assetRepository.saveAndFlush(new Asset("WEGE3", "WEG S.A.", "Bens Industriais"));
		PositionThesis thesis = thesisRepository.saveAndFlush(thesis(asset));
		String token = login(customer, "senha-portfolio-123");

		String created = mockMvc.perform(post("/api/v1/position-records")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "assetId":"%s",
								  "quantity":10,
								  "averagePrice":38.40,
								  "entryDate":"2026-07-07",
								  "userLowerPriceThreshold":33.00,
								  "userUpperPriceThreshold":48.00,
								  "notes":"Posicao acompanhada pelo cliente"
								}
								""".formatted(asset.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.symbol").value("WEGE3"))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andReturn()
				.getResponse()
				.getContentAsString();
		String positionId = objectMapper.readTree(created).get("id").asText();

		mockMvc.perform(get("/api/v1/position-records").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].symbol").value("WEGE3"))
				.andExpect(jsonPath("$[0].userLowerPriceThreshold").value(33.0))
				.andExpect(jsonPath("$[0].userUpperPriceThreshold").value(48.0))
				.andExpect(jsonPath("$[0].regulatoryNotice").isString());

		mockMvc.perform(post("/api/v1/position-records/{positionId}/main-thesis", positionId)
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"thesisId":"%s","notes":"Tese principal aceita"}
								""".formatted(thesis.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accompaniedStudyModel.acceptedThesisId").value(thesis.getId().toString()));

		mockMvc.perform(post("/api/v1/position-records/{positionId}/quantity-adjustments", positionId)
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "quantity":5,
								  "price":42.00,
								  "contributionDate":"2026-07-08",
								  "notes":"Aporte executado na corretora"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quantity").value(15))
				.andExpect(jsonPath("$.averagePrice").value(39.6))
				.andExpect(jsonPath("$.notes").value("Aporte executado na corretora"));

		mockMvc.perform(patch("/api/v1/position-records/{positionId}/close", positionId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CLOSED"));
	}

	@Test
	void customerCannotCloseAnotherUsersPosition() throws Exception {
		User owner = saveUser("Owner", "portfolio-owner-rest@araripe.test", "senha-owner-123",
				SubscriptionStatus.ACTIVE, UserRoleType.CUSTOMER);
		User other = saveUser("Other", "portfolio-other-rest@araripe.test", "senha-other-123",
				SubscriptionStatus.ACTIVE, UserRoleType.CUSTOMER);
		Asset asset = assetRepository.saveAndFlush(new Asset("ITUB4", "Itau Unibanco PN", "Financeiro"));
		CustomerPosition position = positionRepository.saveAndFlush(new CustomerPosition(owner, asset,
				new BigDecimal("100"), new BigDecimal("28.00"), LocalDate.of(2026, 7, 7)));
		String token = login(other, "senha-other-123");

		mockMvc.perform(patch("/api/v1/position-records/{positionId}/close", position.getId())
						.header("Authorization", bearer(token)))
				.andExpect(status().isNotFound());
	}

	@Test
	void adminWithoutCustomerRoleCannotUseCustomerPortfolio() throws Exception {
		User admin = saveUser("Admin", "portfolio-admin-rest@araripe.test", "senha-admin-123",
				SubscriptionStatus.NONE, UserRoleType.ADMIN);
		String token = login(admin, "senha-admin-123");

		mockMvc.perform(get("/api/v1/position-records").header("Authorization", bearer(token)))
				.andExpect(status().isForbidden());
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

	private PositionThesis thesis(Asset asset) {
		PositionThesis thesis = new PositionThesis(asset, LocalDate.of(2026, 7, 7),
				ThesisType.QUALITY_REASONABLE_PRICE, ThesisStatus.CRITERIOS_ATENDIDOS, 82, "rules-v1");
		thesis.setPriceCeiling(new BigDecimal("42.00"));
		thesis.setFairPriceEstimate(new BigDecimal("49.40"));
		thesis.setSafetyMarginPercent(new BigDecimal("0.150000"));
		return thesis;
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}
}
