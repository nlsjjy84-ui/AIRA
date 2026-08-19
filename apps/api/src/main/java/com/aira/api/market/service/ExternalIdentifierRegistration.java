package com.aira.api.market.service;

import java.util.UUID;

public record ExternalIdentifierRegistration(
        UUID entityId,
        ExternalIdentifierKey identifier) {

    public ExternalIdentifierRegistration {
        if (entityId == null || identifier == null) {
            throw new IllegalArgumentException("Entity and external identifier are required");
        }
    }
}
