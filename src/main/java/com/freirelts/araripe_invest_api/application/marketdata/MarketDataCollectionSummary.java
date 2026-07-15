package com.freirelts.araripe_invest_api.application.marketdata;

public record MarketDataCollectionSummary(
		int candlesPersisted,
		int fundamentalSnapshotsPersisted,
		int financialStatementsPersisted,
		int dividendEventsPersisted,
		int macroSnapshotsPersisted,
		int collectionRecordsPersisted,
		int warnings,
		int failedCollections,
		int partialCollections) {

	public MarketDataCollectionSummary(int candlesPersisted, int fundamentalSnapshotsPersisted,
			int financialStatementsPersisted, int dividendEventsPersisted, int macroSnapshotsPersisted,
			int collectionRecordsPersisted, int warnings) {
		this(candlesPersisted, fundamentalSnapshotsPersisted, financialStatementsPersisted, dividendEventsPersisted,
				macroSnapshotsPersisted, collectionRecordsPersisted, warnings, 0, 0);
	}

	public MarketDataCollectionSummary plus(MarketDataCollectionSummary other) {
		return new MarketDataCollectionSummary(candlesPersisted + other.candlesPersisted,
				fundamentalSnapshotsPersisted + other.fundamentalSnapshotsPersisted,
				financialStatementsPersisted + other.financialStatementsPersisted,
				dividendEventsPersisted + other.dividendEventsPersisted,
				macroSnapshotsPersisted + other.macroSnapshotsPersisted,
				collectionRecordsPersisted + other.collectionRecordsPersisted, warnings + other.warnings,
				failedCollections + other.failedCollections, partialCollections + other.partialCollections);
	}

	static MarketDataCollectionSummary empty() {
		return new MarketDataCollectionSummary(0, 0, 0, 0, 0, 0, 0, 0, 0);
	}
}
