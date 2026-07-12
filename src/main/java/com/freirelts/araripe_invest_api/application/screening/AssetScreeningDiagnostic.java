package com.freirelts.araripe_invest_api.application.screening;

import com.freirelts.araripe_invest_api.domain.screening.ScreeningStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AssetScreeningDiagnostic(
		UUID assetId,
		String symbol,
		LocalDate referenceDate,
		ScreeningStatus status,
		String ruleVersion,
		List<EliminatoryFilterReason> failedFilters,
		List<PositionThesisScoreDiagnostic> thesisScores) {
}
