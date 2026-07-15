package com.freirelts.araripe_invest_api.application.risk;

import java.math.BigDecimal;

public record RiskAllocationResult(
		BigDecimal capitalBase,
		BigDecimal targetAllocationPercent,
		BigDecimal maxAllocationPerAssetPercent,
		BigDecimal maxAllocationPerSectorPercent,
		BigDecimal minimumCashReservePercent,
		BigDecimal maxPositionValue,
		BigDecimal currentAssetExposureValue,
		BigDecimal currentSectorExposureValue,
		BigDecimal availableForAsset,
		BigDecimal availableForSector,
		BigDecimal currentTotalExposureValue,
		BigDecimal availableForCash,
		BigDecimal currentPrice,
		BigDecimal priceCeiling,
		BigDecimal fairPriceEstimate,
		BigDecimal safetyMarginPercent,
		BigDecimal estimatedUpsidePercent,
		int suggestedQuantity,
		BigDecimal firstTrancheValue,
		BigDecimal secondTrancheValue,
		BigDecimal thirdTrancheValue,
		BigDecimal remainingPlannedValue,
		BigDecimal stopPrice,
		BigDecimal targetPrice,
		boolean valid,
		String recommendedAction,
		String invalidReason) {
}
