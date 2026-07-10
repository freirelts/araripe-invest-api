package com.freirelts.araripe_invest_api.application.ai;

import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;

public record AiContextValidationResult(
		AiValidationStatus status,
		String errorMessage) {

	public static AiContextValidationResult valid() {
		return new AiContextValidationResult(AiValidationStatus.VALID, null);
	}

	public static AiContextValidationResult invalid(String errorMessage) {
		return new AiContextValidationResult(AiValidationStatus.INVALID, errorMessage);
	}
}
