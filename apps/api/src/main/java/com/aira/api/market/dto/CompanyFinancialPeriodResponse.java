package com.aira.api.market.dto;

import com.aira.api.market.domain.FactPredicate;
import java.time.LocalDate;
import java.util.Set;

public record CompanyFinancialPeriodResponse(
        LocalDate periodStart, LocalDate periodEnd, Set<FactPredicate> predicates) {
    public CompanyFinancialPeriodResponse {
        predicates = Set.copyOf(predicates);
    }
}
