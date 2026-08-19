package com.aira.api.market.service;

import com.aira.api.market.domain.SourceType;
import java.util.Locale;

public record SourceRegistration(
        SourceType sourceType,
        String externalKey,
        String name,
        String canonicalDomain) {

    public SourceRegistration {
        if (sourceType == null) {
            throw new IllegalArgumentException("Source type is required");
        }
        externalKey = requiredTrimmed(externalKey, "Source external key is required", 200);
        name = requiredTrimmed(name, "Source name is required", 200);
        canonicalDomain = optionalDomain(canonicalDomain);
    }

    private static String requiredTrimmed(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String optionalDomain(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 255
                || normalized.contains("://") || normalized.contains("/")
                || normalized.contains(" ")) {
            throw new IllegalArgumentException("Canonical domain must be a host name");
        }
        return normalized;
    }
}
