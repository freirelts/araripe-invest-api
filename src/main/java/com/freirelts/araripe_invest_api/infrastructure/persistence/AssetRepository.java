package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
	Optional<Asset> findBySymbol(String symbol);

	Optional<Asset> findBySymbolIgnoreCase(String symbol);

	List<Asset> findByActiveTrueOrderBySymbolAsc();

	List<Asset> findBySymbolInAndActiveTrue(Collection<String> symbols);
}
