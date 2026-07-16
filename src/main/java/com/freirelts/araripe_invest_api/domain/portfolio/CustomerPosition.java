package com.freirelts.araripe_invest_api.domain.portfolio;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "customer_positions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerPosition {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Column(nullable = false, precision = 24, scale = 8)
	private BigDecimal quantity;

	@Column(name = "average_price", nullable = false, precision = 19, scale = 6)
	private BigDecimal averagePrice;

	@Column(name = "entry_date", nullable = false)
	private LocalDate entryDate;

	@Column(name = "user_lower_price_threshold", precision = 19, scale = 6)
	private BigDecimal userLowerPriceThreshold;

	@Column(name = "user_upper_price_threshold", precision = 19, scale = 6)
	private BigDecimal userUpperPriceThreshold;

	@Column(name = "target_return_percent", precision = 10, scale = 6)
	private BigDecimal targetReturnPercent;

	@Column(length = 1000)
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private PositionStatus status = PositionStatus.OPEN;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	@Column(name = "closed_at")
	private Instant closedAt;

	public CustomerPosition(User user, Asset asset, BigDecimal quantity, BigDecimal averagePrice, LocalDate entryDate) {
		this.user = user;
		this.asset = asset;
		this.quantity = quantity;
		this.averagePrice = averagePrice;
		this.entryDate = entryDate;
	}

	public boolean isOpenAndValidForDailyScan() {
		return status == PositionStatus.OPEN && quantity != null && quantity.signum() > 0 && averagePrice != null
				&& averagePrice.signum() > 0;
	}

	public void close() {
		this.status = PositionStatus.CLOSED;
		this.closedAt = Instant.now();
		this.updatedAt = this.closedAt;
	}
}
