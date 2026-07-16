package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.alerts.AssetWatchItem;
import com.freirelts.araripe_invest_api.domain.alerts.AssetWatchStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetWatchItemRepository extends JpaRepository<AssetWatchItem, UUID> {

	@EntityGraph(attributePaths = { "asset", "sourcePosition", "accompaniedStudyModel" })
	List<AssetWatchItem> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, AssetWatchStatus status);

	Optional<AssetWatchItem> findByUserIdAndAssetIdAndStatus(UUID userId, UUID assetId, AssetWatchStatus status);
}
