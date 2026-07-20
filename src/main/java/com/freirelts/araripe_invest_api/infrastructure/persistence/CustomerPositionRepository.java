package com.freirelts.araripe_invest_api.infrastructure.persistence;

import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.portfolio.PositionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPositionRepository extends JpaRepository<CustomerPosition, UUID> {

	@Override
	@EntityGraph(attributePaths = { "asset", "user" })
	Optional<CustomerPosition> findById(UUID id);

	@EntityGraph(attributePaths = { "asset", "user" })
	List<CustomerPosition> findByUserIdOrderByCreatedAtDesc(UUID userId);

	@EntityGraph(attributePaths = { "asset", "user" })
	List<CustomerPosition> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, PositionStatus status);

	@EntityGraph(attributePaths = { "asset", "user" })
	Optional<CustomerPosition> findByIdAndUserId(UUID id, UUID userId);

	@EntityGraph(attributePaths = { "asset", "user" })
	List<CustomerPosition> findByStatusOrderByCreatedAtAsc(PositionStatus status);
}
