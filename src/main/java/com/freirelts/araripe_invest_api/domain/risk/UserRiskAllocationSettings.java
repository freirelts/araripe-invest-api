package com.freirelts.araripe_invest_api.domain.risk;

import com.freirelts.araripe_invest_api.domain.users.User;
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
@Table(name = "user_risk_allocation_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRiskAllocationSettings {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column(name = "capital_base", nullable = false, precision = 19, scale = 2)
	private BigDecimal capitalBase;

	@Column(name = "max_allocation_per_asset_percent", nullable = false, precision = 10, scale = 6)
	private BigDecimal maxAllocationPerAssetPercent;

	@Column(name = "max_allocation_per_sector_percent", nullable = false, precision = 10, scale = 6)
	private BigDecimal maxAllocationPerSectorPercent;

	@Column(name = "tolerated_drawdown_percent", nullable = false, precision = 10, scale = 6)
	private BigDecimal toleratedDrawdownPercent;

	@Column(name = "minimum_cash_reserve_percent", nullable = false, precision = 10, scale = 6)
	private BigDecimal minimumCashReservePercent;

	@Column(name = "minimum_safety_margin_percent", nullable = false, precision = 10, scale = 6)
	private BigDecimal minimumSafetyMarginPercent;

	@Column(name = "first_tranche_percent", nullable = false, precision = 10, scale = 6)
	private BigDecimal firstTranchePercent;

	@Column(name = "second_tranche_percent", nullable = false, precision = 10, scale = 6)
	private BigDecimal secondTranchePercent;

	@Column(name = "third_tranche_percent", nullable = false, precision = 10, scale = 6)
	private BigDecimal thirdTranchePercent;

	@Column(name = "default_stop_percent", nullable = false, precision = 10, scale = 6)
	private BigDecimal defaultStopPercent;

	@Column(name = "default_target_return_percent", nullable = false, precision = 10, scale = 6)
	private BigDecimal defaultTargetReturnPercent;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	public UserRiskAllocationSettings(User user) {
		this.user = user;
	}
}
