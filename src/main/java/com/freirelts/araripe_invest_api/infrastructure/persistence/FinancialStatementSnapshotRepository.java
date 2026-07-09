package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.marketdata.FinancialStatementSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.marketdata.StatementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface FinancialStatementSnapshotRepository extends JpaRepository<FinancialStatementSnapshot, UUID> {

	Optional<FinancialStatementSnapshot> findByAssetIdAndStatementTypeAndPeriodTypeAndEndDateAndSource(UUID assetId,
			StatementType statementType, PeriodType periodType, LocalDate endDate, String source);
}
