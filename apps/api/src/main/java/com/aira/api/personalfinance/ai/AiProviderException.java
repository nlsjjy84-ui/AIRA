package com.aira.api.personalfinance.ai;

public class AiProviderException extends RuntimeException {
    private final String code;

    public AiProviderException(String code) {
        super("AI provider request failed");
        this.code = code == null || code.isBlank() ? "PROVIDER_FAILURE" : code;
    }

    public String code() { return code; }
}
