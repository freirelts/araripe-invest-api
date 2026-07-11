package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.screening.AssetScreeningResult;
import com.freirelts.araripe_invest_api.domain.screening.ScreeningStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetScreeningResultRepository extends JpaRepository<AssetScreeningResult, UUID> {

	Optional<AssetScreeningResult> findByAssetIdAndReferenceDateAndRuleVersion(UUID assetId, LocalDate referenceDate,
			String ruleVersion);

	List<AssetScreeningResult> findByReferenceDateAndRuleVersionAndStatus(LocalDate referenceDate, String ruleVersion,
			ScreeningStatus status);
}
