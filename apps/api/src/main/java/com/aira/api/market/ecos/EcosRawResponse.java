package com.aira.api.market.ecos;

public record EcosRawResponse(int statusCode, String body) {
    public EcosRawResponse {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be a valid HTTP status");
        }
        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }
    }
}
