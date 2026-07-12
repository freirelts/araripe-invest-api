package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.risk.UserRiskAllocationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRiskAllocationSettingsRepository extends JpaRepository<UserRiskAllocationSettings, UUID> {

	Optional<UserRiskAllocationSettings> findByUserId(UUID userId);
}
