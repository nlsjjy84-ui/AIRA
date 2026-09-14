package com.aira.api.market.query;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FinancialCurrentQuery {
    private final FinancialHistoricalExactQuery exact;
    public FinancialCurrentQuery(FinancialHistoricalExactQuery exact) { this.exact = exact; }

    public CompanyFinancialFactsResult find(ExplicitSelection selection) {
        if (selection == null) throw new IllegalArgumentException("Explicit financial selection is required");
        // This boundary has no accepted latest-annual rule; the caller must supply its chosen filing and period.
        return exact.find(selection.companyId(), selection.periodStart(), selection.periodEnd(),
                selection.receipt(), selection.predicates());
    }

    public record ExplicitSelection(UUID companyId, LocalDate periodStart, LocalDate periodEnd,
            String receipt, Set<String> predicates) {}
}
