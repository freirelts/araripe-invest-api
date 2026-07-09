package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface FundamentalSnapshotRepository extends JpaRepository<FundamentalSnapshot, UUID> {

	Optional<FundamentalSnapshot> findByAssetIdAndReferenceDateAndPeriodTypeAndSource(UUID assetId,
			LocalDate referenceDate, PeriodType periodType, String source);
}
