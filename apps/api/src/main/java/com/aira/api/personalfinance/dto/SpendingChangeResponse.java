package com.aira.api.personalfinance.dto;

import com.aira.api.personalfinance.domain.BudgetCategory;
import com.aira.api.personalfinance.domain.SpendingChangeDirection;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SpendingChangeResponse(
        BudgetCategory scope,
        BigDecimal currentSpent,
        BigDecimal previousSpent,
        BigDecimal delta,
        BigDecimal percentChange,
        SpendingChangeDirection direction,
        List<UUID> currentTransactionIds,
        List<UUID> previousTransactionIds,
        String unknownReason) {
}
