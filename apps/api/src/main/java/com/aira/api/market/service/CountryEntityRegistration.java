package com.aira.api.market.service;

public record CountryEntityRegistration(
        String canonicalName,
        String countryCode) {

    public CountryEntityRegistration {
        if (canonicalName == null || canonicalName.isBlank()) {
            throw new IllegalArgumentException("Country canonical name is required");
        }
        canonicalName = canonicalName.trim();
        if (canonicalName.length() > 300) {
            throw new IllegalArgumentException("Country canonical name is required");
        }
        if (countryCode == null || !countryCode.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException(
                    "Country code must be an ISO alpha-2 uppercase code");
        }
    }

    public String canonicalKey() {
        return "COUNTRY:" + countryCode;
    }
}
