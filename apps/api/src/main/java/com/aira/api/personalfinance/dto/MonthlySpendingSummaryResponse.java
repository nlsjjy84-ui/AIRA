package com.aira.api.personalfinance.dto;

import java.math.BigDecimal;
import java.util.List;

public record MonthlySpendingSummaryResponse(
        String month,
        String currencyCode,
        BigDecimal totalSpent,
        BigDecimal totalBudget,
        BigDecimal totalRemaining,
        List<CategorySpendingResponse> categories) {
}
