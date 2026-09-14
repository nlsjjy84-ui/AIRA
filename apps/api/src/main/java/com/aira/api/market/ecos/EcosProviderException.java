package com.aira.api.market.ecos;

public final class EcosProviderException extends RuntimeException {
    public enum Category {
        NO_DATA,
        AUTHENTICATION,
        INVALID_REQUEST,
        RATE_LIMIT,
        PROVIDER_FAILURE,
        MALFORMED_RESPONSE,
        TRANSPORT
    }

    private final Category category;
    private final String providerCode;

    public EcosProviderException(Category category, String message) {
        this(category, message, null);
    }

    public EcosProviderException(Category category, String message, String providerCode) {
        super(message);
        this.category = category;
        this.providerCode = providerCode;
    }

    public Category category() {
        return category;
    }

    public String providerCode() {
        return providerCode;
    }
}
