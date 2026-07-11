package com.freirelts.araripe_invest_api.application.screening;

import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class EliminatoryFilterInput {

	private BigDecimal currentPrice;
	private BigDecimal averageFinancialVolume60;
	private DataQualityStatus candleQualityStatus;
	private DataQualityStatus fundamentalQualityStatus;
	private TrendStatus trendStatus;
	private BigDecimal sma200;
	private BigDecimal return12m;
	private BigDecimal historicalVolatility;
	private BigDecimal recentDrawdown;
	private BigDecimal trailingPe;
	private BigDecimal priceToBook;
	private BigDecimal enterpriseToEbitda;
	private BigDecimal earningsPerShare;
	private BigDecimal profitMargin;
	private BigDecimal operatingCashflow;
	private BigDecimal freeCashflow;
	@Builder.Default
	private List<BigDecimal> freeCashflowHistory = List.of();
	private BigDecimal debtToEquity;
	private BigDecimal netDebt;
	private BigDecimal revenueGrowth;
	private BigDecimal earningsGrowth;
	private BigDecimal annualRevenueGrowth;
	private BigDecimal quarterlyRevenueGrowth;
	private BigDecimal annualEarningsGrowth;
	private BigDecimal quarterlyEarningsGrowth;
	private BigDecimal ebitdaGrowth;
	private boolean candleMissing;
	private boolean technicalMissing;
	private boolean fundamentalMissing;
	private boolean candleStale;
	private boolean technicalStale;
	private boolean fundamentalStale;
}
