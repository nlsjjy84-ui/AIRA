package com.aira.api.auth.dto;

import jakarta.validation.constraints.NotNull;

public record SignupRequest(@NotNull String nickname, @NotNull String password) {
    @Override
    public String toString() {
        return "SignupRequest[nickname=" + nickname + ", password=<redacted>]";
    }
}
