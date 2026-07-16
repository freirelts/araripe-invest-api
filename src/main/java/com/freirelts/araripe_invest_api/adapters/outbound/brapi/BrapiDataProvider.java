package com.freirelts.araripe_invest_api.adapters.outbound.brapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.freirelts.araripe_invest_api.application.assets.MonitoredAssetUniverseService;
import com.freirelts.araripe_invest_api.application.marketdata.DividendDataRequest;
import com.freirelts.araripe_invest_api.application.marketdata.FundamentalDataProvider;
import com.freirelts.araripe_invest_api.application.marketdata.HistoricalDataRequest;
import com.freirelts.araripe_invest_api.application.marketdata.MacroEconomicDataProvider;
import com.freirelts.araripe_invest_api.application.marketdata.MarketDataProvider;
import com.freirelts.araripe_invest_api.application.marketdata.ProviderRawResponse;
import com.freirelts.araripe_invest_api.application.marketdata.ProviderResponseStatus;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

@Component
class BrapiDataProvider implements MarketDataProvider, FundamentalDataProvider, MacroEconomicDataProvider {

	static final String PROVIDER = "brapi";
	static final String TOKEN_MISSING = "BRAPI_TOKEN_MISSING";
	static final String NO_ACTIVE_ASSETS = "NO_ACTIVE_MONITORED_ASSETS";
	static final int SYMBOL_BATCH_SIZE = 5;
	private static final Logger log = LoggerFactory.getLogger(BrapiDataProvider.class);

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
	public ProviderRawResponse fetchIncomeStatements(Collection<String> symbols, PeriodType periodType) {
		return fetchForSymbols("/v2/stocks/income-statement", symbols, query -> query
				.queryParam("symbols", query.joinedSymbols())
				.queryParam("period", brapiStatementPeriod(periodType)));
	}

	@Override
	public ProviderRawResponse fetchCashFlows(Collection<String> symbols) {
		return fetchForSymbols("/v2/stocks/cash-flow", symbols, query -> query.queryParam("symbols", query.joinedSymbols()));
	}

	@Override
	public ProviderRawResponse fetchDividends(Collection<String> symbols, DividendDataRequest request) {
		return fetchForSymbols("/v2/stocks/dividends", symbols, query -> query
				.queryParam("symbols", query.joinedSymbols())
				.queryParam("sortOrder", request.sortOrder())
				.queryParam("startDate", request.startDate() == null ? null : request.startDate().toString())
				.queryParam("endDate", request.endDate() == null ? null : request.endDate().toString()));
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
		if (activeSymbols.size() <= SYMBOL_BATCH_SIZE) {
			return fetch(endpoint, requestedSymbols, activeSymbols,
					queryCustomizer.apply(new Query(endpoint, activeSymbols)));
		}
		if (!properties.hasToken()) {
			return ProviderRawResponse.failed(PROVIDER, endpoint, requestedSymbols, activeSymbols, Instant.now(), 0,
					TOKEN_MISSING, "Brapi token is not configured for protected endpoint access.");
		}

		List<ProviderRawResponse> responses = new ArrayList<>();
		for (List<String> batch : batches(activeSymbols, SYMBOL_BATCH_SIZE)) {
			responses.add(fetch(endpoint, requestedSymbols, batch, queryCustomizer.apply(new Query(endpoint, batch))));
		}
		return aggregateBatchResponses(endpoint, requestedSymbols, activeSymbols, responses);
	}

	private ProviderRawResponse aggregateBatchResponses(String endpoint, List<String> requestedSymbols,
			List<String> queriedSymbols, List<ProviderRawResponse> responses) {
		String aggregatedEndpoint = aggregateEndpoints(endpoint, responses);
		List<ProviderRawResponse> successfulResponses = responses.stream()
				.filter(response -> response.status() == ProviderResponseStatus.SUCCESS)
				.toList();
		if (successfulResponses.isEmpty()) {
			ProviderRawResponse firstFailure = responses.getFirst();
			return ProviderRawResponse.failed(PROVIDER, aggregatedEndpoint, requestedSymbols, queriedSymbols,
					firstFailure.requestedAt(), totalTookMillis(responses), firstFailure.errorCode(),
					firstFailure.errorMessage());
		}

		ProviderRawResponse firstSuccess = successfulResponses.getFirst();
		ObjectNode aggregatedPayload = objectMapper.createObjectNode();
		ArrayNode results = aggregatedPayload.putArray("results");
		ArrayNode batches = aggregatedPayload.putArray("batches");
		for (ProviderRawResponse response : responses) {
			ObjectNode batch = batches.addObject();
			batch.putPOJO("symbols", response.queriedSymbols());
			batch.put("status", response.status().name());
			batch.put("requestedAt", response.requestedAt().toString());
			batch.put("took", response.tookMillis());
			if (response.errorCode() != null) {
				batch.put("errorCode", response.errorCode());
			}
			if (response.payload() == null) {
				continue;
			}
			for (JsonNode result : response.payload().path("results")) {
				results.add(result);
			}
		}

		long tookMillis = totalTookMillis(responses);
		boolean hasFailure = responses.stream().anyMatch(response -> response.status() == ProviderResponseStatus.FAILED);
		if (!hasFailure) {
			return ProviderRawResponse.success(PROVIDER, aggregatedEndpoint, requestedSymbols, queriedSymbols,
					firstSuccess.requestedAt(), tookMillis, aggregatedPayload);
		}
		return new ProviderRawResponse(PROVIDER, aggregatedEndpoint, List.copyOf(requestedSymbols), List.copyOf(queriedSymbols),
				firstSuccess.requestedAt(), tookMillis, ProviderResponseStatus.PARTIAL, aggregatedPayload,
				"BRAPI_PARTIAL_FAILURE", "At least one brapi batch failed while other batches returned data.");
	}

