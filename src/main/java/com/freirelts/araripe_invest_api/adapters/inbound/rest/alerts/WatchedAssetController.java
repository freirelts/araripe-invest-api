package com.freirelts.araripe_invest_api.adapters.inbound.rest.alerts;

import com.freirelts.araripe_invest_api.application.alerts.WatchedAssetService;
import com.freirelts.araripe_invest_api.application.alerts.WatchedAssetService.UpdateWatchedAssetPreferencesInput;
import com.freirelts.araripe_invest_api.application.alerts.WatchedAssetService.WatchedAssetInput;
import com.freirelts.araripe_invest_api.application.alerts.WatchedAssetService.WatchedAssetSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/watched-assets")
@PreAuthorize("hasRole('CUSTOMER')")
class WatchedAssetController {

	private final WatchedAssetService service;

	WatchedAssetController(WatchedAssetService service) {
		this.service = service;
	}

	@GetMapping
	List<WatchedAssetSummary> list(JwtAuthenticationToken authentication) {
		return service.listActive(userId(authentication));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	WatchedAssetSummary createOrUpdate(JwtAuthenticationToken authentication,
			@Valid @RequestBody UpsertWatchedAssetRequest request) {
		return service.createOrUpdate(userId(authentication), request.toInput());
	}

	@PutMapping("/{watchItemId}")
	WatchedAssetSummary update(JwtAuthenticationToken authentication, @PathVariable UUID watchItemId,
			@Valid @RequestBody UpdateWatchedAssetPreferencesRequest request) {
		return service.update(userId(authentication), watchItemId, request.toInput());
	}

	@PatchMapping("/{watchItemId}/archive")
	WatchedAssetSummary archive(JwtAuthenticationToken authentication, @PathVariable UUID watchItemId) {
		return service.archive(userId(authentication), watchItemId);
	}

	private static UUID userId(JwtAuthenticationToken authentication) {
		Jwt jwt = (Jwt) authentication.getPrincipal();
		return UUID.fromString(jwt.getSubject());
	}

	record UpsertWatchedAssetRequest(
			@NotNull
			UUID assetId,
			@Positive
			BigDecimal userLowerPriceThreshold,
			@Positive
			BigDecimal userUpperPriceThreshold,
			@Size(max = 1000)
			String notes) {

		WatchedAssetInput toInput() {
			return new WatchedAssetInput(assetId, userLowerPriceThreshold, userUpperPriceThreshold, notes);
		}
	}

	record UpdateWatchedAssetPreferencesRequest(
			@Positive
			BigDecimal userLowerPriceThreshold,
			@Positive
			BigDecimal userUpperPriceThreshold,
			@Size(max = 1000)
			String notes) {

		UpdateWatchedAssetPreferencesInput toInput() {
			return new UpdateWatchedAssetPreferencesInput(userLowerPriceThreshold, userUpperPriceThreshold, notes);
		}
	}
}
