package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.alerts.InformationalAlert;
import com.freirelts.araripe_invest_api.domain.alerts.InformationalEventType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InformationalAlertRepository extends JpaRepository<InformationalAlert, UUID> {

	@EntityGraph(attributePaths = { "asset", "watchItem", "sourcePosition", "studyModel", "currentStudyModelSnapshot" })
	Optional<InformationalAlert> findByIdAndUserId(UUID id, UUID userId);

	@EntityGraph(attributePaths = { "asset", "watchItem", "sourcePosition", "studyModel", "currentStudyModelSnapshot" })
	List<InformationalAlert> findByUserIdAndReferenceDateBetweenOrderByReferenceDateDescCreatedAtDesc(UUID userId,
			LocalDate from, LocalDate to);

	Optional<InformationalAlert> findByUserIdAndAssetIdAndSourcePositionIdAndReferenceDateAndEventTypeAndRuleVersionAndSource(
			UUID userId, UUID assetId, UUID sourcePositionId, LocalDate referenceDate, InformationalEventType eventType,
			String ruleVersion, String source);

	Optional<InformationalAlert> findByUserIdAndAssetIdAndWatchItemIdAndReferenceDateAndEventTypeAndRuleVersionAndSource(
			UUID userId, UUID assetId, UUID watchItemId, LocalDate referenceDate, InformationalEventType eventType,
			String ruleVersion, String source);
}
