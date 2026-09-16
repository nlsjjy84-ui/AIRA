package com.aira.api.personalfinance.dto;

import com.aira.api.personalfinance.domain.BudgetCategory;
import java.math.BigDecimal;

public record CategorySpendingResponse(
        BudgetCategory category,
        BigDecimal spent,
        BigDecimal budget,
        BigDecimal remaining) {
}
