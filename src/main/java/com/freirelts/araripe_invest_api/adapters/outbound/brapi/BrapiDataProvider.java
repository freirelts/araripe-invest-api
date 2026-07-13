package com.freirelts.araripe_invest_api.adapters.outbound.brapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.assets.MonitoredAssetUniverseService;
import com.freirelts.araripe_invest_api.application.marketdata.FundamentalDataProvider;
import com.freirelts.araripe_invest_api.application.marketdata.HistoricalDataRequest;
import com.freirelts.araripe_invest_api.application.marketdata.MacroEconomicDataProvider;
import com.freirelts.araripe_invest_api.application.marketdata.MarketDataProvider;
import com.freirelts.araripe_invest_api.application.marketdata.ProviderRawResponse;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

@Component
class BrapiDataProvider implements MarketDataProvider, FundamentalDataProvider, MacroEconomicDataProvider {

	static final String PROVIDER = "brapi";
	static final String TOKEN_MISSING = "BRAPI_TOKEN_MISSING";
	static final String NO_ACTIVE_ASSETS = "NO_ACTIVE_MONITORED_ASSETS";

	private final RestClient restClient;
	private final BrapiProperties properties;
	private final MonitoredAssetUniverseService monitoredAssetUniverse;
	private final ObjectMapper objectMapper = new ObjectMapper();

	BrapiDataProvider(RestClient brapiRestClient, BrapiProperties properties,
			MonitoredAssetUniverseService monitoredAssetUniverse) {
		this.restClient = brapiRestClient;
		this.properties = properties;
		this.monitoredAssetUniverse = monitoredAssetUniverse;
	}

	@Override
	public ProviderRawResponse fetchCurrentQuotes(Collection<String> symbols) {
		return fetchForSymbols("/v2/stocks/quote", symbols, query -> query.queryParam("symbols", query.joinedSymbols()));
	}

	@Override
	public ProviderRawResponse fetchDailyHistory(Collection<String> symbols, HistoricalDataRequest request) {
		return fetchForSymbols("/v2/stocks/historical", symbols, query -> query
				.queryParam("symbols", query.joinedSymbols())
				.queryParam("range", request.range())
				.queryParam("interval", request.interval())
				.queryParam("sortOrder", request.sortOrder()));
	}

	@Override
	public ProviderRawResponse fetchCompanyProfiles(Collection<String> symbols) {
		return fetchForSymbols("/v2/stocks/profile", symbols, query -> query.queryParam("symbols", query.joinedSymbols()));
	}

	@Override
	public ProviderRawResponse fetchStatistics(Collection<String> symbols) {
		return fetchForSymbols("/v2/stocks/statistics", symbols, query -> query.queryParam("symbols", query.joinedSymbols()));
	}

	@Override
	public ProviderRawResponse fetchFinancialData(Collection<String> symbols) {
		return fetchForSymbols("/v2/stocks/financial-data", symbols, query -> query.queryParam("symbols", query.joinedSymbols()));
	}

	@Override
	public ProviderRawResponse fetchBalanceSheets(Collection<String> symbols) {
		return fetchForSymbols("/v2/stocks/balance-sheet", symbols, query -> query.queryParam("symbols", query.joinedSymbols()));
	}

	@Override
	public ProviderRawResponse fetchIncomeStatements(Collection<String> symbols) {
		return fetchForSymbols("/v2/stocks/income-statement", symbols, query -> query.queryParam("symbols", query.joinedSymbols()));
	}

	@Override
	public ProviderRawResponse fetchCashFlows(Collection<String> symbols) {
		return fetchForSymbols("/v2/stocks/cash-flow", symbols, query -> query.queryParam("symbols", query.joinedSymbols()));
	}

	@Override
	public ProviderRawResponse fetchDividends(Collection<String> symbols) {
		return fetchForSymbols("/v2/stocks/dividends", symbols, query -> query
				.queryParam("symbols", query.joinedSymbols())
				.queryParam("sortOrder", "desc"));
	}

	@Override
	public ProviderRawResponse fetchAvailableSeries() {
		return fetchMacro("/v2/macro/available", query -> query);
	}

