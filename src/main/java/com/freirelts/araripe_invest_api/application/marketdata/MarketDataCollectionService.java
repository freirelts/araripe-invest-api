package com.freirelts.araripe_invest_api.application.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.assets.MonitoredAssetUniverseService;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.marketdata.DailyCandle;
import com.freirelts.araripe_invest_api.domain.marketdata.DataCollectionCategory;
import com.freirelts.araripe_invest_api.domain.marketdata.DataCollectionRecord;
import com.freirelts.araripe_invest_api.domain.marketdata.DataCollectionStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.DividendEvent;
import com.freirelts.araripe_invest_api.domain.marketdata.DividendEventType;
import com.freirelts.araripe_invest_api.domain.marketdata.FinancialStatementSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.MacroIndicatorSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.marketdata.StatementType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DailyCandleRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DataCollectionRecordRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.DividendEventRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FinancialStatementSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.MacroIndicatorSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

@Service
public class MarketDataCollectionService {

	private static final String SOURCE = "brapi";

	private final MarketDataProvider marketDataProvider;
	private final FundamentalDataProvider fundamentalDataProvider;
	private final MacroEconomicDataProvider macroEconomicDataProvider;
	private final MonitoredAssetUniverseService monitoredAssetUniverse;
	private final AssetRepository assetRepository;
	private final DailyCandleRepository dailyCandleRepository;
	private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
	private final FinancialStatementSnapshotRepository financialStatementSnapshotRepository;
	private final DividendEventRepository dividendEventRepository;
	private final MacroIndicatorSnapshotRepository macroIndicatorSnapshotRepository;
	private final DataCollectionRecordRepository dataCollectionRecordRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public MarketDataCollectionService(MarketDataProvider marketDataProvider,
			FundamentalDataProvider fundamentalDataProvider, MacroEconomicDataProvider macroEconomicDataProvider,
			MonitoredAssetUniverseService monitoredAssetUniverse, AssetRepository assetRepository,
			DailyCandleRepository dailyCandleRepository, FundamentalSnapshotRepository fundamentalSnapshotRepository,
			FinancialStatementSnapshotRepository financialStatementSnapshotRepository,
			DividendEventRepository dividendEventRepository,
			MacroIndicatorSnapshotRepository macroIndicatorSnapshotRepository,
			DataCollectionRecordRepository dataCollectionRecordRepository) {
		this.marketDataProvider = marketDataProvider;
		this.fundamentalDataProvider = fundamentalDataProvider;
		this.macroEconomicDataProvider = macroEconomicDataProvider;
		this.monitoredAssetUniverse = monitoredAssetUniverse;
		this.assetRepository = assetRepository;
		this.dailyCandleRepository = dailyCandleRepository;
		this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
		this.financialStatementSnapshotRepository = financialStatementSnapshotRepository;
		this.dividendEventRepository = dividendEventRepository;
		this.macroIndicatorSnapshotRepository = macroIndicatorSnapshotRepository;
		this.dataCollectionRecordRepository = dataCollectionRecordRepository;
	}

	@Transactional
	public MarketDataCollectionSummary collectActiveAssetData(Collection<String> macroSlugs) {
		List<String> symbols = monitoredAssetUniverse.findAllActiveAssets().stream()
				.map(Asset::getSymbol)
				.toList();
		return collect(symbols, HistoricalDataRequest.dailyAscending("1y"), macroSlugs);
	}

