package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.recommendations.PositionRecommendation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PositionRecommendationRepository extends JpaRepository<PositionRecommendation, UUID> {

	@Override
	@EntityGraph(attributePaths = { "asset", "position", "customerPositionThesis", "currentThesis", "aiContextAnalysis" })
	Optional<PositionRecommendation> findById(UUID id);

	Optional<PositionRecommendation> findByUserIdAndPositionIdAndReferenceDateAndRuleVersion(UUID userId,
			UUID positionId, LocalDate referenceDate, String ruleVersion);

	@EntityGraph(attributePaths = { "asset", "position", "customerPositionThesis", "currentThesis", "aiContextAnalysis" })
	Optional<PositionRecommendation> findByIdAndUserId(UUID id, UUID userId);

	@EntityGraph(attributePaths = { "asset", "position", "customerPositionThesis", "currentThesis", "aiContextAnalysis" })
	java.util.List<PositionRecommendation> findByUserIdAndReferenceDateOrderByCreatedAtDesc(UUID userId,
			LocalDate referenceDate);

	Optional<PositionRecommendation> findFirstByCurrentThesisIdOrderByCreatedAtDesc(UUID currentThesisId);
}
