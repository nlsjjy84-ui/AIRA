package com.aira.api.market.opendart;

public final class OpenDartProviderException extends RuntimeException {
    public enum Category {
        NO_DATA, AUTHENTICATION, INVALID_REQUEST, RATE_LIMIT, PROVIDER_FAILURE,
        MALFORMED_RESPONSE, TRANSPORT
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
