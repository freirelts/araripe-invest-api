package com.freirelts.araripe_invest_api.adapters.inbound.rest.admin;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.assets.AssetType;
import com.freirelts.araripe_invest_api.domain.assets.Market;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/assets")
class AdminAssetController {

	private final AssetRepository assetRepository;

	AdminAssetController(AssetRepository assetRepository) {
		this.assetRepository = assetRepository;
	}

	@GetMapping
	List<AdminAssetResponse> listAssets() {
		return assetRepository.findAll().stream()
				.map(AdminAssetResponse::from)
				.toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AdminAssetResponse createAsset(@Valid @RequestBody UpsertAssetRequest request) {
		String symbol = normalizeSymbol(request.symbol());
		if (assetRepository.findBySymbolIgnoreCase(symbol).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Asset symbol already exists.");
		}

		Asset asset = new Asset(symbol, request.name().trim(), trimToNull(request.sector()));
		apply(asset, request);
		return AdminAssetResponse.from(assetRepository.saveAndFlush(asset));
	}

	@PutMapping("/{assetId}")
	AdminAssetResponse updateAsset(@PathVariable UUID assetId, @Valid @RequestBody UpsertAssetRequest request) {
		Asset asset = assetRepository.findById(assetId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found."));
		String symbol = normalizeSymbol(request.symbol());
		assetRepository.findBySymbolIgnoreCase(symbol)
				.filter(existing -> !existing.getId().equals(assetId))
				.ifPresent(existing -> {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "Asset symbol already exists.");
				});

		asset.setSymbol(symbol);
		asset.setName(request.name().trim());
		apply(asset, request);
		return AdminAssetResponse.from(assetRepository.saveAndFlush(asset));
	}

	@PatchMapping("/{assetId}/status")
	AdminAssetResponse updateStatus(@PathVariable UUID assetId, @Valid @RequestBody AssetStatusRequest request) {
		Asset asset = assetRepository.findById(assetId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found."));
		asset.setActive(request.active());
		asset.setUpdatedAt(Instant.now());
		return AdminAssetResponse.from(assetRepository.saveAndFlush(asset));
	}

	private static void apply(Asset asset, UpsertAssetRequest request) {
		asset.setSector(trimToNull(request.sector()));
		asset.setIndustry(trimToNull(request.industry()));
		asset.setMarket(request.market());
		asset.setAssetType(request.assetType());
		asset.setActive(request.active());
		asset.setMonitoringReason(trimToNull(request.monitoringReason()));
		asset.setUpdatedAt(Instant.now());
	}

	private static String normalizeSymbol(String symbol) {
		return symbol.trim().toUpperCase(Locale.ROOT);
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	record UpsertAssetRequest(
			@NotBlank
			@Size(max = 20)
			String symbol,
			@NotBlank
			@Size(max = 180)
			String name,
			@Size(max = 120)
			String sector,
			@Size(max = 120)
			String industry,
			@NotNull
			Market market,
			@NotNull
			AssetType assetType,
			boolean active,
			@Size(max = 500)
			String monitoringReason) {
	}

	record AssetStatusRequest(@NotNull Boolean active) {
	}

	record AdminAssetResponse(
			UUID id,
			String symbol,
			String name,
			String sector,
			String industry,
			Market market,
			AssetType assetType,
			boolean active,
			String monitoringReason) {

		static AdminAssetResponse from(Asset asset) {
			return new AdminAssetResponse(asset.getId(), asset.getSymbol(), asset.getName(), asset.getSector(),
					asset.getIndustry(), asset.getMarket(), asset.getAssetType(), asset.isActive(),
					asset.getMonitoringReason());
		}
	}
}
