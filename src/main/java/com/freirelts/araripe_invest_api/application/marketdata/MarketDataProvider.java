package com.freirelts.araripe_invest_api.application.marketdata;

import java.util.Collection;

public interface MarketDataProvider {

	ProviderRawResponse fetchCurrentQuotes(Collection<String> symbols);

	ProviderRawResponse fetchDailyHistory(Collection<String> symbols, HistoricalDataRequest request);
}
