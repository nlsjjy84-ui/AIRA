package com.aira.api.market.query;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record CompanyFinancialFactsQueryInput(
        UUID companyId,
        Set<String> predicates,
        LocalDate periodStart,
        LocalDate periodEnd) {

    public CompanyFinancialFactsQueryInput {
        if (companyId == null || periodStart == null || periodEnd == null
                || periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("Company and exact reporting period are required");
        }
        predicates = predicates == null ? Set.of() : Set.copyOf(predicates);
    }
}
