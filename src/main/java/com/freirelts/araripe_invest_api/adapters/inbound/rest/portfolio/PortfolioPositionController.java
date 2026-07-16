package com.freirelts.araripe_invest_api.adapters.inbound.rest.portfolio;

import com.freirelts.araripe_invest_api.application.portfolio.PortfolioService;
import com.freirelts.araripe_invest_api.application.portfolio.PortfolioService.ContributionInput;
import com.freirelts.araripe_invest_api.application.portfolio.PortfolioService.PositionInput;
import com.freirelts.araripe_invest_api.application.portfolio.PortfolioService.PositionSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/position-records")
@PreAuthorize("hasRole('CUSTOMER')")
class PortfolioPositionController {

	private final PortfolioService portfolioService;

	PortfolioPositionController(PortfolioService portfolioService) {
		this.portfolioService = portfolioService;
	}

	@GetMapping
	List<PositionSummary> listPositions(JwtAuthenticationToken authentication) {
		return portfolioService.listPositions(userId(authentication));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	PositionSummary createPosition(JwtAuthenticationToken authentication,
			@Valid @RequestBody UpsertPositionRequest request) {
		return portfolioService.createPosition(userId(authentication), request.toInput());
	}

	@PutMapping("/{positionId}")
	PositionSummary updatePosition(JwtAuthenticationToken authentication, @PathVariable UUID positionId,
			@Valid @RequestBody UpsertPositionRequest request) {
		return portfolioService.updatePosition(userId(authentication), positionId, request.toInput());
	}

	@PatchMapping("/{positionId}/close")
	PositionSummary closePosition(JwtAuthenticationToken authentication, @PathVariable UUID positionId) {
		return portfolioService.closePosition(userId(authentication), positionId);
	}

	@PostMapping("/{positionId}/contributions")
	PositionSummary registerContribution(JwtAuthenticationToken authentication, @PathVariable UUID positionId,
			@Valid @RequestBody ContributionRequest request) {
		return portfolioService.registerContribution(userId(authentication), positionId, request.toInput());
	}

	@PostMapping("/{positionId}/quantity-adjustments")
	PositionSummary registerQuantityAdjustment(JwtAuthenticationToken authentication, @PathVariable UUID positionId,
			@Valid @RequestBody ContributionRequest request) {
		return portfolioService.registerContribution(userId(authentication), positionId, request.toInput());
	}

	@PostMapping("/{positionId}/main-thesis")
	@ResponseStatus(HttpStatus.CREATED)
	PositionSummary associateMainThesis(JwtAuthenticationToken authentication, @PathVariable UUID positionId,
			@Valid @RequestBody MainThesisRequest request) {
		return portfolioService.associateMainThesis(userId(authentication), positionId, request.thesisId(),
				request.notes());
	}

	@PatchMapping("/{positionId}/main-thesis")
	PositionSummary replaceMainThesis(JwtAuthenticationToken authentication, @PathVariable UUID positionId,
			@Valid @RequestBody MainThesisRequest request) {
		return portfolioService.replaceMainThesis(userId(authentication), positionId, request.thesisId(),
				request.notes());
	}

	private static UUID userId(JwtAuthenticationToken authentication) {
		Jwt jwt = (Jwt) authentication.getPrincipal();
		return UUID.fromString(jwt.getSubject());
	}

	record UpsertPositionRequest(
			@NotNull
			UUID assetId,
			@NotNull
			@Positive
			BigDecimal quantity,
			@NotNull
			@Positive
			BigDecimal averagePrice,
			@NotNull
			@PastOrPresent
			LocalDate entryDate,
			@Positive
			BigDecimal userLowerPriceThreshold,
			@Positive
			BigDecimal userUpperPriceThreshold,
			@Size(max = 1000)
			String notes) {

		PositionInput toInput() {
			return new PositionInput(assetId, quantity, averagePrice, entryDate, userLowerPriceThreshold,
					userUpperPriceThreshold, null, notes);
		}
	}

	record MainThesisRequest(
			@NotNull
			UUID thesisId,
			@Size(max = 1000)
			String notes) {
	}

	record ContributionRequest(
			@NotNull
			@Positive
			BigDecimal quantity,
			@NotNull
			@Positive
			BigDecimal price,
			@PastOrPresent
			LocalDate contributionDate,
			@Size(max = 1000)
			String notes) {

		ContributionInput toInput() {
			return new ContributionInput(quantity, price, contributionDate, notes);
		}
	}
}
