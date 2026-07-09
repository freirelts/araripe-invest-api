package com.freirelts.araripe_invest_api.application.marketdata;

import java.util.Collection;

public interface MacroEconomicDataProvider {

	ProviderRawResponse fetchAvailableSeries();

	ProviderRawResponse fetchSeries(Collection<String> slugs);
}
