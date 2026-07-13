package com.freirelts.araripe_invest_api.application.scoring;

import com.freirelts.araripe_invest_api.application.screening.EliminatoryFilterCode;
import com.freirelts.araripe_invest_api.application.screening.EliminatoryFilterReason;
import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringServiceTests {

	private final ScoringService service = new ScoringService();

	@Test
	void returnsReproducibleWeightedScoreFromDocumentedComponents() {
		ScoreResult first = service.score(strongInput(ThesisType.QUALITY_REASONABLE_PRICE));
		ScoreResult second = service.score(strongInput(ThesisType.QUALITY_REASONABLE_PRICE));

		assertThat(first).isEqualTo(second);
		assertThat(first.finalScore()).isEqualTo(98);
		assertThat(first.components()).extracting(ScoreComponent::code)
				.containsExactly("FUNDAMENTAL_QUALITY", "VALUATION_SAFETY_MARGIN", "CASH_GENERATION",
						"QUALITY_RESILIENCE_SCORE", "LONG_TREND", "RISK_VOLATILITY", "MACRO_SECTOR_CONTEXT");
		assertThat(first.components()).extracting(ScoreComponent::weightPercent)
				.containsExactly(30, 20, 15, 10, 10, 10, 5);
		assertThat(first.ruleVersion()).isEqualTo(ScoringService.RULE_VERSION);
		assertThat(first.blockedByEliminatoryFilter()).isFalse();
		assertThat(first.scoreCalculated()).isTrue();
		assertThat(first.failedFilters()).isEmpty();
	}

	@Test
	void dataQualityFilterZerosScoreBecauseDiagnosticScoreWouldBeUnreliable() {
		ScoreResult result = service.score(new ScoringInput(ThesisType.QUALITY_REASONABLE_PRICE,
				new BigDecimal("20.00"), new BigDecimal("40.00"), new BigDecimal("34.00"),
				new BigDecimal("0.500000"), new BigDecimal("5.000000"), new BigDecimal("1.000000"),
				new BigDecimal("4.000000"), new BigDecimal("4.000000"), BigDecimal.ZERO, new BigDecimal("0.200000"),
				new BigDecimal("0.400000"), new BigDecimal("0.250000"), new BigDecimal("0.220000"),
				new BigDecimal("0.180000"), new BigDecimal("0.100000"), new BigDecimal("0.500000"),
				new BigDecimal("0.120000"), new BigDecimal("0.150000"), new BigDecimal("0.150000"),
				new BigDecimal("0.100000"), new BigDecimal("0.160000"), new BigDecimal("0.110000"),
				new BigDecimal("0.180000"), new BigDecimal("300.000000"), new BigDecimal("500.000000"),
				new BigDecimal("18.000000"), new BigDecimal("0.250000"), new BigDecimal("-0.100000"),
				TrendStatus.HEALTHY, 2,
				List.of(new EliminatoryFilterReason(EliminatoryFilterCode.DATA_QUALITY_BLOCKED,
						"Dados incompletos, atrasados ou inconsistentes bloqueiam a triagem."))));

		assertThat(result.finalScore()).isZero();
		assertThat(result.blockedByEliminatoryFilter()).isTrue();
		assertThat(result.scoreCalculated()).isFalse();
		assertThat(result.failedFilters()).extracting(EliminatoryFilterReason::code)
				.containsExactly(EliminatoryFilterCode.DATA_QUALITY_BLOCKED);
		assertThat(result.components()).singleElement()
				.extracting(ScoreComponent::code)
				.isEqualTo("ELIMINATORY_FILTER_BLOCK");
	}

	@Test
	void nonDataEliminatoryFilterKeepsDiagnosticScoreButBlocksActionability() {
		ScoreResult result = service.score(new ScoringInput(ThesisType.QUALITY_REASONABLE_PRICE,
				new BigDecimal("20.00"), new BigDecimal("40.00"), new BigDecimal("34.00"),
				new BigDecimal("0.500000"), new BigDecimal("5.000000"), new BigDecimal("1.000000"),
				new BigDecimal("4.000000"), new BigDecimal("4.000000"), BigDecimal.ZERO, new BigDecimal("0.200000"),
				new BigDecimal("0.400000"), new BigDecimal("0.250000"), new BigDecimal("0.220000"),
				new BigDecimal("0.180000"), new BigDecimal("0.100000"), new BigDecimal("0.500000"),
				new BigDecimal("0.120000"), new BigDecimal("0.150000"), new BigDecimal("0.150000"),
				new BigDecimal("0.100000"), new BigDecimal("0.160000"), new BigDecimal("0.110000"),
				new BigDecimal("0.180000"), new BigDecimal("300.000000"), new BigDecimal("500.000000"),
				new BigDecimal("18.000000"), new BigDecimal("0.250000"), new BigDecimal("-0.100000"),
				TrendStatus.HEALTHY, 2,
				List.of(new EliminatoryFilterReason(EliminatoryFilterCode.INSUFFICIENT_LIQUIDITY,
						"Volume financeiro medio abaixo do minimo."))));

		assertThat(result.finalScore()).isGreaterThan(0);
		assertThat(result.blockedByEliminatoryFilter()).isTrue();
		assertThat(result.scoreCalculated()).isTrue();
		assertThat(result.failedFilters()).extracting(EliminatoryFilterReason::code)
				.containsExactly(EliminatoryFilterCode.INSUFFICIENT_LIQUIDITY);
		assertThat(result.components()).extracting(ScoreComponent::code)
				.containsExactly("FUNDAMENTAL_QUALITY", "VALUATION_SAFETY_MARGIN", "CASH_GENERATION",
						"QUALITY_RESILIENCE_SCORE", "LONG_TREND", "RISK_VOLATILITY", "MACRO_SECTOR_CONTEXT");
	}

	@Test
	void cheapAssetWithWeakFundamentalsDoesNotReceiveValidHighScoreOnlyByValuation() {
		ScoringInput input = new ScoringInput(ThesisType.QUALITY_REASONABLE_PRICE, new BigDecimal("10.00"),
				new BigDecimal("30.00"), new BigDecimal("25.50"), new BigDecimal("0.666667"),
				new BigDecimal("5.000000"), new BigDecimal("1.000000"), new BigDecimal("4.000000"),
				null, null, new BigDecimal("-0.020000"), null, null, null, null, null, null,
				new BigDecimal("-0.120000"), new BigDecimal("-0.200000"), new BigDecimal("-0.120000"),
				new BigDecimal("-0.200000"), new BigDecimal("-0.200000"), new BigDecimal("-0.200000"),
				null, new BigDecimal("-100.000000"), BigDecimal.ZERO, new BigDecimal("9.000000"),
				new BigDecimal("0.250000"), new BigDecimal("-0.080000"), TrendStatus.HEALTHY, 0, List.of());

		ScoreResult result = service.score(input);

		assertThat(result.finalScore()).isLessThan(60);
		assertThat(result.components()).filteredOn(component -> component.code().equals("VALUATION_SAFETY_MARGIN"))
				.singleElement()
				.extracting(ScoreComponent::rawScore)
				.isEqualTo(100);
		assertThat(result.components()).filteredOn(component -> component.code().equals("FUNDAMENTAL_QUALITY"))
				.singleElement()
				.extracting(ScoreComponent::rawScore)
				.isEqualTo(0);
	}

	private ScoringInput strongInput(ThesisType thesisType) {
		return new ScoringInput(thesisType, new BigDecimal("20.00"), new BigDecimal("36.00"),
				new BigDecimal("30.60"), new BigDecimal("0.444444"), new BigDecimal("6.666667"),
				new BigDecimal("1.333333"), new BigDecimal("8.000000"), new BigDecimal("3.000000"),
				new BigDecimal("0.080000"), new BigDecimal("0.180000"), new BigDecimal("0.420000"),
				new BigDecimal("0.250000"), new BigDecimal("0.220000"), new BigDecimal("0.180000"),
				new BigDecimal("0.100000"), new BigDecimal("0.500000"), new BigDecimal("0.120000"),
				new BigDecimal("0.150000"), new BigDecimal("0.150000"), new BigDecimal("0.100000"),
				new BigDecimal("0.160000"), new BigDecimal("0.110000"), new BigDecimal("0.180000"),
				new BigDecimal("300.000000"), new BigDecimal("500.000000"), new BigDecimal("18.000000"),
				new BigDecimal("0.250000"), new BigDecimal("-0.100000"), TrendStatus.HEALTHY, 2, List.of());
	}
}
