package com.freirelts.araripe_invest_api.application.ai;

import com.freirelts.araripe_invest_api.domain.ai.AiContextAnalysis;
import com.freirelts.araripe_invest_api.domain.ai.AiValidationStatus;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AiContextAnalysisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EconomicContextAnalysisService {

	private final EconomicContextAiProvider aiProvider;
	private final AiContextAnalysisRepository aiContextAnalysisRepository;

	public EconomicContextAnalysisService(EconomicContextAiProvider aiProvider,
			AiContextAnalysisRepository aiContextAnalysisRepository) {
		this.aiProvider = aiProvider;
		this.aiContextAnalysisRepository = aiContextAnalysisRepository;
	}

	@Transactional
	public AiContextAnalysis analyzeAndPersist(Asset asset, EconomicContextAiRequest request) {
		EconomicContextAiResult result;
		try {
			result = aiProvider.analyze(request);
		}
		catch (RuntimeException ex) {
			result = EconomicContextAiResult.failed("unhandled-ai-provider", "unknown", "unknown", "unavailable",
					"{}", "[]", 0L, "AI provider failed before returning a traceable result: "
							+ ex.getClass().getSimpleName() + ".");
		}
		EconomicContextAiResult finalResult = result;

		AiContextAnalysis analysis = aiContextAnalysisRepository
				.findByAssetIdAndReferenceDateAndProviderAndModelAndPromptVersionAndPromptHash(asset.getId(),
						request.referenceDate(), finalResult.provider(), finalResult.model(), finalResult.promptVersion(),
						finalResult.promptHash())
				.orElseGet(() -> new AiContextAnalysis(asset, request.referenceDate(), finalResult.provider(),
						finalResult.model(), finalResult.promptVersion(), finalResult.promptHash()));

		analysis.setAsset(asset);
		analysis.setReferenceDate(request.referenceDate());
		analysis.setProvider(result.provider());
		analysis.setModel(result.model());
		analysis.setPromptVersion(result.promptVersion());
		analysis.setPromptHash(result.promptHash());
		analysis.setInputSummaryJson(defaultJsonObject(result.inputSummaryJson()));
		analysis.setOutputJson(result.outputJson());
		analysis.setSourcesJson(defaultJsonArray(result.sourcesJson()));
		analysis.setValidationStatus(result.validationStatus() == null ? AiValidationStatus.FAILED
				: result.validationStatus());
		analysis.setLatencyMs(result.latencyMs());
		analysis.setErrorMessage(limit(result.errorMessage()));

		return aiContextAnalysisRepository.saveAndFlush(analysis);
	}

	private String defaultJsonObject(String value) {
		return value == null || value.isBlank() ? "{}" : value;
	}

	private String defaultJsonArray(String value) {
		return value == null || value.isBlank() ? "[]" : value;
	}

	private String limit(String value) {
		if (value == null || value.length() <= 1000) {
			return value;
		}
		return value.substring(0, 1000);
	}
}
