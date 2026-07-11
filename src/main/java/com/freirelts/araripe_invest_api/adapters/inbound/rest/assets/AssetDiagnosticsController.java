package com.freirelts.araripe_invest_api.adapters.inbound.rest.assets;

import com.freirelts.araripe_invest_api.application.screening.AssetScreeningDiagnostic;
import com.freirelts.araripe_invest_api.application.screening.AssetScreeningService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/assets")
class AssetDiagnosticsController {

	private final AssetScreeningService assetScreeningService;

	AssetDiagnosticsController(AssetScreeningService assetScreeningService) {
		this.assetScreeningService = assetScreeningService;
	}

	@GetMapping("/{symbol}/diagnostics")
	AssetScreeningDiagnostic diagnostics(@PathVariable String symbol,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate date) {
		LocalDate referenceDate = date == null ? LocalDate.now() : date;
		return assetScreeningService.diagnoseAsset(symbol, referenceDate);
	}
}
