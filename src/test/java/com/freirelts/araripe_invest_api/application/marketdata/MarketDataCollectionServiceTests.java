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
import java.util.Collection;
import java.util.List;

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
		assertThat(summary.financialStatementsPersisted()).isEqualTo(3);
		assertThat(summary.dividendEventsPersisted()).isEqualTo(2);
		assertThat(summary.macroSnapshotsPersisted()).isEqualTo(1);
		assertThat(summary.collectionRecordsPersisted()).isEqualTo(9);
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
		assertThat(financialStatementSnapshotRepository.findAll()).hasSize(3)
				.extracting("statementType")
				.containsExactlyInAnyOrder(StatementType.BALANCE_SHEET, StatementType.INCOME_STATEMENT,
						StatementType.CASH_FLOW);
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
		assertThat(dataCollectionRecordRepository.findAll()).hasSize(9)
				.allSatisfy(record -> assertThat(record.getPayloadJson()).isNotBlank());
	}

	@Test
	void secondCollectionRunUpdatesNaturalKeyRecordsWithoutDuplicatingPersistedData() {
		assetRepository.saveAndFlush(new Asset("PETR4", "Petrobras PN", "Energia"));

		collectionService.collect(List.of("PETR4"), HistoricalDataRequest.dailyAscending("1mo"), List.of("selic"));
		collectionService.collect(List.of("PETR4"), HistoricalDataRequest.dailyAscending("1mo"), List.of("selic"));

		assertThat(dailyCandleRepository.findAll()).hasSize(2);
		assertThat(fundamentalSnapshotRepository.findAll()).hasSize(1);
		assertThat(financialStatementSnapshotRepository.findAll()).hasSize(3);
		assertThat(dividendEventRepository.findAll()).hasSize(2);
		assertThat(macroIndicatorSnapshotRepository.findAll()).hasSize(1);
		assertThat(dataCollectionRecordRepository.findAll()).hasSize(18);
	}

	@Test
	void incompleteOhlcvCreatesPartialCollectionRecordWithoutPersistingInvalidCandle() {
		testProviders().useIncompleteHistory();
		assetRepository.saveAndFlush(new Asset("PETR4", "Petrobras PN", "Energia"));

		MarketDataCollectionSummary summary = collectionService.collect(List.of("PETR4"),
				HistoricalDataRequest.dailyAscending("1mo"), List.of("selic"));

		assertThat(summary.warnings()).isEqualTo(1);
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
		}

		void useIncompleteHistory() {
			mode = Mode.INCOMPLETE_HISTORY;
		}

		void useFailedFinancialData() {
			mode = Mode.FAILED_FINANCIAL_DATA;
		}

		@Override
		public ProviderRawResponse fetchCurrentQuotes(Collection<String> symbols) {
			return success("/v2/stocks/quote", symbols, """
					{"results":[]}
					""");
		}

		@Override
		public ProviderRawResponse fetchDailyHistory(Collection<String> symbols, HistoricalDataRequest request) {
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
			return success("/v2/stocks/profile", symbols, """
					{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":{"sector":"Energia","industry":"Petróleo e Gás Integrado","name":"Petrobras PN"}}]}
					""");
		}

		@Override
		public ProviderRawResponse fetchStatistics(Collection<String> symbols) {
			return success("/v2/stocks/statistics", symbols, """
					{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":{"mostRecentQuarter":"2026-03-31","marketCap":486807440000,"enterpriseValue":1116184500000,"trailingPE":5.0137424,"priceToBook":1.0934849,"enterpriseToEbitda":4.834395,"earningsPerShare":8.347058,"bookValue":34.540943,"dividendYield":0.06}}]}
					""");
		}

		@Override
		public ProviderRawResponse fetchFinancialData(Collection<String> symbols) {
			if (mode == Mode.FAILED_FINANCIAL_DATA) {
				List<String> keys = symbols.stream().map(String::toUpperCase).toList();
				return ProviderRawResponse.failed("brapi", "/v2/stocks/financial-data", keys, keys,
						Instant.parse("2026-07-09T12:00:00Z"), 5, "BRAPI_HTTP_500",
						"Brapi request failed with HTTP status 500.");
			}
			return success("/v2/stocks/financial-data", symbols, """
					{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":{"returnOnEquity":0.24267222,"returnOnAssets":0.08670072,"debtToEquity":1.5206507,"grossMargins":0.47359017,"ebitdaMargins":0.46353778,"operatingMargins":0.28881872,"profitMargins":0.21689811,"freeCashflow":80740000000,"operatingCashflow":194970000000,"earningsGrowth":1.2168349,"revenueGrowth":0.0037057786}}]}
					""");
		}

		@Override
		public ProviderRawResponse fetchBalanceSheets(Collection<String> symbols) {
			return success("/v2/stocks/balance-sheet", symbols, """
					{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":[{"type":"yearly","endDate":"2025-12-31","cash":35608000000,"totalAssets":1223389000000}]}]}
					""");
		}

		@Override
		public ProviderRawResponse fetchIncomeStatements(Collection<String> symbols) {
			return success("/v2/stocks/income-statement", symbols, """
					{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":[{"type":"yearly","endDate":"2025-12-31","totalRevenue":497549000000,"netIncome":110605000000}]}]}
					""");
		}

		@Override
		public ProviderRawResponse fetchCashFlows(Collection<String> symbols) {
			return success("/v2/stocks/cash-flow", symbols, """
					{"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","data":[{"type":"yearly","endDate":"2025-12-31","operatingCashFlow":200333000000,"freeCashFlow":114219000000}]}]}
					""");
		}

		@Override
		public ProviderRawResponse fetchDividends(Collection<String> symbols) {
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
			return success("/v2/macro", slugs, """
					{"results":[{"series":{"slug":"selic","name":"Taxa Selic","unit":"percentPerYear","frequency":"daily","category":"interestRate"},"observations":[{"date":"2026-04-30","value":14.5}]}]}
					""");
		}

		private ProviderRawResponse success(String endpoint, Collection<String> requestedKeys, String payload) {
			try {
				JsonNode json = objectMapper.readTree(payload);
				List<String> keys = requestedKeys.stream().map(String::toUpperCase).toList();
				return ProviderRawResponse.success("brapi", endpoint, keys, keys, Instant.parse(
						"2026-07-09T12:00:00Z"), 5, json);
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}

		private enum Mode {
			NORMAL,
			INCOMPLETE_HISTORY,
			FAILED_FINANCIAL_DATA
		}
	}

	private TestProviders testProviders() {
		return (TestProviders) marketDataProvider;
	}
}
