package com.freirelts.araripe_invest_api.application.alerts;

import com.freirelts.araripe_invest_api.domain.alerts.AssetWatchItem;
import com.freirelts.araripe_invest_api.domain.alerts.AssetWatchStatus;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetWatchItemRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WatchedAssetService {

	private static final String REGULATORY_NOTICE = "Ativo acompanhado para alertas informativos definidos pelo usuario; nao ha suitability nem recomendacao de compra, venda, manutencao, aumento, reducao, alocacao ou encerramento.";

	private final UserRepository userRepository;
	private final AssetRepository assetRepository;
	private final AssetWatchItemRepository watchItemRepository;

	public WatchedAssetService(UserRepository userRepository, AssetRepository assetRepository,
			AssetWatchItemRepository watchItemRepository) {
		this.userRepository = userRepository;
		this.assetRepository = assetRepository;
		this.watchItemRepository = watchItemRepository;
	}

	@Transactional(readOnly = true)
	public List<WatchedAssetSummary> listActive(UUID userId) {
		requireCustomer(userId);
		return watchItemRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, AssetWatchStatus.ACTIVE)
				.stream()
				.map(WatchedAssetSummary::from)
				.toList();
	}

	@Transactional
	public WatchedAssetSummary createOrUpdate(UUID userId, WatchedAssetInput input) {
		User user = requireCustomer(userId);
		validate(input);
		Asset asset = assetRepository.findById(input.assetId())
				.orElseThrow(() -> notFound("Asset not found."));
		AssetWatchItem item = watchItemRepository.findByUserIdAndAssetIdAndStatus(userId, asset.getId(),
				AssetWatchStatus.ACTIVE)
				.orElseGet(() -> new AssetWatchItem(user, asset));
		apply(item, input);
		return WatchedAssetSummary.from(watchItemRepository.saveAndFlush(item));
	}

	@Transactional
	public WatchedAssetSummary update(UUID userId, UUID watchItemId, WatchedAssetPreferencesInput input) {
		requireCustomer(userId);
		validate(input);
		AssetWatchItem item = watchItemRepository.findById(watchItemId)
				.filter(existing -> existing.getUser().getId().equals(userId)
						&& existing.getStatus() == AssetWatchStatus.ACTIVE)
				.orElseThrow(() -> notFound("Watched asset not found."));
		item.setUserLowerPriceThreshold(input.userLowerPriceThreshold());
		item.setUserUpperPriceThreshold(input.userUpperPriceThreshold());
		item.setNotes(trimToNull(input.notes()));
		item.setUpdatedAt(Instant.now());
		return WatchedAssetSummary.from(watchItemRepository.saveAndFlush(item));
	}

	@Transactional
	public WatchedAssetSummary archive(UUID userId, UUID watchItemId) {
		requireCustomer(userId);
		AssetWatchItem item = watchItemRepository.findById(watchItemId)
				.filter(existing -> existing.getUser().getId().equals(userId)
						&& existing.getStatus() == AssetWatchStatus.ACTIVE)
				.orElseThrow(() -> notFound("Watched asset not found."));
		item.setStatus(AssetWatchStatus.ARCHIVED);
		item.setArchivedAt(Instant.now());
		item.setUpdatedAt(item.getArchivedAt());
		return WatchedAssetSummary.from(watchItemRepository.saveAndFlush(item));
	}

	private User requireCustomer(UUID userId) {
		return userRepository.findById(userId)
				.filter(user -> user.hasValidAuthenticatedAccess() && user.hasRole(UserRoleType.CUSTOMER))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user."));
	}

	private void apply(AssetWatchItem item, WatchedAssetInput input) {
		item.setUserLowerPriceThreshold(input.userLowerPriceThreshold());
		item.setUserUpperPriceThreshold(input.userUpperPriceThreshold());
		item.setNotes(trimToNull(input.notes()));
		item.setUpdatedAt(Instant.now());
	}

	private void validate(WatchedAssetInput input) {
		if (input.assetId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset is required.");
		}
		validate((WatchedAssetPreferencesInput) input);
	}

	private void validate(WatchedAssetPreferencesInput input) {
		validatePositiveOptional(input.userLowerPriceThreshold(), "User lower price threshold");
		validatePositiveOptional(input.userUpperPriceThreshold(), "User upper price threshold");
		if (input.userLowerPriceThreshold() != null && input.userUpperPriceThreshold() != null
				&& input.userLowerPriceThreshold().compareTo(input.userUpperPriceThreshold()) >= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"User lower price threshold must be below user upper price threshold.");
		}
	}

	private void validatePositiveOptional(BigDecimal value, String fieldName) {
		if (value != null && value.signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be greater than zero.");
		}
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

	public record WatchedAssetInput(
			UUID assetId,
			BigDecimal userLowerPriceThreshold,
			BigDecimal userUpperPriceThreshold,
			String notes) implements WatchedAssetPreferencesInput {
	}

	public sealed interface WatchedAssetPreferencesInput permits WatchedAssetInput, UpdateWatchedAssetPreferencesInput {
		BigDecimal userLowerPriceThreshold();

		BigDecimal userUpperPriceThreshold();

		String notes();
	}

	public record UpdateWatchedAssetPreferencesInput(
			BigDecimal userLowerPriceThreshold,
			BigDecimal userUpperPriceThreshold,
			String notes) implements WatchedAssetPreferencesInput {
	}

	public record WatchedAssetSummary(
			UUID id,
			UUID assetId,
			String symbol,
			String assetName,
			String sector,
			AssetWatchStatus status,
			BigDecimal userLowerPriceThreshold,
			BigDecimal userUpperPriceThreshold,
			UUID sourcePositionId,
			UUID accompaniedStudyModelId,
			ThesisType accompaniedStudyType,
			String notes,
			Instant createdAt,
			Instant updatedAt,
			Instant archivedAt,
			String regulatoryNotice) {

		static WatchedAssetSummary from(AssetWatchItem item) {
			ThesisType studyType = item.getAccompaniedStudyModel() == null ? null
					: item.getAccompaniedStudyModel().getThesisType();
			return new WatchedAssetSummary(item.getId(), item.getAsset().getId(), item.getAsset().getSymbol(),
					item.getAsset().getName(), item.getAsset().getSector(), item.getStatus(),
					item.getUserLowerPriceThreshold(), item.getUserUpperPriceThreshold(),
					item.getSourcePosition() == null ? null : item.getSourcePosition().getId(),
					item.getAccompaniedStudyModel() == null ? null : item.getAccompaniedStudyModel().getId(), studyType,
					item.getNotes(), item.getCreatedAt(), item.getUpdatedAt(), item.getArchivedAt(),
					REGULATORY_NOTICE);
		}
	}
}
