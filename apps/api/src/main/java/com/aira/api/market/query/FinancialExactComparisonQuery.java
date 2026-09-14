package com.aira.api.market.query;

import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.dto.CanonicalDataState;
import com.aira.api.market.dto.FinancialExactComparisonResponse;
import com.aira.api.market.dto.FinancialExactComparisonResponse.ExactPeriod;
import com.aira.api.market.dto.FinancialExactComparisonResponse.Metric;
import com.aira.api.market.dto.FinancialExactComparisonResponse.Observation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FinancialExactComparisonQuery {
    private static final Set<FactPredicate> SUPPORTED = Set.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME);
    private final FinancialHistoricalExactQuery exact;
    public FinancialExactComparisonQuery(FinancialHistoricalExactQuery exact) { this.exact = exact; }

    public FinancialExactComparisonResponse find(UUID companyId, ExactPeriod a, ExactPeriod b,
            Set<FactPredicate> predicates) {
        if (companyId == null || !valid(a) || !valid(b) || predicates == null || predicates.isEmpty()
                || !SUPPORTED.containsAll(predicates)) return failure(CanonicalDataState.UNSUPPORTED,
                "INVALID_EXACT_SELECTION", companyId, a, b);
        List<Metric> metrics = new ArrayList<>();
        for (FactPredicate predicate : predicates.stream().sorted().toList()) {
            Observation left, right;
            try { left = observation(companyId, a, predicate); }
            catch (CompanyFinancialFactsQueryException missing) { return failure(state(missing),
                    "A_" + reason(missing), companyId, a, b); }
            try { right = observation(companyId, b, predicate); }
            catch (CompanyFinancialFactsQueryException missing) { return failure(state(missing),
                    "B_" + reason(missing), companyId, a, b); }
            if (!left.currency().equals(right.currency())) return failure(CanonicalDataState.BLOCKED,
                    "CURRENCY_MISMATCH", companyId, a, b);
            BigDecimal delta = right.value().subtract(left.value());
            // A zero or negative base cannot support an ordinary percent-change claim.
            BigDecimal percent = left.value().signum() > 0
                    ? delta.multiply(new BigDecimal("100")).divide(left.value(), 4, RoundingMode.HALF_UP) : null;
            metrics.add(new Metric(predicate, left, right, delta, percent,
                    percent == null ? "BASE_NON_POSITIVE" : null));
        }
        return new FinancialExactComparisonResponse(CanonicalDataState.AVAILABLE, null,
                companyId, a, b, List.copyOf(metrics));
    }

    private Observation observation(UUID companyId, ExactPeriod period, FactPredicate predicate) {
        var result = exact.find(companyId, period.periodStart(), period.periodEnd(), period.receipt(),
                Set.of(predicate.name()));
        var views = result.facts().stream().filter(v -> v.predicate() == predicate).toList();
        if (views.isEmpty()) throw new CompanyFinancialFactsQueryException(
                CompanyFinancialFactsQueryException.Category.FACTS_NOT_FOUND, "Exact metric missing");
        BigDecimal value = views.getFirst().value();
        String currency = views.getFirst().currency();
        if (value == null || currency == null || views.stream().anyMatch(v ->
                v.value() == null || v.value().compareTo(value) != 0 || !currency.equals(v.currency())))
            throw new CompanyFinancialFactsQueryException(
                    CompanyFinancialFactsQueryException.Category.INCONSISTENT_PROVENANCE,
                    "Ambiguous exact financial metric");
        List<UUID> evidenceIds = views.stream().map(CompanyFinancialFactView::evidenceId)
                .distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        return new Observation(value, currency, evidenceIds);
    }

    private static boolean valid(ExactPeriod period) {
        return period != null && period.periodStart() != null && period.periodEnd() != null
                && !period.periodStart().isAfter(period.periodEnd()) && period.receipt() != null
                && period.receipt().matches("[0-9]{14}");
    }
    private static CanonicalDataState state(CompanyFinancialFactsQueryException e) {
        return switch (e.category()) {
            case FACTS_NOT_FOUND, COMPANY_NOT_FOUND -> CanonicalDataState.NO_DATA;
            case UNSUPPORTED_PREDICATE -> CanonicalDataState.UNSUPPORTED;
            case INCONSISTENT_PROVENANCE -> CanonicalDataState.BLOCKED;
        };
    }
    private static String reason(CompanyFinancialFactsQueryException e) { return e.category().name(); }
    private static FinancialExactComparisonResponse failure(CanonicalDataState state, String reason,
            UUID companyId, ExactPeriod a, ExactPeriod b) {
        return new FinancialExactComparisonResponse(state, reason, companyId, a, b, List.of());
    }
}
