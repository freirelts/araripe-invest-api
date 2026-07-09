package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PositionThesisRepository extends JpaRepository<PositionThesis, UUID> {

	Optional<PositionThesis> findTopByAssetIdAndThesisTypeOrderByReferenceDateDescCreatedAtDesc(UUID assetId,
			ThesisType thesisType);
}
