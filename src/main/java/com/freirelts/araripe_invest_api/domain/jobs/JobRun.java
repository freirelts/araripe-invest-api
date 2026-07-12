package com.freirelts.araripe_invest_api.domain.jobs;

import com.freirelts.araripe_invest_api.domain.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "job_runs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRun {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "job_name", nullable = false, length = 80)
	private JobName jobName;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private JobRunStatus status = JobRunStatus.RUNNING;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private JobRunTrigger trigger;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "requested_by_user_id")
	private User requestedByUser;

	@Column(name = "parameters_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String parametersJson = "{}";

	@Column(name = "result_summary_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String resultSummaryJson = "{}";

	@Column(name = "error_message", length = 1000)
	private String errorMessage;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt = Instant.now();

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	public JobRun(JobName jobName, LocalDate referenceDate, JobRunTrigger trigger, User requestedByUser,
			String parametersJson) {
		this.jobName = jobName;
		this.referenceDate = referenceDate;
		this.trigger = trigger;
		this.requestedByUser = requestedByUser;
		this.parametersJson = parametersJson == null || parametersJson.isBlank() ? "{}" : parametersJson;
	}
}
