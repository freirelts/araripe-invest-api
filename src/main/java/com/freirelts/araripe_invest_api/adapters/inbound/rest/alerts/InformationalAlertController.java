package com.freirelts.araripe_invest_api.adapters.inbound.rest.alerts;

import com.freirelts.araripe_invest_api.application.api.ApiQueryService;
import com.freirelts.araripe_invest_api.application.api.ApiQueryService.InformationalAlertResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@PreAuthorize("hasRole('CUSTOMER')")
class InformationalAlertController {

	private final ApiQueryService apiQueryService;

	InformationalAlertController(ApiQueryService apiQueryService) {
		this.apiQueryService = apiQueryService;
	}

	@GetMapping
	List<InformationalAlertResponse> list(JwtAuthenticationToken authentication,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate from,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate to) {
		return apiQueryService.alerts(userId(authentication), from, to);
	}

	@GetMapping("/{alertId}")
	InformationalAlertResponse detail(JwtAuthenticationToken authentication, @PathVariable UUID alertId) {
		return apiQueryService.alert(userId(authentication), alertId);
	}

	@PatchMapping("/{alertId}/read")
	InformationalAlertResponse markRead(JwtAuthenticationToken authentication, @PathVariable UUID alertId) {
		return apiQueryService.markAlertRead(userId(authentication), alertId);
	}

	private static UUID userId(JwtAuthenticationToken authentication) {
		Jwt jwt = (Jwt) authentication.getPrincipal();
		return UUID.fromString(jwt.getSubject());
	}
}
