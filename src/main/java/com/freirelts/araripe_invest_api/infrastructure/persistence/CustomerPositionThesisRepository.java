package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesis;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesisStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPositionThesisRepository extends JpaRepository<CustomerPositionThesis, UUID> {

	Optional<CustomerPositionThesis> findByPositionIdAndStatus(UUID positionId, CustomerPositionThesisStatus status);

	List<CustomerPositionThesis> findByAssetIdAndThesisTypeAndStatus(UUID assetId, ThesisType thesisType,
			CustomerPositionThesisStatus status);
}
