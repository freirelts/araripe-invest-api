package com.freirelts.araripe_invest_api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.domain.users.UserStatus;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AuthSecurityTests {

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
	private JwtEncoder jwtEncoder;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void registeredCustomerCanAuthenticateAndPasswordIsHashed() throws Exception {
		String token = register("Cliente Araripe", "cliente-auth@araripe.test", "senha-forte-123");

		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("cliente-auth@araripe.test"))
				.andExpect(jsonPath("$.roles[*]").value(containsInAnyOrder("CUSTOMER")))
				.andExpect(jsonPath("$.termsVersionAccepted").value("terms-educational-v1"))
				.andExpect(jsonPath("$.termsAcceptedAt").isString());

		User user = userRepository.findByEmailIgnoreCase("cliente-auth@araripe.test").orElseThrow();
		assertThat(user.getPasswordHash()).isNotEqualTo("senha-forte-123");
		assertThat(passwordEncoder.matches("senha-forte-123", user.getPasswordHash())).isTrue();
	}

	@Test
	void currentTermsArePublicAndRegistrationRequiresAcceptance() throws Exception {
		mockMvc.perform(get("/api/v1/legal/terms/current"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value("terms-educational-v1"))
				.andExpect(jsonPath("$.clauses[0]").isString());

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Sem Termos","email":"sem-termos@araripe.test","password":"senha-forte-123","acceptedTerms":false,"acceptedTermsVersion":"terms-educational-v1"}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void loginReturnsSignedJwtForValidUser() throws Exception {
		saveUser("Login Customer", "login@araripe.test", "senha-login-123", SubscriptionStatus.ACTIVE,
				UserRoleType.CUSTOMER);

		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"login@araripe.test","password":"senha-login-123"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.accessToken").isString())
				.andExpect(jsonPath("$.user.roles[*]").value(containsInAnyOrder("CUSTOMER")))
				.andReturn()
				.getResponse()
				.getContentAsString();

		String token = objectMapper.readTree(response).get("accessToken").asText();
		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("login@araripe.test"));
	}

	@Test
	void invalidMissingOrExpiredJwtReceivesUnauthorized() throws Exception {
		User customer = saveUser("Expired Customer", "expired@araripe.test", "senha-expirada-123",
				SubscriptionStatus.ACTIVE, UserRoleType.CUSTOMER);

		mockMvc.perform(get("/api/v1/auth/me"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer("invalid.jwt.value")))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(expiredToken(customer))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void customerCannotAccessAdminEndpointsButAdminCan() throws Exception {
		String customerToken = login(saveUser("Customer", "customer-admin-denied@araripe.test", "senha-customer-123",
				SubscriptionStatus.ACTIVE, UserRoleType.CUSTOMER), "senha-customer-123");
		User admin = saveUser("Admin", "admin-allowed@araripe.test", "senha-admin-123",
				SubscriptionStatus.NONE, UserRoleType.ADMIN);
		String adminToken = login(admin, "senha-admin-123");

		mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(customerToken)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/admin/users/{userId}", admin.getId())
						.header("Authorization", bearer(customerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name":"Denied",
								  "email":"denied@araripe.test",
								  "status":"ACTIVE",
								  "subscriptionStatus":"ACTIVE",
								  "roles":["CUSTOMER"]
								}
								"""))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/admin/assets").header("Authorization", bearer(adminToken)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/admin/jobs/SCREENER/runs").header("Authorization", bearer(customerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"referenceDate":"2026-07-07"}
								"""))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/admin/jobs/SCREENER/runs").header("Authorization", bearer(adminToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"referenceDate":"2026-07-07"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.jobName").value("SCREENER"))
				.andExpect(jsonPath("$.referenceDate").value("2026-07-07"))
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.requestedByUserId").isString());
	}

	@Test
	void adminCanUpdateUserIncludingRolesAndRemovedRolesInvalidateOldJwt() throws Exception {
		User target = saveUser("Role Target", "role-target@araripe.test", "senha-role-target-123",
				SubscriptionStatus.ACTIVE, UserRoleType.CUSTOMER);
		String oldCustomerToken = login(target, "senha-role-target-123");
		String adminToken = login(saveUser("Role Admin", "role-admin@araripe.test", "senha-role-admin-123",
				SubscriptionStatus.NONE, UserRoleType.ADMIN), "senha-role-admin-123");

		mockMvc.perform(put("/api/v1/admin/users/{userId}", target.getId())
						.header("Authorization", bearer(adminToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name":"Role Target Updated",
								  "email":"ROLE-TARGET-UPDATED@ARARIPE.TEST",
								  "status":"ACTIVE",
								  "subscriptionStatus":"NONE",
								  "roles":["ADMIN"]
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Role Target Updated"))
				.andExpect(jsonPath("$.email").value("role-target-updated@araripe.test"))
				.andExpect(jsonPath("$.subscriptionStatus").value("NONE"))
				.andExpect(jsonPath("$.roles[*]").value(containsInAnyOrder("ADMIN")));

		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(oldCustomerToken)))
				.andExpect(status().isUnauthorized());

		String adminRoleToken = login(userRepository.findById(target.getId()).orElseThrow(), "senha-role-target-123");
		mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(adminRoleToken)))
				.andExpect(status().isOk());
	}

	@Test
	void blockedUserCannotUsePreviouslyIssuedJwt() throws Exception {
		User user = saveUser("Blocked", "blocked@araripe.test", "senha-blocked-123", SubscriptionStatus.ACTIVE,
				UserRoleType.CUSTOMER);
		String token = login(user, "senha-blocked-123");

		user.setStatus(UserStatus.BLOCKED);
		userRepository.saveAndFlush(user);

		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void inactiveSubscriptionCannotAuthenticateAsCustomer() throws Exception {
		saveUser("Past Due", "pastdue@araripe.test", "senha-pastdue-123", SubscriptionStatus.PAST_DUE,
				UserRoleType.CUSTOMER);

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"pastdue@araripe.test","password":"senha-pastdue-123"}
								"""))
				.andExpect(status().isUnauthorized());
	}

	private String register(String name, String email, String password) throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"%s","email":"%s","password":"%s","acceptedTerms":true,"acceptedTermsVersion":"terms-educational-v1"}
								""".formatted(name, email, password)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").isString())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(response).get("accessToken").asText();
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

	private String expiredToken(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("araripe-invest-api")
				.audience(List.of("araripe-invest-fed"))
				.issuedAt(now.minusSeconds(7200))
				.expiresAt(now.minusSeconds(3600))
				.subject(user.getId().toString())
				.claim("email", user.getEmail())
				.claim("roles", List.of("CUSTOMER"))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}
}