	private static String aggregateEndpoints(String fallback, List<ProviderRawResponse> responses) {
		List<String> endpoints = responses.stream()
				.map(ProviderRawResponse::endpoint)
				.distinct()
				.toList();
		if (endpoints.isEmpty()) {
			return fallback;
		}
		if (endpoints.size() == 1) {
			return endpoints.getFirst();
		}
		return String.join(",", endpoints);
	}

	private static List<List<String>> batches(List<String> symbols, int batchSize) {
		List<List<String>> batches = new ArrayList<>();
		for (int start = 0; start < symbols.size(); start += batchSize) {
			batches.add(symbols.subList(start, Math.min(start + batchSize, symbols.size())));
		}
		return batches;
	}

	private static long totalTookMillis(List<ProviderRawResponse> responses) {
		return responses.stream()
				.mapToLong(ProviderRawResponse::tookMillis)
				.sum();
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
		String requestUri = query.toUriString();
		if (!properties.hasToken()) {
			return ProviderRawResponse.failed(PROVIDER, requestUri, requestedSymbols, queriedSymbols, Instant.now(), 0,
					TOKEN_MISSING, "Brapi token is not configured for protected endpoint access.");
		}

		Instant requestedAt = Instant.now();
		try {
			String rawPayload = restClient.get()
					.uri(requestUri)
					.headers(headers -> headers.setBearerAuth(properties.token()))
					.retrieve()
					.body(String.class);
			JsonNode payload = objectMapper.readTree(rawPayload);
			return ProviderRawResponse.success(PROVIDER, requestUri, requestedSymbols, queriedSymbols, requestedAt,
					Duration.between(requestedAt, Instant.now()).toMillis(), payload);
		}
		catch (JsonProcessingException ex) {
			log.warn("Brapi provider failed to parse JSON endpoint={} requestedSymbols={} queriedSymbols={}",
					requestUri, requestedSymbols, queriedSymbols, ex);
			return ProviderRawResponse.failed(PROVIDER, requestUri, requestedSymbols, queriedSymbols, requestedAt,
					Duration.between(requestedAt, Instant.now()).toMillis(), "BRAPI_INVALID_JSON",
					"Brapi response could not be parsed as JSON.");
		}
		catch (RestClientResponseException ex) {
			log.warn("Brapi provider HTTP failure endpoint={} status={} responseBody={} requestedSymbols={} queriedSymbols={}",
					requestUri, ex.getStatusCode().value(), ex.getResponseBodyAsString(), requestedSymbols,
					queriedSymbols, ex);
			return ProviderRawResponse.failed(PROVIDER, requestUri, requestedSymbols, queriedSymbols, requestedAt,
					Duration.between(requestedAt, Instant.now()).toMillis(), "BRAPI_HTTP_" + ex.getStatusCode().value(),
					"Brapi request failed with HTTP status " + ex.getStatusCode().value() + ".");
		}
		catch (RuntimeException ex) {
			log.warn("Brapi provider request failed endpoint={} requestedSymbols={} queriedSymbols={}",
					requestUri, requestedSymbols, queriedSymbols, ex);
			return ProviderRawResponse.failed(PROVIDER, requestUri, requestedSymbols, queriedSymbols, requestedAt,
					Duration.between(requestedAt, Instant.now()).toMillis(), "BRAPI_REQUEST_FAILED",
					"Brapi request failed before a valid response was received: " + ex.getClass().getSimpleName() + ".");
		}
	}

	private static String brapiStatementPeriod(PeriodType periodType) {
		return switch (periodType) {
			case ANNUAL -> "annual";
			case QUARTERLY -> "quarterly";
			case TTM -> throw new IllegalArgumentException("Brapi statement endpoints do not support TTM period.");
		};
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
