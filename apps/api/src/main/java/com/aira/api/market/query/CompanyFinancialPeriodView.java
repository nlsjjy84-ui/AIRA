package com.aira.api.market.query;

import com.aira.api.market.domain.FactPredicate;
import java.time.LocalDate;
import java.util.Set;

public record CompanyFinancialPeriodView(
        LocalDate periodStart, LocalDate periodEnd, Set<FactPredicate> predicates) {}
