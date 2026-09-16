package com.aira.api.personalfinance.dto;

import com.aira.api.personalfinance.domain.BudgetCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record BudgetUpsertRequest(
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String month,
        @NotNull BudgetCategory category,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode) {
}
