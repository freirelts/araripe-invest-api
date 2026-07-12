package com.freirelts.araripe_invest_api.adapters.inbound.rest.jobs;

import com.freirelts.araripe_invest_api.application.api.ApiQueryService;
import com.freirelts.araripe_invest_api.application.api.ApiQueryService.JobStatusResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/jobs")
class JobStatusController {

	private final ApiQueryService apiQueryService;

	JobStatusController(ApiQueryService apiQueryService) {
		this.apiQueryService = apiQueryService;
	}

	@GetMapping("/status")
	JobStatusResponse status(
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate date) {
		return apiQueryService.jobStatus(date);
	}
}
