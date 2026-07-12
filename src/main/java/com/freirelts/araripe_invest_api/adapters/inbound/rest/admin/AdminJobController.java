package com.freirelts.araripe_invest_api.adapters.inbound.rest.admin;

import com.freirelts.araripe_invest_api.application.jobs.JobRunResult;
import com.freirelts.araripe_invest_api.application.jobs.OperationalJobService;
import com.freirelts.araripe_invest_api.domain.jobs.JobName;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunStatus;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunTrigger;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/jobs")
class AdminJobController {

	private final OperationalJobService operationalJobService;

	AdminJobController(OperationalJobService operationalJobService) {
		this.operationalJobService = operationalJobService;
	}

	@GetMapping("/status")
	List<JobRunResponse> listRuns(
			@RequestParam
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate date) {
		return operationalJobService.listRuns(date).stream()
				.map(JobRunResponse::from)
				.toList();
	}

	@PostMapping("/{jobName}/runs")
	@ResponseStatus(HttpStatus.CREATED)
	JobRunResponse runJob(@PathVariable String jobName, @Valid @RequestBody ManualJobRunRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		JobRunResult result = operationalJobService.execute(parseJobName(jobName), request.referenceDate(),
				JobRunTrigger.MANUAL, requesterId(jwt));
		return JobRunResponse.from(result);
	}

	private static JobName parseJobName(String value) {
		try {
			return JobName.valueOf(value.trim().toUpperCase());
		}
		catch (RuntimeException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown job name.");
		}
	}

	private static UUID requesterId(Jwt jwt) {
		if (jwt == null || jwt.getSubject() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated principal not found.");
		}
		return UUID.fromString(jwt.getSubject());
	}

	record ManualJobRunRequest(
			@NotNull
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate referenceDate) {
	}

	record JobRunResponse(
			UUID runId,
			JobName jobName,
			LocalDate referenceDate,
			JobRunStatus status,
			JobRunTrigger trigger,
			UUID requestedByUserId,
			Map<String, Object> summary,
			String errorMessage,
			Instant startedAt,
			Instant completedAt) {

		static JobRunResponse from(JobRunResult result) {
			return new JobRunResponse(result.runId(), result.jobName(), result.referenceDate(), result.status(),
					result.trigger(), result.requestedByUserId(), result.summary(), result.errorMessage(),
					result.startedAt(), result.completedAt());
		}
	}
}
