package com.aira.api.auth.service;

import com.aira.api.auth.dto.LoginResponse;

public record LoginResult(LoginResponse response, String rawSessionToken) {
    @Override
    public String toString() {
        return "LoginResult[response=" + response + ", rawSessionToken=<redacted>]";
    }
}
