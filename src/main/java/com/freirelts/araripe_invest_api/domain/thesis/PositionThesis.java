package com.freirelts.araripe_invest_api.domain.thesis;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "position_theses", uniqueConstraints = @UniqueConstraint(name = "uk_position_theses_asset_date_type_version", columnNames = {
		"asset_id", "reference_date", "thesis_type", "rule_version" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionThesis {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "thesis_type", nullable = false, length = 60)
	private ThesisType thesisType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ThesisStatus status;

	@Column(nullable = false)
	private int score;

	@Column(name = "score_breakdown_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String scoreBreakdownJson = "{}";

	@Column(name = "reasons_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String reasonsJson = "[]";

	@Column(name = "failed_filters_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String failedFiltersJson = "[]";

	@Column(name = "price_ceiling", precision = 19, scale = 6)
	private BigDecimal priceCeiling;

	@Column(name = "fair_price_estimate", precision = 19, scale = 6)
	private BigDecimal fairPriceEstimate;

	@Column(name = "safety_margin_percent", precision = 19, scale = 6)
	private BigDecimal safetyMarginPercent;

	@Column(name = "stop_price", precision = 19, scale = 6)
	private BigDecimal stopPrice;

	@Column(name = "target_price", precision = 19, scale = 6)
	private BigDecimal targetPrice;

	@Column(name = "review_points_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String reviewPointsJson = "[]";

	@Column(name = "rule_version", nullable = false, length = 40)
	private String ruleVersion;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@OneToOne(mappedBy = "thesis", fetch = FetchType.LAZY)
	private AllocationPlan allocationPlan;

	public PositionThesis(Asset asset, LocalDate referenceDate, ThesisType thesisType, ThesisStatus status, int score,
			String ruleVersion) {
		this.asset = asset;
		this.referenceDate = referenceDate;
		this.thesisType = thesisType;
		this.status = status;
		this.score = score;
		this.ruleVersion = ruleVersion;
	}
}