	@Transactional
	public MarketDataCollectionSummary collect(Collection<String> symbols, HistoricalDataRequest historicalDataRequest,
			Collection<String> macroSlugs) {
		List<String> normalizedSymbols = monitoredAssetUniverse.normalizeSymbols(symbols);
		MarketDataCollectionSummary summary = MarketDataCollectionSummary.empty();

		summary = summary.plus(processDailyHistory(marketDataProvider.fetchDailyHistory(normalizedSymbols,
				historicalDataRequest)));
		summary = summary.plus(processProfiles(fundamentalDataProvider.fetchCompanyProfiles(normalizedSymbols)));
		summary = summary.plus(processFundamentals(fundamentalDataProvider.fetchStatistics(normalizedSymbols),
				DataCollectionCategory.STATISTICS, this::applyStatistics));
		summary = summary.plus(processFundamentals(fundamentalDataProvider.fetchFinancialData(normalizedSymbols),
				DataCollectionCategory.FINANCIAL_DATA, this::applyFinancialData));
		summary = summary.plus(processStatements(fundamentalDataProvider.fetchBalanceSheets(normalizedSymbols),
				DataCollectionCategory.BALANCE_SHEET, StatementType.BALANCE_SHEET));
		summary = summary.plus(processStatements(fundamentalDataProvider.fetchIncomeStatements(normalizedSymbols),
				DataCollectionCategory.INCOME_STATEMENT, StatementType.INCOME_STATEMENT));
		summary = summary.plus(processStatements(fundamentalDataProvider.fetchCashFlows(normalizedSymbols),
				DataCollectionCategory.CASH_FLOW, StatementType.CASH_FLOW));
		summary = summary.plus(processDividends(fundamentalDataProvider.fetchDividends(normalizedSymbols)));
		summary = summary.plus(processMacro(macroEconomicDataProvider.fetchSeries(macroSlugs)));

		return summary;
	}

	private MarketDataCollectionSummary processDailyHistory(ProviderRawResponse response) {
		List<String> warnings = new ArrayList<>();
		int persisted = 0;
		if (response.payload() != null) {
			Map<String, Asset> assets = activeAssetsBySymbol(response.queriedSymbols());
			for (JsonNode result : response.payload().path("results")) {
				Asset asset = assets.get(symbol(result));
				if (asset == null) {
					warnings.add("Historical result references an asset outside the active monitored universe.");
					continue;
				}
				for (JsonNode candleNode : result.path("data").path("historicalDataPrice")) {
					DailyCandle candle = toDailyCandle(asset, candleNode, response.requestedAt(), warnings);
					if (candle == null) {
						continue;
					}
					upsertCandle(candle);
					persisted++;
				}
			}
		}
		persistRecord(response, DataCollectionCategory.DAILY_HISTORY, warnings);
		return new MarketDataCollectionSummary(persisted, 0, 0, 0, 0, 1, warnings.size());
	}

	private MarketDataCollectionSummary processProfiles(ProviderRawResponse response) {
		List<String> warnings = new ArrayList<>();
		if (response.payload() != null) {
			Map<String, Asset> assets = activeAssetsBySymbol(response.queriedSymbols());
			for (JsonNode result : response.payload().path("results")) {
				Asset asset = assets.get(symbol(result));
				if (asset == null) {
					warnings.add("Profile result references an asset outside the active monitored universe.");
					continue;
				}
				JsonNode data = result.path("data");
				String name = text(data, "name");
				String sector = text(data, "sector");
				String industry = text(data, "industry");
				if (name != null) {
					asset.setName(name);
				}
				if (sector != null) {
					asset.setSector(sector);
				}
				if (industry != null) {
					asset.setIndustry(industry);
				}
				assetRepository.save(asset);
			}
		}
		persistRecord(response, DataCollectionCategory.COMPANY_PROFILE, warnings);
		return new MarketDataCollectionSummary(0, 0, 0, 0, 0, 1, warnings.size());
	}

	private MarketDataCollectionSummary processFundamentals(ProviderRawResponse response,
			DataCollectionCategory category, BiConsumer<FundamentalSnapshot, JsonNode> mapper) {
		List<String> warnings = new ArrayList<>();
		int persisted = 0;
		if (response.payload() != null) {
			Map<String, Asset> assets = activeAssetsBySymbol(response.queriedSymbols());
			for (JsonNode result : response.payload().path("results")) {
				Asset asset = assets.get(symbol(result));
				if (asset == null) {
					warnings.add(category + " result references an asset outside the active monitored universe.");
					continue;
				}
				JsonNode data = result.path("data");
				if (data.isMissingNode() || data.isNull() || data.isEmpty()) {
					warnings.add(category + " returned empty data for " + asset.getSymbol() + ".");
					continue;
				}
				LocalDate parsedReferenceDate = date(data, "mostRecentQuarter");
				LocalDate referenceDate = parsedReferenceDate == null ? referenceDate(response.requestedAt())
						: parsedReferenceDate;
				FundamentalSnapshot snapshot = fundamentalSnapshotRepository
						.findByAssetIdAndReferenceDateAndPeriodTypeAndSource(asset.getId(), referenceDate,
								PeriodType.TTM, SOURCE)
						.orElseGet(() -> new FundamentalSnapshot(asset, referenceDate, PeriodType.TTM, SOURCE));
				mapper.accept(snapshot, data);
				snapshot.setQualityStatus(fundamentalQuality(snapshot));
				fundamentalSnapshotRepository.save(snapshot);
				persisted++;
			}
		}
		persistRecord(response, category, warnings);
		return new MarketDataCollectionSummary(0, persisted, 0, 0, 0, 1, warnings.size());
	}

