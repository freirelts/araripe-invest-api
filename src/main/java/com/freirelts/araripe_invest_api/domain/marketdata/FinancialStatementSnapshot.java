package com.freirelts.araripe_invest_api.domain.marketdata;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "financial_statement_snapshots", uniqueConstraints = @UniqueConstraint(name = "uk_financial_statements_asset_type_period_source", columnNames = {
		"asset_id", "statement_type", "period_type", "end_date", "source" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialStatementSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Enumerated(EnumType.STRING)
	@Column(name = "statement_type", nullable = false, length = 32)
	private StatementType statementType;

	@Enumerated(EnumType.STRING)
	@Column(name = "period_type", nullable = false, length = 32)
	private PeriodType periodType;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(nullable = false, length = 80)
	private String source;

	@Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String payloadJson;

	@Enumerated(EnumType.STRING)
	@Column(name = "quality_status", nullable = false, length = 32)
	private DataQualityStatus qualityStatus = DataQualityStatus.PENDING;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();
}
