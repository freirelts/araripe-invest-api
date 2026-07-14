package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.ai.AiContextAnalysis;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AiContextAnalysisRepository extends JpaRepository<AiContextAnalysis, UUID>,
		JpaSpecificationExecutor<AiContextAnalysis> {

	@Override
	@EntityGraph(attributePaths = { "asset", "thesis", "requestedByUser" })
	Optional<AiContextAnalysis> findById(UUID id);

	@Override
	@EntityGraph(attributePaths = { "asset", "thesis", "requestedByUser" })
	Page<AiContextAnalysis> findAll(Specification<AiContextAnalysis> specification, Pageable pageable);

	Optional<AiContextAnalysis> findByAssetIdAndReferenceDateAndProviderAndModelAndPromptVersionAndPromptHash(
			UUID assetId, LocalDate referenceDate, String provider, String model, String promptVersion,
			String promptHash);

	Optional<AiContextAnalysis> findTopByAssetIdAndReferenceDateLessThanEqualAndValidationStatusOrderByReferenceDateDescCreatedAtDesc(
			UUID assetId, LocalDate referenceDate, AiValidationStatus validationStatus);

	Optional<AiContextAnalysis> findByThesisIdAndReferenceDateAndProviderAndModelAndPromptVersionAndInputHash(
			UUID thesisId, LocalDate referenceDate, String provider, String model, String promptVersion, String inputHash);

	Optional<AiContextAnalysis> findTopByThesisIdAndReferenceDateLessThanEqualAndValidationStatusOrderByReferenceDateDescCreatedAtDesc(
			UUID thesisId, LocalDate referenceDate, AiValidationStatus validationStatus);
}
