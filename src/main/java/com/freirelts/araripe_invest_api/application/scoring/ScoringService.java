package com.freirelts.araripe_invest_api.application.scoring;

import com.freirelts.araripe_invest_api.application.screening.EliminatoryFilterCode;
import com.freirelts.araripe_invest_api.application.screening.EliminatoryFilterReason;
import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScoringService {

	public static final String RULE_VERSION = "phase7-v1";

	private static final BigDecimal MIN_SAFETY_MARGIN = new BigDecimal("0.150000");
	private static final BigDecimal MAX_REASONABLE_VOLATILITY = new BigDecimal("0.400000");
	private static final BigDecimal MAX_REASONABLE_DEBT_TO_EQUITY = new BigDecimal("1.500000");
	private static final BigDecimal MIN_DIVIDEND_YIELD = new BigDecimal("0.040000");
	private static final BigDecimal MAX_DIVIDEND_YIELD = new BigDecimal("0.150000");
	private static final BigDecimal MIN_GROWTH = new BigDecimal("0.080000");

	public ScoreResult score(ScoringInput input) {
		List<EliminatoryFilterReason> failedFilters = failedFilters(input);
		if (hasUnscorableFilter(failedFilters)) {
			return blockedScore(input, failedFilters);
		}
		List<ScoreComponent> components = List.of(
				component("FUNDAMENTAL_QUALITY", "Qualidade fundamentalista", 30,
						fundamentalQuality(input), "Lucro, margens e retorno sobre capital sustentam a tese."),
				component("VALUATION_SAFETY_MARGIN", "Valuation e margem de seguranca", 20,
						valuation(input), "Preco atual, preco teto e margem de seguranca limitam entrada cara."),
				component("CASH_GENERATION", "Geracao de caixa", 15,
						cashGeneration(input), "Caixa operacional e fluxo de caixa livre reduzem risco financeiro."),
				component(thesisSpecificCode(input.thesisType()), thesisSpecificLabel(input.thesisType()), 10,
						thesisSpecific(input), thesisSpecificMessage(input.thesisType())),
				component("LONG_TREND", "Tendencia longa", 10,
						longTrend(input), "Media de 200 periodos, retorno e drawdown evitam tese contra tendencia deteriorada."),
				component("RISK_VOLATILITY", "Risco e volatilidade", 10,
						risk(input), "Volatilidade e endividamento reduzem a nota quando elevam risco de position trade."),
				component("MACRO_SECTOR_CONTEXT", "Contexto macro/setorial", 5,
						macroSectorContext(), "Sem sinal macro/setorial estruturado nesta fase, a regra usa nota neutra auditavel."));
		int finalScore = components.stream()
				.map(ScoreComponent::weightedPoints)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(0, RoundingMode.HALF_UP)
				.intValue();
		return new ScoreResult(clamp(finalScore), input.thesisType(), RULE_VERSION, !failedFilters.isEmpty(), true,
				failedFilters, components);
	}

	private ScoreResult blockedScore(ScoringInput input, List<EliminatoryFilterReason> failedFilters) {
		// Dados ou fundamentos minimos ausentes tornam a nota artificial. Nesses casos o motor registra o bloqueio sem
		// calcular subscores; outros filtros continuam bloqueando recomendacao, mas preservam score diagnostico.
		List<String> evidence = failedFilters.stream()
				.map(reason -> reason.code().name())
				.toList();
		ScoreComponent blocked = new ScoreComponent("ELIMINATORY_FILTER_BLOCK", "Bloqueio por filtro eliminatorio",
				100, 0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
				"Score zerado porque filtros obrigatorios falharam antes da pontuacao.", evidence);
		return new ScoreResult(0, input.thesisType(), RULE_VERSION, true, false, failedFilters, List.of(blocked));
	}

	private List<EliminatoryFilterReason> failedFilters(ScoringInput input) {
		return input.failedFilters() == null ? List.of() : input.failedFilters();
	}

	private boolean hasUnscorableFilter(List<EliminatoryFilterReason> failedFilters) {
		return failedFilters.stream()
				.map(EliminatoryFilterReason::code)
				.anyMatch(code -> code == EliminatoryFilterCode.DATA_QUALITY_BLOCKED
						|| code == EliminatoryFilterCode.MINIMUM_FUNDAMENTALS_MISSING);
	}

	private ScoreComponent component(String code, String label, int weightPercent, ScoreRuleEvaluation evaluation,
			String message) {
		BigDecimal weighted = BigDecimal.valueOf(weightPercent)
				.multiply(BigDecimal.valueOf(evaluation.rawScore()))
				.divide(new BigDecimal("100.000000"), 2, RoundingMode.HALF_UP);
		return new ScoreComponent(code, label, weightPercent, evaluation.rawScore(), weighted, message,
				evaluation.evidence());
	}

	private ScoreRuleEvaluation fundamentalQuality(ScoringInput input) {
		int points = 0;
		List<String> evidence = new ArrayList<>();
		// Qualidade fundamentalista privilegia lucro positivo, margens, ROE/ROA e crescimento sem deterioracao forte.
		points += pass(evidence, positive(input.earningsPerShare()) && greaterOrEqual(input.profitMargin(),
				new BigDecimal("0.080000")), 30, "lucro e margem liquida adequados");
		points += pass(evidence, greaterOrEqual(any(input.grossMargin(), input.ebitdaMargin(), input.operatingMargin(),
				input.profitMargin()), new BigDecimal("0.100000")), 20, "margens operacionais saudaveis");
		points += pass(evidence, greaterOrEqual(input.roe(), new BigDecimal("0.120000"))
				|| greaterOrEqual(input.roa(), new BigDecimal("0.060000")), 25, "ROE ou ROA acima do minimo");
		points += pass(evidence, greaterOrEqual(any(input.revenueGrowth(), input.annualRevenueGrowth(),
				input.quarterlyRevenueGrowth()), new BigDecimal("-0.050000"))
				&& greaterOrEqual(any(input.earningsGrowth(), input.annualEarningsGrowth(),
						input.quarterlyEarningsGrowth()), new BigDecimal("-0.100000")), 25,
				"sem deterioracao forte de receita e lucro");
		return new ScoreRuleEvaluation(points, evidence);
	}

	private ScoreRuleEvaluation valuation(ScoringInput input) {
		int points = 0;
		List<String> evidence = new ArrayList<>();
		// Valuation so pontua bem quando ha preco teto calculavel e margem minima; multiplo barato sozinho nao compensa
		// fundamento fraco porque os blocos de qualidade e caixa continuam limitando a nota final.
		points += pass(evidence, positive(input.priceCeiling()) && positive(input.currentPrice())
				&& input.currentPrice().compareTo(input.priceCeiling()) <= 0, 35, "preco atual abaixo do preco teto");
		points += pass(evidence, input.safetyMargin() != null
				&& input.safetyMargin().compareTo(MIN_SAFETY_MARGIN) >= 0, 35, "margem de seguranca minima atingida");
		points += pass(evidence, lessOrEqual(input.trailingPe(), new BigDecimal("18.000000"))
				&& (input.priceToBook() == null || lessOrEqual(input.priceToBook(), new BigDecimal("3.000000")))
				&& (input.enterpriseToEbitda() == null
						|| lessOrEqual(input.enterpriseToEbitda(), new BigDecimal("12.000000"))), 30,
				"multiplos em faixa razoavel");
		return new ScoreRuleEvaluation(points, evidence);
	}

	private ScoreRuleEvaluation cashGeneration(ScoringInput input) {
		int points = 0;
		List<String> evidence = new ArrayList<>();
		// Caixa operacional positivo e FCF nao negativo indicam que a tese nao depende so de lucro contabil.
		points += pass(evidence, positive(input.operatingCashflow()), 55, "caixa operacional positivo");
		points += pass(evidence, nonNegative(input.freeCashflow()), 45, "fluxo de caixa livre nao negativo");
		return new ScoreRuleEvaluation(points, evidence);
	}

	private ScoreRuleEvaluation thesisSpecific(ScoringInput input) {
		return switch (input.thesisType()) {
			case SUSTAINABLE_DIVIDENDS -> dividendScore(input);
			case PROFITABLE_GROWTH_HEALTHY_TREND -> growthScore(input);
			case QUALITY_REASONABLE_PRICE -> qualitySpecificScore(input);
		};
	}

	private ScoreRuleEvaluation dividendScore(ScoringInput input) {
		int points = 0;
		List<String> evidence = new ArrayList<>();
		// Dividendos pontuam apenas quando yield tem faixa sustentavel, recorrencia e cobertura por lucro/caixa.
		points += pass(evidence, greaterOrEqual(input.dividendYield(), MIN_DIVIDEND_YIELD)
				&& lessOrEqual(input.dividendYield(), MAX_DIVIDEND_YIELD), 35, "dividend yield em faixa sustentavel");
		points += pass(evidence, input.cashDividendEventsLastThreeYears() >= 2, 35, "recorrencia de proventos");
		points += pass(evidence, positive(input.earningsPerShare()) && positive(input.freeCashflow()), 30,
				"proventos cobertos por lucro e caixa");
		return new ScoreRuleEvaluation(points, evidence);
	}

	private ScoreRuleEvaluation growthScore(ScoringInput input) {
		int points = 0;
		List<String> evidence = new ArrayList<>();
		// Crescimento so recebe pontos quando receita, lucro/EBITDA e rentabilidade caminham juntos.
		points += pass(evidence, greaterOrEqual(any(input.annualRevenueGrowth(), input.revenueGrowth(),
				input.quarterlyRevenueGrowth()), MIN_GROWTH), 35, "crescimento de receita");
		points += pass(evidence, greaterOrEqual(any(input.annualEarningsGrowth(), input.earningsGrowth(),
				input.quarterlyEarningsGrowth(), input.ebitdaGrowth()), MIN_GROWTH), 35,
				"crescimento de lucro ou EBITDA");
		points += pass(evidence, greaterOrEqual(input.roe(), new BigDecimal("0.120000"))
				&& greaterOrEqual(input.profitMargin(), new BigDecimal("0.080000")), 30,
				"crescimento com rentabilidade");
		return new ScoreRuleEvaluation(points, evidence);
	}

	private ScoreRuleEvaluation qualitySpecificScore(ScoringInput input) {
		int points = 0;
		List<String> evidence = new ArrayList<>();
		// Na tese de qualidade, o bloco especifico substitui dividendos/crescimento por resiliencia financeira adicional.
		points += pass(evidence, lessOrEqual(input.debtToEquity(), MAX_REASONABLE_DEBT_TO_EQUITY), 35,
				"endividamento controlado");
		points += pass(evidence, greaterOrEqual(input.roe(), new BigDecimal("0.150000")), 35,
				"ROE forte para tese de qualidade");
		points += pass(evidence, positive(input.operatingCashflow()) && positive(input.freeCashflow()), 30,
				"caixa recorrente para sustentar qualidade");
		return new ScoreRuleEvaluation(points, evidence);
	}

	private ScoreRuleEvaluation longTrend(ScoringInput input) {
		int points = 0;
		List<String> evidence = new ArrayList<>();
		// Tendencia longa evita aprovar position trade contra perda estrutural da media de 200 periodos.
		points += pass(evidence, input.trendStatus() == TrendStatus.HEALTHY, 45, "tendencia longa saudavel");
		points += pass(evidence, positive(input.currentPrice()) && positive(input.sma200())
				&& input.currentPrice().compareTo(input.sma200()) >= 0, 35, "preco acima da media de 200");
		points += pass(evidence, input.recentDrawdown() != null
				&& input.recentDrawdown().compareTo(new BigDecimal("-0.250000")) >= 0, 20,
				"drawdown recente controlado");
		return new ScoreRuleEvaluation(points, evidence);
	}

	private ScoreRuleEvaluation risk(ScoringInput input) {
		int points = 0;
		List<String> evidence = new ArrayList<>();
		// Risco combina volatilidade e alavancagem; quanto menor a oscilacao e a divida, maior a capacidade de manter a tese.
		points += pass(evidence, input.historicalVolatility() != null
				&& input.historicalVolatility().compareTo(MAX_REASONABLE_VOLATILITY) <= 0, 55,
				"volatilidade compativel com position trade conservador");
		points += pass(evidence, input.debtToEquity() == null
				|| input.debtToEquity().compareTo(MAX_REASONABLE_DEBT_TO_EQUITY) <= 0, 45,
				"divida/patrimonio dentro do limite inicial");
		return new ScoreRuleEvaluation(points, evidence);
	}

	private ScoreRuleEvaluation macroSectorContext() {
		// A integracao macro/setorial estruturada ainda nao escolhe setores sensiveis; nota neutra preserva reproducibilidade
		// e impede que ausencia de IA ou noticia aprove ou bloqueie tese sozinha.
		return new ScoreRuleEvaluation(50, List.of("contexto macro/setorial neutro por regra deterministica"));
	}

	private String thesisSpecificCode(ThesisType thesisType) {
		return switch (thesisType) {
			case SUSTAINABLE_DIVIDENDS -> "DIVIDEND_SCORE";
			case PROFITABLE_GROWTH_HEALTHY_TREND -> "GROWTH_SCORE";
			case QUALITY_REASONABLE_PRICE -> "QUALITY_RESILIENCE_SCORE";
		};
	}

	private String thesisSpecificLabel(ThesisType thesisType) {
		return switch (thesisType) {
			case SUSTAINABLE_DIVIDENDS -> "Dividendos sustentaveis";
			case PROFITABLE_GROWTH_HEALTHY_TREND -> "Crescimento rentavel";
			case QUALITY_REASONABLE_PRICE -> "Resiliencia da qualidade";
		};
	}

	private String thesisSpecificMessage(ThesisType thesisType) {
		return switch (thesisType) {
			case SUSTAINABLE_DIVIDENDS -> "Dividendos recebem nota quando sao recorrentes e sustentados por lucro e caixa.";
			case PROFITABLE_GROWTH_HEALTHY_TREND -> "Crescimento recebe nota quando receita, lucro e rentabilidade avancam juntos.";
			case QUALITY_REASONABLE_PRICE -> "Qualidade recebe nota adicional quando retorno, caixa e divida reforcam a tese.";
		};
	}

	private int pass(List<String> evidence, boolean condition, int points, String message) {
		if (condition) {
			evidence.add(message);
			return points;
		}
		return 0;
	}

	private BigDecimal any(BigDecimal... values) {
		for (BigDecimal value : values) {
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private boolean positive(BigDecimal value) {
		return value != null && value.signum() > 0;
	}

	private boolean nonNegative(BigDecimal value) {
		return value != null && value.signum() >= 0;
	}

	private boolean lessOrEqual(BigDecimal value, BigDecimal limit) {
		return value != null && limit != null && value.compareTo(limit) <= 0;
	}

	private boolean greaterOrEqual(BigDecimal value, BigDecimal limit) {
		return value != null && limit != null && value.compareTo(limit) >= 0;
	}

	private int clamp(int value) {
		return Math.min(100, Math.max(0, value));
	}

	private record ScoreRuleEvaluation(int rawScore, List<String> evidence) {
	}
}
