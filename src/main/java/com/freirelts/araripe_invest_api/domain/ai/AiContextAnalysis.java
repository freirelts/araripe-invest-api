package com.freirelts.araripe_invest_api.domain.ai;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
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
@Table(name = "ai_context_analyses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiContextAnalysis {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "thesis_id")
	private PositionThesis thesis;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "requested_by_user_id")
	private User requestedByUser;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Column(nullable = false, length = 80)
	private String provider;

	@Column(nullable = false, length = 120)
	private String model;

	@Column(name = "prompt_version", nullable = false, length = 40)
	private String promptVersion;

	@Column(name = "prompt_hash", nullable = false, length = 128)
	private String promptHash;

	@Column(name = "input_hash", nullable = false, length = 128)
	private String inputHash;

	@Column(name = "input_summary_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String inputSummaryJson = "{}";

	@Column(name = "output_json", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String outputJson;

	@Column(name = "sources_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String sourcesJson = "[]";

	@Enumerated(EnumType.STRING)
	@Column(name = "validation_status", nullable = false, length = 32)
	private AiValidationStatus validationStatus = AiValidationStatus.PENDING;

	@Enumerated(EnumType.STRING)
	@Column(name = "processing_status", nullable = false, length = 32)
	private AiProcessingStatus processingStatus = AiProcessingStatus.PENDING;

	@Column(name = "latency_ms")
	private Long latencyMs;

	@Column(name = "error_message", length = 1000)
	private String errorMessage;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	public AiContextAnalysis(Asset asset, LocalDate referenceDate, String provider, String model, String promptVersion,
			String promptHash, String inputHash) {
		this.asset = asset;
		this.referenceDate = referenceDate;
		this.provider = provider;
		this.model = model;
		this.promptVersion = promptVersion;
		this.promptHash = promptHash;
		this.inputHash = inputHash;
	}

	public AiContextAnalysis(Asset asset, LocalDate referenceDate, String provider, String model, String promptVersion,
			String promptHash) {
		this(asset, referenceDate, provider, model, promptVersion, promptHash, promptHash);
	}
}
