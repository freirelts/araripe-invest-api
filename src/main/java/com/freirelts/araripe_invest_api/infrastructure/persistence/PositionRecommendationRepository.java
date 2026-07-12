package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.recommendations.PositionRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PositionRecommendationRepository extends JpaRepository<PositionRecommendation, UUID> {

	Optional<PositionRecommendation> findByUserIdAndPositionIdAndReferenceDateAndRuleVersion(UUID userId,
			UUID positionId, LocalDate referenceDate, String ruleVersion);
}