	@Override
	public ProviderRawResponse fetchSeries(Collection<String> slugs) {
		List<String> normalizedSlugs = monitoredAssetUniverse.normalizeSymbols(slugs);
		if (normalizedSlugs.isEmpty()) {
			return ProviderRawResponse.skipped(PROVIDER, "/v2/macro", List.of(), "NO_MACRO_SERIES",
					"No macroeconomic series were requested.");
		}
		String joinedSlugs = String.join(",", normalizedSlugs);
		return fetchMacro("/v2/macro", query -> query.queryParam("symbols", joinedSlugs), normalizedSlugs);
	}

	private ProviderRawResponse fetchForSymbols(String endpoint, Collection<String> symbols,
			Function<Query, Query> queryCustomizer) {
		List<String> requestedSymbols = monitoredAssetUniverse.normalizeSymbols(symbols);
		List<String> activeSymbols = monitoredAssetUniverse.findActiveAssets(requestedSymbols).stream()
				.map(Asset::getSymbol)
				.toList();

		if (activeSymbols.isEmpty()) {
			return ProviderRawResponse.skipped(PROVIDER, endpoint, requestedSymbols, NO_ACTIVE_ASSETS,
					"No active monitored assets were found for the requested symbols.");
		}
		return fetch(endpoint, requestedSymbols, activeSymbols, queryCustomizer.apply(new Query(endpoint, activeSymbols)));
	}

	private ProviderRawResponse fetchMacro(String endpoint, Function<Query, Query> queryCustomizer) {
		return fetchMacro(endpoint, queryCustomizer, List.of());
	}

	private ProviderRawResponse fetchMacro(String endpoint, Function<Query, Query> queryCustomizer,
			List<String> requestedSlugs) {
		return fetch(endpoint, requestedSlugs, requestedSlugs, queryCustomizer.apply(new Query(endpoint, List.of())));
	}

	private ProviderRawResponse fetch(String endpoint, List<String> requestedSymbols, List<String> queriedSymbols,
			Query query) {
		if (!properties.hasToken()) {
			return ProviderRawResponse.failed(PROVIDER, endpoint, requestedSymbols, queriedSymbols, Instant.now(), 0,
					TOKEN_MISSING, "Brapi token is not configured for protected endpoint access.");
		}

		Instant requestedAt = Instant.now();
		try {
			String rawPayload = restClient.get()
					.uri(query.toUriString())
					.headers(headers -> headers.setBearerAuth(properties.token()))
					.retrieve()
					.body(String.class);
			JsonNode payload = objectMapper.readTree(rawPayload);
			return ProviderRawResponse.success(PROVIDER, endpoint, requestedSymbols, queriedSymbols, requestedAt,
					Duration.between(requestedAt, Instant.now()).toMillis(), payload);
		}
		catch (JsonProcessingException ex) {
			return ProviderRawResponse.failed(PROVIDER, endpoint, requestedSymbols, queriedSymbols, requestedAt,
					Duration.between(requestedAt, Instant.now()).toMillis(), "BRAPI_INVALID_JSON",
					"Brapi response could not be parsed as JSON.");
		}
		catch (RestClientResponseException ex) {
			return ProviderRawResponse.failed(PROVIDER, endpoint, requestedSymbols, queriedSymbols, requestedAt,
					Duration.between(requestedAt, Instant.now()).toMillis(), "BRAPI_HTTP_" + ex.getStatusCode().value(),
					"Brapi request failed with HTTP status " + ex.getStatusCode().value() + ".");
		}
		catch (RuntimeException ex) {
			return ProviderRawResponse.failed(PROVIDER, endpoint, requestedSymbols, queriedSymbols, requestedAt,
					Duration.between(requestedAt, Instant.now()).toMillis(), "BRAPI_REQUEST_FAILED",
					"Brapi request failed before a valid response was received: " + ex.getClass().getSimpleName() + ".");
		}
	}

	record Query(String path, List<String> symbols, StringBuilder queryString) {

		Query(String path, List<String> symbols) {
			this(path, symbols, new StringBuilder());
		}

		Query queryParam(String name, String value) {
			if (value == null || value.isBlank()) {
				return this;
			}
			if (!queryString.isEmpty()) {
				queryString.append('&');
			}
			queryString.append(name).append('=').append(value);
			return this;
		}

		String joinedSymbols() {
			return String.join(",", symbols);
		}

		String toUriString() {
			String rawQuery = queryString.toString();
			return path + (rawQuery.isBlank() ? "" : "?" + rawQuery);
		}
	}
}
