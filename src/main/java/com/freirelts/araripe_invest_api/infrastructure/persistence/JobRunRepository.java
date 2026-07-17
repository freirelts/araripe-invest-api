package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.jobs.JobName;
import com.freirelts.araripe_invest_api.domain.jobs.JobRun;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRunRepository extends JpaRepository<JobRun, UUID> {

	Optional<JobRun> findByJobNameAndReferenceDateAndStatus(JobName jobName, LocalDate referenceDate,
			JobRunStatus status);

	List<JobRun> findByReferenceDateOrderByStartedAtDesc(LocalDate referenceDate);

	Optional<JobRun> findTopByJobNameInAndStatusInOrderByReferenceDateDescCompletedAtDescStartedAtDesc(
			List<JobName> jobNames, List<JobRunStatus> statuses);
}
