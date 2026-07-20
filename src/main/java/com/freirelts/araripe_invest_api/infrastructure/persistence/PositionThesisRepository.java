package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionThesisRepository extends JpaRepository<PositionThesis, UUID> {

	@Override
	@EntityGraph(attributePaths = "asset")
	Optional<PositionThesis> findById(UUID id);

	Optional<PositionThesis> findTopByAssetIdAndThesisTypeOrderByReferenceDateDescCreatedAtDesc(UUID assetId,
			ThesisType thesisType);

	Optional<PositionThesis> findTopByAssetIdAndThesisTypeAndReferenceDateLessThanEqualOrderByReferenceDateDescCreatedAtDesc(
			UUID assetId, ThesisType thesisType, LocalDate referenceDate);

	Optional<PositionThesis> findByAssetIdAndReferenceDateAndThesisTypeAndRuleVersion(UUID assetId,
			LocalDate referenceDate, ThesisType thesisType, String ruleVersion);

	List<PositionThesis> findByAssetIdAndReferenceDateAndRuleVersionOrderByScoreDesc(UUID assetId,
			LocalDate referenceDate, String ruleVersion);

	List<PositionThesis> findByReferenceDateAndRuleVersionOrderByScoreDesc(LocalDate referenceDate,
			String ruleVersion);

	@EntityGraph(attributePaths = "asset")
	List<PositionThesis> findByReferenceDateAndRuleVersion(LocalDate referenceDate, String ruleVersion);

	@EntityGraph(attributePaths = "asset")
	List<PositionThesis> findByAssetSymbolIgnoreCaseAndReferenceDateAndRuleVersion(String symbol,
			LocalDate referenceDate, String ruleVersion);

	@EntityGraph(attributePaths = "asset")
	List<PositionThesis> findByAssetIdAndReferenceDateBetweenOrderByReferenceDateDescScoreDesc(UUID assetId,
			LocalDate from, LocalDate to);
}
