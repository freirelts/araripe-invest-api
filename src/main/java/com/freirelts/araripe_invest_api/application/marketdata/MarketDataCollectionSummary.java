package com.freirelts.araripe_invest_api.application.marketdata;

public record MarketDataCollectionSummary(
		int candlesPersisted,
		int fundamentalSnapshotsPersisted,
		int financialStatementsPersisted,
		int dividendEventsPersisted,
		int macroSnapshotsPersisted,
		int collectionRecordsPersisted,
		int warnings) {

	public MarketDataCollectionSummary plus(MarketDataCollectionSummary other) {
		return new MarketDataCollectionSummary(candlesPersisted + other.candlesPersisted,
				fundamentalSnapshotsPersisted + other.fundamentalSnapshotsPersisted,
				financialStatementsPersisted + other.financialStatementsPersisted,
				dividendEventsPersisted + other.dividendEventsPersisted,
				macroSnapshotsPersisted + other.macroSnapshotsPersisted,
				collectionRecordsPersisted + other.collectionRecordsPersisted, warnings + other.warnings);
	}

	static MarketDataCollectionSummary empty() {
		return new MarketDataCollectionSummary(0, 0, 0, 0, 0, 0, 0);
	}
}
