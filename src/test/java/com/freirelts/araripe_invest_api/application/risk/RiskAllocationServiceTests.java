package com.freirelts.araripe_invest_api.application.risk;

import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RiskAllocationServiceTests {

	private final RiskAllocationService service = new RiskAllocationService();

	@Test
	void calculatesSuggestedQuantityFromCapitalAllocationAndCurrentPrice() {
		RiskAllocationResult result = service.calculate(validInputBuilder().build());

		assertThat(result.capitalBase()).isEqualByComparingTo("10000.00");
		assertThat(result.maxPositionValue()).isEqualByComparingTo("1000.00");
		assertThat(result.suggestedQuantity()).isEqualTo(40);
		assertThat(result.firstTrancheValue()).isEqualByComparingTo("500.00");
		assertThat(result.secondTrancheValue()).isEqualByComparingTo("250.00");
		assertThat(result.thirdTrancheValue()).isEqualByComparingTo("250.00");
		assertThat(result.remainingPlannedValue()).isEqualByComparingTo("500.00");
		assertThat(result.stopPrice()).isEqualByComparingTo("21.250000");
		assertThat(result.targetPrice()).isEqualByComparingTo("32.000000");
		assertThat(result.valid()).isTrue();
	}

	@Test
	void neverExceedsConfiguredMaximumAllocationPerAsset() {
		RiskAllocationResult result = service.calculate(validInputBuilder()
				.targetAllocationPercent(new BigDecimal("25.000000"))
				.build());

		assertThat(result.targetAllocationPercent()).isEqualByComparingTo("10.000000");
		assertThat(result.maxPositionValue()).isEqualByComparingTo("1000.00");
		assertThat(result.suggestedQuantity()).isEqualTo(40);
	}

	@Test
	void blocksNewAllocationWhenCurrentPriceIsAbovePriceCeiling() {
		RiskAllocationResult result = service.calculate(validInputBuilder()
				.currentPrice(new BigDecimal("28.00"))
				.priceCeiling(new BigDecimal("27.20"))
				.build());

		assertThat(result.valid()).isFalse();
		assertThat(result.suggestedQuantity()).isZero();
		assertThat(result.recommendedAction()).isEqualTo("BLOQUEADO");
		assertThat(result.invalidReason()).contains("referencia do estudo");
	}

	@Test
	void blocksNewAllocationWhenSectorExposureIsAlreadyAtLimit() {
		RiskAllocationResult result = service.calculate(validInputBuilder()
				.currentSectorExposureValue(new BigDecimal("2500.00"))
				.build());

		assertThat(result.valid()).isFalse();
		assertThat(result.suggestedQuantity()).isZero();
		assertThat(result.invalidReason()).contains("limite por ativo, setor ou capital");
	}

	@Test
	void blocksNewAllocationWhenTotalExposureWouldConsumeCashReserve() {
		RiskAllocationResult result = service.calculate(validInputBuilder()
				.currentAssetExposureValue(new BigDecimal("100.00"))
				.currentSectorExposureValue(new BigDecimal("100.00"))
				.currentTotalExposureValue(new BigDecimal("9000.00"))
				.build());

		assertThat(result.availableForCash()).isEqualByComparingTo("0.00");
		assertThat(result.valid()).isFalse();
		assertThat(result.suggestedQuantity()).isZero();
		assertThat(result.invalidReason()).contains("capital");
	}

	@Test
	void mapsDrawdownTrendOrFundamentalDeteriorationToReassessment() {
		RiskAllocationResult drawdown = service.calculate(validInputBuilder()
				.recentDrawdown(new BigDecimal("-0.300000"))
				.build());
		RiskAllocationResult trend = service.calculate(validInputBuilder()
				.trendStatus(TrendStatus.DETERIORATING)
				.build());
		RiskAllocationResult fundamentals = service.calculate(validInputBuilder()
				.fundamentalsDeteriorated(true)
				.build());

		assertThat(drawdown.valid()).isFalse();
		assertThat(drawdown.recommendedAction()).isEqualTo(ThesisStatus.PREMISSAS_ALTERADAS.name());
		assertThat(trend.invalidReason()).contains("Tendencia longa");
		assertThat(fundamentals.invalidReason()).contains("Fundamentos");
	}

	private RiskAllocationInputBuilder validInputBuilder() {
		return new RiskAllocationInputBuilder()
				.settings(RiskAllocationSettings.conservativeDefault())
				.thesisStatus(ThesisStatus.CRITERIOS_ATENDIDOS)
				.currentPrice(new BigDecimal("25.00"))
				.fairPriceEstimate(new BigDecimal("32.00"))
				.priceCeiling(new BigDecimal("27.20"))
				.safetyMargin(new BigDecimal("0.218750"))
					.targetAllocationPercent(new BigDecimal("10.000000"))
					.currentAssetExposureValue(BigDecimal.ZERO)
					.currentSectorExposureValue(BigDecimal.ZERO)
					.currentTotalExposureValue(BigDecimal.ZERO)
					.averagePrice(new BigDecimal("25.00"))
				.recentDrawdown(new BigDecimal("-0.100000"))
				.trendStatus(TrendStatus.HEALTHY)
				.fundamentalsDeteriorated(false);
	}

	private static final class RiskAllocationInputBuilder {
		private RiskAllocationSettings settings;
		private ThesisStatus thesisStatus;
		private BigDecimal currentPrice;
		private BigDecimal fairPriceEstimate;
		private BigDecimal priceCeiling;
		private BigDecimal safetyMargin;
		private BigDecimal targetAllocationPercent;
			private BigDecimal currentAssetExposureValue;
			private BigDecimal currentSectorExposureValue;
			private BigDecimal currentTotalExposureValue;
			private BigDecimal averagePrice;
		private BigDecimal userStopPrice;
		private BigDecimal userTargetPrice;
		private BigDecimal recentDrawdown;
		private TrendStatus trendStatus;
		private boolean fundamentalsDeteriorated;

		RiskAllocationInputBuilder settings(RiskAllocationSettings settings) {
			this.settings = settings;
			return this;
		}

		RiskAllocationInputBuilder thesisStatus(ThesisStatus thesisStatus) {
			this.thesisStatus = thesisStatus;
			return this;
		}

		RiskAllocationInputBuilder currentPrice(BigDecimal currentPrice) {
			this.currentPrice = currentPrice;
			return this;
		}

		RiskAllocationInputBuilder fairPriceEstimate(BigDecimal fairPriceEstimate) {
			this.fairPriceEstimate = fairPriceEstimate;
			return this;
		}

		RiskAllocationInputBuilder priceCeiling(BigDecimal priceCeiling) {
			this.priceCeiling = priceCeiling;
			return this;
		}

		RiskAllocationInputBuilder safetyMargin(BigDecimal safetyMargin) {
			this.safetyMargin = safetyMargin;
			return this;
		}

		RiskAllocationInputBuilder targetAllocationPercent(BigDecimal targetAllocationPercent) {
			this.targetAllocationPercent = targetAllocationPercent;
			return this;
		}

		RiskAllocationInputBuilder currentAssetExposureValue(BigDecimal currentAssetExposureValue) {
			this.currentAssetExposureValue = currentAssetExposureValue;
			return this;
		}

			RiskAllocationInputBuilder currentSectorExposureValue(BigDecimal currentSectorExposureValue) {
				this.currentSectorExposureValue = currentSectorExposureValue;
				return this;
			}

			RiskAllocationInputBuilder currentTotalExposureValue(BigDecimal currentTotalExposureValue) {
				this.currentTotalExposureValue = currentTotalExposureValue;
				return this;
			}

		RiskAllocationInputBuilder averagePrice(BigDecimal averagePrice) {
			this.averagePrice = averagePrice;
			return this;
		}

		RiskAllocationInputBuilder recentDrawdown(BigDecimal recentDrawdown) {
			this.recentDrawdown = recentDrawdown;
			return this;
		}

		RiskAllocationInputBuilder trendStatus(TrendStatus trendStatus) {
			this.trendStatus = trendStatus;
			return this;
		}

		RiskAllocationInputBuilder fundamentalsDeteriorated(boolean fundamentalsDeteriorated) {
			this.fundamentalsDeteriorated = fundamentalsDeteriorated;
			return this;
		}

		RiskAllocationInput build() {
				return new RiskAllocationInput(settings, thesisStatus, currentPrice, fairPriceEstimate, priceCeiling,
						safetyMargin, targetAllocationPercent, currentAssetExposureValue, currentSectorExposureValue,
						currentTotalExposureValue, averagePrice, userStopPrice, userTargetPrice, recentDrawdown, trendStatus,
						fundamentalsDeteriorated);
			}
	}
}
