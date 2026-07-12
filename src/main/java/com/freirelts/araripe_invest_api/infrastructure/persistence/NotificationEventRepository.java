package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.notifications.NotificationEvent;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationEventType;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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

	@EntityGraph(attributePaths = { "asset", "position", "recommendation" })
	Optional<NotificationEvent> findByIdAndUserId(UUID id, UUID userId);

	@EntityGraph(attributePaths = { "asset", "position", "recommendation" })
	List<NotificationEvent> findByUserIdAndReferenceDateBetweenOrderByReferenceDateDescCreatedAtDesc(UUID userId,
			LocalDate from, LocalDate to);
}
