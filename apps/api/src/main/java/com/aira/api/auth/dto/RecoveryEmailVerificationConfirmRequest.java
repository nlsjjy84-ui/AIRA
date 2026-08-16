package com.aira.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecoveryEmailVerificationConfirmRequest(
        @NotBlank @Size(max = 512) String token) {}
