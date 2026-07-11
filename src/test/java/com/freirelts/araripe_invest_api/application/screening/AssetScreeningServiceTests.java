package com.freirelts.araripe_invest_api.application.screening;

import com.freirelts.araripe_invest_api.application.indicators.IndicatorCalculationService;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.marketdata.DailyCandle;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.marketdata.TechnicalIndicatorSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.TrendStatus;
import com.freirelts.araripe_invest_api.domain.screening.ScreeningStatus;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetScreeningResultRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DailyCandleRepository;
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
@Import({ AssetScreeningService.class, EliminatoryFilterEvaluator.class })
class AssetScreeningServiceTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@Autowired
	private AssetScreeningService service;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private DailyCandleRepository dailyCandleRepository;

	@Autowired
	private TechnicalIndicatorSnapshotRepository technicalIndicatorSnapshotRepository;

	@Autowired
	private FundamentalSnapshotRepository fundamentalSnapshotRepository;

	@Autowired
	private AssetScreeningResultRepository assetScreeningResultRepository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void diagnosesAndPersistsEliminatoryFilterReasonsByAssetAndDate() {
		LocalDate referenceDate = LocalDate.of(2026, 7, 10);
		Asset asset = assetRepository.saveAndFlush(new Asset("WEGE3", "WEG S.A.", "Bens Industriais"));
		saveValidCandle(asset, referenceDate, new BigDecimal("20.00"));
		saveTechnical(asset, referenceDate, new BigDecimal("4999999.99"));
		saveFundamental(asset, referenceDate);

		AssetScreeningDiagnostic diagnostic = service.diagnoseAsset("wege3", referenceDate);

		assertThat(diagnostic.status()).isEqualTo(ScreeningStatus.ELIMINATED);
		assertThat(diagnostic.failedFilters()).extracting(EliminatoryFilterReason::code)
				.containsExactly(EliminatoryFilterCode.INSUFFICIENT_LIQUIDITY);
		assertThat(assetScreeningResultRepository
				.findByAssetIdAndReferenceDateAndRuleVersion(asset.getId(), referenceDate,
						AssetScreeningService.RULE_VERSION)
				.orElseThrow()
				.getFailedFiltersJson()).contains("INSUFFICIENT_LIQUIDITY");
		assertThat(assetScreeningResultRepository
				.findByReferenceDateAndRuleVersionAndStatus(referenceDate, AssetScreeningService.RULE_VERSION,
						ScreeningStatus.ELIGIBLE))
				.isEmpty();
	}

	private void saveValidCandle(Asset asset, LocalDate referenceDate, BigDecimal close) {
		DailyCandle candle = new DailyCandle(asset, referenceDate, close, close.add(BigDecimal.ONE),
				close.subtract(BigDecimal.ONE), close, "brapi");
		candle.setQualityStatus(DataQualityStatus.VALID);
		candle.setVolumeFinancial(new BigDecimal("10000000"));
		dailyCandleRepository.saveAndFlush(candle);
	}

	private void saveTechnical(Asset asset, LocalDate referenceDate, BigDecimal averageFinancialVolume60) {
		TechnicalIndicatorSnapshot snapshot = new TechnicalIndicatorSnapshot(asset, referenceDate,
				IndicatorCalculationService.CALCULATION_VERSION);
		snapshot.setAvgVolume60(averageFinancialVolume60);
		snapshot.setSma200(new BigDecimal("18.00"));
		snapshot.setReturn12m(new BigDecimal("0.10"));
		snapshot.setHistoricalVolatility(new BigDecimal("0.25"));
		snapshot.setRecentDrawdown(new BigDecimal("-0.10"));
		snapshot.setTrendStatus(TrendStatus.HEALTHY);
		technicalIndicatorSnapshotRepository.saveAndFlush(snapshot);
	}

	private void saveFundamental(Asset asset, LocalDate referenceDate) {
		FundamentalSnapshot snapshot = new FundamentalSnapshot(asset, referenceDate, PeriodType.TTM,
				"araripe-indicators");
		snapshot.setCalculationVersion(IndicatorCalculationService.CALCULATION_VERSION);
		snapshot.setQualityStatus(DataQualityStatus.VALID);
		snapshot.setTrailingPe(new BigDecimal("12.00"));
		snapshot.setPriceToBook(new BigDecimal("2.00"));
		snapshot.setEnterpriseToEbitda(new BigDecimal("8.00"));
		snapshot.setEarningsPerShare(new BigDecimal("2.00"));
		snapshot.setProfitMargin(new BigDecimal("0.12"));
		snapshot.setOperatingCashflow(new BigDecimal("100"));
		snapshot.setFreeCashflow(new BigDecimal("50"));
		snapshot.setDebtToEquity(new BigDecimal("0.80"));
		snapshot.setNetDebt(new BigDecimal("100"));
		snapshot.setRevenueGrowth(new BigDecimal("0.05"));
		snapshot.setEarningsGrowth(new BigDecimal("0.05"));
		fundamentalSnapshotRepository.saveAndFlush(snapshot);
	}
}
