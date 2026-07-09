package com.freirelts.araripe_invest_api.domain.backtest;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "backtest_positions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BacktestPosition {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "backtest_run_id", nullable = false)
	private BacktestRun backtestRun;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Column(name = "thesis_date", nullable = false)
	private LocalDate thesisDate;

	@Column(name = "entry_date")
	private LocalDate entryDate;

	@Column(name = "exit_date")
	private LocalDate exitDate;

	@Column(name = "entry_price", precision = 19, scale = 6)
	private BigDecimal entryPrice;

	@Column(name = "average_price", precision = 19, scale = 6)
	private BigDecimal averagePrice;

	@Column(name = "exit_price", precision = 19, scale = 6)
	private BigDecimal exitPrice;

	@Column(precision = 24, scale = 8)
	private BigDecimal quantity;

	@Column(name = "allocated_value", precision = 19, scale = 2)
	private BigDecimal allocatedValue;

	@Column(name = "dividends_received", precision = 19, scale = 2)
	private BigDecimal dividendsReceived;

	@Column(name = "exit_reason", length = 120)
	private String exitReason;

	@Column(name = "return_percent", precision = 10, scale = 6)
	private BigDecimal returnPercent;

	@Column(name = "result_amount", precision = 19, scale = 2)
	private BigDecimal resultAmount;
}
