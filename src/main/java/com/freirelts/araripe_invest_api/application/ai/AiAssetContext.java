package com.freirelts.araripe_invest_api.application.ai;

import com.freirelts.araripe_invest_api.domain.assets.Asset;

public record AiAssetContext(
		String symbol,
		String name,
		String sector,
		String industry) {

	public static AiAssetContext from(Asset asset) {
		return new AiAssetContext(asset.getSymbol(), asset.getName(), asset.getSector(), asset.getIndustry());
	}
}
