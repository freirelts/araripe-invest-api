package com.freirelts.araripe_invest_api.application.risk;

import java.math.BigDecimal;

public record RiskAllocationSettings(
		BigDecimal capitalBase,
		BigDecimal maxAllocationPerAssetPercent,
		BigDecimal maxAllocationPerSectorPercent,
		BigDecimal toleratedDrawdownPercent,
		BigDecimal minimumCashReservePercent,
		BigDecimal minimumSafetyMarginPercent,
		BigDecimal firstTranchePercent,
		BigDecimal secondTranchePercent,
		BigDecimal thirdTranchePercent,
		BigDecimal defaultStopPercent,
		BigDecimal defaultTargetReturnPercent) {

	public static RiskAllocationSettings conservativeDefault() {
		return new RiskAllocationSettings(new BigDecimal("10000.00"), new BigDecimal("10.000000"),
				new BigDecimal("25.000000"), new BigDecimal("25.000000"), new BigDecimal("10.000000"),
				new BigDecimal("15.000000"), new BigDecimal("50.000000"), new BigDecimal("25.000000"),
				new BigDecimal("25.000000"), new BigDecimal("15.000000"), new BigDecimal("25.000000"));
	}
}
