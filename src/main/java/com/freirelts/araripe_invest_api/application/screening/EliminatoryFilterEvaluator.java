package com.freirelts.araripe_invest_api.application.screening;

import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class EliminatoryFilterEvaluator {

	private static final BigDecimal MIN_AVERAGE_FINANCIAL_VOLUME_60 = new BigDecimal("5000000");
	private static final BigDecimal MIN_PRICE = new BigDecimal("2.00");
	private static final BigDecimal MAX_DEBT_TO_EQUITY = new BigDecimal("2.50");
	private static final BigDecimal MAX_NET_DEBT_TO_OPERATING_CASHFLOW = new BigDecimal("5.00");
	private static final BigDecimal STRONG_REVENUE_DROP = new BigDecimal("-0.15");
	private static final BigDecimal STRONG_EARNINGS_DROP = new BigDecimal("-0.20");
	private static final BigDecimal NEGATIVE_MARGIN_LIMIT = new BigDecimal("-0.05");
	private static final BigDecimal EXTREME_PE = new BigDecimal("40.00");
	private static final BigDecimal EXTREME_PB = new BigDecimal("8.00");
	private static final BigDecimal EXTREME_EV_EBITDA = new BigDecimal("20.00");
	private static final BigDecimal GROWTH_JUSTIFICATION = new BigDecimal("0.25");
	private static final BigDecimal SMA200_DAMAGE_LIMIT = new BigDecimal("0.85");
	private static final BigDecimal RETURN_12M_DAMAGE_LIMIT = new BigDecimal("-0.30");
	private static final BigDecimal EXTREME_ANNUAL_VOLATILITY = new BigDecimal("0.65");
	private static final BigDecimal EXTREME_DRAWDOWN = new BigDecimal("-0.45");

	public List<EliminatoryFilterReason> evaluate(EliminatoryFilterInput input) {
		List<EliminatoryFilterReason> reasons = new ArrayList<>();
		evaluateDataQuality(input, reasons);
		evaluateLiquidity(input, reasons);
		evaluatePrice(input, reasons);
		evaluateMinimumFundamentals(input, reasons);
		evaluateRecurringLosses(input, reasons);
		evaluateFreeCashflow(input, reasons);
		evaluateDebt(input, reasons);
		evaluateFundamentalDeterioration(input, reasons);
		evaluateValuation(input, reasons);
		evaluateLongTrend(input, reasons);
		evaluateVolatility(input, reasons);
		return reasons;
	}

	private void evaluateDataQuality(EliminatoryFilterInput input, List<EliminatoryFilterReason> reasons) {
		// Dados incompletos, atrasados ou inconsistentes bloqueiam tese porque o motor nao pode preencher lacunas.
		if (input.isCandleMissing() || input.isTechnicalMissing() || input.isFundamentalMissing()
				|| input.isCandleStale() || input.isTechnicalStale() || input.isFundamentalStale()
				|| invalidQuality(input.getCandleQualityStatus()) || invalidQuality(input.getFundamentalQualityStatus())
				|| input.getTrendStatus() == TrendStatus.INSUFFICIENT_DATA) {
			reasons.add(reason(EliminatoryFilterCode.DATA_QUALITY_BLOCKED,
					"Dados incompletos, atrasados ou inconsistentes bloqueiam a triagem."));
		}
	}

	private void evaluateLiquidity(EliminatoryFilterInput input, List<EliminatoryFilterReason> reasons) {
		// Liquidez minima de R$ 5 milhoes/dia reduz risco de execucao ruim e de ficar preso no ativo.
		if (input.getAverageFinancialVolume60() == null
				|| input.getAverageFinancialVolume60().compareTo(MIN_AVERAGE_FINANCIAL_VOLUME_60) < 0) {
			reasons.add(reason(EliminatoryFilterCode.INSUFFICIENT_LIQUIDITY,
					"Volume financeiro medio de 60 dias abaixo de R$ 5 milhoes."));
		}
	}

	private void evaluatePrice(EliminatoryFilterInput input, List<EliminatoryFilterReason> reasons) {
		// Preco abaixo de R$ 2,00 costuma elevar spread, ruido e risco de movimentos percentuais extremos.
		if (input.getCurrentPrice() == null || input.getCurrentPrice().compareTo(MIN_PRICE) < 0) {
			reasons.add(reason(EliminatoryFilterCode.PRICE_BELOW_MINIMUM,
					"Preco de fechamento abaixo de R$ 2,00."));
		}
	}

	private void evaluateMinimumFundamentals(EliminatoryFilterInput input, List<EliminatoryFilterReason> reasons) {
		// Fundamentos minimos exigem valuation, lucratividade e caixa para evitar tese sem base auditavel.
		if (input.isFundamentalMissing() || input.getFundamentalQualityStatus() != DataQualityStatus.VALID
				|| allNull(input.getTrailingPe(), input.getPriceToBook(), input.getEnterpriseToEbitda())
				|| allNull(input.getEarningsPerShare(), input.getProfitMargin())
				|| allNull(input.getOperatingCashflow(), input.getFreeCashflow())) {
			reasons.add(reason(EliminatoryFilterCode.MINIMUM_FUNDAMENTALS_MISSING,
					"Dados fundamentalistas minimos ausentes para valuation, lucro ou caixa."));
		}
	}

	private void evaluateRecurringLosses(EliminatoryFilterInput input, List<EliminatoryFilterReason> reasons) {
		// Position trade conservador exige lucro recorrente; EPS e margem negativos sinalizam prejuizo estrutural.
		if (nonPositive(input.getEarningsPerShare()) && nonPositive(input.getProfitMargin())) {
			reasons.add(reason(EliminatoryFilterCode.RECURRING_LOSSES,
					"Prejuizo recorrente identificado por lucro por acao e margem liquida nao positivos."));
		}
	}

	private void evaluateFreeCashflow(EliminatoryFilterInput input, List<EliminatoryFilterReason> reasons) {
		// Fluxo de caixa livre negativo em mais de um snapshot indica que o lucro pode nao virar caixa distribuivel.
		long negativePeriods = input.getFreeCashflowHistory().stream()
				.filter(value -> value != null && value.signum() < 0)
				.count();
		if (negativePeriods >= 2 || (negative(input.getFreeCashflow()) && nonPositive(input.getOperatingCashflow()))) {
			reasons.add(reason(EliminatoryFilterCode.PERSISTENT_NEGATIVE_FREE_CASHFLOW,
					"Fluxo de caixa livre persistentemente negativo ou sem suporte de caixa operacional."));
		}
	}

	private void evaluateDebt(EliminatoryFilterInput input, List<EliminatoryFilterReason> reasons) {
		// Endividamento e excessivo quando divida/patrimonio passa de 2,5x e o caixa operacional nao cobre a
		// divida liquida.
		if (input.getDebtToEquity() != null && input.getDebtToEquity().compareTo(MAX_DEBT_TO_EQUITY) > 0
				&& weakCashCoverage(input.getNetDebt(), input.getOperatingCashflow())) {
			reasons.add(reason(EliminatoryFilterCode.EXCESSIVE_DEBT,
					"Endividamento elevado sem capacidade clara de geracao de caixa."));
		}
	}

	private void evaluateFundamentalDeterioration(EliminatoryFilterInput input,
			List<EliminatoryFilterReason> reasons) {
		// Quedas fortes de receita, lucro ou margem indicam deterioracao antes de qualquer pontuacao de oportunidade.
		if (lessOrEqual(any(input.getRevenueGrowth(), input.getAnnualRevenueGrowth(), input.getQuarterlyRevenueGrowth()),
				STRONG_REVENUE_DROP)
				|| lessOrEqual(any(input.getEarningsGrowth(), input.getAnnualEarningsGrowth(),
						input.getQuarterlyEarningsGrowth()), STRONG_EARNINGS_DROP)
				|| lessOrEqual(input.getProfitMargin(), NEGATIVE_MARGIN_LIMIT)) {
			reasons.add(reason(EliminatoryFilterCode.STRONG_FUNDAMENTAL_DETERIORATION,
					"Deterioracao forte de receita, lucro ou margem."));
		}
	}

	private void evaluateValuation(EliminatoryFilterInput input, List<EliminatoryFilterReason> reasons) {
		// Multiplo extremo so passa para tese futura se houver crescimento material que justifique pagar premio.
		boolean extremeValuation = greaterThan(input.getTrailingPe(), EXTREME_PE)
				|| greaterThan(input.getPriceToBook(), EXTREME_PB)
				|| greaterThan(input.getEnterpriseToEbitda(), EXTREME_EV_EBITDA);
		if (extremeValuation && !hasGrowthJustification(input)) {
			reasons.add(reason(EliminatoryFilterCode.EXTREME_VALUATION_WITHOUT_GROWTH,
					"Valuation extremo sem crescimento suficiente para justificar o premio."));
		}
	}

	private void evaluateLongTrend(EliminatoryFilterInput input, List<EliminatoryFilterReason> reasons) {
		// Tendencia longa deteriorada bloqueia position trade porque sugere perda estrutural de interesse do mercado.
		boolean belowDamagedSma200 = input.getCurrentPrice() != null && input.getSma200() != null
				&& input.getCurrentPrice().compareTo(input.getSma200().multiply(SMA200_DAMAGE_LIMIT)) < 0;
		if (input.getTrendStatus() == TrendStatus.DOWN_TREND || belowDamagedSma200
				|| lessOrEqual(input.getReturn12m(), RETURN_12M_DAMAGE_LIMIT)) {
			reasons.add(reason(EliminatoryFilterCode.LONG_TREND_DETERIORATED,
					"Tendencia longa claramente deteriorada."));
		}
	}

	private void evaluateVolatility(EliminatoryFilterInput input, List<EliminatoryFilterReason> reasons) {
		// Volatilidade anual acima de 65% ou drawdown maior que 45% foge do perfil conservador do MVP.
		if (greaterThan(input.getHistoricalVolatility(), EXTREME_ANNUAL_VOLATILITY)
				|| lessOrEqual(input.getRecentDrawdown(), EXTREME_DRAWDOWN)) {
			reasons.add(reason(EliminatoryFilterCode.EXTREME_VOLATILITY,
					"Volatilidade ou drawdown incompativel com position trade conservador."));
		}
	}

	private boolean invalidQuality(DataQualityStatus status) {
		return status != null && status != DataQualityStatus.VALID;
	}

	private boolean allNull(Object... values) {
		for (Object value : values) {
			if (value != null) {
				return false;
			}
		}
		return true;
	}

	private boolean nonPositive(BigDecimal value) {
		return value != null && value.signum() <= 0;
	}

	private boolean negative(BigDecimal value) {
		return value != null && value.signum() < 0;
	}

	private boolean weakCashCoverage(BigDecimal netDebt, BigDecimal operatingCashflow) {
		if (netDebt == null || netDebt.signum() <= 0) {
			return false;
		}
		if (operatingCashflow == null || operatingCashflow.signum() <= 0) {
			return true;
		}
		return netDebt.divide(operatingCashflow, 6, RoundingMode.HALF_UP)
				.compareTo(MAX_NET_DEBT_TO_OPERATING_CASHFLOW) > 0;
	}

	private boolean lessOrEqual(BigDecimal value, BigDecimal limit) {
		return value != null && value.compareTo(limit) <= 0;
	}

	private boolean greaterThan(BigDecimal value, BigDecimal limit) {
		return value != null && value.compareTo(limit) > 0;
	}

	private BigDecimal any(BigDecimal... values) {
		for (BigDecimal value : values) {
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private boolean hasGrowthJustification(EliminatoryFilterInput input) {
		return greaterThan(input.getRevenueGrowth(), GROWTH_JUSTIFICATION)
				|| greaterThan(input.getAnnualRevenueGrowth(), GROWTH_JUSTIFICATION)
				|| greaterThan(input.getQuarterlyRevenueGrowth(), GROWTH_JUSTIFICATION)
				|| greaterThan(input.getEarningsGrowth(), GROWTH_JUSTIFICATION)
				|| greaterThan(input.getAnnualEarningsGrowth(), GROWTH_JUSTIFICATION)
				|| greaterThan(input.getQuarterlyEarningsGrowth(), GROWTH_JUSTIFICATION)
				|| greaterThan(input.getEbitdaGrowth(), GROWTH_JUSTIFICATION);
	}

	private EliminatoryFilterReason reason(EliminatoryFilterCode code, String message) {
		return new EliminatoryFilterReason(code, message);
	}
}
