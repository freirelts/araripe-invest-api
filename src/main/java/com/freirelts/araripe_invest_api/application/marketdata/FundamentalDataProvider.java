package com.freirelts.araripe_invest_api.application.marketdata;

import java.util.Collection;

import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;

public interface FundamentalDataProvider {

	ProviderRawResponse fetchCompanyProfiles(Collection<String> symbols);

	ProviderRawResponse fetchStatistics(Collection<String> symbols);

	ProviderRawResponse fetchFinancialData(Collection<String> symbols);

	ProviderRawResponse fetchBalanceSheets(Collection<String> symbols);

	ProviderRawResponse fetchIncomeStatements(Collection<String> symbols, PeriodType periodType);

	ProviderRawResponse fetchCashFlows(Collection<String> symbols);

	ProviderRawResponse fetchDividends(Collection<String> symbols);
}