	private MarketDataCollectionSummary processStatements(ProviderRawResponse response, DataCollectionCategory category,
			StatementType statementType) {
		List<String> warnings = new ArrayList<>();
		int persisted = 0;
		if (response.payload() != null) {
			Map<String, Asset> assets = activeAssetsBySymbol(response.queriedSymbols());
			for (JsonNode result : response.payload().path("results")) {
				Asset asset = assets.get(symbol(result));
				if (asset == null) {
					warnings.add(category + " result references an asset outside the active monitored universe.");
					continue;
				}
				for (JsonNode statementNode : result.path("data")) {
					LocalDate endDate = date(statementNode, "endDate");
					if (endDate == null) {
						warnings.add(category + " returned statement without endDate for " + asset.getSymbol() + ".");
						continue;
					}
					PeriodType periodType = periodType(text(statementNode, "type"));
					FinancialStatementSnapshot snapshot = financialStatementSnapshotRepository
							.findByAssetIdAndStatementTypeAndPeriodTypeAndEndDateAndSource(asset.getId(),
									statementType, periodType, endDate, SOURCE)
							.orElseGet(() -> new FinancialStatementSnapshot(asset, statementType, periodType,
									endDate, SOURCE, json(statementNode)));
					snapshot.setAsset(asset);
					snapshot.setStatementType(statementType);
					snapshot.setPeriodType(periodType);
					snapshot.setEndDate(endDate);
					snapshot.setSource(SOURCE);
					snapshot.setPayloadJson(json(statementNode));
					snapshot.setQualityStatus(statementNode.isEmpty() ? DataQualityStatus.INCOMPLETE
							: DataQualityStatus.VALID);
					financialStatementSnapshotRepository.save(snapshot);
					persisted++;
				}
			}
		}
		persistRecord(response, category, warnings);
		return new MarketDataCollectionSummary(0, 0, persisted, 0, 0, 1, warnings.size());
	}

	private MarketDataCollectionSummary processDividends(ProviderRawResponse response) {
		List<String> warnings = new ArrayList<>();
		int persisted = 0;
		if (response.payload() != null) {
			Map<String, Asset> assets = activeAssetsBySymbol(response.queriedSymbols());
			for (JsonNode result : response.payload().path("results")) {
				Asset asset = assets.get(symbol(result));
				if (asset == null) {
					warnings.add("Dividend result references an asset outside the active monitored universe.");
					continue;
				}
				persisted += persistDividendArray(asset, result.path("data").path("cashDividends"), false, null);
				persisted += persistDividendArray(asset, result.path("data").path("stockDividends"), true, null);
				persisted += persistDividendArray(asset, result.path("data").path("subscriptions"), false,
						DividendEventType.SUBSCRIPTION);
			}
		}
		persistRecord(response, DataCollectionCategory.DIVIDENDS, warnings);
		return new MarketDataCollectionSummary(0, 0, 0, persisted, 0, 1, warnings.size());
	}

