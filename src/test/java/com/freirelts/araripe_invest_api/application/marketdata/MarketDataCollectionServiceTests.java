package com.freirelts.araripe_invest_api.application.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.assets.MonitoredAssetUniverseService;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.marketdata.DataCollectionCategory;
import com.freirelts.araripe_invest_api.domain.marketdata.DataCollectionStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.DividendEventType;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.marketdata.StatementType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DailyCandleRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DataCollectionRecordRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DividendEventRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FinancialStatementSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.MacroIndicatorSnapshotRepository;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ MonitoredAssetUniverseService.class, MarketDataCollectionService.class,
		MarketDataCollectionServiceTests.TestProviders.class })
class MarketDataCollectionServiceTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@Autowired
	private MarketDataCollectionService collectionService;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private DailyCandleRepository dailyCandleRepository;

	@Autowired
	private FundamentalSnapshotRepository fundamentalSnapshotRepository;

	@Autowired
	private FinancialStatementSnapshotRepository financialStatementSnapshotRepository;

	@Autowired
	private DividendEventRepository dividendEventRepository;

	@Autowired
	private MacroIndicatorSnapshotRepository macroIndicatorSnapshotRepository;

	@Autowired
	private DataCollectionRecordRepository dataCollectionRecordRepository;

	@Autowired
	private MarketDataProvider marketDataProvider;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@AfterEach
	void resetProviders() {
		testProviders().reset();
	}

	@Test
	void collectsAndNormalizesMarketFundamentalStatementDividendAndMacroData() {
		Asset asset = assetRepository.saveAndFlush(new Asset("PETR4", "Petrobras PN", "Energia"));

		MarketDataCollectionSummary summary = collectionService.collect(List.of("PETR4"),
				HistoricalDataRequest.dailyAscending("1mo"), List.of("selic"));

		assertThat(summary.candlesPersisted()).isEqualTo(2);
		assertThat(summary.fundamentalSnapshotsPersisted()).isEqualTo(2);
		assertThat(summary.financialStatementsPersisted()).isEqualTo(4);
		assertThat(summary.dividendEventsPersisted()).isEqualTo(2);
		assertThat(summary.macroSnapshotsPersisted()).isEqualTo(1);
		assertThat(summary.collectionRecordsPersisted()).isEqualTo(10);
		assertThat(summary.warnings()).isZero();

		assertThat(dailyCandleRepository.findAll()).hasSize(2)
				.allSatisfy(candle -> assertThat(candle.getQualityStatus()).isEqualTo(DataQualityStatus.VALID));
		assertThat(fundamentalSnapshotRepository.findAll()).singleElement()
				.satisfies(snapshot -> {
					assertThat(snapshot.getAsset().getId()).isEqualTo(asset.getId());
					assertThat(snapshot.getReferenceDate()).isEqualTo(LocalDate.of(2026, 7, 9));
					assertThat(snapshot.getMostRecentQuarter()).isEqualTo(LocalDate.of(2026, 3, 31));
					assertThat(snapshot.getPeriodType()).isEqualTo(PeriodType.TTM);
					assertThat(snapshot.getTrailingPe()).isEqualByComparingTo("5.0137424");
					assertThat(snapshot.getRoe()).isEqualByComparingTo("0.24267222");
					assertThat(snapshot.getQualityStatus()).isEqualTo(DataQualityStatus.VALID);
				});
		assertThat(financialStatementSnapshotRepository.findAll()).hasSize(4)
				.extracting("statementType")
				.containsExactlyInAnyOrder(StatementType.BALANCE_SHEET, StatementType.INCOME_STATEMENT,
						StatementType.INCOME_STATEMENT, StatementType.CASH_FLOW);
		assertThat(financialStatementSnapshotRepository.findAll().stream()
				.filter(snapshot -> snapshot.getStatementType() == StatementType.INCOME_STATEMENT)
				.toList())
			.extracting("periodType")
			.containsExactlyInAnyOrder(PeriodType.ANNUAL, PeriodType.QUARTERLY);
		assertThat(dividendEventRepository.findAll()).hasSize(2)
				.extracting("eventType")
				.containsExactlyInAnyOrder(DividendEventType.JCP, DividendEventType.SPLIT);
		assertThat(macroIndicatorSnapshotRepository.findAll()).singleElement().satisfies(snapshot -> {
			assertThat(snapshot.getSlug()).isEqualTo("selic");
			assertThat(snapshot.getReferenceDate()).isEqualTo(LocalDate.of(2026, 4, 30));
			assertThat(snapshot.getValue()).isEqualByComparingTo("14.5");
		});
		assertThat(dataCollectionRecordRepository.findByReferenceDateAndCategory(LocalDate.of(2026, 7, 9),
				DataCollectionCategory.DAILY_HISTORY)).singleElement()
				.satisfies(record -> assertThat(record.getStatus()).isEqualTo(DataCollectionStatus.SUCCESS));
		assertThat(dataCollectionRecordRepository.findAll()).hasSize(10)
				.allSatisfy(record -> assertThat(record.getPayloadJson()).isNotBlank());
	}

	@Test
	void mapsBrapiStatisticsAndFinancialDataFieldsToCorrectFundamentalColumns() {
		testProviders().useVale3RealFundamentalPayloads();
		Asset asset = assetRepository.saveAndFlush(new Asset("VALE3", "Vale S.A.", "Materiais Básicos"));

		MarketDataCollectionSummary summary = collectionService.collect(List.of("VALE3"),
				HistoricalDataRequest.dailyAscending("1mo"), List.of());

		assertThat(summary.fundamentalSnapshotsPersisted()).isEqualTo(2);
		assertThat(summary.warnings()).isZero();
		assertThat(fundamentalSnapshotRepository.findAll()).singleElement()
				.satisfies(snapshot -> {
					assertThat(snapshot.getAsset().getId()).isEqualTo(asset.getId());
					assertThat(snapshot.getReferenceDate()).isEqualTo(LocalDate.of(2026, 7, 13));
					assertThat(snapshot.getMostRecentQuarter()).isEqualTo(LocalDate.of(2026, 3, 31));
					assertThat(snapshot.getMarketCap()).isEqualByComparingTo("329296900000");
					assertThat(snapshot.getEnterpriseValue()).isEqualByComparingTo("496444900000");
					assertThat(snapshot.getTrailingPe()).isEqualByComparingTo("23.798286");
					assertThat(snapshot.getPriceToBook()).isEqualByComparingTo("1.7221198");
					assertThat(snapshot.getEnterpriseToRevenue()).isEqualByComparingTo("2.3105075");
					assertThat(snapshot.getEnterpriseToEbitda()).isEqualByComparingTo("9.660904");
					assertThat(snapshot.getEarningsPerShare()).isEqualByComparingTo("3.117031");
					assertThat(snapshot.getNetIncomeToCommon()).isEqualByComparingTo("13837000000");
					assertThat(snapshot.getBookValue()).isEqualByComparingTo("43.074818");
					assertThat(snapshot.getDividendYield()).isEqualByComparingTo("0.07");
					assertThat(snapshot.getLastDividendDate()).isEqualTo(LocalDate.of(2025, 12, 11));
					assertThat(snapshot.getBeta()).isEqualByComparingTo("0.7609478");
					assertThat(snapshot.getFloatShares()).isEqualByComparingTo("4268646700");
					assertThat(snapshot.getSharesOutstanding()).isEqualByComparingTo("4439160000");
					assertThat(snapshot.getFiftyTwoWeekChange()).isEqualByComparingTo("0.4846645");
					assertThat(snapshot.getTotalCash()).isEqualByComparingTo("27552000000");
					assertThat(snapshot.getTotalCashPerShare()).isEqualByComparingTo("6.2065797");
					assertThat(snapshot.getEbitda()).isEqualByComparingTo("51387000000");
					assertThat(snapshot.getTotalDebt()).isEqualByComparingTo("194700000000");
					assertThat(snapshot.getQuickRatio()).isEqualByComparingTo("0.78578115");
					assertThat(snapshot.getCurrentRatio()).isEqualByComparingTo("1.2398882");
					assertThat(snapshot.getTotalRevenue()).isEqualByComparingTo("214864000000");
					assertThat(snapshot.getGrossProfits()).isEqualByComparingTo("75365000000");
					assertThat(snapshot.getProfitMargin()).isEqualByComparingTo("0.06439888");
					assertThat(snapshot.getQuarterlyEarningsGrowth()).isEqualByComparingTo("0.24788938");
					assertThat(snapshot.getGrossMargin()).isEqualByComparingTo("0.35075676");
					assertThat(snapshot.getEbitdaMargin()).isEqualByComparingTo("0.23916058");
					assertThat(snapshot.getOperatingMargin()).isEqualByComparingTo("0.15708075");
					assertThat(snapshot.getRoe()).isEqualByComparingTo("0.07236319");
					assertThat(snapshot.getRoa()).isEqualByComparingTo("0.030266441");
					assertThat(snapshot.getDebtToEquity()).isEqualByComparingTo("1.0182202");
					assertThat(snapshot.getRevenueGrowth()).isEqualByComparingTo("0.015785368");
					assertThat(snapshot.getQuarterlyRevenueGrowth()).isEqualByComparingTo("0.015785368");
					assertThat(snapshot.getEarningsGrowth()).isEqualByComparingTo("-0.54291093");
					assertThat(snapshot.getAnnualRevenueGrowth()).isEqualByComparingTo("0.036843766");
					assertThat(snapshot.getAnnualEarningsGrowth()).isEqualByComparingTo("-0.5627374");
					assertThat(snapshot.getFreeCashflow()).isEqualByComparingTo("9223999000");
					assertThat(snapshot.getOperatingCashflow()).isEqualByComparingTo("48816000000");
					assertThat(snapshot.getNetDebt()).isEqualByComparingTo("167148000000");
					assertThat(snapshot.getQualityStatus()).isEqualTo(DataQualityStatus.VALID);
				});
	}

	@Test
	void secondCollectionRunUpdatesNaturalKeyRecordsWithoutDuplicatingPersistedData() {
		assetRepository.saveAndFlush(new Asset("PETR4", "Petrobras PN", "Energia"));

		collectionService.collect(List.of("PETR4"), HistoricalDataRequest.dailyAscending("1mo"), List.of("selic"));
		collectionService.collect(List.of("PETR4"), HistoricalDataRequest.dailyAscending("1mo"), List.of("selic"));

		assertThat(dailyCandleRepository.findAll()).hasSize(2);
		assertThat(fundamentalSnapshotRepository.findAll()).hasSize(1);
		assertThat(financialStatementSnapshotRepository.findAll()).hasSize(4);
		assertThat(dividendEventRepository.findAll()).hasSize(2);
		assertThat(macroIndicatorSnapshotRepository.findAll()).hasSize(1);
		assertThat(dataCollectionRecordRepository.findAll()).hasSize(20);
	}

	@Test
	void collectionSplitsAssetEndpointCallsIntoBatchesOfFiveSymbols() {
		testProviders().useEmptyAssetPayloads();
		List<String> symbols = List.of("PETR4", "VALE3", "ITUB4", "BBDC4", "ABEV3", "WEGE3", "BBAS3", "MGLU3",
				"RENT3", "LREN3", "SUZB3", "RAIL3");
		symbols.forEach(symbol -> assetRepository.save(new Asset(symbol, symbol, "Setor")));
		assetRepository.flush();

		MarketDataCollectionSummary summary = collectionService.collect(symbols,
				HistoricalDataRequest.dailyAscending("1mo"), List.of());

		List<String> firstBatch = List.of("PETR4", "VALE3", "ITUB4", "BBDC4", "ABEV3");
		List<String> secondBatch = List.of("WEGE3", "BBAS3", "MGLU3", "RENT3", "LREN3");
		List<String> thirdBatch = List.of("SUZB3", "RAIL3");
		assertThat(summary.collectionRecordsPersisted()).isEqualTo(28);
		assertThat(testProviders().requestedSymbols("/v2/stocks/historical"))
				.containsExactly(firstBatch, secondBatch, thirdBatch);
		assertThat(testProviders().requestedSymbols("/v2/stocks/statistics"))
				.containsExactly(firstBatch, secondBatch, thirdBatch);
		assertThat(testProviders().requestedSymbols("/v2/stocks/financial-data"))
				.containsExactly(firstBatch, secondBatch, thirdBatch);
		assertThat(testProviders().requestedSymbols("/v2/stocks/income-statement"))
				.containsExactly(firstBatch, firstBatch, secondBatch, secondBatch, thirdBatch, thirdBatch);
		assertThat(testProviders().requestedSymbols("/v2/stocks/dividends"))
				.containsExactly(firstBatch, secondBatch, thirdBatch);
	}

	@Test
	void activeAssetJobUsesBackfillForNewAssetsAndFiveDayWindowForInitializedAssets() {
		testProviders().useEmptyAssetPayloads();
		Asset newAsset = assetRepository.save(new Asset("ABEV3", "Ambev ON", "Consumo"));
		Asset initializedAsset = new Asset("PETR4", "Petrobras PN", "Energia");
		initializedAsset.markDataCollectionInitialized();
		assetRepository.saveAndFlush(initializedAsset);

		LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);

		collectionService.collectActiveAssetData(List.of());

		assertThat(testProviders().requestedSymbols("/v2/stocks/historical"))
				.containsExactly(List.of("ABEV3"), List.of("PETR4"));
		assertThat(testProviders().historicalRequests())
				.extracting(HistoricalDataRequest::range)
				.containsExactly("2y", "5d");
		assertThat(testProviders().requestedSymbols("/v2/stocks/dividends"))
				.containsExactly(List.of("ABEV3"), List.of("PETR4"));
		assertThat(testProviders().dividendRequests()).satisfiesExactly(
				request -> {
					assertThat(request.startDate()).isNull();
					assertThat(request.endDate()).isNull();
					assertThat(request.sortOrder()).isEqualTo("desc");
				},
				request -> {
					assertThat(request.startDate()).isEqualTo(today.minusDays(5));
					assertThat(request.endDate()).isEqualTo(today);
					assertThat(request.sortOrder()).isEqualTo("desc");
				});
		assertThat(assetRepository.findById(newAsset.getId()))
				.get()
				.satisfies(asset -> assertThat(asset.isDataCollectionInitialized()).isTrue());
	}

	@Test
	void incompleteOhlcvCreatesPartialCollectionRecordWithoutPersistingInvalidCandle() {
		testProviders().useIncompleteHistory();
		assetRepository.saveAndFlush(new Asset("PETR4", "Petrobras PN", "Energia"));

		MarketDataCollectionSummary summary = collectionService.collect(List.of("PETR4"),
				HistoricalDataRequest.dailyAscending("1mo"), List.of("selic"));

			assertThat(summary.warnings()).isEqualTo(1);
			assertThat(summary.partialCollections()).isEqualTo(1);
			assertThat(summary.failedCollections()).isZero();
			assertThat(dailyCandleRepository.findAll()).isEmpty();
		assertThat(dataCollectionRecordRepository.findByReferenceDateAndCategory(LocalDate.of(2026, 7, 9),
				DataCollectionCategory.DAILY_HISTORY)).singleElement().satisfies(record -> {
					assertThat(record.getStatus()).isEqualTo(DataCollectionStatus.PARTIAL_SUCCESS);
					assertThat(record.getErrorCode()).isEqualTo("NORMALIZATION_PARTIAL");
				});
	}

	@Test
	void failedProviderResponseIsTraceableAndDoesNotStopOtherCategories() {
		testProviders().useFailedFinancialData();
		assetRepository.saveAndFlush(new Asset("PETR4", "Petrobras PN", "Energia"));

		MarketDataCollectionSummary summary = collectionService.collect(List.of("PETR4"),
				HistoricalDataRequest.dailyAscending("1mo"), List.of("selic"));

			assertThat(summary.candlesPersisted()).isEqualTo(2);
			assertThat(summary.failedCollections()).isEqualTo(1);
			assertThat(summary.partialCollections()).isZero();
			assertThat(macroIndicatorSnapshotRepository.findAll()).hasSize(1);
		assertThat(dataCollectionRecordRepository.findByReferenceDateAndCategory(LocalDate.of(2026, 7, 9),
				DataCollectionCategory.FINANCIAL_DATA)).singleElement().satisfies(record -> {
					assertThat(record.getStatus()).isEqualTo(DataCollectionStatus.FAILED);
					assertThat(record.getErrorCode()).isEqualTo("BRAPI_HTTP_500");
				});
	}

	@TestConfiguration
	static class TestProviders implements MarketDataProvider, FundamentalDataProvider, MacroEconomicDataProvider {

		private final ObjectMapper objectMapper = new ObjectMapper();
		private Mode mode = Mode.NORMAL;
		private final Map<String, List<List<String>>> requestedSymbolsByEndpoint = new LinkedHashMap<>();
		private final List<HistoricalDataRequest> historicalRequests = new ArrayList<>();
		private final List<DividendDataRequest> dividendRequests = new ArrayList<>();

		@Bean
		ObjectMapper objectMapper() {
			return objectMapper;
		}

		@Bean
		MarketDataProvider marketDataProvider() {
			return this;
		}

		@Bean
		FundamentalDataProvider fundamentalDataProvider() {
			return this;
		}

		@Bean
		MacroEconomicDataProvider macroEconomicDataProvider() {
			return this;
		}

		void reset() {
			mode = Mode.NORMAL;
			requestedSymbolsByEndpoint.clear();
			historicalRequests.clear();
			dividendRequests.clear();
		}

		void useIncompleteHistory() {
			mode = Mode.INCOMPLETE_HISTORY;
		}

		void useFailedFinancialData() {
			mode = Mode.FAILED_FINANCIAL_DATA;
		}

		void useVale3RealFundamentalPayloads() {
			mode = Mode.VALE3_REAL_FUNDAMENTALS;
		}

		void useEmptyAssetPayloads() {
			mode = Mode.EMPTY_ASSET_PAYLOADS;
		}

		List<List<String>> requestedSymbols(String endpoint) {
			return requestedSymbolsByEndpoint.getOrDefault(endpoint, List.of());
		}

		List<HistoricalDataRequest> historicalRequests() {
			return historicalRequests;
		}

		List<DividendDataRequest> dividendRequests() {
			return dividendRequests;
		}

		@Override
		public ProviderRawResponse fetchCurrentQuotes(Collection<String> symbols) {
			recordRequest("/v2/stocks/quote", symbols);
			return success("/v2/stocks/quote", symbols, """
					{"results":[]}
					""");
		}

		@Override
		public ProviderRawResponse fetchDailyHistory(Collection<String> symbols, HistoricalDataRequest request) {
			recordRequest("/v2/stocks/historical", symbols);
			historicalRequests.add(request);
			if (mode == Mode.EMPTY_ASSET_PAYLOADS) {
				return success("/v2/stocks/historical", symbols, """
						{"results":[]}
						""");
			}
			if (mode == Mode.VALE3_REAL_FUNDAMENTALS) {
				return successAt("/v2/stocks/historical", symbols, """
						{"results":[]}
						""", Instant.parse("2026-07-13T15:35:36.125Z"));
			}
			if (mode == Mode.INCOMPLETE_HISTORY) {
				return success("/v2/stocks/historical", symbols, """
						{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":{"historicalDataPrice":[{"date":1780887600,"open":41.20,"high":40.00,"low":40.83,"close":41.22,"volume":34043600}]}}]}
						""");
			}
			return success("/v2/stocks/historical", symbols, """
					{
					  "results": [
					    {
					      "requestedSymbol": "PETR4",
					      "symbol": "PETR4",
					      "data": {
					        "historicalDataPrice": [
					          {"date": 1780887600, "open": 41.20, "high": 41.32, "low": 40.83, "close": 41.22, "volume": 34043600, "adjustedClose": 41.22},
					          {"date": 1780974000, "open": 40.88, "high": 41.37, "low": 40.70, "close": 41.17, "volume": 56680400, "adjustedClose": 41.17}
					        ]
					      }
					    }
					  ]
					}
					""");
		}

		@Override
		public ProviderRawResponse fetchCompanyProfiles(Collection<String> symbols) {
			recordRequest("/v2/stocks/profile", symbols);
			if (mode == Mode.EMPTY_ASSET_PAYLOADS) {
				return success("/v2/stocks/profile", symbols, """
						{"results":[]}
						""");
			}
			if (mode == Mode.VALE3_REAL_FUNDAMENTALS) {
				return successAt("/v2/stocks/profile", symbols, """
						{"results":[]}
						""", Instant.parse("2026-07-13T15:35:36.125Z"));
			}
			return success("/v2/stocks/profile", symbols, """
					{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":{"sector":"Energia","industry":"Petróleo e Gás Integrado","name":"Petrobras PN"}}]}
					""");
		}

		@Override
		public ProviderRawResponse fetchStatistics(Collection<String> symbols) {
			recordRequest("/v2/stocks/statistics", symbols);
			if (mode == Mode.EMPTY_ASSET_PAYLOADS) {
				return success("/v2/stocks/statistics", symbols, """
						{"results":[]}
						""");
			}
			if (mode == Mode.VALE3_REAL_FUNDAMENTALS) {
				return successAt("/v2/stocks/statistics", symbols, """
						{"results":[{"requestedSymbol":"VALE3","symbol":"VALE3","changed":false,"data":{"priceHint":null,"enterpriseValue":496444900000,"forwardPE":null,"profitMargins":0.06439888,"floatShares":4268646700,"sharesOutstanding":4439160000,"sharesShort":null,"sharesShortPriorMonth":null,"sharesShortPreviousMonthDate":null,"dateShortInterest":null,"sharesPercentSharesOut":null,"heldPercentInsiders":null,"heldPercentInstitutions":null,"shortRatio":null,"shortPercentOfFloat":null,"beta":0.7609478,"impliedSharesOutstanding":null,"category":null,"bookValue":43.074818,"priceToBook":1.7221198,"fundFamily":null,"legalType":null,"lastFiscalYearEnd":null,"nextFiscalYearEnd":"2026-12-31 00:00:00+00","mostRecentQuarter":"2026-03-31","earningsQuarterlyGrowth":0.24788938,"netIncomeToCommon":13837000000,"trailingEps":3.117031,"forwardEps":null,"pegRatio":null,"lastSplitFactor":null,"lastSplitDate":null,"enterpriseToRevenue":2.3105075,"enterpriseToEbitda":9.660904,"52WeekChange":0.4846645,"SandP52WeekChange":null,"lastDividendValue":null,"lastDividendDate":"2025-12-11","ytdReturn":null,"beta3Year":null,"totalAssets":null,"yield":0.07,"fundInceptionDate":null,"threeYearAverageReturn":null,"fiveYearAverageReturn":null,"morningStarOverallRating":null,"morningStarRiskRating":null,"annualReportExpenseRatio":null,"lastCapGain":null,"annualHoldingsTurnover":null,"marketCap":329296900000,"trailingPE":23.798286,"earningsPerShare":3.117031,"dividendYield":0.07}}]}
						""", Instant.parse("2026-07-13T16:10:45.597Z"));
			}
			return success("/v2/stocks/statistics", symbols, """
					{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":{"mostRecentQuarter":"2026-03-31","marketCap":486807440000,"enterpriseValue":1116184500000,"trailingPE":5.0137424,"priceToBook":1.0934849,"enterpriseToEbitda":4.834395,"earningsPerShare":8.347058,"bookValue":34.540943,"dividendYield":0.06}}]}
					""");
		}

		@Override
		public ProviderRawResponse fetchFinancialData(Collection<String> symbols) {
			recordRequest("/v2/stocks/financial-data", symbols);
			if (mode == Mode.EMPTY_ASSET_PAYLOADS) {
				return success("/v2/stocks/financial-data", symbols, """
						{"results":[]}
						""");
			}
			if (mode == Mode.FAILED_FINANCIAL_DATA) {
				List<String> keys = symbols.stream().map(String::toUpperCase).toList();
				return ProviderRawResponse.failed("brapi", "/v2/stocks/financial-data", keys, keys,
						Instant.parse("2026-07-09T12:00:00Z"), 5, "BRAPI_HTTP_500",
						"Brapi request failed with HTTP status 500.");
			}
			if (mode == Mode.VALE3_REAL_FUNDAMENTALS) {
				return successAt("/v2/stocks/financial-data", symbols, """
						{"results":[{"requestedSymbol":"VALE3","symbol":"VALE3","changed":false,"data":{"currentPrice":null,"targetHighPrice":null,"targetLowPrice":null,"targetMeanPrice":null,"targetMedianPrice":null,"recommendationMean":null,"recommendationKey":null,"numberOfAnalystOpinions":null,"totalCash":27552000000,"totalCashPerShare":6.2065797,"ebitda":51387000000,"totalDebt":194700000000,"quickRatio":0.78578115,"currentRatio":1.2398882,"totalRevenue":214864000000,"debtToEquity":1.0182202,"revenuePerShare":null,"returnOnAssets":0.030266441,"returnOnEquity":0.07236319,"grossProfits":75365000000,"freeCashflow":9223999000,"operatingCashflow":48816000000,"earningsGrowth":-0.54291093,"revenueGrowth":0.015785368,"earningsGrowthAnnual":-0.5627374,"revenueGrowthAnnual":0.036843766,"grossMargins":0.35075676,"ebitdaMargins":0.23916058,"operatingMargins":0.15708075,"profitMargins":0.06439888,"financialCurrency":null}}]}
						""", Instant.parse("2026-07-13T15:35:36.125Z"));
			}
			return success("/v2/stocks/financial-data", symbols, """
					{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":{"returnOnEquity":0.24267222,"returnOnAssets":0.08670072,"debtToEquity":1.5206507,"grossMargins":0.47359017,"ebitdaMargins":0.46353778,"operatingMargins":0.28881872,"profitMargins":0.21689811,"freeCashflow":80740000000,"operatingCashflow":194970000000,"earningsGrowth":1.2168349,"revenueGrowth":0.0037057786}}]}
					""");
		}

		@Override
		public ProviderRawResponse fetchBalanceSheets(Collection<String> symbols) {
			recordRequest("/v2/stocks/balance-sheet", symbols);
			if (mode == Mode.EMPTY_ASSET_PAYLOADS) {
				return success("/v2/stocks/balance-sheet", symbols, """
						{"results":[]}
						""");
			}
			if (mode == Mode.VALE3_REAL_FUNDAMENTALS) {
				return successAt("/v2/stocks/balance-sheet", symbols, """
						{"results":[]}
						""", Instant.parse("2026-07-13T15:35:36.125Z"));
			}
			return success("/v2/stocks/balance-sheet", symbols, """
					{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":[{"type":"yearly","endDate":"2025-12-31","cash":35608000000,"totalAssets":1223389000000}]}]}
					""");
		}

		@Override
		public ProviderRawResponse fetchIncomeStatements(Collection<String> symbols, PeriodType periodType) {
			recordRequest("/v2/stocks/income-statement", symbols);
			if (mode == Mode.EMPTY_ASSET_PAYLOADS) {
				return success("/v2/stocks/income-statement", symbols, """
						{"results":[]}
						""");
			}
			if (mode == Mode.VALE3_REAL_FUNDAMENTALS) {
				return successAt("/v2/stocks/income-statement", symbols, """
						{"results":[]}
						""", Instant.parse("2026-07-13T15:35:36.125Z"));
			}
			if (periodType == PeriodType.QUARTERLY) {
				return success("/v2/stocks/income-statement", symbols, """
						{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":[{"endDate":"2026-03-31","totalRevenue":125000000000,"netIncome":30000000000}]}]}
						""");
			}
			return success("/v2/stocks/income-statement", symbols, """
					{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":[{"type":"yearly","endDate":"2025-12-31","totalRevenue":497549000000,"netIncome":110605000000}]}]}
					""");
		}

		@Override
		public ProviderRawResponse fetchCashFlows(Collection<String> symbols) {
			recordRequest("/v2/stocks/cash-flow", symbols);
			if (mode == Mode.EMPTY_ASSET_PAYLOADS) {
				return success("/v2/stocks/cash-flow", symbols, """
						{"results":[]}
						""");
			}
			if (mode == Mode.VALE3_REAL_FUNDAMENTALS) {
				return successAt("/v2/stocks/cash-flow", symbols, """
						{"results":[]}
						""", Instant.parse("2026-07-13T15:35:36.125Z"));
			}
			return success("/v2/stocks/cash-flow", symbols, """
					{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":[{"type":"yearly","endDate":"2025-12-31","operatingCashFlow":200333000000,"freeCashFlow":114219000000}]}]}
					""");
		}

		@Override
		public ProviderRawResponse fetchDividends(Collection<String> symbols, DividendDataRequest request) {
			recordRequest("/v2/stocks/dividends", symbols);
			dividendRequests.add(request);
			if (mode == Mode.EMPTY_ASSET_PAYLOADS) {
				return success("/v2/stocks/dividends", symbols, """
						{"results":[]}
						""");
			}
			if (mode == Mode.VALE3_REAL_FUNDAMENTALS) {
				return successAt("/v2/stocks/dividends", symbols, """
						{"results":[]}
						""", Instant.parse("2026-07-13T15:35:36.125Z"));
			}
			return success("/v2/stocks/dividends", symbols, """
					{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":{"cashDividends":[{"paymentDate":"2026-08-20T03:00:00.000Z","rate":0.350486,"isinCode":"BRPETRACNPR6","label":"JCP","lastDatePrior":"2026-06-01T03:00:00.000Z"}],"stockDividends":[{"factor":2,"approvedOn":"2008-04-25T03:00:00.000Z","isinCode":"BRPETRACNPR6","label":"DESDOBRAMENTO","lastDatePrior":"2008-04-25T03:00:00.000Z"}],"subscriptions":[]}}]}
					""");
		}

		@Override
		public ProviderRawResponse fetchAvailableSeries() {
			return success("/v2/macro/available", List.of(), """
					{"results":[]}
					""");
		}

		@Override
		public ProviderRawResponse fetchSeries(Collection<String> slugs) {
			if (mode == Mode.VALE3_REAL_FUNDAMENTALS) {
				return successAt("/v2/macro", slugs, """
						{"results":[]}
						""", Instant.parse("2026-07-13T15:35:36.125Z"));
			}
			return success("/v2/macro", slugs, """
					{"results":[{"series":{"slug":"selic","name":"Taxa Selic","unit":"percentPerYear","frequency":"daily","category":"interestRate"},"observations":[{"date":"2026-04-30","value":14.5}]}]}
					""");
		}

		private ProviderRawResponse success(String endpoint, Collection<String> requestedKeys, String payload) {
			return successAt(endpoint, requestedKeys, payload, Instant.parse("2026-07-09T12:00:00Z"));
		}

		private ProviderRawResponse successAt(String endpoint, Collection<String> requestedKeys, String payload,
				Instant requestedAt) {
			try {
				JsonNode json = objectMapper.readTree(payload);
				List<String> keys = requestedKeys.stream().map(String::toUpperCase).toList();
				return ProviderRawResponse.success("brapi", endpoint, keys, keys, requestedAt, 5, json);
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}

		private void recordRequest(String endpoint, Collection<String> symbols) {
			List<String> keys = symbols.stream().map(String::toUpperCase).toList();
			requestedSymbolsByEndpoint.computeIfAbsent(endpoint, ignored -> new ArrayList<>()).add(keys);
		}

		private enum Mode {
			NORMAL,
			INCOMPLETE_HISTORY,
			FAILED_FINANCIAL_DATA,
			VALE3_REAL_FUNDAMENTALS,
			EMPTY_ASSET_PAYLOADS
		}
	}

	private TestProviders testProviders() {
		return (TestProviders) marketDataProvider;
	}
}
