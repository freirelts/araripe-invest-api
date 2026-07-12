package com.freirelts.araripe_invest_api.domain.thesis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "allocation_plans")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AllocationPlan {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "thesis_id", nullable = false, unique = true)
	private PositionThesis thesis;

	@Column(name = "capital_base", nullable = false, precision = 19, scale = 2)
	private BigDecimal capitalBase;

	@Column(name = "target_allocation_percent", nullable = false, precision = 10, scale = 6)
	private BigDecimal targetAllocationPercent;

	@Column(name = "max_allocation_per_asset_percent", nullable = false, precision = 10, scale = 6)
	private BigDecimal maxAllocationPerAssetPercent;

	@Column(name = "max_allocation_per_sector_percent", precision = 10, scale = 6)
	private BigDecimal maxAllocationPerSectorPercent;

	@Column(name = "minimum_cash_reserve_percent", precision = 10, scale = 6)
	private BigDecimal minimumCashReservePercent;

	@Column(name = "max_position_value", nullable = false, precision = 19, scale = 2)
	private BigDecimal maxPositionValue;

	@Column(name = "available_for_asset", precision = 19, scale = 2)
	private BigDecimal availableForAsset;

	@Column(name = "available_for_sector", precision = 19, scale = 2)
	private BigDecimal availableForSector;

	@Column(name = "current_price", nullable = false, precision = 19, scale = 6)
	private BigDecimal currentPrice;

	@Column(name = "price_ceiling", nullable = false, precision = 19, scale = 6)
	private BigDecimal priceCeiling;

	@Column(name = "fair_price_estimate", precision = 19, scale = 6)
	private BigDecimal fairPriceEstimate;

	@Column(name = "safety_margin_percent", precision = 10, scale = 6)
	private BigDecimal safetyMarginPercent;

	@Column(name = "estimated_upside_percent", precision = 10, scale = 6)
	private BigDecimal estimatedUpsidePercent;

	@Column(name = "suggested_quantity", nullable = false)
	private int suggestedQuantity;

	@Column(name = "recommended_action", nullable = false, length = 80)
	private String recommendedAction;

	@Column(name = "first_tranche_value", precision = 19, scale = 2)
	private BigDecimal firstTrancheValue;

	@Column(name = "second_tranche_value", precision = 19, scale = 2)
	private BigDecimal secondTrancheValue;

	@Column(name = "third_tranche_value", precision = 19, scale = 2)
	private BigDecimal thirdTrancheValue;

	@Column(name = "remaining_planned_value", precision = 19, scale = 2)
	private BigDecimal remainingPlannedValue;

	@Column(name = "stop_price", precision = 19, scale = 6)
	private BigDecimal stopPrice;

	@Column(name = "target_price", precision = 19, scale = 6)
	private BigDecimal targetPrice;

	@Column(nullable = false)
	private boolean valid;

	@Column(name = "invalid_reason", length = 500)
	private String invalidReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	public AllocationPlan(PositionThesis thesis) {
		this.thesis = thesis;
	}
}
