package com.freirelts.araripe_invest_api.application.risk;

import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RiskAllocationService {

	private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.000000");

	public RiskAllocationResult calculate(RiskAllocationInput input) {
		RiskAllocationSettings settings = input.settings() == null ? RiskAllocationSettings.conservativeDefault()
				: input.settings();
		BigDecimal currentPrice = input.currentPrice();
		BigDecimal targetAllocationPercent = capPercent(input.targetAllocationPercent(),
				settings.maxAllocationPerAssetPercent());
		BigDecimal deployableCapital = settings.capitalBase()
				.multiply(BigDecimal.ONE.subtract(percent(settings.minimumCashReservePercent())));
		BigDecimal maxPositionValue = money(settings.capitalBase().multiply(percent(targetAllocationPercent)));
		BigDecimal maxSectorValue = money(settings.capitalBase()
				.multiply(percent(settings.maxAllocationPerSectorPercent())));
		BigDecimal currentAssetExposure = money(nonNull(input.currentAssetExposureValue()));
		BigDecimal currentSectorExposure = money(nonNull(input.currentSectorExposureValue()));
		BigDecimal availableForAsset = money(maxPositionValue.subtract(currentAssetExposure));
		BigDecimal availableForSector = money(maxSectorValue.subtract(currentSectorExposure));
		BigDecimal currentTotalExposure = money(nonNull(input.currentTotalExposureValue()));
		BigDecimal availableForCash = money(deployableCapital.subtract(currentTotalExposure));
		BigDecimal allocatableValue = minNonNegative(maxPositionValue, availableForAsset, availableForSector,
				availableForCash);

		String invalidReason = invalidReason(input, settings, allocatableValue);
		boolean valid = invalidReason == null;
		int suggestedQuantity = valid && positive(currentPrice)
				? allocatableValue.divide(currentPrice, 0, RoundingMode.DOWN).intValue() : 0;
		BigDecimal plannedValue = money(currentPrice == null ? BigDecimal.ZERO
				: currentPrice.multiply(BigDecimal.valueOf(suggestedQuantity)));

		BigDecimal firstTranche = valid ? money(plannedValue.multiply(percent(settings.firstTranchePercent())))
				: money(BigDecimal.ZERO);
		BigDecimal secondTranche = valid ? money(plannedValue.multiply(percent(settings.secondTranchePercent())))
				: money(BigDecimal.ZERO);
		BigDecimal thirdTranche = valid ? money(plannedValue.multiply(percent(settings.thirdTranchePercent())))
				: money(BigDecimal.ZERO);

		return new RiskAllocationResult(money(settings.capitalBase()), targetAllocationPercent,
				settings.maxAllocationPerAssetPercent(), settings.maxAllocationPerSectorPercent(),
				settings.minimumCashReservePercent(), maxPositionValue, currentAssetExposure, currentSectorExposure,
				availableForAsset, availableForSector, currentTotalExposure, availableForCash, money6(currentPrice),
				money6(input.priceCeiling()), money6(input.fairPriceEstimate()),
				toPercent(input.safetyMargin()), estimatedUpside(input.fairPriceEstimate(), currentPrice),
				suggestedQuantity, firstTranche, secondTranche, thirdTranche, money(secondTranche.add(thirdTranche)),
				stopPrice(input, settings), targetPrice(input, settings), valid, action(input, valid, invalidReason),
				invalidReason);
	}

	private String invalidReason(RiskAllocationInput input, RiskAllocationSettings settings, BigDecimal allocatableValue) {
		if (!positive(input.currentPrice())) {
			return "Preco atual indisponivel ou invalido para calcular alocacao.";
		}
		if (!positive(input.priceCeiling())) {
			return "Referencia de preco do estudo indisponivel para validar o plano analitico.";
		}
		// Preco acima da referencia bloqueia o plano analitico, mas nao gera comando operacional.
		if (input.currentPrice().compareTo(input.priceCeiling()) > 0) {
			return "Preco atual acima da referencia do estudo; plano analitico bloqueado por valuation.";
		}
		if (input.safetyMargin() == null
				|| toPercent(input.safetyMargin()).compareTo(settings.minimumSafetyMarginPercent()) < 0) {
			return "Margem de seguranca abaixo do minimo configurado.";
		}
		// Limites por ativo e setor impedem concentracao excessiva mesmo quando a tese esta forte.
		if (allocatableValue.compareTo(BigDecimal.ZERO) <= 0) {
			return "Exposicao atual ja atingiu o limite por ativo, setor ou capital disponivel.";
		}
		if (input.thesisStatus() != ThesisStatus.CRITERIOS_ATENDIDOS) {
			return "Estado do modelo de estudo nao atende todos os criterios analiticos.";
		}
		// Drawdown, perda de tendencia ou deterioracao fundamental alteram premissas do estudo.
		if (input.fundamentalsDeteriorated()) {
			return "Fundamentos deterioraram e alteraram premissas do modelo de estudo.";
		}
		if (input.trendStatus() == TrendStatus.DOWN_TREND || input.trendStatus() == TrendStatus.DETERIORATING) {
			return "Tendencia longa deteriorou e alterou premissas do modelo de estudo.";
		}
		if (input.recentDrawdown() != null
				&& input.recentDrawdown().compareTo(percent(settings.toleratedDrawdownPercent()).negate()) <= 0) {
			return "Drawdown recente superou o limite analitico definido para o estudo.";
		}
		return null;
	}

	private String action(RiskAllocationInput input, boolean valid, String invalidReason) {
		if (valid) {
			return input.thesisStatus().name();
		}
		if (invalidReason != null && (invalidReason.contains("alterou premissas")
				|| invalidReason.contains("superou o limite analitico"))) {
			return ThesisStatus.PREMISSAS_ALTERADAS.name();
		}
		return "BLOQUEADO";
	}

	private BigDecimal stopPrice(RiskAllocationInput input, RiskAllocationSettings settings) {
		if (positive(input.userStopPrice())) {
			return money6(input.userStopPrice());
		}
		BigDecimal basePrice = positive(input.averagePrice()) ? input.averagePrice() : input.currentPrice();
		// Stop padrao usa percentual conservador abaixo do preco medio; sem carteira, usa preco atual da tese.
		return positive(basePrice)
				? money6(basePrice.multiply(BigDecimal.ONE.subtract(percent(settings.defaultStopPercent()))))
				: null;
	}

	private BigDecimal targetPrice(RiskAllocationInput input, RiskAllocationSettings settings) {
		if (positive(input.userTargetPrice())) {
			return money6(input.userTargetPrice());
		}
		BigDecimal basePrice = positive(input.averagePrice()) ? input.averagePrice() : input.currentPrice();
		// Objetivo padrao prioriza valor justo; se ele nao existir, aplica retorno alvo configurado sobre o preco base.
		if (positive(input.fairPriceEstimate())) {
			return money6(input.fairPriceEstimate());
		}
		return positive(basePrice)
				? money6(basePrice.multiply(BigDecimal.ONE.add(percent(settings.defaultTargetReturnPercent()))))
				: null;
	}

	private BigDecimal estimatedUpside(BigDecimal fairPriceEstimate, BigDecimal currentPrice) {
		return positive(fairPriceEstimate) && positive(currentPrice)
				? fairPriceEstimate.subtract(currentPrice).divide(currentPrice, 6, RoundingMode.HALF_UP)
						.multiply(ONE_HUNDRED).setScale(6, RoundingMode.HALF_UP)
				: null;
	}

	private BigDecimal capPercent(BigDecimal value, BigDecimal max) {
		if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
			return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
		}
		return value.compareTo(max) > 0 ? max : value.setScale(6, RoundingMode.HALF_UP);
	}

	private BigDecimal percent(BigDecimal percent) {
		return percent.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
	}

	private BigDecimal toPercent(BigDecimal ratio) {
		return ratio == null ? null : ratio.multiply(ONE_HUNDRED).setScale(6, RoundingMode.HALF_UP);
	}

	private BigDecimal minNonNegative(BigDecimal... values) {
		BigDecimal selected = null;
		for (BigDecimal value : values) {
			BigDecimal normalized = value == null || value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
			if (selected == null || normalized.compareTo(selected) < 0) {
				selected = normalized;
			}
		}
		return money(selected == null ? BigDecimal.ZERO : selected);
	}

	private BigDecimal nonNull(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private boolean positive(BigDecimal value) {
		return value != null && value.signum() > 0;
	}

	private BigDecimal money(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
	}

	private BigDecimal money6(BigDecimal value) {
		return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
	}
}
