package com.aira.api.auth.dto;

import com.aira.api.auth.config.AuthProperties;
import jakarta.validation.constraints.*;

public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = 512) String token,
        @NotBlank @Size(min = AuthProperties.PASSWORD_MIN_LENGTH,
                max = AuthProperties.PASSWORD_MAX_LENGTH) String newPassword) {}
