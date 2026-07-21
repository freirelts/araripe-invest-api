package com.freirelts.araripe_invest_api.adapters.outbound.brapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.freirelts.araripe_invest_api.application.assets.MonitoredAssetUniverseService;
import com.freirelts.araripe_invest_api.application.auth.AuthService;
import com.freirelts.araripe_invest_api.application.marketdata.DividendDataRequest;
import com.freirelts.araripe_invest_api.application.marketdata.FundamentalDataProvider;
import com.freirelts.araripe_invest_api.application.marketdata.MarketDataProvider;
import com.freirelts.araripe_invest_api.application.marketdata.ProviderRawResponse;
import com.freirelts.araripe_invest_api.application.marketdata.ProviderResponseStatus;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class BrapiDataProviderIntegrationTests {

	private static final TestBrapiServer BRAPI_SERVER = new TestBrapiServer();

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@Autowired
	private MarketDataProvider marketDataProvider;

	@Autowired
	private FundamentalDataProvider fundamentalDataProvider;

	@Autowired
	private MonitoredAssetUniverseService monitoredAssetUniverse;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuthService authService;

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		BRAPI_SERVER.start();
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("araripe.integrations.brapi.base-url", BRAPI_SERVER::baseUrl);
		registry.add("araripe.integrations.brapi.token", () -> "test-brapi-token");
		registry.add("araripe.integrations.brapi.timeout-seconds", () -> "5");
		registry.add("araripe.integrations.brapi.retry-max-attempts", () -> "1");
	}

	@AfterEach
	void cleanUp() {
		BRAPI_SERVER.reset();
		assetRepository.deleteAll();
	}

	@AfterAll
	static void stopServer() {
		BRAPI_SERVER.stop();
	}

	@Test
	void adminCanCreateAndInactivateMonitoredAsset() throws Exception {
		String token = adminToken("admin-assets@araripe.test");

		String response = mockMvc.perform(post("/api/v1/admin/assets")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "symbol": "wege3",
								  "name": "WEG S.A.",
								  "sector": "Bens Industriais",
								  "industry": "Motores e Equipamentos",
								  "market": "B3",
								  "assetType": "STOCK",
								  "active": true,
								  "monitoringReason": "Liquidez e cobertura de dados adequadas."
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.symbol").value("WEGE3"))
				.andExpect(jsonPath("$.active").value(true))
				.andReturn()
				.getResponse()
				.getContentAsString();

		String assetId = response.substring(response.indexOf("\"id\":\"") + 6, response.indexOf("\",\"symbol\""));

		mockMvc.perform(patch("/api/v1/admin/assets/{assetId}/status", assetId)
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"active": false}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void providerOnlyQueriesRegisteredActiveAssets() {
		assetRepository.saveAndFlush(new Asset("PETR4", "Petrobras PN", "Energia"));
		Asset inactive = new Asset("VALE3", "Vale S.A.", "Materiais Básicos");
		inactive.setActive(false);
		assetRepository.saveAndFlush(inactive);

		ProviderRawResponse response = marketDataProvider.fetchCurrentQuotes(List.of("petr4", "vale3", "missing11"));

		assertThat(response.status()).as(response.toString()).isEqualTo(ProviderResponseStatus.SUCCESS);
		assertThat(response.requestedSymbols()).containsExactly("PETR4", "VALE3", "MISSING11");
		assertThat(response.queriedSymbols()).containsExactly("PETR4");
		assertThat(response.endpoint()).isEqualTo("/v2/stocks/quote?symbols=PETR4");
		assertThat(BRAPI_SERVER.requestUris()).hasSize(1);
		assertThat(BRAPI_SERVER.requestUris().getFirst().getRawQuery()).isEqualTo("symbols=PETR4");
		assertThat(BRAPI_SERVER.authorizationHeaders()).containsExactly("Bearer test-brapi-token");
		assertThat(response.payload()).isInstanceOf(JsonNode.class);
	}

	@Test
	void providerSplitsQuoteRequestsIntoBatchesOfFiveSymbols() {
		List<String> symbols = List.of("PETR4", "VALE3", "ITUB4", "BBDC4", "ABEV3", "WEGE3", "BBAS3", "MGLU3",
				"RENT3", "LREN3", "SUZB3", "RAIL3");
		symbols.forEach(symbol -> assetRepository.save(new Asset(symbol, symbol, "Setor")));
		assetRepository.flush();

		ProviderRawResponse response = marketDataProvider.fetchCurrentQuotes(symbols);

		assertThat(response.status()).isEqualTo(ProviderResponseStatus.SUCCESS);
		assertThat(response.requestedSymbols()).containsExactlyElementsOf(symbols);
		assertThat(response.queriedSymbols()).containsExactlyElementsOf(symbols);
		assertThat(response.endpoint()).isEqualTo(
				"/v2/stocks/quote?symbols=PETR4,VALE3,ITUB4,BBDC4,ABEV3,"
						+ "/v2/stocks/quote?symbols=WEGE3,BBAS3,MGLU3,RENT3,LREN3,"
						+ "/v2/stocks/quote?symbols=SUZB3,RAIL3");
		assertThat(BRAPI_SERVER.requestUris()).extracting(URI::getQuery)
				.containsExactly("symbols=PETR4,VALE3,ITUB4,BBDC4,ABEV3",
						"symbols=WEGE3,BBAS3,MGLU3,RENT3,LREN3", "symbols=SUZB3,RAIL3");
		assertThat(BRAPI_SERVER.authorizationHeaders()).containsExactly("Bearer test-brapi-token",
				"Bearer test-brapi-token", "Bearer test-brapi-token");
	}

	@Test
	void providerAddsPeriodWhenFetchingAnnualAndQuarterlyIncomeStatements() {
		assetRepository.saveAndFlush(new Asset("PETR4", "Petrobras PN", "Energia"));

		ProviderRawResponse annual = fundamentalDataProvider.fetchIncomeStatements(List.of("petr4"),
				PeriodType.ANNUAL);
		ProviderRawResponse quarterly = fundamentalDataProvider.fetchIncomeStatements(List.of("petr4"),
				PeriodType.QUARTERLY);

		assertThat(annual.status()).isEqualTo(ProviderResponseStatus.SUCCESS);
		assertThat(quarterly.status()).isEqualTo(ProviderResponseStatus.SUCCESS);
		assertThat(annual.endpoint()).isEqualTo("/v2/stocks/income-statement?symbols=PETR4&period=annual");
		assertThat(quarterly.endpoint()).isEqualTo("/v2/stocks/income-statement?symbols=PETR4&period=quarterly");
		assertThat(BRAPI_SERVER.requestUris()).hasSize(2);
		assertThat(BRAPI_SERVER.requestUris().get(0).getRawQuery()).isEqualTo("symbols=PETR4&period=annual");
		assertThat(BRAPI_SERVER.requestUris().get(1).getRawQuery()).isEqualTo("symbols=PETR4&period=quarterly");
	}

	@Test
	void providerAddsDateWindowWhenFetchingIncrementalDividends() {
		assetRepository.saveAndFlush(new Asset("VALE3", "Vale S.A.", "Materiais Básicos"));

		ProviderRawResponse response = fundamentalDataProvider.fetchDividends(List.of("vale3"),
				new DividendDataRequest(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 9), "desc"));

		assertThat(response.status()).isEqualTo(ProviderResponseStatus.SUCCESS);
		assertThat(response.endpoint())
				.isEqualTo("/v2/stocks/dividends?symbols=VALE3&sortOrder=desc&startDate=2026-03-04&endDate=2026-03-09");
		assertThat(BRAPI_SERVER.requestUris()).hasSize(1);
		assertThat(BRAPI_SERVER.requestUris().getFirst().getRawQuery())
				.isEqualTo("symbols=VALE3&sortOrder=desc&startDate=2026-03-04&endDate=2026-03-09");
	}

	@Test
	void providerSplitsIncomeStatementRequestsAndKeepsPeriodInEveryBatch() {
		List<String> symbols = List.of("PETR4", "VALE3", "ITUB4", "BBDC4", "ABEV3", "WEGE3");
		symbols.forEach(symbol -> assetRepository.save(new Asset(symbol, symbol, "Setor")));
		assetRepository.flush();

		ProviderRawResponse response = fundamentalDataProvider.fetchIncomeStatements(symbols, PeriodType.QUARTERLY);

		assertThat(response.status()).isEqualTo(ProviderResponseStatus.SUCCESS);
		assertThat(BRAPI_SERVER.requestUris()).extracting(URI::getQuery)
				.containsExactly("symbols=PETR4,VALE3,ITUB4,BBDC4,ABEV3&period=quarterly",
						"symbols=WEGE3&period=quarterly");
	}

	@Test
	void providerSkipsExternalCallWhenNoRequestedAssetIsActive() {
		Asset inactive = new Asset("ABEV3", "Ambev ON", "Consumo");
		inactive.setActive(false);
		assetRepository.saveAndFlush(inactive);

		ProviderRawResponse response = marketDataProvider.fetchCurrentQuotes(List.of("ABEV3", "UNKNOWN3"));

		assertThat(response.status()).isEqualTo(ProviderResponseStatus.SKIPPED);
		assertThat(response.errorCode()).isEqualTo(BrapiDataProvider.NO_ACTIVE_ASSETS);
		assertThat(BRAPI_SERVER.requestUris()).isEmpty();
	}

	@Test
	void protectedEndpointWithoutTokenReturnsTraceableFailureWithoutCallingBrapi() {
		assetRepository.saveAndFlush(new Asset("ITUB4", "Itaú Unibanco PN", "Financeiro"));
		BrapiDataProvider providerWithoutToken = new BrapiDataProvider(new FailingBrapiFeignClient(),
				new BrapiProperties(BRAPI_SERVER.baseUrl(), "", 5, 1), monitoredAssetUniverse);

		ProviderRawResponse response = providerWithoutToken.fetchCurrentQuotes(List.of("ITUB4"));

		assertThat(response.status()).isEqualTo(ProviderResponseStatus.FAILED);
		assertThat(response.endpoint()).isEqualTo("/v2/stocks/quote?symbols=ITUB4");
		assertThat(response.errorCode()).isEqualTo(BrapiDataProvider.TOKEN_MISSING);
		assertThat(response.queriedSymbols()).containsExactly("ITUB4");
		assertThat(BRAPI_SERVER.requestUris()).isEmpty();
	}

	private String adminToken(String email) {
		User admin = new User("Admin", email, passwordEncoder.encode("senha-admin-123"), SubscriptionStatus.NONE);
		admin.addRole(UserRoleType.ADMIN);
		userRepository.saveAndFlush(admin);
		return authService.login(email, "senha-admin-123").accessToken();
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private static final class TestBrapiServer {

		private HttpServer server;
		private final List<URI> requestUris = new ArrayList<>();
		private final List<String> authorizationHeaders = new ArrayList<>();

		void start() {
			if (server != null) {
				return;
			}
			try {
				server = HttpServer.create(new InetSocketAddress(0), 0);
			}
			catch (IOException ex) {
				throw new IllegalStateException("Could not start test brapi server.", ex);
			}
			server.createContext("/api/v2/stocks/quote", this::handleQuote);
			server.createContext("/api/v2/stocks/income-statement", this::handleIncomeStatement);
			server.createContext("/api/v2/stocks/dividends", this::handleDividends);
			server.start();
		}

		void reset() {
			requestUris.clear();
			authorizationHeaders.clear();
		}

		void stop() {
			if (server != null) {
				server.stop(0);
			}
		}

		String baseUrl() {
			return "http://localhost:%d/api".formatted(server.getAddress().getPort());
		}

		List<URI> requestUris() {
			return requestUris;
		}

		List<String> authorizationHeaders() {
			return authorizationHeaders;
		}

		private void handleQuote(HttpExchange exchange) throws IOException {
			recordRequest(exchange);
			writeJson(exchange, """
					{"results":[],"requestedAt":"2026-07-09T12:00:00.000Z","took":1}
					""");
		}

		private void handleIncomeStatement(HttpExchange exchange) throws IOException {
			recordRequest(exchange);
			writeJson(exchange, """
					{"results":[],"requestedAt":"2026-07-09T12:00:00.000Z","took":1}
					""");
		}

		private void handleDividends(HttpExchange exchange) throws IOException {
			recordRequest(exchange);
			writeJson(exchange, """
					{"results":[],"requestedAt":"2026-07-09T12:00:00.000Z","took":1}
					""");
		}

		private void recordRequest(HttpExchange exchange) {
			requestUris.add(exchange.getRequestURI());
			authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
		}

		private void writeJson(HttpExchange exchange, String json) throws IOException {
			byte[] body = json.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream outputStream = exchange.getResponseBody()) {
				outputStream.write(body);
			}
		}
	}

	private static final class FailingBrapiFeignClient implements BrapiFeignClient {

		@Override
		public String quote(String authorization, String symbols) {
			throw unexpectedCall();
		}

		@Override
		public String historical(String authorization, String symbols, String range, String interval, String sortOrder) {
			throw unexpectedCall();
		}

		@Override
		public String profile(String authorization, String symbols) {
			throw unexpectedCall();
		}

		@Override
		public String statistics(String authorization, String symbols) {
			throw unexpectedCall();
		}

		@Override
		public String financialData(String authorization, String symbols) {
			throw unexpectedCall();
		}

		@Override
		public String balanceSheet(String authorization, String symbols) {
			throw unexpectedCall();
		}

		@Override
		public String incomeStatement(String authorization, String symbols, String period) {
			throw unexpectedCall();
		}

		@Override
		public String cashFlow(String authorization, String symbols) {
			throw unexpectedCall();
		}

		@Override
		public String dividends(String authorization, String symbols, String sortOrder, String startDate, String endDate) {
			throw unexpectedCall();
		}

		@Override
		public String availableMacroSeries(String authorization) {
			throw unexpectedCall();
		}

		@Override
		public String macroSeries(String authorization, String symbols) {
			throw unexpectedCall();
		}

		private IllegalStateException unexpectedCall() {
			return new IllegalStateException("Brapi client should not be called without a configured token.");
		}
	}
}
