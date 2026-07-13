package com.freirelts.araripe_invest_api.application.scoring;

import com.freirelts.araripe_invest_api.application.screening.EliminatoryFilterReason;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;

import java.util.List;

public record ScoreResult(
		int finalScore,
		ThesisType thesisType,
		String ruleVersion,
		boolean blockedByEliminatoryFilter,
		boolean scoreCalculated,
		List<EliminatoryFilterReason> failedFilters,
		List<ScoreComponent> components) {
}
