package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.notifications.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, UUID> {
}
