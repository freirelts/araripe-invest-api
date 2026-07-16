package com.freirelts.araripe_invest_api.adapters.inbound.rest.theses;

import com.freirelts.araripe_invest_api.application.api.ApiQueryService;
import com.freirelts.araripe_invest_api.application.api.ApiQueryService.ScreenerSortBy;
import com.freirelts.araripe_invest_api.application.api.ApiQueryService.SortDirection;
import com.freirelts.araripe_invest_api.application.api.ApiQueryService.ThesisSummaryResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/screener")
class ScreenerController {

	private final ApiQueryService apiQueryService;

	ScreenerController(ApiQueryService apiQueryService) {
		this.apiQueryService = apiQueryService;
	}

	@GetMapping
	List<ThesisSummaryResponse> screener(
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate date,
			@RequestParam(required = false)
			ScreenerSortBy sortBy,
			@RequestParam(required = false)
			SortDirection direction) {
		return apiQueryService.screener(date, sortBy, direction);
	}
}
