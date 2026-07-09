package com.freirelts.araripe_invest_api.application.marketdata;

import java.util.Objects;

public record HistoricalDataRequest(String range, String interval, String sortOrder) {

	public HistoricalDataRequest {
		range = requireText(range, "range");
		interval = requireText(interval, "interval");
		sortOrder = requireText(sortOrder, "sortOrder");
	}

	public static HistoricalDataRequest dailyAscending(String range) {
		return new HistoricalDataRequest(range, "1d", "asc");
	}

	private static String requireText(String value, String field) {
		String normalized = Objects.requireNonNull(value, field + " is required").trim();
		if (normalized.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return normalized;
	}
}