	private MarketDataCollectionSummary processMacro(ProviderRawResponse response) {
		List<String> warnings = new ArrayList<>();
		int persisted = 0;
		if (response.payload() != null) {
			for (JsonNode result : response.payload().path("results")) {
				JsonNode series = result.path("series");
				String slug = text(series, "slug");
				String name = text(series, "name");
				if (slug == null || name == null) {
					warnings.add("Macro series returned without slug or name.");
					continue;
				}
				for (JsonNode observation : result.path("observations")) {
					LocalDate referenceDate = date(observation, "date");
					BigDecimal value = decimal(observation, "value");
					if (referenceDate == null || value == null) {
						warnings.add("Macro observation for " + slug + " is incomplete.");
						continue;
					}
					MacroIndicatorSnapshot snapshot = macroIndicatorSnapshotRepository
							.findBySlugAndReferenceDateAndSource(slug, referenceDate, SOURCE)
							.orElseGet(() -> new MacroIndicatorSnapshot(slug, name, referenceDate, value, SOURCE));
					snapshot.setSlug(slug);
					snapshot.setName(name);
					snapshot.setCategory(text(series, "category"));
					snapshot.setUnit(text(series, "unit"));
					snapshot.setFrequency(text(series, "frequency"));
					snapshot.setReferenceDate(referenceDate);
					snapshot.setValue(value);
					snapshot.setSource(SOURCE);
					macroIndicatorSnapshotRepository.save(snapshot);
					persisted++;
				}
			}
		}
		persistRecord(response, DataCollectionCategory.MACRO_SERIES, warnings);
		return new MarketDataCollectionSummary(0, 0, 0, 0, persisted, 1, warnings.size());
	}

	private void upsertCandle(DailyCandle candle) {
		DailyCandle target = dailyCandleRepository
				.findByAssetIdAndTradeDateAndSource(candle.getAsset().getId(), candle.getTradeDate(),
						candle.getSource())
				.orElse(candle);
		target.setAsset(candle.getAsset());
		target.setTradeDate(candle.getTradeDate());
		target.setOpenPrice(candle.getOpenPrice());
		target.setHighPrice(candle.getHighPrice());
		target.setLowPrice(candle.getLowPrice());
		target.setClosePrice(candle.getClosePrice());
		target.setAdjustedClosePrice(candle.getAdjustedClosePrice());
		target.setVolumeQuantity(candle.getVolumeQuantity());
		target.setVolumeFinancial(candle.getVolumeFinancial());
		target.setSource(candle.getSource());
		target.setCollectedAt(candle.getCollectedAt());
		target.setQualityStatus(candle.getQualityStatus());
		dailyCandleRepository.save(target);
	}

	private DailyCandle toDailyCandle(Asset asset, JsonNode node, Instant collectedAt, List<String> warnings) {
		LocalDate tradeDate = epochDate(node, "date");
		BigDecimal open = decimal(node, "open");
		BigDecimal high = decimal(node, "high");
		BigDecimal low = decimal(node, "low");
		BigDecimal close = decimal(node, "close");
		BigDecimal volume = decimal(node, "volume");
		if (tradeDate == null || !positive(open) || !positive(high) || !positive(low) || !positive(close)) {
			warnings.add("Incomplete OHLCV data for " + asset.getSymbol() + ".");
			return null;
		}
		DataQualityStatus quality = high.compareTo(low) < 0 ? DataQualityStatus.INCONSISTENT
				: volume == null || volume.signum() <= 0 ? DataQualityStatus.INCOMPLETE : DataQualityStatus.VALID;
		if (quality == DataQualityStatus.INCONSISTENT) {
			warnings.add("Inconsistent OHLCV data for " + asset.getSymbol() + " on " + tradeDate + ".");
			return null;
		}
		DailyCandle candle = new DailyCandle(asset, tradeDate, open, high, low, close, SOURCE);
		candle.setAdjustedClosePrice(decimal(node, "adjustedClose"));
		candle.setVolumeQuantity(volume);
		candle.setVolumeFinancial(volume == null ? null : volume.multiply(close));
		candle.setCollectedAt(collectedAt);
		candle.setQualityStatus(quality);
		return candle;
	}

	private int persistDividendArray(Asset asset, JsonNode events, boolean stockEvent, DividendEventType forcedType) {
		int persisted = 0;
		if (!events.isArray()) {
			return 0;
		}
		for (JsonNode eventNode : events) {
			DividendEventType type = forcedType == null ? dividendType(text(eventNode, "label"), stockEvent)
					: forcedType;
			LocalDate lastDatePrior = dateTimeDate(eventNode, "lastDatePrior");
			LocalDate paymentDate = dateTimeDate(eventNode, "paymentDate");
			DividendEvent event = dividendEventRepository
					.findByAssetIdAndEventTypeAndLastDatePriorAndPaymentDateAndSource(asset.getId(), type,
							lastDatePrior, paymentDate, SOURCE)
					.orElseGet(() -> new DividendEvent(asset, type, lastDatePrior, paymentDate, SOURCE));
			event.setAsset(asset);
			event.setEventType(type);
			event.setLastDatePrior(lastDatePrior);
			event.setPaymentDate(paymentDate);
			event.setApprovedOn(dateTimeDate(eventNode, "approvedOn"));
			event.setRate(decimal(eventNode, "rate"));
			event.setFactor(decimal(eventNode, "factor"));
			event.setLabel(text(eventNode, "label"));
			event.setIsinCode(text(eventNode, "isinCode"));
			event.setSource(SOURCE);
			dividendEventRepository.save(event);
			persisted++;
		}
		return persisted;
	}

