package com.freirelts.araripe_invest_api.adapters.inbound.rest.alerts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
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

import static org.hamcrest.Matchers.hasSize;
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
class WatchedAssetControllerTests {

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

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void customerCanCreateUpdateListAndArchiveWatchedAsset() throws Exception {
		User customer = saveUser("Watched Customer", "watched-rest@araripe.test", "senha-watched-123");
		Asset asset = assetRepository.saveAndFlush(new Asset("WEGE3", "WEG S.A.", "Bens Industriais"));
		String token = login(customer, "senha-watched-123");

		String created = mockMvc.perform(post("/api/v1/watched-assets")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "assetId":"%s",
								  "userLowerPriceThreshold":33.00,
								  "userUpperPriceThreshold":48.00,
								  "notes":"Acompanhar limiares informativos"
								}
								""".formatted(asset.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.symbol").value("WEGE3"))
				.andExpect(jsonPath("$.userLowerPriceThreshold").value(33.0))
				.andExpect(jsonPath("$.userUpperPriceThreshold").value(48.0))
				.andExpect(jsonPath("$.regulatoryNotice").isString())
				.andReturn()
				.getResponse()
				.getContentAsString();
		String watchItemId = objectMapper.readTree(created).get("id").asText();

		mockMvc.perform(get("/api/v1/watched-assets").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));

		mockMvc.perform(put("/api/v1/watched-assets/{watchItemId}", watchItemId)
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "userLowerPriceThreshold":31.00,
								  "userUpperPriceThreshold":52.00,
								  "notes":"Preferencias revisadas"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userLowerPriceThreshold").value(31.0))
				.andExpect(jsonPath("$.userUpperPriceThreshold").value(52.0));

		mockMvc.perform(patch("/api/v1/watched-assets/{watchItemId}/archive", watchItemId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ARCHIVED"));
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

	private User saveUser(String name, String email, String password) {
		User user = new User(name, email, passwordEncoder.encode(password), SubscriptionStatus.ACTIVE);
		user.addRole(UserRoleType.CUSTOMER);
		return userRepository.saveAndFlush(user);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}
}
