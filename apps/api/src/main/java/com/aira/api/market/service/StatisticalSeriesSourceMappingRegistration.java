package com.aira.api.market.service;

import java.util.UUID;

public record StatisticalSeriesSourceMappingRegistration(
        UUID statisticalSeriesId,
        UUID sourceId,
        String providerBindingKey,
        String providerSeriesName,
        String providerItemName,
        String providerFrequencyCode,
        String providerUnitName,
        String metadataLocator) {

    public StatisticalSeriesSourceMappingRegistration {
        if (statisticalSeriesId == null || sourceId == null) {
            throw new IllegalArgumentException("Statistical series and source are required");
        }
        providerBindingKey = required(providerBindingKey, 512, "Provider binding key is required");
        providerSeriesName = required(providerSeriesName, Integer.MAX_VALUE,
                "Provider series name is required");
        providerItemName = required(providerItemName, Integer.MAX_VALUE,
                "Provider item name is required");
        providerFrequencyCode = required(providerFrequencyCode, 32,
                "Provider frequency code is required");
        providerUnitName = required(providerUnitName, 100, "Provider unit name is required");
        metadataLocator = required(metadataLocator, Integer.MAX_VALUE,
                "Metadata locator is required");
    }

    private static String required(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        if (!value.equals(value.trim()) || value.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
