package com.freirelts.araripe_invest_api.application.assets;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MonitoredAssetUniverseService {

	private final AssetRepository assetRepository;

	public MonitoredAssetUniverseService(AssetRepository assetRepository) {
		this.assetRepository = assetRepository;
	}

	@Transactional(readOnly = true)
	public List<Asset> findActiveAssets(Collection<String> requestedSymbols) {
		List<String> normalizedSymbols = normalizeSymbols(requestedSymbols);
		if (normalizedSymbols.isEmpty()) {
			return List.of();
		}

		Map<String, Asset> assetsBySymbol = assetRepository.findBySymbolInAndActiveTrue(normalizedSymbols).stream()
				.collect(Collectors.toMap(Asset::getSymbol, Function.identity()));

		return normalizedSymbols.stream()
				.map(assetsBySymbol::get)
				.filter(Objects::nonNull)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<Asset> findAllActiveAssets() {
		return assetRepository.findByActiveTrueOrderBySymbolAsc();
	}

	public List<String> normalizeSymbols(Collection<String> symbols) {
		if (symbols == null) {
			return List.of();
		}
		return symbols.stream()
				.filter(Objects::nonNull)
				.map(symbol -> symbol.trim().toUpperCase(Locale.ROOT))
				.filter(symbol -> !symbol.isBlank())
				.collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), List::copyOf));
	}
}
