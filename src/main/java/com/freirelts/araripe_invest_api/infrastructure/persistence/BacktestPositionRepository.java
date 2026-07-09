package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.backtest.BacktestPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BacktestPositionRepository extends JpaRepository<BacktestPosition, UUID> {
}
