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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "fundamental_snapshots", uniqueConstraints = @UniqueConstraint(name = "uk_fundamental_snapshots_asset_period_source_version", columnNames = {
		"asset_id", "reference_date", "period_type", "source", "calculation_version" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FundamentalSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Column(name = "most_recent_quarter")
	private LocalDate mostRecentQuarter;

	@Enumerated(EnumType.STRING)
	@Column(name = "period_type", nullable = false, length = 32)
	private PeriodType periodType;

	@Column(nullable = false, length = 80)
	private String source;

	@Column(name = "market_cap", precision = 24, scale = 6)
	private BigDecimal marketCap;

	@Column(name = "enterprise_value", precision = 24, scale = 6)
	private BigDecimal enterpriseValue;

	@Column(name = "trailing_pe", precision = 19, scale = 6)
	private BigDecimal trailingPe;

	@Column(name = "price_to_book", precision = 19, scale = 6)
	private BigDecimal priceToBook;

	@Column(name = "enterprise_to_revenue", precision = 19, scale = 6)
	private BigDecimal enterpriseToRevenue;

	@Column(name = "enterprise_to_ebitda", precision = 19, scale = 6)
	private BigDecimal enterpriseToEbitda;

	@Column(name = "forward_pe", precision = 19, scale = 6)
	private BigDecimal forwardPe;

	@Column(name = "peg_ratio", precision = 19, scale = 6)
	private BigDecimal pegRatio;

	@Column(name = "earnings_per_share", precision = 19, scale = 6)
	private BigDecimal earningsPerShare;

	@Column(name = "net_income_to_common", precision = 24, scale = 6)
	private BigDecimal netIncomeToCommon;

	@Column(name = "book_value", precision = 19, scale = 6)
	private BigDecimal bookValue;

	@Column(name = "dividend_yield", precision = 10, scale = 6)
	private BigDecimal dividendYield;

	@Column(name = "last_dividend_value", precision = 19, scale = 6)
	private BigDecimal lastDividendValue;

	@Column(name = "last_dividend_date")
	private LocalDate lastDividendDate;

	@Column(name = "beta", precision = 19, scale = 6)
	private BigDecimal beta;

	@Column(name = "float_shares", precision = 24, scale = 6)
	private BigDecimal floatShares;

	@Column(name = "shares_outstanding", precision = 24, scale = 6)
	private BigDecimal sharesOutstanding;

	@Column(name = "fifty_two_week_change", precision = 10, scale = 6)
	private BigDecimal fiftyTwoWeekChange;

	@Column(name = "total_cash", precision = 24, scale = 6)
	private BigDecimal totalCash;

	@Column(name = "total_cash_per_share", precision = 19, scale = 6)
	private BigDecimal totalCashPerShare;

	@Column(name = "ebitda", precision = 24, scale = 6)
	private BigDecimal ebitda;

	@Column(name = "total_debt", precision = 24, scale = 6)
	private BigDecimal totalDebt;

	@Column(name = "quick_ratio", precision = 19, scale = 6)
	private BigDecimal quickRatio;

	@Column(name = "current_ratio", precision = 19, scale = 6)
	private BigDecimal currentRatio;

	@Column(name = "total_revenue", precision = 24, scale = 6)
	private BigDecimal totalRevenue;

	@Column(name = "gross_profits", precision = 24, scale = 6)
	private BigDecimal grossProfits;

	@Column(name = "profit_margin", precision = 10, scale = 6)
	private BigDecimal profitMargin;

	@Column(name = "gross_margin", precision = 10, scale = 6)
	private BigDecimal grossMargin;

	@Column(name = "ebitda_margin", precision = 10, scale = 6)
	private BigDecimal ebitdaMargin;

	@Column(name = "operating_margin", precision = 10, scale = 6)
	private BigDecimal operatingMargin;

	@Column(precision = 10, scale = 6)
	private BigDecimal roe;

	@Column(precision = 10, scale = 6)
	private BigDecimal roa;

	@Column(name = "debt_to_equity", precision = 19, scale = 6)
	private BigDecimal debtToEquity;

	@Column(name = "revenue_growth", precision = 10, scale = 6)
	private BigDecimal revenueGrowth;

	@Column(name = "earnings_growth", precision = 10, scale = 6)
	private BigDecimal earningsGrowth;

	@Column(name = "annual_revenue_growth", precision = 10, scale = 6)
	private BigDecimal annualRevenueGrowth;

	@Column(name = "quarterly_revenue_growth", precision = 10, scale = 6)
	private BigDecimal quarterlyRevenueGrowth;

	@Column(name = "annual_earnings_growth", precision = 10, scale = 6)
	private BigDecimal annualEarningsGrowth;

	@Column(name = "quarterly_earnings_growth", precision = 10, scale = 6)
	private BigDecimal quarterlyEarningsGrowth;

	@Column(name = "ebitda_growth", precision = 10, scale = 6)
	private BigDecimal ebitdaGrowth;

	@Column(name = "free_cashflow", precision = 24, scale = 6)
	private BigDecimal freeCashflow;

	@Column(name = "operating_cashflow", precision = 24, scale = 6)
	private BigDecimal operatingCashflow;

	@Column(name = "net_debt", precision = 24, scale = 6)
	private BigDecimal netDebt;

	@Enumerated(EnumType.STRING)
	@Column(name = "quality_status", nullable = false, length = 32)
	private DataQualityStatus qualityStatus = DataQualityStatus.PENDING;

	@Column(name = "calculation_version", nullable = false, length = 40)
	private String calculationVersion = "collector-v1";

	@Column(name = "missing_fields_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String missingFieldsJson = "[]";

	@Column(name = "assumptions_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String assumptionsJson = "[]";

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	public FundamentalSnapshot(Asset asset, LocalDate referenceDate, PeriodType periodType, String source) {
		this.asset = asset;
		this.referenceDate = referenceDate;
		this.periodType = periodType;
		this.source = source;
	}
}
