package com.aira.api.personalfinance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FinanceReauthenticationRequest(
        @NotBlank @Size(max = 72) String password) {
}
