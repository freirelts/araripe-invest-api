package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.thesis.AllocationPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AllocationPlanRepository extends JpaRepository<AllocationPlan, UUID> {
}
