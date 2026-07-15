package com.freirelts.araripe_invest_api.application.screening;

import java.time.LocalDate;

public final class DataFreshnessPolicy {

	private static final int MAX_MARKET_DATA_LAG_DAYS = 4;
	private static final int MAX_FUNDAMENTAL_LAG_MONTHS = 6;
	private static final int QUARTERLY_REPORT_DUE_DAYS = 45;
	private static final int ANNUAL_REPORT_DUE_MONTHS = 3;
	private static final int FUNDAMENTAL_REPORT_TOLERANCE_DAYS = 15;

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

	public static boolean fundamentalAccountingPeriodStale(LocalDate referenceDate, LocalDate accountingPeriodEndDate,
			LocalDate latestAnnualStatementEndDate, LocalDate processingDate) {
		if (referenceDate == null) {
			return false;
		}
		if (accountingPeriodEndDate == null) {
			return fundamentalDataStale(referenceDate, processingDate);
		}
		LocalDate nextExpectedPeriodEnd = accountingPeriodEndDate.plusMonths(3);
		LocalDate dueDate = isFiscalYearEnd(nextExpectedPeriodEnd, latestAnnualStatementEndDate)
				? nextExpectedPeriodEnd.plusMonths(ANNUAL_REPORT_DUE_MONTHS)
				: nextExpectedPeriodEnd.plusDays(QUARTERLY_REPORT_DUE_DAYS);
		return referenceDate.isAfter(dueDate.plusDays(FUNDAMENTAL_REPORT_TOLERANCE_DAYS));
	}

	private static boolean isFiscalYearEnd(LocalDate periodEndDate, LocalDate latestAnnualStatementEndDate) {
		return periodEndDate != null && latestAnnualStatementEndDate != null
				&& periodEndDate.getMonth() == latestAnnualStatementEndDate.getMonth()
				&& periodEndDate.getDayOfMonth() == latestAnnualStatementEndDate.getDayOfMonth();
	}
}
