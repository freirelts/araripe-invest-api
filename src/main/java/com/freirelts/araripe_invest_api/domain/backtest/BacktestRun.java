package com.freirelts.araripe_invest_api.domain.backtest;

import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "backtest_runs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BacktestRun {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "thesis_type", nullable = false, length = 60)
	private ThesisType thesisType;

	@Column(name = "rule_version", nullable = false, length = 40)
	private String ruleVersion;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "asset_universe", nullable = false, length = 500)
	private String assetUniverse;

	@Column(length = 80)
	private String benchmark;

	@Column(name = "data_source", nullable = false, length = 80)
	private String dataSource;

	@Column(name = "parameters_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String parametersJson = "{}";

	@Column(name = "metrics_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String metricsJson = "{}";

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();
}
