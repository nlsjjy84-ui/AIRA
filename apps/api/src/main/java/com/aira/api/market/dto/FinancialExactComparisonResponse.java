package com.aira.api.market.dto;

import com.aira.api.market.domain.FactPredicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FinancialExactComparisonResponse(CanonicalDataState state, String reason,
        UUID companyId, ExactPeriod a, ExactPeriod b, List<Metric> metrics) {
    public record ExactPeriod(LocalDate periodStart, LocalDate periodEnd, String receipt) {}
    public record Observation(BigDecimal value, String currency, List<UUID> evidenceIds) {}
    public record Metric(FactPredicate predicate, Observation a, Observation b,
            BigDecimal changeAmountBMinusA, BigDecimal changePercentBOverA, String percentReason) {}
}
