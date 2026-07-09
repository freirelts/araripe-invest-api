package com.freirelts.araripe_invest_api.domain.notifications;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.recommendations.PositionRecommendation;
import com.freirelts.araripe_invest_api.domain.recommendations.RecommendationType;
import com.freirelts.araripe_invest_api.domain.recommendations.Severity;
import com.freirelts.araripe_invest_api.domain.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "notification_events", uniqueConstraints = @UniqueConstraint(name = "uk_notification_events_daily_digest", columnNames = {
		"user_id", "reference_date", "channel", "rule_version", "event_type", "recommendation_id" }))
@NoArgsConstructor
public class NotificationEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "position_id", nullable = false)
	private CustomerPosition position;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recommendation_id", nullable = false)
	private PositionRecommendation recommendation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private NotificationChannel channel;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 40)
	private NotificationEventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(name = "recommendation_type", nullable = false, length = 40)
	private RecommendationType recommendationType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private Severity severity;

	@Column(nullable = false, length = 1000)
	private String summary;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private NotificationStatus status = NotificationStatus.PENDING;

	@Column(name = "provider", length = 80)
	private String provider;

	@Column(name = "provider_message_id", length = 160)
	private String providerMessageId;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "last_error", length = 1000)
	private String lastError;

	@Column(name = "rule_version", nullable = false, length = 40)
	private String ruleVersion;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "sent_at")
	private Instant sentAt;
}
