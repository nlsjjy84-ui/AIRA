package com.aira.api.auth.security;

import java.security.Principal;
import java.util.UUID;

public record AiraPrincipal(UUID userId, String nickname) implements Principal {
    public AiraPrincipal {
        if (userId == null || nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("Valid principal values are required");
        }
    }

    @Override
    public String getName() {
        return nickname;
    }
}
