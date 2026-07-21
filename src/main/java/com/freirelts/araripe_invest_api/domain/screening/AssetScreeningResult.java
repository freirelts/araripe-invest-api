package com.freirelts.araripe_invest_api.domain.screening;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "asset_screening_results", uniqueConstraints = @UniqueConstraint(name = "uk_asset_screening_asset_date_version", columnNames = {
		"asset_id", "reference_date", "rule_version" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetScreeningResult {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Column(name = "rule_version", nullable = false, length = 40)
	private String ruleVersion;

	@Column(name = "failed_filters_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String failedFiltersJson = "[]";

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	public AssetScreeningResult(Asset asset, LocalDate referenceDate, String ruleVersion) {
		this.asset = asset;
		this.referenceDate = referenceDate;
		this.ruleVersion = ruleVersion;
	}
}
