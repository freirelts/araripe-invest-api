package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.marketdata.MacroIndicatorSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MacroIndicatorSnapshotRepository extends JpaRepository<MacroIndicatorSnapshot, UUID> {

	Optional<MacroIndicatorSnapshot> findBySlugAndReferenceDateAndSource(String slug, LocalDate referenceDate,
			String source);

	@Query(value = """
			select *
			from (
				select mis.*,
					row_number() over (
						partition by mis.slug
						order by mis.reference_date desc, mis.created_at desc
					) as row_number
				from macro_indicator_snapshots mis
				where mis.reference_date <= :referenceDate
			) latest_macro
			where latest_macro.row_number = 1
			order by latest_macro.slug
			""", nativeQuery = true)
	List<MacroIndicatorSnapshot> findLatestByReferenceDateLessThanEqual(
			@Param("referenceDate") LocalDate referenceDate);
}
