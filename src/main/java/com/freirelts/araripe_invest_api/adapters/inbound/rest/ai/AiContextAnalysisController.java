package com.freirelts.araripe_invest_api.adapters.inbound.rest.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.ai.EconomicContextAnalysisService;
import com.freirelts.araripe_invest_api.domain.ai.AiContextAnalysis;
import com.freirelts.araripe_invest_api.domain.ai.AiProcessingStatus;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AiContextAnalysisRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
class AiContextAnalysisController {

	private final EconomicContextAnalysisService analysisService;
	private final AiContextAnalysisRepository analysisRepository;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	AiContextAnalysisController(EconomicContextAnalysisService analysisService,
			AiContextAnalysisRepository analysisRepository) {
		this.analysisService = analysisService;
		this.analysisRepository = analysisRepository;
	}

	@GetMapping("/api/v1/ai/context-analyses")
	PageResponse<AiContextAnalysisResponse> list(
			@RequestParam(required = false) UUID thesisId,
			@RequestParam(required = false) LocalDate referenceDate,
			@RequestParam(required = false) String symbol,
			@RequestParam(required = false) AiValidationStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.max(1, Math.min(size, 100));
		Page<AiContextAnalysisResponse> result = analysisRepository
				.findAll(specification(thesisId, referenceDate, normalizedSymbol(symbol), status),
						PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))
				.map(this::response);
		return PageResponse.from(result);
	}

	@GetMapping("/api/v1/ai/context-analyses/{analysisId}")
	AiContextAnalysisResponse detail(@PathVariable UUID analysisId) {
		return analysisRepository.findById(analysisId)
				.map(this::response)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI analysis not found."));
	}

	@PostMapping("/api/v1/admin/ai/context-analyses")
	@ResponseStatus(HttpStatus.CREATED)
	AiContextAnalysisResponse analyze(@Valid @RequestBody AiContextAnalysisRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return response(analysisService.analyzeThesis(request.thesisId(), requesterId(jwt), request.forceRefresh()));
	}

	private AiContextAnalysisResponse response(AiContextAnalysis analysis) {
		return new AiContextAnalysisResponse(analysis.getId(),
				analysis.getThesis() == null ? null : analysis.getThesis().getId(),
				analysis.getAsset().getId(), analysis.getAsset().getSymbol(), analysis.getReferenceDate(),
				analysis.getProvider(), analysis.getModel(), analysis.getPromptVersion(), analysis.getPromptHash(),
				analysis.getInputHash(), jsonValue(analysis.getInputSummaryJson()), jsonValue(analysis.getOutputJson()),
				jsonValue(analysis.getSourcesJson()), analysis.getValidationStatus(), analysis.getProcessingStatus(),
				analysis.getLatencyMs(), analysis.getErrorMessage(),
				analysis.getRequestedByUser() == null ? null : analysis.getRequestedByUser().getId(),
				analysis.getCreatedAt(), analysis.getStartedAt(), analysis.getFinishedAt());
	}

	private Object jsonValue(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readValue(json, Object.class);
		}
		catch (JsonProcessingException ex) {
			return json;
		}
	}

	private static String normalizedSymbol(String value) {
		return value == null || value.isBlank() ? null : value.trim().toLowerCase();
	}

	private static Specification<AiContextAnalysis> specification(UUID thesisId, LocalDate referenceDate, String symbol,
			AiValidationStatus status) {
		return (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (thesisId != null) {
				predicates.add(builder.equal(root.get("thesis").get("id"), thesisId));
			}
			if (referenceDate != null) {
				predicates.add(builder.equal(root.get("referenceDate"), referenceDate));
			}
			if (symbol != null) {
				predicates.add(builder.equal(builder.lower(root.get("asset").get("symbol")), symbol));
			}
			if (status != null) {
				predicates.add(builder.equal(root.get("validationStatus"), status));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static UUID requesterId(Jwt jwt) {
		if (jwt == null || jwt.getSubject() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated principal not found.");
		}
		return UUID.fromString(jwt.getSubject());
	}

	record AiContextAnalysisRequest(
			@NotNull
			UUID thesisId,
			boolean forceRefresh) {
	}

	record AiContextAnalysisResponse(
			UUID analysisId,
			UUID thesisId,
			UUID assetId,
			String symbol,
			LocalDate referenceDate,
			String provider,
			String model,
			String promptVersion,
			String promptHash,
			String inputHash,
			Object inputSummary,
			Object output,
			Object sources,
			AiValidationStatus validationStatus,
			AiProcessingStatus processingStatus,
			Long latencyMs,
			String errorMessage,
			UUID requestedByUserId,
			Instant createdAt,
			Instant startedAt,
			Instant finishedAt) {
	}

	record PageResponse<T>(
			List<T> items,
			int page,
			int size,
			long totalElements,
			int totalPages) {

		static <T> PageResponse<T> from(Page<T> page) {
			return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
					page.getTotalPages());
		}
	}
}
