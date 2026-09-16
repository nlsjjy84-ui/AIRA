package com.aira.api.personalfinance.dto;

import com.aira.api.personalfinance.domain.BudgetCategory;
import java.math.BigDecimal;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        String month,
        BudgetCategory category,
        BigDecimal amount,
        String currencyCode) {
}
