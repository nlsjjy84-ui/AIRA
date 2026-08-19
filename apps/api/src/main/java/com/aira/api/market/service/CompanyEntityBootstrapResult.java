package com.aira.api.market.service;

import java.util.UUID;

public record CompanyEntityBootstrapResult(
        UUID entityId,
        String canonicalKey,
        String canonicalName,
        String countryCode) {

    public CompanyEntityBootstrapResult {
        if (entityId == null || canonicalKey == null || canonicalName == null) {
            throw new IllegalArgumentException("Company bootstrap result is incomplete");
        }
    }
}