	private void applyStatistics(FundamentalSnapshot snapshot, JsonNode data) {
		setIfPresent(data, "marketCap", snapshot::setMarketCap);
		setIfPresent(data, "enterpriseValue", snapshot::setEnterpriseValue);
		setIfPresent(data, "trailingPE", snapshot::setTrailingPe);
		setIfPresent(data, "priceToBook", snapshot::setPriceToBook);
		setIfPresent(data, "enterpriseToRevenue", snapshot::setEnterpriseToRevenue);
		setIfPresent(data, "enterpriseToEbitda", snapshot::setEnterpriseToEbitda);
		setIfPresent(data, "earningsPerShare", snapshot::setEarningsPerShare);
		setIfPresent(data, "trailingEps", snapshot::setEarningsPerShare);
		setIfPresent(data, "bookValue", snapshot::setBookValue);
		setIfPresent(data, "dividendYield", snapshot::setDividendYield);
		setIfPresent(data, "yield", snapshot::setDividendYield);
		setIfPresent(data, "profitMargins", snapshot::setProfitMargin);
		setIfPresent(data, "earningsQuarterlyGrowth", snapshot::setEarningsGrowth);
	}

	private void applyFinancialData(FundamentalSnapshot snapshot, JsonNode data) {
		setIfPresent(data, "grossMargins", snapshot::setGrossMargin);
		setIfPresent(data, "ebitdaMargins", snapshot::setEbitdaMargin);
		setIfPresent(data, "operatingMargins", snapshot::setOperatingMargin);
		setIfPresent(data, "profitMargins", snapshot::setProfitMargin);
		setIfPresent(data, "returnOnEquity", snapshot::setRoe);
		setIfPresent(data, "returnOnAssets", snapshot::setRoa);
		setIfPresent(data, "debtToEquity", snapshot::setDebtToEquity);
		setIfPresent(data, "revenueGrowth", snapshot::setRevenueGrowth);
		setIfPresent(data, "earningsGrowth", snapshot::setEarningsGrowth);
		setIfPresent(data, "freeCashflow", snapshot::setFreeCashflow);
		setIfPresent(data, "freeCashFlow", snapshot::setFreeCashflow);
		setIfPresent(data, "operatingCashflow", snapshot::setOperatingCashflow);
		setIfPresent(data, "operatingCashFlow", snapshot::setOperatingCashflow);
	}

	private void persistRecord(ProviderRawResponse response, DataCollectionCategory category, List<String> warnings) {
		DataCollectionRecord record = new DataCollectionRecord(category, response.provider(), response.endpoint(),
				referenceDate(response.requestedAt()), response.requestedAt());
		record.setRequestedKeysJson(json(response.requestedSymbols()));
		record.setQueriedKeysJson(json(response.queriedSymbols()));
		record.setStatus(collectionStatus(response, warnings));
		record.setErrorCode(errorCode(response, warnings));
		record.setErrorMessage(errorMessage(response, warnings));
		record.setPayloadJson(response.payload() == null ? null : json(response.payload()));
		record.setTookMillis(Math.max(response.tookMillis(), 0));
		dataCollectionRecordRepository.save(record);
	}

	private DataCollectionStatus collectionStatus(ProviderRawResponse response, List<String> warnings) {
		return switch (response.status()) {
			case SUCCESS -> warnings.isEmpty() ? DataCollectionStatus.SUCCESS : DataCollectionStatus.PARTIAL_SUCCESS;
			case FAILED -> DataCollectionStatus.FAILED;
			case SKIPPED -> DataCollectionStatus.SKIPPED;
		};
	}

