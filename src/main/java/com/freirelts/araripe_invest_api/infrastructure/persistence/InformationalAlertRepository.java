package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.alerts.InformationalAlert;
import com.freirelts.araripe_invest_api.domain.alerts.InformationalEventType;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	@Query("""
			select alert
			from InformationalAlert alert
			join fetch alert.user
			join fetch alert.asset
			left join fetch alert.watchItem
			left join fetch alert.sourcePosition
			left join fetch alert.studyModel
			left join fetch alert.currentStudyModelSnapshot
			where alert.referenceDate = :referenceDate
			  and alert.notificationChannel = :channel
			  and alert.notificationStatus = :status
			order by alert.user.id asc, alert.ruleVersion asc, alert.createdAt asc
			""")
	List<InformationalAlert> findDigestCandidates(@Param("referenceDate") LocalDate referenceDate,
			@Param("channel") NotificationChannel channel, @Param("status") NotificationStatus status);

	Optional<InformationalAlert> findByUserIdAndAssetIdAndSourcePositionIdAndReferenceDateAndEventTypeAndRuleVersionAndSource(
			UUID userId, UUID assetId, UUID sourcePositionId, LocalDate referenceDate, InformationalEventType eventType,
			String ruleVersion, String source);

	Optional<InformationalAlert> findByUserIdAndAssetIdAndWatchItemIdAndReferenceDateAndEventTypeAndRuleVersionAndSource(
			UUID userId, UUID assetId, UUID watchItemId, LocalDate referenceDate, InformationalEventType eventType,
			String ruleVersion, String source);
}
