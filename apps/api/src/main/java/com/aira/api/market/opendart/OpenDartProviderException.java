package com.aira.api.market.opendart;

public final class OpenDartProviderException extends RuntimeException {
    public enum Category {
        NO_DATA, AUTHENTICATION, INVALID_REQUEST, RATE_LIMIT, PROVIDER_FAILURE,
        MALFORMED_RESPONSE, TRANSPORT,
        PERIOD_WITNESS_MISSING, PERIOD_WITNESS_IDENTITY_MISMATCH,
        PERIOD_WITNESS_MALFORMED, PERIOD_WITNESS_CONFLICT, LEGACY_PERIOD_MISMATCH
    }

    private final Category category;

    public OpenDartProviderException(Category category, String message) {
        super(message);
        this.category = category;
    }

    public Category category() {
        return category;
    }
}
