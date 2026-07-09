package com.freirelts.araripe_invest_api.adapters.inbound.rest.admin;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.assets.AssetType;
import com.freirelts.araripe_invest_api.domain.assets.Market;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

	record AdminAssetResponse(
			UUID id,
			String symbol,
			String name,
			String sector,
			String industry,
			Market market,
			AssetType assetType,
			boolean active) {

		static AdminAssetResponse from(Asset asset) {
			return new AdminAssetResponse(asset.getId(), asset.getSymbol(), asset.getName(), asset.getSector(),
					asset.getIndustry(), asset.getMarket(), asset.getAssetType(), asset.isActive());
		}
	}
}
