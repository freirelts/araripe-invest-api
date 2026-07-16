package com.freirelts.araripe_invest_api.domain.marketdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "data_collection_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataCollectionRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private DataCollectionCategory category;

	@Column(nullable = false, length = 80)
	private String provider;

	@Column(nullable = false, length = 2048)
	private String endpoint;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Column(name = "requested_keys_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String requestedKeysJson = "[]";

	@Column(name = "queried_keys_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String queriedKeysJson = "[]";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private DataCollectionStatus status = DataCollectionStatus.PENDING;

	@Column(name = "error_code", length = 120)
	private String errorCode;

	@Column(name = "error_message", length = 1000)
	private String errorMessage;

	@Column(name = "payload_json", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String payloadJson;

	@Column(name = "requested_at", nullable = false)
	private Instant requestedAt;

	@Column(name = "completed_at", nullable = false)
	private Instant completedAt = Instant.now();

	@Column(name = "took_ms", nullable = false)
	private long tookMillis;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	public DataCollectionRecord(DataCollectionCategory category, String provider, String endpoint,
			LocalDate referenceDate, Instant requestedAt) {
		this.category = category;
		this.provider = provider;
		this.endpoint = endpoint;
		this.referenceDate = referenceDate;
		this.requestedAt = requestedAt;
	}
}
