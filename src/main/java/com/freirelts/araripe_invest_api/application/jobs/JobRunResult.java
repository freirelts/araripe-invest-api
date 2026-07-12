package com.freirelts.araripe_invest_api.application.jobs;

import com.freirelts.araripe_invest_api.domain.jobs.JobName;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunStatus;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunTrigger;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record JobRunResult(
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
}
