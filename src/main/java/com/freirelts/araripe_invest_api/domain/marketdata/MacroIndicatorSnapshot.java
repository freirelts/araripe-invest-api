package com.freirelts.araripe_invest_api.domain.marketdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "macro_indicator_snapshots", uniqueConstraints = @UniqueConstraint(name = "uk_macro_snapshots_slug_date_source", columnNames = {
		"slug", "reference_date", "source" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MacroIndicatorSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 120)
	private String slug;

	@Column(nullable = false, length = 180)
	private String name;

	@Column(length = 120)
	private String category;

	@Column(length = 40)
	private String unit;

	@Column(length = 40)
	private String frequency;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Column(nullable = false, precision = 24, scale = 8)
	private BigDecimal value;

	@Column(nullable = false, length = 80)
	private String source;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();
}
