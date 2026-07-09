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
@Table(name = "daily_candles", uniqueConstraints = @UniqueConstraint(name = "uk_daily_candles_asset_date_source", columnNames = {
		"asset_id", "trade_date", "source" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyCandle {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Column(name = "trade_date", nullable = false)
	private LocalDate tradeDate;

	@Column(name = "open_price", nullable = false, precision = 19, scale = 6)
	private BigDecimal openPrice;

	@Column(name = "high_price", nullable = false, precision = 19, scale = 6)
	private BigDecimal highPrice;

	@Column(name = "low_price", nullable = false, precision = 19, scale = 6)
	private BigDecimal lowPrice;

	@Column(name = "close_price", nullable = false, precision = 19, scale = 6)
	private BigDecimal closePrice;

	@Column(name = "adjusted_close_price", precision = 19, scale = 6)
	private BigDecimal adjustedClosePrice;

	@Column(name = "volume_quantity", precision = 24, scale = 6)
	private BigDecimal volumeQuantity;

	@Column(name = "volume_financial", precision = 24, scale = 6)
	private BigDecimal volumeFinancial;

	@Column(nullable = false, length = 80)
	private String source;

	@Column(name = "collected_at", nullable = false)
	private Instant collectedAt = Instant.now();

	@Enumerated(EnumType.STRING)
	@Column(name = "quality_status", nullable = false, length = 32)
	private DataQualityStatus qualityStatus = DataQualityStatus.PENDING;

	public DailyCandle(Asset asset, LocalDate tradeDate, BigDecimal openPrice, BigDecimal highPrice,
			BigDecimal lowPrice, BigDecimal closePrice, String source) {
		this.asset = asset;
		this.tradeDate = tradeDate;
		this.openPrice = openPrice;
		this.highPrice = highPrice;
		this.lowPrice = lowPrice;
		this.closePrice = closePrice;
		this.source = source;
	}
}
