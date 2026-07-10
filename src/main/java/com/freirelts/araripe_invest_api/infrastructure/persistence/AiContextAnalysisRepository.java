package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.ai.AiContextAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AiContextAnalysisRepository extends JpaRepository<AiContextAnalysis, UUID> {

	Optional<AiContextAnalysis> findByAssetIdAndReferenceDateAndProviderAndModelAndPromptVersionAndPromptHash(
			UUID assetId, LocalDate referenceDate, String provider, String model, String promptVersion,
			String promptHash);
}
