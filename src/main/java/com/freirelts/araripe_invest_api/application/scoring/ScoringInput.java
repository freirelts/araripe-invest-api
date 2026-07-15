package com.freirelts.araripe_invest_api.application.scoring;

import com.freirelts.araripe_invest_api.application.screening.EliminatoryFilterReason;
import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;

import java.math.BigDecimal;
import java.util.List;

public record ScoringInput(
		ThesisType thesisType,
		BigDecimal currentPrice,
		BigDecimal fairPriceEstimate,
		BigDecimal priceCeiling,
		BigDecimal safetyMargin,
		BigDecimal trailingPe,
		BigDecimal priceToBook,
		BigDecimal enterpriseToEbitda,
		BigDecimal earningsPerShare,
		BigDecimal dividendYield,
		BigDecimal profitMargin,
		BigDecimal grossMargin,
		BigDecimal ebitdaMargin,
		BigDecimal operatingMargin,
		BigDecimal roe,
		BigDecimal roa,
		BigDecimal debtToEquity,
		BigDecimal revenueGrowth,
		BigDecimal earningsGrowth,
		BigDecimal annualRevenueGrowth,
		BigDecimal quarterlyRevenueGrowth,
		BigDecimal annualEarningsGrowth,
		BigDecimal quarterlyEarningsGrowth,
		BigDecimal ebitdaGrowth,
		BigDecimal freeCashflow,
		BigDecimal operatingCashflow,
		BigDecimal sma200,
		BigDecimal historicalVolatility,
			BigDecimal recentDrawdown,
			TrendStatus trendStatus,
			long cashDividendEventsLastThreeYears,
			String sector,
			BigDecimal selicRate,
			BigDecimal ipcaRate,
			BigDecimal usdBrlRate,
			List<EliminatoryFilterReason> failedFilters) {
	}
