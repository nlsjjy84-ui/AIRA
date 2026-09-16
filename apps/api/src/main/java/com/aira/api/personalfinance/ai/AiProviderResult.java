package com.aira.api.personalfinance.ai;

public record AiProviderResult(
        String text,
        long inputTokens,
        long outputTokens) {
    public AiProviderResult {
        if (text == null || text.isBlank() || inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("Valid AI provider result is required");
        }
    }
}
