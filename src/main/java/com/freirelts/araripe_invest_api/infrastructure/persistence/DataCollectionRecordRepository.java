package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.marketdata.DataCollectionCategory;
import com.freirelts.araripe_invest_api.domain.marketdata.DataCollectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DataCollectionRecordRepository extends JpaRepository<DataCollectionRecord, UUID> {

	List<DataCollectionRecord> findByReferenceDateAndCategory(LocalDate referenceDate,
			DataCollectionCategory category);

	List<DataCollectionRecord> findByReferenceDateOrderByCreatedAtDesc(LocalDate referenceDate);
}
