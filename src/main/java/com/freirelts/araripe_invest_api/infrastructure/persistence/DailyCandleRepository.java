package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.marketdata.DailyCandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DailyCandleRepository extends JpaRepository<DailyCandle, UUID> {

	Optional<DailyCandle> findByAssetIdAndTradeDateAndSource(UUID assetId, LocalDate tradeDate, String source);
}
