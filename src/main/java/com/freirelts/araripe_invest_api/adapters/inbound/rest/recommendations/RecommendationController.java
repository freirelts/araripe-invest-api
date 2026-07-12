package com.freirelts.araripe_invest_api.adapters.inbound.rest.recommendations;

import com.freirelts.araripe_invest_api.application.api.ApiQueryService;
import com.freirelts.araripe_invest_api.application.api.ApiQueryService.RecommendationResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recommendations")
@PreAuthorize("hasRole('CUSTOMER')")
class RecommendationController {

	private final ApiQueryService apiQueryService;

	RecommendationController(ApiQueryService apiQueryService) {
		this.apiQueryService = apiQueryService;
	}

	@GetMapping
	List<RecommendationResponse> list(JwtAuthenticationToken authentication,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate date) {
		return apiQueryService.recommendations(userId(authentication), date);
	}

	@GetMapping("/{recommendationId}")
	RecommendationResponse detail(JwtAuthenticationToken authentication, @PathVariable UUID recommendationId) {
		return apiQueryService.recommendation(userId(authentication), recommendationId);
	}

	private static UUID userId(JwtAuthenticationToken authentication) {
		Jwt jwt = (Jwt) authentication.getPrincipal();
		return UUID.fromString(jwt.getSubject());
	}
}
