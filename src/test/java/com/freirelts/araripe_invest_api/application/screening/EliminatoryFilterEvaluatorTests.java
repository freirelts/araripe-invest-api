package com.freirelts.araripe_invest_api.application.screening;

import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EliminatoryFilterEvaluatorTests {

	private final EliminatoryFilterEvaluator evaluator = new EliminatoryFilterEvaluator();

	@Test
	void approvesAssetWhenNoEliminatoryFilterIsTriggered() {
		assertThat(evaluator.evaluate(validInput().build())).isEmpty();
	}

	@Test
	void filtersAverageFinancialVolumeBelowFiveMillionPerDay() {
		assertCodes(validInput().averageFinancialVolume60(new BigDecimal("4999999.99")).build())
				.containsExactly(EliminatoryFilterCode.INSUFFICIENT_LIQUIDITY);
	}

	@Test
	void filtersPriceBelowTwoReais() {
		assertCodes(validInput().currentPrice(new BigDecimal("1.99")).sma200(BigDecimal.ONE).build())
				.containsExactly(EliminatoryFilterCode.PRICE_BELOW_MINIMUM);
	}

	@Test
	void filtersMissingMinimumFundamentalData() {
		assertCodes(validInput().trailingPe(null).priceToBook(null).enterpriseToEbitda(null).build())
				.containsExactly(EliminatoryFilterCode.MINIMUM_FUNDAMENTALS_MISSING);
	}

	@Test
	void filtersRecurringLossesWhenProfitabilityIsRequired() {
		assertCodes(validInput().earningsPerShare(new BigDecimal("-0.10")).profitMargin(BigDecimal.ZERO).build())
				.containsExactly(EliminatoryFilterCode.RECURRING_LOSSES);
	}

	@Test
	void filtersPersistentlyNegativeFreeCashflow() {
		assertCodes(validInput().freeCashflow(new BigDecimal("-20"))
				.freeCashflowHistory(List.of(new BigDecimal("-20"), new BigDecimal("-10"), new BigDecimal("5")))
				.build()).containsExactly(EliminatoryFilterCode.PERSISTENT_NEGATIVE_FREE_CASHFLOW);
	}

	@Test
	void filtersExcessiveDebtWithoutCashGenerationCoverage() {
		assertCodes(validInput().debtToEquity(new BigDecimal("3.00"))
				.netDebt(new BigDecimal("700"))
				.operatingCashflow(new BigDecimal("100"))
				.build()).containsExactly(EliminatoryFilterCode.EXCESSIVE_DEBT);
	}

	@Test
	void filtersStrongRevenueDeterioration() {
		assertCodes(validInput().revenueGrowth(new BigDecimal("-0.16")).build())
				.containsExactly(EliminatoryFilterCode.STRONG_REVENUE_DETERIORATION);
	}

	@Test
	void filtersStrongEarningsDeteriorationWithoutBlamingRevenueOrMargin() {
		assertThat(evaluator.evaluate(validInput()
				.revenueGrowth(new BigDecimal("0.015785"))
				.annualRevenueGrowth(new BigDecimal("0.036844"))
				.earningsGrowth(new BigDecimal("-0.542911"))
				.annualEarningsGrowth(new BigDecimal("-0.562737"))
				.profitMargin(new BigDecimal("0.064399"))
				.build())).satisfiesExactly(reason -> {
					assertThat(reason.code()).isEqualTo(EliminatoryFilterCode.STRONG_EARNINGS_DETERIORATION);
					assertThat(reason.message()).contains("Lucro");
				});
	}

	@Test
	void filtersQuarterlyDeteriorationEvenWhenGenericGrowthIsPositive() {
		assertCodes(validInput()
				.revenueGrowth(new BigDecimal("0.05"))
				.annualRevenueGrowth(new BigDecimal("0.04"))
				.quarterlyRevenueGrowth(new BigDecimal("-0.35"))
				.build()).containsExactly(EliminatoryFilterCode.STRONG_REVENUE_DETERIORATION);
	}

	@Test
	void filtersNegativeProfitMarginSeparately() {
		assertCodes(validInput().profitMargin(new BigDecimal("-0.06")).build())
				.containsExactly(EliminatoryFilterCode.NEGATIVE_PROFIT_MARGIN);
	}

	@Test
	void filtersExtremeValuationWithoutGrowthJustification() {
		assertCodes(validInput().trailingPe(new BigDecimal("45.00")).build())
				.containsExactly(EliminatoryFilterCode.EXTREME_VALUATION_WITHOUT_GROWTH);
	}

	@Test
	void doesNotFilterExtremeValuationWhenGrowthJustifiesPremium() {
		assertThat(evaluator.evaluate(validInput()
				.trailingPe(new BigDecimal("45.00"))
				.annualRevenueGrowth(new BigDecimal("0.35"))
				.build())).isEmpty();
	}

	@Test
	void filtersClearlyDeterioratedLongTrend() {
		assertCodes(validInput().trendStatus(TrendStatus.DOWN_TREND).build())
				.containsExactly(EliminatoryFilterCode.LONG_TREND_DETERIORATED);
	}

	@Test
	void filtersExtremeVolatilityForConservativePositionTrade() {
		assertCodes(validInput().historicalVolatility(new BigDecimal("0.66")).build())
				.containsExactly(EliminatoryFilterCode.EXTREME_VOLATILITY);
	}

	@Test
	void filtersIncompleteStaleOrInconsistentData() {
		assertCodes(validInput().candleQualityStatus(DataQualityStatus.INCONSISTENT).build())
				.containsExactly(EliminatoryFilterCode.DATA_QUALITY_BLOCKED);
	}

	private EliminatoryFilterInput.EliminatoryFilterInputBuilder validInput() {
		return EliminatoryFilterInput.builder()
				.currentPrice(new BigDecimal("20.00"))
				.averageFinancialVolume60(new BigDecimal("10000000"))
				.candleQualityStatus(DataQualityStatus.VALID)
				.fundamentalQualityStatus(DataQualityStatus.VALID)
				.trendStatus(TrendStatus.HEALTHY)
				.sma200(new BigDecimal("18.00"))
				.return12m(new BigDecimal("0.10"))
				.historicalVolatility(new BigDecimal("0.25"))
				.recentDrawdown(new BigDecimal("-0.10"))
				.trailingPe(new BigDecimal("12.00"))
				.priceToBook(new BigDecimal("2.00"))
				.enterpriseToEbitda(new BigDecimal("8.00"))
				.earningsPerShare(new BigDecimal("2.00"))
				.profitMargin(new BigDecimal("0.12"))
				.operatingCashflow(new BigDecimal("100"))
				.freeCashflow(new BigDecimal("50"))
				.freeCashflowHistory(List.of(new BigDecimal("50"), new BigDecimal("60")))
				.debtToEquity(new BigDecimal("0.80"))
				.netDebt(new BigDecimal("100"))
				.revenueGrowth(new BigDecimal("0.05"))
				.earningsGrowth(new BigDecimal("0.05"))
				.annualRevenueGrowth(new BigDecimal("0.05"))
				.quarterlyRevenueGrowth(new BigDecimal("0.05"))
				.annualEarningsGrowth(new BigDecimal("0.05"))
				.quarterlyEarningsGrowth(new BigDecimal("0.05"))
				.ebitdaGrowth(new BigDecimal("0.05"));
	}

	private org.assertj.core.api.ListAssert<EliminatoryFilterCode> assertCodes(EliminatoryFilterInput input) {
		return assertThat(evaluator.evaluate(input).stream()
				.map(EliminatoryFilterReason::code)
				.toList());
	}
}
