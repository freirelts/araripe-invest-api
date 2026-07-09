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
@Table(name = "dividend_events", uniqueConstraints = @UniqueConstraint(name = "uk_dividend_events_asset_type_dates_source", columnNames = {
		"asset_id", "event_type", "last_date_prior", "payment_date", "source" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DividendEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 32)
	private DividendEventType eventType;

	@Column(name = "payment_date")
	private LocalDate paymentDate;

	@Column(name = "last_date_prior")
	private LocalDate lastDatePrior;

	@Column(name = "approved_on")
	private LocalDate approvedOn;

	@Column(precision = 19, scale = 8)
	private BigDecimal rate;

	@Column(precision = 19, scale = 8)
	private BigDecimal factor;

	@Column(length = 120)
	private String label;

	@Column(name = "isin_code", length = 40)
	private String isinCode;

	@Column(nullable = false, length = 80)
	private String source;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	public DividendEvent(Asset asset, DividendEventType eventType, LocalDate lastDatePrior, LocalDate paymentDate,
			String source) {
		this.asset = asset;
		this.eventType = eventType;
		this.lastDatePrior = lastDatePrior;
		this.paymentDate = paymentDate;
		this.source = source;
	}
}
