package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FundamentalSnapshotRepository extends JpaRepository<FundamentalSnapshot, UUID> {
}
