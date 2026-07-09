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
@Table(name = "technical_indicator_snapshots", uniqueConstraints = @UniqueConstraint(name = "uk_technical_snapshots_asset_date_version", columnNames = {
		"asset_id", "trade_date", "calculation_version" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TechnicalIndicatorSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Column(name = "trade_date", nullable = false)
	private LocalDate tradeDate;

	@Column(name = "sma_50", precision = 19, scale = 6)
	private BigDecimal sma50;

	@Column(name = "sma_100", precision = 19, scale = 6)
	private BigDecimal sma100;

	@Column(name = "sma_200", precision = 19, scale = 6)
	private BigDecimal sma200;

	@Column(name = "ema_50", precision = 19, scale = 6)
	private BigDecimal ema50;

	@Column(name = "ema_100", precision = 19, scale = 6)
	private BigDecimal ema100;

	@Column(name = "ema_200", precision = 19, scale = 6)
	private BigDecimal ema200;

	@Column(name = "return_6m", precision = 10, scale = 6)
	private BigDecimal return6m;

	@Column(name = "return_12m", precision = 10, scale = 6)
	private BigDecimal return12m;

	@Column(name = "avg_volume_60", precision = 24, scale = 6)
	private BigDecimal avgVolume60;

	@Column(name = "high_52w", precision = 19, scale = 6)
	private BigDecimal high52w;

	@Column(name = "low_52w", precision = 19, scale = 6)
	private BigDecimal low52w;

	@Column(name = "historical_volatility", precision = 10, scale = 6)
	private BigDecimal historicalVolatility;

	@Column(name = "recent_drawdown", precision = 10, scale = 6)
	private BigDecimal recentDrawdown;

	@Enumerated(EnumType.STRING)
	@Column(name = "trend_status", nullable = false, length = 32)
	private TrendStatus trendStatus = TrendStatus.INSUFFICIENT_DATA;

	@Column(name = "calculation_version", nullable = false, length = 40)
	private String calculationVersion;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();
}
