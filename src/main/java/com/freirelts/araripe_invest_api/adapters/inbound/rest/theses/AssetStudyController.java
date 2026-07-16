package com.freirelts.araripe_invest_api.adapters.inbound.rest.theses;

import com.freirelts.araripe_invest_api.application.api.ApiQueryService;
import com.freirelts.araripe_invest_api.application.api.ApiQueryService.ThesisDetailResponse;
import com.freirelts.araripe_invest_api.application.api.ApiQueryService.ThesisSummaryResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/asset-studies")
class AssetStudyController {

	private final ApiQueryService apiQueryService;

	AssetStudyController(ApiQueryService apiQueryService) {
		this.apiQueryService = apiQueryService;
	}

	@GetMapping("/{studyId}")
	ThesisDetailResponse detail(@PathVariable UUID studyId) {
		return apiQueryService.thesisDetail(studyId);
	}

	@GetMapping("/history")
	List<ThesisSummaryResponse> history(@RequestParam String symbol,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate from,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate to) {
		return apiQueryService.thesisHistory(symbol, from, to);
	}
}
