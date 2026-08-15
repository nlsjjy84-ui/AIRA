package com.aira.api.auth.dto;

import jakarta.validation.constraints.NotNull;

public record LoginRequest(@NotNull String nickname, @NotNull String password) {
    @Override
    public String toString() {
        return "LoginRequest[nickname=" + nickname + ", password=<redacted>]";
    }
}
