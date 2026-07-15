package com.freirelts.araripe_invest_api.application.risk;

import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;

import java.math.BigDecimal;

public record RiskAllocationInput(
		RiskAllocationSettings settings,
		ThesisStatus thesisStatus,
		BigDecimal currentPrice,
		BigDecimal fairPriceEstimate,
		BigDecimal priceCeiling,
		BigDecimal safetyMargin,
		BigDecimal targetAllocationPercent,
		BigDecimal currentAssetExposureValue,
		BigDecimal currentSectorExposureValue,
		BigDecimal currentTotalExposureValue,
		BigDecimal averagePrice,
		BigDecimal userStopPrice,
		BigDecimal userTargetPrice,
		BigDecimal recentDrawdown,
		TrendStatus trendStatus,
		boolean fundamentalsDeteriorated) {
}
