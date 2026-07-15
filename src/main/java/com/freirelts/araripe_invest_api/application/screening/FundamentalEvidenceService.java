package com.freirelts.araripe_invest_api.application.screening;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freirelts.araripe_invest_api.application.indicators.IndicatorCalculationService;
import com.freirelts.araripe_invest_api.domain.marketdata.DataQualityStatus;
import com.freirelts.araripe_invest_api.domain.marketdata.FinancialStatementSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.FundamentalSnapshot;
import com.freirelts.araripe_invest_api.domain.marketdata.PeriodType;
import com.freirelts.araripe_invest_api.domain.marketdata.StatementType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FinancialStatementSnapshotRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.FundamentalSnapshotRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class FundamentalEvidenceService {

	private static final String DERIVED_SOURCE = "araripe-indicators";

	private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
	private final FinancialStatementSnapshotRepository financialStatementSnapshotRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public FundamentalEvidenceService(FundamentalSnapshotRepository fundamentalSnapshotRepository,
			FinancialStatementSnapshotRepository financialStatementSnapshotRepository) {
		this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
		this.financialStatementSnapshotRepository = financialStatementSnapshotRepository;
	}

	public List<EliminatoryFilterInput.FreeCashflowPeriod> freeCashflowHistory(UUID assetId, LocalDate referenceDate) {
		List<EliminatoryFilterInput.FreeCashflowPeriod> statementHistory = financialStatementSnapshotRepository
				.findByAssetIdAndStatementTypeAndPeriodTypeAndEndDateLessThanEqualAndQualityStatusOrderByEndDateDesc(
						assetId, StatementType.CASH_FLOW, PeriodType.ANNUAL, referenceDate, DataQualityStatus.VALID)
				.stream()
				.map(this::freeCashflowPeriod)
				.flatMap(Optional::stream)
				.limit(4)
				.toList();
		if (!statementHistory.isEmpty()) {
			return statementHistory;
		}
		Map<LocalDate, BigDecimal> byPeriod = new LinkedHashMap<>();
		fundamentalSnapshotRepository
				.findTop12ByAssetIdAndReferenceDateLessThanEqualAndPeriodTypeAndSourceAndCalculationVersionOrderByReferenceDateDescCreatedAtDesc(
						assetId, referenceDate, PeriodType.TTM, DERIVED_SOURCE,
						IndicatorCalculationService.CALCULATION_VERSION)
				.forEach(snapshot -> {
					LocalDate periodEndDate = snapshot.getMostRecentQuarter() == null ? snapshot.getReferenceDate()
							: snapshot.getMostRecentQuarter();
					byPeriod.putIfAbsent(periodEndDate, snapshot.getFreeCashflow());
				});
		return byPeriod.entrySet().stream()
				.limit(4)
				.map(entry -> new EliminatoryFilterInput.FreeCashflowPeriod(entry.getKey(), entry.getValue()))
				.toList();
	}

	public LocalDate latestAnnualStatementEndDate(UUID assetId, LocalDate referenceDate) {
		return financialStatementSnapshotRepository
				.findTopByAssetIdAndPeriodTypeAndEndDateLessThanEqualAndQualityStatusOrderByEndDateDesc(assetId,
						PeriodType.ANNUAL, referenceDate, DataQualityStatus.VALID)
				.map(FinancialStatementSnapshot::getEndDate)
				.orElse(null);
	}

	private Optional<EliminatoryFilterInput.FreeCashflowPeriod> freeCashflowPeriod(FinancialStatementSnapshot snapshot) {
		try {
			JsonNode root = objectMapper.readTree(snapshot.getPayloadJson());
			BigDecimal operatingCashflow = decimal(root, "operatingCashflow", "operatingCashFlow",
					"totalCashFromOperatingActivities");
			BigDecimal freeCashflow = decimal(root, "freeCashflow", "freeCashFlow");
			BigDecimal capitalExpenditures = decimal(root, "capitalExpenditures", "capitalExpenditure", "capex");
			BigDecimal value = freeCashflow == null ? calculatedFreeCashflow(operatingCashflow, capitalExpenditures)
					: freeCashflow;
			return value == null ? Optional.empty()
					: Optional.of(new EliminatoryFilterInput.FreeCashflowPeriod(snapshot.getEndDate(), value));
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not parse financial statement snapshot.", ex);
		}
	}

	private BigDecimal calculatedFreeCashflow(BigDecimal operatingCashflow, BigDecimal capitalExpenditures) {
		if (operatingCashflow == null || capitalExpenditures == null) {
			return null;
		}
		BigDecimal normalizedCapex = capitalExpenditures.signum() < 0 ? capitalExpenditures : capitalExpenditures.negate();
		return operatingCashflow.add(normalizedCapex);
	}

	private BigDecimal decimal(JsonNode root, String... aliases) {
		for (String alias : aliases) {
			BigDecimal value = findDecimal(root, alias.toLowerCase(Locale.ROOT));
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private BigDecimal findDecimal(JsonNode node, String fieldName) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isObject()) {
			var fields = node.fields();
			while (fields.hasNext()) {
				var entry = fields.next();
				if (entry.getKey().toLowerCase(Locale.ROOT).equals(fieldName) && entry.getValue().isNumber()) {
					return entry.getValue().decimalValue();
				}
				BigDecimal nested = findDecimal(entry.getValue(), fieldName);
				if (nested != null) {
					return nested;
				}
			}
		}
		if (node.isArray()) {
			for (JsonNode child : node) {
				BigDecimal nested = findDecimal(child, fieldName);
				if (nested != null) {
					return nested;
				}
			}
		}
		return null;
	}
}
