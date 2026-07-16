package com.freirelts.araripe_invest_api.domain.alerts;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesis;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationStatus;
import com.freirelts.araripe_invest_api.domain.alerts.Severity;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "informational_alerts", uniqueConstraints = @UniqueConstraint(name = "uk_informational_alerts_idempotency", columnNames = {
		"user_id", "asset_id", "source_position_id", "reference_date", "event_type", "rule_version", "source" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InformationalAlert {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "watch_item_id")
	private AssetWatchItem watchItem;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_position_id")
	private CustomerPosition sourcePosition;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "study_model_id")
	private CustomerPositionThesis studyModel;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "current_study_model_snapshot_id")
	private PositionThesis currentStudyModelSnapshot;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 60)
	private InformationalEventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private Severity severity;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(nullable = false, length = 2000)
	private String summary;

	@Column(name = "evidence_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String evidenceJson = "{}";

	@Column(name = "rule_version", nullable = false, length = 40)
	private String ruleVersion;

	@Column(nullable = false, length = 80)
	private String source = "araripe-rules";

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "read_at")
	private Instant readAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "notification_channel", nullable = false, length = 32)
	private NotificationChannel notificationChannel = NotificationChannel.EMAIL;

	@Enumerated(EnumType.STRING)
	@Column(name = "notification_status", nullable = false, length = 32)
	private NotificationStatus notificationStatus = NotificationStatus.PENDING;

	@Column(name = "notification_provider", length = 80)
	private String notificationProvider;

	@Column(name = "notification_provider_message_id", length = 160)
	private String notificationProviderMessageId;

	@Column(name = "notification_attempt_count", nullable = false)
	private int notificationAttemptCount;

	@Column(name = "notification_last_error", length = 1000)
	private String notificationLastError;

	@Column(name = "notification_sent_at")
	private Instant notificationSentAt;

	public InformationalAlert(User user, Asset asset, LocalDate referenceDate, InformationalEventType eventType,
			Severity severity, String title, String summary, String ruleVersion) {
		if (user == null || asset == null || referenceDate == null || eventType == null || severity == null) {
			throw new IllegalArgumentException("User, asset, date, event type and severity are required.");
		}
		this.user = user;
		this.asset = asset;
		this.referenceDate = referenceDate;
		this.eventType = eventType;
		this.severity = severity;
		this.title = title;
		this.summary = summary;
		this.ruleVersion = ruleVersion;
	}
}
