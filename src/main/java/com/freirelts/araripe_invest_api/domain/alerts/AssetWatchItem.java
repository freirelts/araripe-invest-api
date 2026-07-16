package com.freirelts.araripe_invest_api.domain.alerts;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesis;
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
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "asset_watch_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetWatchItem {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_position_id")
	private CustomerPosition sourcePosition;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "accompanied_study_model_id")
	private CustomerPositionThesis accompaniedStudyModel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private AssetWatchStatus status = AssetWatchStatus.ACTIVE;

	@Column(name = "user_lower_price_threshold", precision = 19, scale = 6)
	private BigDecimal userLowerPriceThreshold;

	@Column(name = "user_upper_price_threshold", precision = 19, scale = 6)
	private BigDecimal userUpperPriceThreshold;

	@Column(length = 1000)
	private String notes;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	@Column(name = "archived_at")
	private Instant archivedAt;

	public AssetWatchItem(User user, Asset asset) {
		if (user == null || asset == null) {
			throw new IllegalArgumentException("User and asset are required.");
		}
		this.user = user;
		this.asset = asset;
	}
}