	private String errorCode(ProviderRawResponse response, List<String> warnings) {
		if (response.errorCode() != null) {
			return response.errorCode();
		}
		return warnings.isEmpty() ? null : "NORMALIZATION_PARTIAL";
	}

	private String errorMessage(ProviderRawResponse response, List<String> warnings) {
		if (response.errorMessage() != null) {
			return response.errorMessage();
		}
		if (warnings.isEmpty()) {
			return null;
		}
		String message = String.join(" ", warnings);
		return message.substring(0, Math.min(1000, message.length()));
	}

	private Map<String, Asset> activeAssetsBySymbol(Collection<String> symbols) {
		Map<String, Asset> assets = new HashMap<>();
		for (Asset asset : monitoredAssetUniverse.findActiveAssets(symbols)) {
			assets.put(asset.getSymbol(), asset);
		}
		return assets;
	}

	private DataQualityStatus fundamentalQuality(FundamentalSnapshot snapshot) {
		if (snapshot.getTrailingPe() == null && snapshot.getPriceToBook() == null
				&& snapshot.getEnterpriseToEbitda() == null && snapshot.getRoe() == null
				&& snapshot.getOperatingCashflow() == null) {
			return DataQualityStatus.INCOMPLETE;
		}
		return DataQualityStatus.VALID;
	}

	private static PeriodType periodType(String type) {
		if (type == null) {
			return PeriodType.TTM;
		}
		String normalized = type.toLowerCase(Locale.ROOT);
		if (normalized.contains("year") || normalized.contains("annual")) {
			return PeriodType.ANNUAL;
		}
		if (normalized.contains("quarter")) {
			return PeriodType.QUARTERLY;
		}
		return PeriodType.TTM;
	}

	private static DividendEventType dividendType(String label, boolean stockEvent) {
		String normalized = label == null ? "" : label.toUpperCase(Locale.ROOT);
		if (normalized.contains("JCP") || normalized.contains("JUROS")) {
			return DividendEventType.JCP;
		}
		if (stockEvent && normalized.contains("DESDOBRAMENTO")) {
			return DividendEventType.SPLIT;
		}
		if (stockEvent && normalized.contains("BONIF")) {
			return DividendEventType.BONUS;
		}
		if (stockEvent) {
			return DividendEventType.STOCK_DIVIDEND;
		}
		if (normalized.contains("SUBSCR")) {
			return DividendEventType.SUBSCRIPTION;
		}
		return DividendEventType.DIVIDEND;
	}

	private static String symbol(JsonNode result) {
		String symbol = text(result, "symbol");
		if (symbol == null) {
			symbol = text(result, "requestedSymbol");
		}
		return symbol == null ? null : symbol.toUpperCase(Locale.ROOT);
	}

	private static boolean positive(BigDecimal value) {
		return value != null && value.signum() > 0;
	}

	private static LocalDate referenceDate(Instant instant) {
		Instant value = instant == null ? Instant.now() : instant;
		return value.atZone(ZoneOffset.UTC).toLocalDate();
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		String text = value.asText();
		return text == null || text.isBlank() ? null : text.trim();
	}

	private static BigDecimal decimal(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull() || !value.isNumber()) {
			return null;
		}
		return value.decimalValue();
	}

	private static LocalDate date(JsonNode node, String field) {
		String text = text(node, field);
		return text == null ? null : LocalDate.parse(text);
	}

	private static LocalDate dateTimeDate(JsonNode node, String field) {
		String text = text(node, field);
		if (text == null) {
			return null;
		}
		if (text.length() == 10) {
			return LocalDate.parse(text);
		}
		return Instant.parse(text).atZone(ZoneOffset.UTC).toLocalDate();
	}

	private static LocalDate epochDate(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull() || !value.canConvertToLong()) {
			return null;
		}
		return Instant.ofEpochSecond(value.longValue()).atZone(ZoneOffset.UTC).toLocalDate();
	}

	private void setIfPresent(JsonNode data, String field, java.util.function.Consumer<BigDecimal> setter) {
		BigDecimal value = decimal(data, field);
		if (value != null) {
			setter.accept(value);
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not serialize collection audit payload.", ex);
		}
	}
}
