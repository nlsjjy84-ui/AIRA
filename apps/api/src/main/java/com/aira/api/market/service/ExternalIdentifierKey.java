package com.aira.api.market.service;

public record ExternalIdentifierKey(
        String namespace,
        String identifierType,
        String identifierValue) {

    public ExternalIdentifierKey {
        namespace = required(namespace, 64, "External identifier namespace is required");
        identifierType = required(identifierType, 64, "External identifier type is required");
        identifierValue = required(identifierValue, 255, "External identifier value is required");
        if ("OPENDART".equals(namespace) && "CORP_CODE".equals(identifierType)
                && !identifierValue.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("OpenDART corp code must contain eight digits");
        }
    }

    private static String required(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
