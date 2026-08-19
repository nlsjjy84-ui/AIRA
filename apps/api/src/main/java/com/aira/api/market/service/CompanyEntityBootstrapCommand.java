package com.aira.api.market.service;

public record CompanyEntityBootstrapCommand(
        String canonicalName,
        String countryCode) {

    public CompanyEntityBootstrapCommand(String canonicalName) {
        this(canonicalName, null);
    }
}
