package com.freirelts.araripe_invest_api.application.screening;

import com.fasterxml.jackson.databind.JsonNode;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;

import java.util.UUID;

public record PositionThesisScoreDiagnostic(
		UUID thesisId,
		ThesisType thesisType,
		ThesisStatus status,
		int score,
		String ruleVersion,
		JsonNode scoreBreakdown) {
}
