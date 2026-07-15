package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.notifications.NotificationEvent;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationEventType;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, UUID> {

	@Override
	@EntityGraph(attributePaths = { "asset", "position", "recommendation" })
	Optional<NotificationEvent> findById(UUID id);

	Optional<NotificationEvent> findByRecommendationIdAndChannelAndEventType(UUID recommendationId,
			NotificationChannel channel, NotificationEventType eventType);

	List<NotificationEvent> findByReferenceDateAndChannelAndStatus(LocalDate referenceDate,
			NotificationChannel channel, NotificationStatus status);

	@Query("""
			select event
			from NotificationEvent event
			join fetch event.user
			join fetch event.asset
			join fetch event.position
			join fetch event.recommendation
			where event.referenceDate = :referenceDate
			  and event.channel = :channel
			  and event.status = :status
			order by event.user.id asc, event.ruleVersion asc, event.createdAt asc
			""")
	List<NotificationEvent> findDigestCandidates(@Param("referenceDate") LocalDate referenceDate,
			@Param("channel") NotificationChannel channel, @Param("status") NotificationStatus status);

	@EntityGraph(attributePaths = { "asset", "position", "recommendation" })
	Optional<NotificationEvent> findByIdAndUserId(UUID id, UUID userId);

	@EntityGraph(attributePaths = { "asset", "position", "recommendation" })
	List<NotificationEvent> findByUserIdAndReferenceDateBetweenOrderByReferenceDateDescCreatedAtDesc(UUID userId,
			LocalDate from, LocalDate to);
}
