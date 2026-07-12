package com.freirelts.araripe_invest_api.adapters.inbound.rest.risk;

import com.freirelts.araripe_invest_api.application.api.ApiQueryService;
import com.freirelts.araripe_invest_api.application.api.ApiQueryService.RiskSettingsResponse;
import com.freirelts.araripe_invest_api.application.risk.RiskAllocationSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/allocation-settings")
@PreAuthorize("hasRole('CUSTOMER')")
class AllocationSettingsController {

	private final ApiQueryService apiQueryService;

	AllocationSettingsController(ApiQueryService apiQueryService) {
		this.apiQueryService = apiQueryService;
	}

	@GetMapping
	RiskSettingsResponse get(JwtAuthenticationToken authentication) {
		return apiQueryService.riskSettings(userId(authentication));
	}

	@PutMapping
	RiskSettingsResponse update(JwtAuthenticationToken authentication,
			@Valid @RequestBody RiskSettingsRequest request) {
		return apiQueryService.updateRiskSettings(userId(authentication), request.toSettings());
	}

	private static UUID userId(JwtAuthenticationToken authentication) {
		Jwt jwt = (Jwt) authentication.getPrincipal();
		return UUID.fromString(jwt.getSubject());
	}

	record RiskSettingsRequest(
			@NotNull
			@Positive
			BigDecimal capitalBase,
			@NotNull
			@Positive
			BigDecimal maxAllocationPerAssetPercent,
			@NotNull
			@Positive
			BigDecimal maxAllocationPerSectorPercent,
			@NotNull
			@Positive
			BigDecimal toleratedDrawdownPercent,
			@NotNull
			@PositiveOrZero
			BigDecimal minimumCashReservePercent,
			@NotNull
			@PositiveOrZero
			BigDecimal minimumSafetyMarginPercent,
			@NotNull
			@PositiveOrZero
			BigDecimal firstTranchePercent,
			@NotNull
			@PositiveOrZero
			BigDecimal secondTranchePercent,
			@NotNull
			@PositiveOrZero
			BigDecimal thirdTranchePercent,
			@NotNull
			@Positive
			BigDecimal defaultStopPercent,
			@NotNull
			@Positive
			BigDecimal defaultTargetReturnPercent) {

		RiskAllocationSettings toSettings() {
			return new RiskAllocationSettings(capitalBase, maxAllocationPerAssetPercent, maxAllocationPerSectorPercent,
					toleratedDrawdownPercent, minimumCashReservePercent, minimumSafetyMarginPercent, firstTranchePercent,
					secondTranchePercent, thirdTranchePercent, defaultStopPercent, defaultTargetReturnPercent);
		}
	}
}
