package com.freirelts.araripe_invest_api.adapters.inbound.rest.assets;

import com.freirelts.araripe_invest_api.application.api.ApiQueryService;
import com.freirelts.araripe_invest_api.application.api.ApiQueryService.AssetFundamentalsResponse;
import com.freirelts.araripe_invest_api.application.api.ApiQueryService.AssetResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
class AssetCatalogController {

	private final ApiQueryService apiQueryService;

	AssetCatalogController(ApiQueryService apiQueryService) {
		this.apiQueryService = apiQueryService;
	}

	@GetMapping
	List<AssetResponse> listActiveAssets() {
		return apiQueryService.listActiveAssets();
	}

	@GetMapping("/{symbol}/fundamentals")
	AssetFundamentalsResponse fundamentals(@PathVariable String symbol,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate date) {
		return apiQueryService.assetFundamentals(symbol, date);
	}
}
