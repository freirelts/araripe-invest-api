package com.freirelts.araripe_invest_api.application.marketdata;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public record ProviderRawResponse(
		String provider,
		String endpoint,
		List<String> requestedSymbols,
		List<String> queriedSymbols,
		Instant requestedAt,
		long tookMillis,
		ProviderResponseStatus status,
		JsonNode payload,
		String errorCode,
		String errorMessage) {

	public static ProviderRawResponse success(String provider, String endpoint, List<String> requestedSymbols,
			List<String> queriedSymbols, Instant requestedAt, long tookMillis, JsonNode payload) {
		return new ProviderRawResponse(provider, endpoint, List.copyOf(requestedSymbols), List.copyOf(queriedSymbols),
				requestedAt, tookMillis, ProviderResponseStatus.SUCCESS, payload, null, null);
	}

	public static ProviderRawResponse skipped(String provider, String endpoint, List<String> requestedSymbols,
			String errorCode, String errorMessage) {
		return new ProviderRawResponse(provider, endpoint, List.copyOf(requestedSymbols), List.of(), Instant.now(), 0,
				ProviderResponseStatus.SKIPPED, null, errorCode, errorMessage);
	}

	public static ProviderRawResponse failed(String provider, String endpoint, List<String> requestedSymbols,
			List<String> queriedSymbols, Instant requestedAt, long tookMillis, String errorCode, String errorMessage) {
		return new ProviderRawResponse(provider, endpoint, List.copyOf(requestedSymbols), List.copyOf(queriedSymbols),
				requestedAt, tookMillis, ProviderResponseStatus.FAILED, null, errorCode, errorMessage);
	}
}
