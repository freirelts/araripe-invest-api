package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.marketdata.MacroIndicatorSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MacroIndicatorSnapshotRepository extends JpaRepository<MacroIndicatorSnapshot, UUID> {

	Optional<MacroIndicatorSnapshot> findBySlugAndReferenceDateAndSource(String slug, LocalDate referenceDate,
			String source);
}
