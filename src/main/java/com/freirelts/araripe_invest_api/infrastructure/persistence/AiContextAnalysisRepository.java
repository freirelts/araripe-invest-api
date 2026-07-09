package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.ai.AiContextAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiContextAnalysisRepository extends JpaRepository<AiContextAnalysis, UUID> {
}
