package com.freirelts.araripe_invest_api.domain.recommendations;

import com.freirelts.araripe_invest_api.domain.ai.AiContextAnalysis;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
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
		"user_id", "position_id", "reference_date", "recommendation_type", "rule_version" }))
@NoArgsConstructor
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
