package com.freirelts.araripe_invest_api.application.thesis;

import com.freirelts.araripe_invest_api.application.indicators.IndicatorCalculationService;
import com.freirelts.araripe_invest_api.application.risk.RiskAllocationService;
import com.freirelts.araripe_invest_api.application.scoring.ScoringService;
import com.freirelts.araripe_invest_api.application.screening.EliminatoryFilterEvaluator;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.marketdata.DailyCandle;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.DividendEvent;
import com.freirelts.araripe_invest_api.domain.marketdata.DividendEventType;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.marketdata.TechnicalIndicatorSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AllocationPlanRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DailyCandleRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DividendEventRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.TechnicalIndicatorSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PositionThesisGenerationService.class, EliminatoryFilterEvaluator.class, ScoringService.class,
		RiskAllocationService.class })
class PositionThesisGenerationServiceTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@Autowired
	private PositionThesisGenerationService service;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private DailyCandleRepository dailyCandleRepository;

	@Autowired
	private TechnicalIndicatorSnapshotRepository technicalIndicatorSnapshotRepository;

	@Autowired
	private FundamentalSnapshotRepository fundamentalSnapshotRepository;

	@Autowired
	private DividendEventRepository dividendEventRepository;

	@Autowired
	private PositionThesisRepository positionThesisRepository;

	@Autowired
	private AllocationPlanRepository allocationPlanRepository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void generatesThreeActionableMvpThesesWithValuationRiskAndAllocation() {
		LocalDate referenceDate = LocalDate.of(2026, 7, 10);
		Asset asset = assetRepository.saveAndFlush(new Asset("WEGE3", "WEG S.A.", "Bens Industriais"));
		saveValidCandle(asset, referenceDate, new BigDecimal("20.00"));
		saveTechnical(asset, referenceDate, TrendStatus.HEALTHY);
		saveStrongFundamental(asset, referenceDate);
		saveDividend(asset, LocalDate.of(2025, 4, 1));
		saveDividend(asset, LocalDate.of(2026, 4, 1));

		List<PositionThesis> theses = service.generateForAsset(asset, referenceDate);
		service.generateForAsset(asset, referenceDate);

		assertThat(theses).hasSize(3);
		assertThat(positionThesisRepository.findAll()).hasSize(3);
		assertThat(allocationPlanRepository.findAll()).hasSize(3);
		assertThat(theses).allSatisfy(thesis -> {
			assertThat(thesis.getStatus()).isEqualTo(ThesisStatus.APORTE_PLANEJADO);
			assertThat(thesis.getFairPriceEstimate()).isPositive();
			assertThat(thesis.getPriceCeiling()).isPositive();
			assertThat(thesis.getSafetyMarginPercent()).isGreaterThanOrEqualTo(new BigDecimal("0.150000"));
			assertThat(thesis.getStopPrice()).isPositive();
			assertThat(thesis.getTargetPrice()).isPositive();
			assertThat(thesis.getRuleVersion()).isEqualTo(ScoringService.RULE_VERSION);
			assertThat(thesis.getScore()).isBetween(0, 100);
			assertThat(thesis.getScoreBreakdownJson()).contains("FUNDAMENTAL_QUALITY",
					"VALUATION_SAFETY_MARGIN", "CASH_GENERATION", "RISK_VOLATILITY",
					"MACRO_SECTOR_CONTEXT");
			assertThat(thesis.getReasonsJson()).contains("ENTRY_ZONE");
			assertThat(thesis.getReviewPointsJson()).contains("preco", "fundamentos", "tendencia");
			assertThat(allocationPlanRepository.findByThesisId(thesis.getId()).orElseThrow().isValid()).isTrue();
			assertThat(allocationPlanRepository.findByThesisId(thesis.getId()).orElseThrow().getSuggestedQuantity())
					.isEqualTo(50);
			assertThat(allocationPlanRepository.findByThesisId(thesis.getId()).orElseThrow()
					.getMaxAllocationPerSectorPercent()).isEqualByComparingTo("25.000000");
			assertThat(allocationPlanRepository.findByThesisId(thesis.getId()).orElseThrow()
					.getMinimumCashReservePercent()).isEqualByComparingTo("10.000000");
			assertThat(allocationPlanRepository.findByThesisId(thesis.getId()).orElseThrow().getFirstTrancheValue())
					.isEqualByComparingTo("500.00");
			assertThat(allocationPlanRepository.findByThesisId(thesis.getId()).orElseThrow().getSecondTrancheValue())
					.isEqualByComparingTo("250.00");
			assertThat(allocationPlanRepository.findByThesisId(thesis.getId()).orElseThrow().getThirdTrancheValue())
					.isEqualByComparingTo("250.00");
			assertThat(allocationPlanRepository.findByThesisId(thesis.getId()).orElseThrow().getStopPrice())
					.isEqualByComparingTo(thesis.getStopPrice());
			assertThat(allocationPlanRepository.findByThesisId(thesis.getId()).orElseThrow().getTargetPrice())
					.isEqualByComparingTo(thesis.getTargetPrice());
		});
	}

	@Test
	void persistsBlockedAllocationPlanWhenPriceIsAboveCeiling() {
		LocalDate referenceDate = LocalDate.of(2026, 7, 10);
		Asset asset = assetRepository.saveAndFlush(new Asset("CARO3", "Companhia Cara", "Consumo"));
		saveValidCandle(asset, referenceDate, new BigDecimal("34.50"));
		saveTechnical(asset, referenceDate, TrendStatus.HEALTHY);
		saveStrongFundamental(asset, referenceDate);
		saveDividend(asset, LocalDate.of(2025, 4, 1));
		saveDividend(asset, LocalDate.of(2026, 4, 1));

		service.generateForAsset(asset, referenceDate);

		PositionThesis qualityThesis = positionThesisRepository
				.findByAssetIdAndReferenceDateAndThesisTypeAndRuleVersion(asset.getId(), referenceDate,
						ThesisType.QUALITY_REASONABLE_PRICE, PositionThesisGenerationService.RULE_VERSION)
				.orElseThrow();
		assertThat(allocationPlanRepository.findByThesisId(qualityThesis.getId())).hasValueSatisfying(plan -> {
			assertThat(plan.isValid()).isFalse();
			assertThat(plan.getSuggestedQuantity()).isZero();
			assertThat(plan.getInvalidReason()).contains("preco teto");
		});
	}

	@Test
	void exposesPersistedScoreBreakdownInAssetDiagnostic() {
		LocalDate referenceDate = LocalDate.of(2026, 7, 10);
		Asset asset = assetRepository.saveAndFlush(new Asset("RADL3", "Raia Drogasil", "Saude"));
		saveValidCandle(asset, referenceDate, new BigDecimal("20.00"));
		saveTechnical(asset, referenceDate, TrendStatus.HEALTHY);
		saveStrongFundamental(asset, referenceDate);
		saveDividend(asset, LocalDate.of(2025, 4, 1));
		saveDividend(asset, LocalDate.of(2026, 4, 1));

		service.generateForAsset(asset, referenceDate);

		assertThat(positionThesisRepository
				.findByAssetIdAndReferenceDateAndRuleVersionOrderByScoreDesc(asset.getId(), referenceDate,
						PositionThesisGenerationService.RULE_VERSION))
				.hasSize(3)
				.allSatisfy(thesis -> assertThat(thesis.getScoreBreakdownJson()).contains("components",
						"weightedPoints"));
	}

	@Test
	void sustainableDividendThesisIsNotApprovedWithOnlyOneDividendEvent() {
		LocalDate referenceDate = LocalDate.of(2026, 7, 10);
		Asset asset = assetRepository.saveAndFlush(new Asset("EGIE3", "Engie Brasil", "Utilidade Publica"));
		saveValidCandle(asset, referenceDate, new BigDecimal("20.00"));
		saveTechnical(asset, referenceDate, TrendStatus.HEALTHY);
		saveStrongFundamental(asset, referenceDate);
		saveDividend(asset, LocalDate.of(2026, 4, 1));

		service.generateForAsset(asset, referenceDate);

		PositionThesis dividendThesis = positionThesisRepository
				.findByAssetIdAndReferenceDateAndThesisTypeAndRuleVersion(asset.getId(), referenceDate,
						ThesisType.SUSTAINABLE_DIVIDENDS, PositionThesisGenerationService.RULE_VERSION)
				.orElseThrow();
		assertThat(dividendThesis.getStatus()).isNotEqualTo(ThesisStatus.APORTE_PLANEJADO);
		assertThat(dividendThesis.getReasonsJson()).contains("DIVIDEND_RECURRENCE_FAILED");
	}

	@Test
	void mapsExtremeVolatilityToReduceExposureStatus() {
		LocalDate referenceDate = LocalDate.of(2026, 7, 10);
		Asset asset = assetRepository.saveAndFlush(new Asset("VOL3", "Volatilidade Alta", "Consumo"));
		saveValidCandle(asset, referenceDate, new BigDecimal("20.00"));
		saveTechnical(asset, referenceDate, TrendStatus.HEALTHY, new BigDecimal("0.700000"));
		saveStrongFundamental(asset, referenceDate);

		List<PositionThesis> theses = service.generateForAsset(asset, referenceDate);

		assertThat(theses)
				.allSatisfy(thesis -> assertThat(thesis.getStatus()).isEqualTo(ThesisStatus.REDUZIR_EXPOSICAO));
	}

	@Test
	void mapsStrongEarningsDeteriorationToReduceExposureWithoutExitThesis() {
		LocalDate referenceDate = LocalDate.of(2026, 7, 10);
		Asset asset = assetRepository.saveAndFlush(new Asset("LUCRO3", "Lucro Pressionado", "Energia"));
		saveValidCandle(asset, referenceDate, new BigDecimal("20.00"));
		saveTechnical(asset, referenceDate, TrendStatus.HEALTHY);
		FundamentalSnapshot snapshot = saveStrongFundamental(asset, referenceDate);
		snapshot.setRevenueGrowth(new BigDecimal("0.015785"));
		snapshot.setAnnualRevenueGrowth(new BigDecimal("0.036844"));
		snapshot.setEarningsGrowth(new BigDecimal("-0.542911"));
		snapshot.setAnnualEarningsGrowth(new BigDecimal("-0.562737"));
		snapshot.setProfitMargin(new BigDecimal("0.064399"));
		fundamentalSnapshotRepository.saveAndFlush(snapshot);

		List<PositionThesis> theses = service.generateForAsset(asset, referenceDate);

		assertThat(theses).allSatisfy(thesis -> {
			assertThat(thesis.getStatus()).isEqualTo(ThesisStatus.REDUZIR_EXPOSICAO);
			assertThat(thesis.getFailedFiltersJson()).contains("STRONG_EARNINGS_DETERIORATION")
				.doesNotContain("STRONG_REVENUE_DETERIORATION", "NEGATIVE_PROFIT_MARGIN");
		});
	}

	private void saveValidCandle(Asset asset, LocalDate referenceDate, BigDecimal close) {
		DailyCandle candle = new DailyCandle(asset, referenceDate, close, close.add(BigDecimal.ONE),
				close.subtract(BigDecimal.ONE), close, "brapi");
		candle.setAdjustedClosePrice(close);
		candle.setVolumeFinancial(new BigDecimal("10000000"));
		candle.setQualityStatus(DataQualityStatus.VALID);
		dailyCandleRepository.saveAndFlush(candle);
	}

	private void saveTechnical(Asset asset, LocalDate referenceDate, TrendStatus trendStatus) {
		saveTechnical(asset, referenceDate, trendStatus, new BigDecimal("0.250000"));
	}

	private void saveTechnical(Asset asset, LocalDate referenceDate, TrendStatus trendStatus,
			BigDecimal historicalVolatility) {
		TechnicalIndicatorSnapshot snapshot = new TechnicalIndicatorSnapshot(asset, referenceDate,
				IndicatorCalculationService.CALCULATION_VERSION);
		snapshot.setAvgVolume60(new BigDecimal("10000000"));
		snapshot.setSma200(new BigDecimal("18.00"));
		snapshot.setReturn12m(new BigDecimal("0.120000"));
		snapshot.setHistoricalVolatility(historicalVolatility);
		snapshot.setRecentDrawdown(new BigDecimal("-0.100000"));
		snapshot.setTrendStatus(trendStatus);
		technicalIndicatorSnapshotRepository.saveAndFlush(snapshot);
	}

	private FundamentalSnapshot saveStrongFundamental(Asset asset, LocalDate referenceDate) {
		FundamentalSnapshot snapshot = new FundamentalSnapshot(asset, referenceDate, PeriodType.TTM,
				"araripe-indicators");
		snapshot.setCalculationVersion(IndicatorCalculationService.CALCULATION_VERSION);
		snapshot.setQualityStatus(DataQualityStatus.VALID);
		snapshot.setTrailingPe(new BigDecimal("6.666667"));
		snapshot.setPriceToBook(new BigDecimal("1.333333"));
		snapshot.setEnterpriseToEbitda(new BigDecimal("8.000000"));
		snapshot.setEarningsPerShare(new BigDecimal("3.000000"));
		snapshot.setBookValue(new BigDecimal("15.000000"));
		snapshot.setDividendYield(new BigDecimal("0.080000"));
		snapshot.setProfitMargin(new BigDecimal("0.180000"));
		snapshot.setGrossMargin(new BigDecimal("0.420000"));
		snapshot.setEbitdaMargin(new BigDecimal("0.250000"));
		snapshot.setOperatingMargin(new BigDecimal("0.220000"));
		snapshot.setRoe(new BigDecimal("0.180000"));
		snapshot.setRoa(new BigDecimal("0.100000"));
		snapshot.setDebtToEquity(new BigDecimal("0.500000"));
		snapshot.setRevenueGrowth(new BigDecimal("0.120000"));
		snapshot.setEarningsGrowth(new BigDecimal("0.150000"));
		snapshot.setAnnualRevenueGrowth(new BigDecimal("0.150000"));
		snapshot.setQuarterlyRevenueGrowth(new BigDecimal("0.100000"));
		snapshot.setAnnualEarningsGrowth(new BigDecimal("0.160000"));
		snapshot.setQuarterlyEarningsGrowth(new BigDecimal("0.110000"));
		snapshot.setEbitdaGrowth(new BigDecimal("0.180000"));
		snapshot.setOperatingCashflow(new BigDecimal("500.000000"));
		snapshot.setFreeCashflow(new BigDecimal("300.000000"));
		return fundamentalSnapshotRepository.saveAndFlush(snapshot);
	}

	private void saveDividend(Asset asset, LocalDate lastDatePrior) {
		DividendEvent event = new DividendEvent(asset, DividendEventType.DIVIDEND, lastDatePrior,
				lastDatePrior.plusMonths(1), "brapi");
		event.setRate(new BigDecimal("0.80000000"));
		dividendEventRepository.saveAndFlush(event);
	}
}
