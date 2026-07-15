package com.freirelts.araripe_invest_api.application.marketdata;

import java.time.LocalDate;
import java.util.Objects;

public record DividendDataRequest(LocalDate startDate, LocalDate endDate, String sortOrder) {

	public DividendDataRequest {
		sortOrder = requireText(sortOrder, "sortOrder");
		if ((startDate == null) != (endDate == null)) {
			throw new IllegalArgumentException("startDate and endDate must be provided together.");
		}
		if (startDate != null && startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("startDate must be before or equal to endDate.");
		}
	}

	public static DividendDataRequest allDescending() {
		return new DividendDataRequest(null, null, "desc");
	}

	public static DividendDataRequest windowDescending(LocalDate endDate, int daysBack) {
		if (daysBack < 0) {
			throw new IllegalArgumentException("daysBack must be greater than or equal to zero.");
		}
		LocalDate normalizedEndDate = Objects.requireNonNull(endDate, "endDate is required");
		return new DividendDataRequest(normalizedEndDate.minusDays(daysBack), normalizedEndDate, "desc");
	}

	private static String requireText(String value, String field) {
		String normalized = Objects.requireNonNull(value, field + " is required").trim();
		if (normalized.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return normalized;
	}
}
