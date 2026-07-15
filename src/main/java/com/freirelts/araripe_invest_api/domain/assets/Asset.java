package com.freirelts.araripe_invest_api.domain.assets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "assets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 20)
	private String symbol;

	@Column(nullable = false, length = 180)
	private String name;

	@Column(length = 120)
	private String sector;

	@Column(length = 120)
	private String industry;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private Market market = Market.B3;

	@Enumerated(EnumType.STRING)
	@Column(name = "asset_type", nullable = false, length = 32)
	private AssetType assetType = AssetType.STOCK;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "data_collection_initialized", nullable = false)
	private boolean dataCollectionInitialized = false;

	@Column(name = "monitoring_reason", length = 500)
	private String monitoringReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	public Asset(String symbol, String name, String sector) {
		this.symbol = symbol;
		this.name = name;
		this.sector = sector;
	}

	public void markDataCollectionInitialized() {
		this.dataCollectionInitialized = true;
		this.updatedAt = Instant.now();
	}
}
