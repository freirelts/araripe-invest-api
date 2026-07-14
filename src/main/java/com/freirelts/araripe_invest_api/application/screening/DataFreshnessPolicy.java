package com.freirelts.araripe_invest_api.application.screening;

import java.time.LocalDate;

public final class DataFreshnessPolicy {

	private static final int MAX_MARKET_DATA_LAG_DAYS = 4;
	private static final int MAX_FUNDAMENTAL_LAG_MONTHS = 6;

	private DataFreshnessPolicy() {
	}

	public static boolean marketDataStale(LocalDate referenceDate, LocalDate dataDate) {
		return referenceDate != null && dataDate != null
				&& dataDate.isBefore(referenceDate.minusDays(MAX_MARKET_DATA_LAG_DAYS));
	}

	public static boolean fundamentalDataStale(LocalDate referenceDate, LocalDate dataDate) {
		return referenceDate != null && dataDate != null
				&& dataDate.isBefore(referenceDate.minusMonths(MAX_FUNDAMENTAL_LAG_MONTHS));
	}
}
