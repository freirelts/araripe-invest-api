package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.marketdata.DividendEvent;
import com.freirelts.araripe_invest_api.domain.marketdata.DividendEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DividendEventRepository extends JpaRepository<DividendEvent, UUID> {

	Optional<DividendEvent> findByAssetIdAndEventTypeAndLastDatePriorAndPaymentDateAndSource(UUID assetId,
			DividendEventType eventType, LocalDate lastDatePrior, LocalDate paymentDate, String source);

	long countByAssetIdAndEventTypeInAndLastDatePriorBetween(UUID assetId, Collection<DividendEventType> eventTypes,
			LocalDate from, LocalDate to);
}
