package com.aira.api.personalfinance.dto;

import com.aira.api.personalfinance.domain.FinanceConnectionSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FinanceConsentRequest(
        @NotNull FinanceConnectionSource sourceType,
        @NotBlank @Size(max = 64) String providerKey,
        @NotBlank @Size(max = 32) String policyVersion,
        boolean allowAccounts,
        boolean allowTransactions) {
}
