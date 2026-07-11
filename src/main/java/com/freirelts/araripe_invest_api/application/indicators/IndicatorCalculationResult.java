package com.freirelts.araripe_invest_api.application.indicators;

import java.time.LocalDate;
import java.util.UUID;

public record IndicatorCalculationResult(UUID assetId, LocalDate referenceDate, boolean technicalComplete,
		boolean fundamentalComplete) {
}
