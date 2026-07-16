package com.freirelts.araripe_invest_api.domain.recommendations;

import com.freirelts.araripe_invest_api.domain.ai.AiContextAnalysis;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
@Table(name = "position_recommendations", uniqueConstraints = @UniqueConstraint(name = "uk_position_recommendations_idempotency", columnNames = {
		"user_id", "position_id", "reference_date", "rule_version" }))
@NoArgsConstructor
@Deprecated(since = "2026-07-16", forRemoval = false)
public class PositionRecommendation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "position_id", nullable = false)
	private CustomerPosition position;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_position_thesis_id")
	private CustomerPositionThesis customerPositionThesis;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "current_thesis_id")
	private PositionThesis currentThesis;

	@Enumerated(EnumType.STRING)
	@Column(name = "thesis_type", length = 60)
	private ThesisType thesisType;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "recommendation_type", nullable = false, length = 40)
	private RecommendationType recommendationType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private Severity severity;

	@Column(name = "current_price", precision = 19, scale = 6)
	private BigDecimal currentPrice;

	@Column(name = "average_price", precision = 19, scale = 6)
	private BigDecimal averagePrice;

	@Column(name = "stop_price", precision = 19, scale = 6)
	private BigDecimal stopPrice;

	@Column(name = "target_price", precision = 19, scale = 6)
	private BigDecimal targetPrice;

	@Column(name = "price_ceiling", precision = 19, scale = 6)
	private BigDecimal priceCeiling;

	@Column(name = "fair_price_estimate", precision = 19, scale = 6)
	private BigDecimal fairPriceEstimate;

	@Column(name = "safety_margin_percent", precision = 19, scale = 6)
	private BigDecimal safetyMarginPercent;

	@Column(name = "estimated_upside_percent", precision = 19, scale = 6)
	private BigDecimal estimatedUpsidePercent;

	@Column(name = "suggested_quantity")
	private Integer suggestedQuantity;

	@Column(name = "current_asset_exposure_value", precision = 19, scale = 2)
	private BigDecimal currentAssetExposureValue;

	@Column(name = "current_sector_exposure_value", precision = 19, scale = 2)
	private BigDecimal currentSectorExposureValue;

	@Column(name = "current_total_exposure_value", precision = 19, scale = 2)
	private BigDecimal currentTotalExposureValue;

	@Column(name = "available_for_asset", precision = 19, scale = 2)
	private BigDecimal availableForAsset;

	@Column(name = "available_for_sector", precision = 19, scale = 2)
	private BigDecimal availableForSector;

	@Column(name = "available_for_cash", precision = 19, scale = 2)
	private BigDecimal availableForCash;

	@Column(name = "allocation_valid")
	private Boolean allocationValid;

	@Column(name = "allocation_invalid_reason", length = 1000)
	private String allocationInvalidReason;

	@Column
	private Integer score;

	@Column(name = "deterministic_reason_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String deterministicReasonJson = "[]";

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ai_context_analysis_id")
	private AiContextAnalysis aiContextAnalysis;

	@Column(name = "final_message", nullable = false, length = 2000)
	private String finalMessage;

	@Column(name = "rule_version", nullable = false, length = 40)
	private String ruleVersion;

	@Column(name = "ai_model", length = 120)
	private String aiModel;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();
}
