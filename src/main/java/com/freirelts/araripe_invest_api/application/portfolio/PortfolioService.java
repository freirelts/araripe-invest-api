package com.freirelts.araripe_invest_api.application.portfolio;

import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesis;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesisStatus;
import com.freirelts.araripe_invest_api.domain.portfolio.PositionStatus;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PortfolioService {

	private static final String REPLACED_BY_CUSTOMER = "Tese principal substituida pelo cliente.";
	private static final String CLOSED_WITH_POSITION = "Posicao encerrada pelo cliente.";
	private static final int PRICE_SCALE = 6;

	private final UserRepository userRepository;
	private final AssetRepository assetRepository;
	private final CustomerPositionRepository positionRepository;
	private final CustomerPositionThesisRepository positionThesisRepository;
	private final PositionThesisRepository thesisRepository;

	PortfolioService(UserRepository userRepository, AssetRepository assetRepository,
			CustomerPositionRepository positionRepository, CustomerPositionThesisRepository positionThesisRepository,
			PositionThesisRepository thesisRepository) {
		this.userRepository = userRepository;
		this.assetRepository = assetRepository;
		this.positionRepository = positionRepository;
		this.positionThesisRepository = positionThesisRepository;
		this.thesisRepository = thesisRepository;
	}

	@Transactional(readOnly = true)
	public List<PositionSummary> listPositions(UUID userId) {
		requireCustomer(userId);
		return positionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(this::summary)
				.toList();
	}

	@Transactional
	public PositionSummary createPosition(UUID userId, PositionInput input) {
		User user = requireCustomer(userId);
		validateInput(input);
		Asset asset = assetRepository.findById(input.assetId())
				.orElseThrow(() -> notFound("Asset not found."));

		CustomerPosition position = new CustomerPosition(user, asset, input.quantity(), input.averagePrice(),
				input.entryDate());
		applyEditableFields(position, input);
		return summary(positionRepository.saveAndFlush(position));
	}

	@Transactional
	public PositionSummary updatePosition(UUID userId, UUID positionId, PositionInput input) {
		requireCustomer(userId);
		validateInput(input);
		CustomerPosition position = openOwnedPosition(userId, positionId);
		position.setQuantity(input.quantity());
		position.setAveragePrice(input.averagePrice());
		position.setEntryDate(input.entryDate());
		applyEditableFields(position, input);
		return summary(positionRepository.saveAndFlush(position));
	}

	@Transactional
	public PositionSummary registerContribution(UUID userId, UUID positionId, ContributionInput input) {
		requireCustomer(userId);
		validateContributionInput(input);
		CustomerPosition position = openOwnedPosition(userId, positionId);

		BigDecimal currentQuantity = position.getQuantity();
		BigDecimal currentAveragePrice = position.getAveragePrice();
		BigDecimal contributionQuantity = input.quantity();
		BigDecimal contributionPrice = input.price();
		BigDecimal newQuantity = currentQuantity.add(contributionQuantity);
		BigDecimal newAveragePrice = currentQuantity.multiply(currentAveragePrice)
				.add(contributionQuantity.multiply(contributionPrice))
				.divide(newQuantity, PRICE_SCALE, RoundingMode.HALF_UP);

		position.setQuantity(newQuantity);
		position.setAveragePrice(newAveragePrice);
		if (input.contributionDate() != null && input.contributionDate().isBefore(position.getEntryDate())) {
			position.setEntryDate(input.contributionDate());
		}
		if (trimToNull(input.notes()) != null) {
			position.setNotes(trimToNull(input.notes()));
		}
		position.setUpdatedAt(Instant.now());
		return summary(positionRepository.saveAndFlush(position));
	}

	@Transactional
	public PositionSummary closePosition(UUID userId, UUID positionId) {
		requireCustomer(userId);
		CustomerPosition position = openOwnedPosition(userId, positionId);
		positionThesisRepository.findByPositionIdAndStatus(positionId, CustomerPositionThesisStatus.ACTIVE)
				.ifPresent(activeThesis -> activeThesis.close(CLOSED_WITH_POSITION));
		position.close();
		return summary(positionRepository.saveAndFlush(position));
	}

	@Transactional
	public PositionSummary associateMainThesis(UUID userId, UUID positionId, UUID thesisId, String notes) {
		requireCustomer(userId);
		CustomerPosition position = openOwnedPosition(userId, positionId);
		positionThesisRepository.findByPositionIdAndStatus(positionId, CustomerPositionThesisStatus.ACTIVE)
				.ifPresent(active -> {
					throw new ResponseStatusException(HttpStatus.CONFLICT,
							"Position already has an active accompanied study model.");
				});
		createAssociation(userId, position, thesisId, notes);
		return summary(positionRepository.saveAndFlush(position));
	}

	@Transactional
	public PositionSummary replaceMainThesis(UUID userId, UUID positionId, UUID thesisId, String notes) {
		requireCustomer(userId);
		CustomerPosition position = openOwnedPosition(userId, positionId);
		positionThesisRepository.findByPositionIdAndStatus(positionId, CustomerPositionThesisStatus.ACTIVE)
				.ifPresent(activeThesis -> {
					activeThesis.close(REPLACED_BY_CUSTOMER);
					positionThesisRepository.saveAndFlush(activeThesis);
				});
		createAssociation(userId, position, thesisId, notes);
		return summary(positionRepository.saveAndFlush(position));
	}

	private void createAssociation(UUID userId, CustomerPosition position, UUID thesisId, String notes) {
		PositionThesis thesis = thesisRepository.findById(thesisId)
				.orElseThrow(() -> notFound("Thesis not found."));
		if (!sameAsset(position.getAsset(), thesis.getAsset())) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					"The accepted thesis must reference the same asset as the position.");
		}
		validateAssociableThesis(thesis);
		CustomerPositionThesis association = new CustomerPositionThesis(position.getUser(), position, thesis,
				position.getAveragePrice());
		association.setNotes(trimToNull(notes));
		positionThesisRepository.saveAndFlush(association);
	}

	private PositionSummary summary(CustomerPosition position) {
		AccompaniedStudyModelSummary accompaniedStudyModel = positionThesisRepository
				.findByPositionIdAndStatus(position.getId(), CustomerPositionThesisStatus.ACTIVE)
				.map(AccompaniedStudyModelSummary::from)
				.orElse(null);
		return PositionSummary.from(position, accompaniedStudyModel);
	}

	private User requireCustomer(UUID userId) {
		return userRepository.findById(userId)
				.filter(user -> user.hasValidAuthenticatedAccess() && user.hasRole(UserRoleType.CUSTOMER))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user."));
	}

	private CustomerPosition openOwnedPosition(UUID userId, UUID positionId) {
		CustomerPosition position = positionRepository.findByIdAndUserId(positionId, userId)
				.orElseThrow(() -> notFound("Position not found."));
		if (position.getStatus() != PositionStatus.OPEN) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Only open positions can be changed.");
		}
		return position;
	}

	private void validateInput(PositionInput input) {
		if (input.assetId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset is required.");
		}
		if (input.quantity() == null || input.quantity().signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Position quantity must be greater than zero.");
		}
		if (input.averagePrice() == null || input.averagePrice().signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Average price must be greater than zero.");
		}
		if (input.entryDate() == null || input.entryDate().isAfter(LocalDate.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entry date must be present and not in the future.");
		}
		validatePositiveOptional(input.stopPrice(), "Stop price");
		validatePositiveOptional(input.targetPrice(), "Target price");
		validatePositiveOptional(input.targetReturnPercent(), "Target return percent");
	}

	private void validateContributionInput(ContributionInput input) {
		if (input.quantity() == null || input.quantity().signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Contribution quantity must be greater than zero.");
		}
		if (input.price() == null || input.price().signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contribution price must be greater than zero.");
		}
		if (input.contributionDate() != null && input.contributionDate().isAfter(LocalDate.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contribution date must not be in the future.");
		}
	}

	private void applyEditableFields(CustomerPosition position, PositionInput input) {
		position.setStopPrice(input.stopPrice());
		position.setTargetPrice(input.targetPrice());
		position.setTargetReturnPercent(input.targetReturnPercent());
		position.setNotes(trimToNull(input.notes()));
		position.setUpdatedAt(Instant.now());
	}

	private void validatePositiveOptional(BigDecimal value, String fieldName) {
		if (value != null && value.signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be greater than zero.");
		}
	}

	private boolean sameAsset(Asset left, Asset right) {
		return left == right
				|| (left != null && right != null && left.getId() != null && left.getId().equals(right.getId()));
	}

	private void validateAssociableThesis(PositionThesis thesis) {
		if (thesis.getStatus() != ThesisStatus.MONITORAR && thesis.getStatus() != ThesisStatus.OPORTUNIDADE
				&& thesis.getStatus() != ThesisStatus.APORTE_PLANEJADO) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					"Only monitorable study models can be accompanied.");
		}
		if (thesis.getScore() < 60 || !positive(thesis.getFairPriceEstimate()) || !positive(thesis.getPriceCeiling())
				|| thesis.getSafetyMarginPercent() == null || !positive(thesis.getStopPrice())
				|| !positive(thesis.getTargetPrice()) || hasDataBlockingFilter(thesis.getFailedFiltersJson())) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					"Accompanied study model requires complete data, valuation references and no data quality blockers.");
		}
	}

	private boolean hasDataBlockingFilter(String failedFiltersJson) {
		if (failedFiltersJson == null || failedFiltersJson.isBlank()) {
			return false;
		}
		return failedFiltersJson.contains("DATA_QUALITY_BLOCKED")
				|| failedFiltersJson.contains("MINIMUM_FUNDAMENTALS_MISSING");
	}

	private boolean positive(BigDecimal value) {
		return value != null && value.signum() > 0;
	}

	private ResponseStatusException notFound(String message) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	public record PositionInput(
			UUID assetId,
			BigDecimal quantity,
			BigDecimal averagePrice,
			LocalDate entryDate,
			BigDecimal stopPrice,
			BigDecimal targetPrice,
			BigDecimal targetReturnPercent,
			String notes) {
	}

	public record ContributionInput(
			BigDecimal quantity,
			BigDecimal price,
			LocalDate contributionDate,
			String notes) {
	}

	public record PositionSummary(
			UUID id,
			UUID assetId,
			String symbol,
			String assetName,
			BigDecimal quantity,
			BigDecimal averagePrice,
			LocalDate entryDate,
			BigDecimal stopPrice,
			BigDecimal targetPrice,
			BigDecimal targetReturnPercent,
			String notes,
			PositionStatus status,
			Instant createdAt,
			Instant updatedAt,
			Instant closedAt,
			AccompaniedStudyModelSummary accompaniedStudyModel) {

		static PositionSummary from(CustomerPosition position, AccompaniedStudyModelSummary accompaniedStudyModel) {
			return new PositionSummary(position.getId(), position.getAsset().getId(), position.getAsset().getSymbol(),
					position.getAsset().getName(), position.getQuantity(), position.getAveragePrice(),
					position.getEntryDate(), position.getStopPrice(), position.getTargetPrice(),
					position.getTargetReturnPercent(), position.getNotes(), position.getStatus(), position.getCreatedAt(),
					position.getUpdatedAt(), position.getClosedAt(), accompaniedStudyModel);
		}
	}

	public record AccompaniedStudyModelSummary(
			UUID id,
			UUID acceptedThesisId,
			ThesisType thesisType,
			CustomerPositionThesisStatus status,
			int acceptedScore,
			BigDecimal acceptedPrice,
			BigDecimal acceptedPriceCeiling,
			BigDecimal acceptedSafetyMarginPercent,
			String ruleVersion,
			Instant acceptedAt,
			String notes) {

			static AccompaniedStudyModelSummary from(CustomerPositionThesis association) {
				return new AccompaniedStudyModelSummary(association.getId(), association.getAcceptedThesis().getId(),
						association.getThesisType(), association.getStatus(), association.getAcceptedScore(),
						association.getAcceptedPrice(), association.getAcceptedPriceCeiling(),
						percent(association.getAcceptedSafetyMarginPercent()), association.getRuleVersion(),
						association.getAcceptedAt(), association.getNotes());
			}

			private static BigDecimal percent(BigDecimal ratio) {
				return ratio == null ? null : ratio.multiply(new BigDecimal("100.000000")).setScale(6, RoundingMode.HALF_UP);
			}
		}
	}
