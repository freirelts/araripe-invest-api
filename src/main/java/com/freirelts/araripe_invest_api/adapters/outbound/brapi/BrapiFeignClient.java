package com.freirelts.araripe_invest_api.adapters.outbound.brapi;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "brapiClient", url = "${araripe.integrations.brapi.base-url}")
interface BrapiFeignClient {

	@GetMapping("/v2/stocks/quote")
	String quote(@RequestHeader("Authorization") String authorization, @RequestParam("symbols") String symbols);

	@GetMapping("/v2/stocks/historical")
	String historical(@RequestHeader("Authorization") String authorization, @RequestParam("symbols") String symbols,
			@RequestParam("range") String range, @RequestParam("interval") String interval,
			@RequestParam("sortOrder") String sortOrder);

	@GetMapping("/v2/stocks/profile")
	String profile(@RequestHeader("Authorization") String authorization, @RequestParam("symbols") String symbols);

	@GetMapping("/v2/stocks/statistics")
	String statistics(@RequestHeader("Authorization") String authorization, @RequestParam("symbols") String symbols);

	@GetMapping("/v2/stocks/financial-data")
	String financialData(@RequestHeader("Authorization") String authorization, @RequestParam("symbols") String symbols);

	@GetMapping("/v2/stocks/balance-sheet")
	String balanceSheet(@RequestHeader("Authorization") String authorization, @RequestParam("symbols") String symbols);

	@GetMapping("/v2/stocks/income-statement")
	String incomeStatement(@RequestHeader("Authorization") String authorization, @RequestParam("symbols") String symbols,
			@RequestParam("period") String period);

	@GetMapping("/v2/stocks/cash-flow")
	String cashFlow(@RequestHeader("Authorization") String authorization, @RequestParam("symbols") String symbols);

	@GetMapping("/v2/stocks/dividends")
	String dividends(@RequestHeader("Authorization") String authorization, @RequestParam("symbols") String symbols,
			@RequestParam("sortOrder") String sortOrder, @RequestParam(value = "startDate", required = false) String startDate,
			@RequestParam(value = "endDate", required = false) String endDate);

	@GetMapping("/v2/macro/available")
	String availableMacroSeries(@RequestHeader("Authorization") String authorization);

	@GetMapping("/v2/macro")
	String macroSeries(@RequestHeader("Authorization") String authorization, @RequestParam("symbols") String symbols);
}
