package com.freirelts.araripe_invest_api.application.jobs;

import com.freirelts.araripe_invest_api.application.ai.EconomicContextAnalysisService;
import com.freirelts.araripe_invest_api.application.indicators.IndicatorCalculationService;
import com.freirelts.araripe_invest_api.application.marketdata.MarketDataCollectionService;
import com.freirelts.araripe_invest_api.application.notifications.NotificationDigestService;
import com.freirelts.araripe_invest_api.application.recommendations.PositionRecommendationService;
import com.freirelts.araripe_invest_api.application.screening.AssetScreeningService;
import com.freirelts.araripe_invest_api.application.thesis.PositionThesisGenerationService;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.jobs.JobName;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunStatus;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunTrigger;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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
import static org.mockito.Mockito.mock;

@Testcontainers
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ OperationalJobService.class, IndicatorCalculationService.class,
		OperationalJobServiceIndicatorCalculationTests.MockedJobDependencies.class })
class OperationalJobServiceIndicatorCalculationTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 13);

	@Autowired
	private OperationalJobService service;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private FundamentalSnapshotRepository fundamentalSnapshotRepository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void indicatorCalculationJobBuildsDerivedFundamentalsFromCollectedBrapiFields() {
		Asset asset = assetRepository.saveAndFlush(new Asset("VALE3", "Vale S.A.", "Materiais Básicos"));
		FundamentalSnapshot collector = new FundamentalSnapshot(asset, REFERENCE_DATE, PeriodType.TTM, "brapi");
		collector.setCalculationVersion("collector-v1");
		collector.setMostRecentQuarter(LocalDate.of(2026, 3, 31));
		collector.setMarketCap(new BigDecimal("329296900000"));
		collector.setEnterpriseValue(new BigDecimal("496444900000"));
		collector.setTrailingPe(new BigDecimal("23.798286"));
		collector.setPriceToBook(new BigDecimal("1.7221198"));
		collector.setEnterpriseToRevenue(new BigDecimal("2.3105075"));
		collector.setEnterpriseToEbitda(new BigDecimal("9.660904"));
		collector.setEarningsPerShare(new BigDecimal("3.117031"));
		collector.setBookValue(new BigDecimal("43.074818"));
		collector.setDividendYield(new BigDecimal("0.070000"));
		collector.setBeta(new BigDecimal("0.7609478"));
		collector.setFloatShares(new BigDecimal("4268646700"));
		collector.setSharesOutstanding(new BigDecimal("4439160000"));
		collector.setFiftyTwoWeekChange(new BigDecimal("0.4846645"));
		collector.setTotalCash(new BigDecimal("27552000000"));
		collector.setEbitda(new BigDecimal("51387000000"));
		collector.setTotalDebt(new BigDecimal("194700000000"));
		collector.setTotalRevenue(new BigDecimal("214864000000"));
		collector.setGrossProfits(new BigDecimal("75365000000"));
		collector.setProfitMargin(new BigDecimal("0.06439888"));
		collector.setGrossMargin(new BigDecimal("0.35075676"));
		collector.setEbitdaMargin(new BigDecimal("0.23916058"));
		collector.setOperatingMargin(new BigDecimal("0.15708075"));
		collector.setRoe(new BigDecimal("0.07236319"));
		collector.setRoa(new BigDecimal("0.030266441"));
		collector.setDebtToEquity(new BigDecimal("1.0182202"));
		collector.setRevenueGrowth(new BigDecimal("0.015785368"));
		collector.setQuarterlyRevenueGrowth(new BigDecimal("0.015785368"));
		collector.setEarningsGrowth(new BigDecimal("-0.54291093"));
		collector.setQuarterlyEarningsGrowth(new BigDecimal("0.24788938"));
		collector.setAnnualRevenueGrowth(new BigDecimal("0.036843766"));
		collector.setAnnualEarningsGrowth(new BigDecimal("-0.5627374"));
		collector.setFreeCashflow(new BigDecimal("9223999000"));
		collector.setOperatingCashflow(new BigDecimal("48816000000"));
		collector.setNetDebt(new BigDecimal("167148000000"));
		collector.setQualityStatus(DataQualityStatus.VALID);
		fundamentalSnapshotRepository.saveAndFlush(collector);

		JobRunResult result = service.execute(JobName.INDICATOR_CALCULATION, REFERENCE_DATE,
				JobRunTrigger.MANUAL, null);

		FundamentalSnapshot derived = fundamentalSnapshotRepository
				.findByAssetIdAndReferenceDateAndPeriodTypeAndSourceAndCalculationVersion(asset.getId(),
						REFERENCE_DATE, PeriodType.TTM, "araripe-indicators",
						IndicatorCalculationService.CALCULATION_VERSION)
				.orElseThrow();
		assertThat(result.status()).isEqualTo(JobRunStatus.SUCCESS);
		assertThat(result.summary()).containsEntry("indicatorResults", 1);
		assertThat(derived.getQualityStatus()).isEqualTo(DataQualityStatus.VALID);
		assertThat(derived.getAnnualRevenueGrowth()).isEqualByComparingTo("0.036843766");
		assertThat(derived.getQuarterlyRevenueGrowth()).isEqualByComparingTo("0.015785368");
		assertThat(derived.getAnnualEarningsGrowth()).isEqualByComparingTo("-0.5627374");
		assertThat(derived.getQuarterlyEarningsGrowth()).isEqualByComparingTo("0.24788938");
		assertThat(derived.getNetDebt()).isEqualByComparingTo("167148000000");
		assertThat(derived.getTotalRevenue()).isEqualByComparingTo("214864000000");
		assertThat(derived.getTotalDebt()).isEqualByComparingTo("194700000000");
		assertThat(derived.getTotalCash()).isEqualByComparingTo("27552000000");
		assertThat(derived.getBeta()).isEqualByComparingTo("0.7609478");
		assertThat(derived.getFloatShares()).isEqualByComparingTo("4268646700");
		assertThat(derived.getSharesOutstanding()).isEqualByComparingTo("4439160000");
	}

	@TestConfiguration
	static class MockedJobDependencies {

		@Bean
		MarketDataCollectionService marketDataCollectionService() {
			return mock(MarketDataCollectionService.class);
		}

		@Bean
		AssetScreeningService assetScreeningService() {
			return mock(AssetScreeningService.class);
		}

		@Bean
		PositionThesisGenerationService positionThesisGenerationService() {
			return mock(PositionThesisGenerationService.class);
		}

		@Bean
		EconomicContextAnalysisService economicContextAnalysisService() {
			return mock(EconomicContextAnalysisService.class);
		}

		@Bean
		PositionRecommendationService positionRecommendationService() {
			return mock(PositionRecommendationService.class);
		}

		@Bean
		NotificationDigestService notificationDigestService() {
			return mock(NotificationDigestService.class);
		}
	}
}
