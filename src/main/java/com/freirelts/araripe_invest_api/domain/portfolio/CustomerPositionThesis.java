package com.freirelts.araripe_invest_api.domain.portfolio;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
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
@Table(name = "customer_position_theses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerPositionThesis {

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

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "accepted_thesis_id", nullable = false)
	private PositionThesis acceptedThesis;

	@Enumerated(EnumType.STRING)
	@Column(name = "thesis_type", nullable = false, length = 60)
	private ThesisType thesisType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private CustomerPositionThesisStatus status = CustomerPositionThesisStatus.ACTIVE;

	@Column(name = "accepted_at", nullable = false)
	private Instant acceptedAt = Instant.now();

	@Column(name = "accepted_score", nullable = false)
	private int acceptedScore;

	@Column(name = "accepted_price", precision = 19, scale = 6)
	private BigDecimal acceptedPrice;

	@Column(name = "accepted_price_ceiling", precision = 19, scale = 6)
	private BigDecimal acceptedPriceCeiling;

	@Column(name = "accepted_safety_margin_percent", precision = 10, scale = 6)
	private BigDecimal acceptedSafetyMarginPercent;

	@Column(name = "rule_version", nullable = false, length = 40)
	private String ruleVersion;

	@Column(length = 1000)
	private String notes;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "exit_reason", length = 500)
	private String exitReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	public CustomerPositionThesis(User user, CustomerPosition position, PositionThesis acceptedThesis,
			BigDecimal acceptedPrice) {
		if (position == null || acceptedThesis == null) {
			throw new IllegalArgumentException("Position and accepted thesis are required.");
		}
		if (user == null || !sameUser(user, position.getUser())) {
			throw new IllegalArgumentException("The position must belong to the same user.");
		}
		if (!sameAsset(position.getAsset(), acceptedThesis.getAsset())) {
			throw new IllegalArgumentException("The accepted thesis must reference the same asset as the position.");
		}
		this.user = user;
		this.position = position;
		this.asset = position.getAsset();
		this.acceptedThesis = acceptedThesis;
		this.thesisType = acceptedThesis.getThesisType();
		this.acceptedScore = acceptedThesis.getScore();
		this.acceptedPrice = acceptedPrice;
		this.acceptedPriceCeiling = acceptedThesis.getPriceCeiling();
		this.acceptedSafetyMarginPercent = acceptedThesis.getSafetyMarginPercent();
		this.ruleVersion = acceptedThesis.getRuleVersion();
	}

	public boolean isActive() {
		return status == CustomerPositionThesisStatus.ACTIVE;
	}

	public void close(String exitReason) {
		Instant now = Instant.now();
		this.status = CustomerPositionThesisStatus.CLOSED;
		this.exitReason = exitReason;
		this.closedAt = now;
		this.updatedAt = now;
	}

	private boolean sameUser(User left, User right) {
		return left == right
				|| (left != null && right != null && left.getId() != null && left.getId().equals(right.getId()));
	}

	private boolean sameAsset(Asset left, Asset right) {
		return left == right
				|| (left != null && right != null && left.getId() != null && left.getId().equals(right.getId()));
	}
}
