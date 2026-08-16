package com.aira.api.auth.dto;

import jakarta.validation.constraints.*;

public record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {}
