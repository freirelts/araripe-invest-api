package com.freirelts.araripe_invest_api.application.indicators;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.marketdata.DailyCandle;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.FinancialStatementSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.marketdata.StatementType;
import com.freirelts.araripe_invest_api.domain.marketdata.TechnicalIndicatorSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DailyCandleRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FinancialStatementSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IndicatorCalculationService.class)
class IndicatorCalculationServiceTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@Autowired
	private IndicatorCalculationService service;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private DailyCandleRepository dailyCandleRepository;

	@Autowired
	private TechnicalIndicatorSnapshotRepository technicalIndicatorSnapshotRepository;

	@Autowired
	private FundamentalSnapshotRepository fundamentalSnapshotRepository;

	@Autowired
	private FinancialStatementSnapshotRepository financialStatementSnapshotRepository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void calculatesAndPersistsTechnicalIndicatorsWithKnownPriceSeries() {
		Asset asset = assetRepository.saveAndFlush(new Asset("WEGE3", "WEG S.A.", "Bens Industriais"));
		LocalDate firstDate = LocalDate.of(2025, 10, 23);
		LocalDate referenceDate = firstDate.plusDays(259);
		for (int i = 1; i <= 260; i++) {
			BigDecimal close = BigDecimal.valueOf(i + 10L);
			DailyCandle candle = new DailyCandle(asset, firstDate.plusDays(i - 1L), close.subtract(BigDecimal.ONE),
					close.add(BigDecimal.ONE), close.subtract(TWO()), close, "brapi");
			candle.setVolumeQuantity(new BigDecimal("1000"));
			candle.setVolumeFinancial(close.multiply(new BigDecimal("1000")));
			candle.setQualityStatus(DataQualityStatus.VALID);
			dailyCandleRepository.save(candle);
		}
		dailyCandleRepository.flush();

		IndicatorCalculationResult result = service.calculateForAsset(asset, referenceDate);

		TechnicalIndicatorSnapshot snapshot = technicalIndicatorSnapshotRepository
				.findByAssetIdAndTradeDateAndCalculationVersion(asset.getId(), referenceDate,
						IndicatorCalculationService.CALCULATION_VERSION)
				.orElseThrow();
		assertThat(result.technicalComplete()).isTrue();
		assertThat(snapshot.getSma50()).isEqualByComparingTo("245.500000");
		assertThat(snapshot.getSma100()).isEqualByComparingTo("220.500000");
		assertThat(snapshot.getSma200()).isEqualByComparingTo("170.500000");
		assertThat(snapshot.getReturn6m()).isEqualByComparingTo("0.875000");
		assertThat(snapshot.getReturn12m()).isEqualByComparingTo("14.000000");
		assertThat(snapshot.getAvgVolume60()).isEqualByComparingTo("240500.000000");
		assertThat(snapshot.getHigh52w()).isEqualByComparingTo("271.000000");
		assertThat(snapshot.getLow52w()).isEqualByComparingTo("17.000000");
		assertThat(snapshot.getHistoricalVolatility()).isPositive();
		assertThat(snapshot.getRecentDrawdown()).isEqualByComparingTo("0.000000");
		assertThat(snapshot.getTrendStatus()).isEqualTo(TrendStatus.HEALTHY);
	}

	@Test
	void technicalSnapshotMarksInsufficientDataWhenLongWindowIsMissing() {
		Asset asset = assetRepository.saveAndFlush(new Asset("VALE3", "Vale S.A.", "Materiais Básicos"));
		LocalDate firstDate = LocalDate.of(2026, 1, 1);
		for (int i = 1; i <= 120; i++) {
			BigDecimal close = BigDecimal.valueOf(i + 20L);
			DailyCandle candle = new DailyCandle(asset, firstDate.plusDays(i - 1L), close, close.add(BigDecimal.ONE),
					close.subtract(BigDecimal.ONE), close, "brapi");
			candle.setVolumeQuantity(new BigDecimal("500"));
			candle.setQualityStatus(DataQualityStatus.VALID);
			dailyCandleRepository.save(candle);
		}
		dailyCandleRepository.flush();

		IndicatorCalculationResult result = service.calculateForAsset(asset, firstDate.plusDays(119));

		TechnicalIndicatorSnapshot snapshot = technicalIndicatorSnapshotRepository
				.findByAssetIdAndTradeDateAndCalculationVersion(asset.getId(), firstDate.plusDays(119),
						IndicatorCalculationService.CALCULATION_VERSION)
				.orElseThrow();
		assertThat(result.technicalComplete()).isFalse();
		assertThat(snapshot.getSma200()).isNull();
		assertThat(snapshot.getTrendStatus()).isEqualTo(TrendStatus.INSUFFICIENT_DATA);
	}

	@Test
	void calculatesAndPersistsDerivedFundamentalIndicators() {
		Asset asset = assetRepository.saveAndFlush(new Asset("EGIE3", "Engie Brasil", "Utilidade Pública"));
		LocalDate referenceDate = LocalDate.of(2026, 3, 31);
		FundamentalSnapshot collector = new FundamentalSnapshot(asset, referenceDate, PeriodType.TTM, "brapi");
		collector.setCalculationVersion("collector-v1");
		collector.setMostRecentQuarter(LocalDate.of(2025, 12, 31));
		collector.setTrailingPe(new BigDecimal("9.500000"));
		collector.setPriceToBook(new BigDecimal("1.800000"));
		collector.setEnterpriseToEbitda(new BigDecimal("6.200000"));
		collector.setDividendYield(new BigDecimal("0.080000"));
		collector.setRevenueGrowth(new BigDecimal("0.070000"));
		collector.setEarningsGrowth(new BigDecimal("0.080000"));
		collector.setQualityStatus(DataQualityStatus.VALID);
		fundamentalSnapshotRepository.saveAndFlush(collector);
		saveStatement(asset, StatementType.INCOME_STATEMENT, PeriodType.ANNUAL, LocalDate.of(2025, 12, 31),
				"""
						{"totalRevenue":1200,"netIncome":180,"cleanEbitda":300,"grossProfit":600,"ebit":240}
						""");
		saveStatement(asset, StatementType.INCOME_STATEMENT, PeriodType.ANNUAL, LocalDate.of(2024, 12, 31),
				"""
						{"totalRevenue":1000,"netIncome":150,"cleanEbitda":250,"grossProfit":500,"cleanEbit":200}
						""");
		saveStatement(asset, StatementType.INCOME_STATEMENT, PeriodType.QUARTERLY, LocalDate.of(2026, 3, 31),
				"""
						{"totalRevenue":350,"netIncome":42,"cleanEbitda":80}
						""");
		saveStatement(asset, StatementType.INCOME_STATEMENT, PeriodType.QUARTERLY, LocalDate.of(2025, 12, 31),
				"""
						{"totalRevenue":300,"netIncome":35,"cleanEbitda":70}
						""");
		saveStatement(asset, StatementType.INCOME_STATEMENT, PeriodType.QUARTERLY, LocalDate.of(2025, 3, 31),
				"""
						{"totalRevenue":280,"netIncome":35,"cleanEbitda":64}
						""");
		saveStatement(asset, StatementType.BALANCE_SHEET, PeriodType.ANNUAL, LocalDate.of(2025, 12, 31),
				"""
						{"totalAssets":2000,"shareholdersEquity":900,"loansAndFinancing":100,"longTermLoansAndFinancing":400,"cash":200}
						""");
		saveStatement(asset, StatementType.CASH_FLOW, PeriodType.ANNUAL, LocalDate.of(2025, 12, 31),
				"""
						{"operatingCashFlow":260,"freeCashFlow":200}
						""");

		IndicatorCalculationResult result = service.calculateForAsset(asset, referenceDate);

		FundamentalSnapshot snapshot = fundamentalSnapshotRepository
				.findByAssetIdAndReferenceDateAndPeriodTypeAndSourceAndCalculationVersion(asset.getId(), referenceDate,
						PeriodType.TTM, "araripe-indicators", IndicatorCalculationService.CALCULATION_VERSION)
				.orElseThrow();
		assertThat(result.fundamentalComplete()).isTrue();
		assertThat(snapshot.getMostRecentQuarter()).isEqualTo(LocalDate.of(2026, 3, 31));
		assertThat(snapshot.getTrailingPe()).isEqualByComparingTo("9.500000");
		assertThat(snapshot.getRevenueGrowth()).isEqualByComparingTo("0.070000");
		assertThat(snapshot.getEarningsGrowth()).isEqualByComparingTo("0.080000");
		assertThat(snapshot.getAnnualRevenueGrowth()).isEqualByComparingTo("0.200000");
		assertThat(snapshot.getQuarterlyRevenueGrowth()).isEqualByComparingTo("0.250000");
		assertThat(snapshot.getAnnualEarningsGrowth()).isEqualByComparingTo("0.200000");
		assertThat(snapshot.getQuarterlyEarningsGrowth()).isEqualByComparingTo("0.200000");
		assertThat(snapshot.getEbitdaGrowth()).isEqualByComparingTo("0.200000");
		assertThat(snapshot.getGrossMargin()).isEqualByComparingTo("0.500000");
		assertThat(snapshot.getEbitdaMargin()).isEqualByComparingTo("0.250000");
		assertThat(snapshot.getOperatingMargin()).isEqualByComparingTo("0.200000");
		assertThat(snapshot.getProfitMargin()).isEqualByComparingTo("0.150000");
		assertThat(snapshot.getRoe()).isEqualByComparingTo("0.200000");
		assertThat(snapshot.getRoa()).isEqualByComparingTo("0.090000");
		assertThat(snapshot.getDebtToEquity()).isEqualByComparingTo("0.555556");
		assertThat(snapshot.getNetDebt()).isEqualByComparingTo("300.000000");
		assertThat(snapshot.getOperatingCashflow()).isEqualByComparingTo("260.000000");
		assertThat(snapshot.getFreeCashflow()).isEqualByComparingTo("200.000000");
		assertThat(snapshot.getQualityStatus()).isEqualTo(DataQualityStatus.VALID);
		assertThat(snapshot.getAssumptionsJson()).contains("Fluxo de caixa livre");
		assertThat(snapshot.getAssumptionsJson()).contains("brapi");
	}

	@Test
	void preservesCollectedCurrentFieldsWhenDerivedStatementValuesDiffer() {
		Asset asset = assetRepository.saveAndFlush(new Asset("PRES3", "Preserva S.A.", "Consumo"));
		LocalDate referenceDate = LocalDate.of(2026, 3, 31);
		FundamentalSnapshot collector = new FundamentalSnapshot(asset, referenceDate, PeriodType.TTM, "brapi");
		collector.setCalculationVersion("collector-v1");
		collector.setTrailingPe(new BigDecimal("9.500000"));
		collector.setPriceToBook(new BigDecimal("1.800000"));
		collector.setEnterpriseToEbitda(new BigDecimal("6.200000"));
		collector.setEarningsPerShare(new BigDecimal("1.000000"));
		collector.setProfitMargin(new BigDecimal("0.110000"));
		collector.setGrossMargin(new BigDecimal("0.440000"));
		collector.setEbitdaMargin(new BigDecimal("0.220000"));
		collector.setOperatingMargin(new BigDecimal("0.180000"));
		collector.setRoe(new BigDecimal("0.160000"));
		collector.setRoa(new BigDecimal("0.070000"));
		collector.setDebtToEquity(new BigDecimal("0.400000"));
		collector.setOperatingCashflow(new BigDecimal("900.000000"));
		collector.setFreeCashflow(new BigDecimal("700.000000"));
		collector.setNetDebt(new BigDecimal("1200.000000"));
		collector.setRevenueGrowth(new BigDecimal("0.030000"));
		collector.setEarningsGrowth(new BigDecimal("0.040000"));
		collector.setQualityStatus(DataQualityStatus.VALID);
		fundamentalSnapshotRepository.saveAndFlush(collector);
		saveStatement(asset, StatementType.INCOME_STATEMENT, PeriodType.ANNUAL, LocalDate.of(2025, 12, 31),
				"""
						{"totalRevenue":1000,"netIncome":250,"cleanEbitda":350,"grossProfit":600,"ebit":300}
						""");
		saveStatement(asset, StatementType.INCOME_STATEMENT, PeriodType.ANNUAL, LocalDate.of(2024, 12, 31),
				"""
						{"totalRevenue":800,"netIncome":200,"cleanEbitda":280,"grossProfit":480,"ebit":240}
						""");
		saveStatement(asset, StatementType.BALANCE_SHEET, PeriodType.ANNUAL, LocalDate.of(2025, 12, 31),
				"""
						{"totalAssets":2000,"shareholdersEquity":1000,"totalDebt":800,"cash":100}
						""");
		saveStatement(asset, StatementType.CASH_FLOW, PeriodType.ANNUAL, LocalDate.of(2025, 12, 31),
				"""
						{"operatingCashFlow":300,"freeCashFlow":200}
						""");

		service.calculateForAsset(asset, referenceDate);

		FundamentalSnapshot snapshot = fundamentalSnapshotRepository
				.findByAssetIdAndReferenceDateAndPeriodTypeAndSourceAndCalculationVersion(asset.getId(), referenceDate,
						PeriodType.TTM, "araripe-indicators", IndicatorCalculationService.CALCULATION_VERSION)
				.orElseThrow();
		assertThat(snapshot.getProfitMargin()).isEqualByComparingTo("0.110000");
		assertThat(snapshot.getGrossMargin()).isEqualByComparingTo("0.440000");
		assertThat(snapshot.getEbitdaMargin()).isEqualByComparingTo("0.220000");
		assertThat(snapshot.getOperatingMargin()).isEqualByComparingTo("0.180000");
		assertThat(snapshot.getRoe()).isEqualByComparingTo("0.160000");
		assertThat(snapshot.getRoa()).isEqualByComparingTo("0.070000");
		assertThat(snapshot.getDebtToEquity()).isEqualByComparingTo("0.400000");
		assertThat(snapshot.getOperatingCashflow()).isEqualByComparingTo("900.000000");
		assertThat(snapshot.getFreeCashflow()).isEqualByComparingTo("700.000000");
		assertThat(snapshot.getNetDebt()).isEqualByComparingTo("1200.000000");
		assertThat(snapshot.getRevenueGrowth()).isEqualByComparingTo("0.030000");
		assertThat(snapshot.getEarningsGrowth()).isEqualByComparingTo("0.040000");
		assertThat(snapshot.getAnnualRevenueGrowth()).isEqualByComparingTo("0.250000");
		assertThat(snapshot.getAnnualEarningsGrowth()).isEqualByComparingTo("0.250000");
	}

	@Test
	void doesNotUseUnsafePercentageGrowthWhenPreviousResultWasNegative() {
		Asset asset = assetRepository.saveAndFlush(new Asset("TURN3", "Turnaround S.A.", "Consumo"));
		LocalDate referenceDate = LocalDate.of(2026, 3, 31);
		FundamentalSnapshot collector = new FundamentalSnapshot(asset, referenceDate, PeriodType.TTM, "brapi");
		collector.setCalculationVersion("collector-v1");
		collector.setTrailingPe(new BigDecimal("9.500000"));
		collector.setPriceToBook(new BigDecimal("1.800000"));
		collector.setEnterpriseToEbitda(new BigDecimal("6.200000"));
		collector.setEarningsPerShare(new BigDecimal("1.000000"));
		collector.setQualityStatus(DataQualityStatus.VALID);
		fundamentalSnapshotRepository.saveAndFlush(collector);
		saveStatement(asset, StatementType.INCOME_STATEMENT, PeriodType.ANNUAL, LocalDate.of(2025, 12, 31),
				"""
						{"totalRevenue":1200,"netIncome":20,"cleanEbitda":60,"grossProfit":600,"ebit":40}
						""");
		saveStatement(asset, StatementType.INCOME_STATEMENT, PeriodType.ANNUAL, LocalDate.of(2024, 12, 31),
				"""
						{"totalRevenue":1000,"netIncome":-100,"cleanEbitda":-80,"grossProfit":500,"ebit":-90}
						""");
		saveStatement(asset, StatementType.BALANCE_SHEET, PeriodType.ANNUAL, LocalDate.of(2025, 12, 31),
				"""
						{"totalAssets":2000,"shareholdersEquity":900,"totalDebt":300,"cash":100}
						""");
		saveStatement(asset, StatementType.CASH_FLOW, PeriodType.ANNUAL, LocalDate.of(2025, 12, 31),
				"""
						{"operatingCashFlow":120,"freeCashFlow":80}
						""");

		service.calculateForAsset(asset, referenceDate);

		FundamentalSnapshot snapshot = fundamentalSnapshotRepository
				.findByAssetIdAndReferenceDateAndPeriodTypeAndSourceAndCalculationVersion(asset.getId(), referenceDate,
						PeriodType.TTM, "araripe-indicators", IndicatorCalculationService.CALCULATION_VERSION)
				.orElseThrow();
		assertThat(snapshot.getAnnualEarningsGrowth()).isEqualByComparingTo("0.000000");
		assertThat(snapshot.getEbitdaGrowth()).isEqualByComparingTo("0.000000");
		assertThat(snapshot.getAssumptionsJson()).contains("base negativa");
	}

	@Test
	void ignoresIncompleteCollectedSnapshotAndClearsPreviouslyCopiedFields() {
		Asset asset = assetRepository.saveAndFlush(new Asset("BAD3", "Dados Incompletos S.A.", "Consumo"));
		LocalDate referenceDate = LocalDate.of(2026, 3, 31);
		FundamentalSnapshot previousDerived = new FundamentalSnapshot(asset, referenceDate, PeriodType.TTM,
				"araripe-indicators");
		previousDerived.setCalculationVersion(IndicatorCalculationService.CALCULATION_VERSION);
		previousDerived.setTrailingPe(new BigDecimal("9.500000"));
		previousDerived.setOperatingCashflow(new BigDecimal("500.000000"));
		previousDerived.setQualityStatus(DataQualityStatus.VALID);
		fundamentalSnapshotRepository.saveAndFlush(previousDerived);
		FundamentalSnapshot incompleteCollector = new FundamentalSnapshot(asset, referenceDate, PeriodType.TTM,
				"brapi");
		incompleteCollector.setCalculationVersion("collector-v1");
		incompleteCollector.setTrailingPe(new BigDecimal("8.000000"));
		incompleteCollector.setOperatingCashflow(new BigDecimal("300.000000"));
		incompleteCollector.setQualityStatus(DataQualityStatus.INCOMPLETE);
		fundamentalSnapshotRepository.saveAndFlush(incompleteCollector);

		service.calculateForAsset(asset, referenceDate);

		FundamentalSnapshot snapshot = fundamentalSnapshotRepository
				.findByAssetIdAndReferenceDateAndPeriodTypeAndSourceAndCalculationVersion(asset.getId(), referenceDate,
						PeriodType.TTM, "araripe-indicators", IndicatorCalculationService.CALCULATION_VERSION)
				.orElseThrow();
		assertThat(snapshot.getTrailingPe()).isNull();
		assertThat(snapshot.getOperatingCashflow()).isNull();
		assertThat(snapshot.getQualityStatus()).isEqualTo(DataQualityStatus.INCOMPLETE);
	}

	@Test
	void fundamentalSnapshotIsIncompleteWithoutMinimumInputs() {
		Asset asset = assetRepository.saveAndFlush(new Asset("ABCD3", "Companhia Incompleta", "Consumo"));
		LocalDate referenceDate = LocalDate.of(2026, 3, 31);

		IndicatorCalculationResult result = service.calculateForAsset(asset, referenceDate);

		FundamentalSnapshot snapshot = fundamentalSnapshotRepository
				.findByAssetIdAndReferenceDateAndPeriodTypeAndSourceAndCalculationVersion(asset.getId(), referenceDate,
						PeriodType.TTM, "araripe-indicators", IndicatorCalculationService.CALCULATION_VERSION)
				.orElseThrow();
		assertThat(result.fundamentalComplete()).isFalse();
		assertThat(snapshot.getQualityStatus()).isEqualTo(DataQualityStatus.INCOMPLETE);
		assertThat(snapshot.getMissingFieldsJson()).contains("minimumFundamentalSet");
	}

	private void saveStatement(Asset asset, StatementType statementType, PeriodType periodType, LocalDate endDate,
			String payloadJson) {
		FinancialStatementSnapshot snapshot = new FinancialStatementSnapshot(asset, statementType, periodType, endDate,
				"brapi", payloadJson);
		snapshot.setQualityStatus(DataQualityStatus.VALID);
		financialStatementSnapshotRepository.saveAndFlush(snapshot);
	}

	private BigDecimal TWO() {
		return new BigDecimal("2");
	}
}
