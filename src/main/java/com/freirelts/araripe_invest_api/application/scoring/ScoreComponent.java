package com.freirelts.araripe_invest_api.application.scoring;

import java.math.BigDecimal;
import java.util.List;

public record ScoreComponent(
		String code,
		String label,
		int weightPercent,
		int rawScore,
		BigDecimal weightedPoints,
		String message,
		List<String> evidence) {
}
