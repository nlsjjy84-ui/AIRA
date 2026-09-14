package com.aira.api.market.ecos;

import java.util.UUID;

public record EcosRealGdpSeriesBindingResult(
        UUID countryEntityId,
        UUID sourceId,
        UUID statisticalSeriesId,
        UUID sourceMappingId) {

    public EcosRealGdpSeriesBindingResult {
        if (countryEntityId == null || sourceId == null
                || statisticalSeriesId == null || sourceMappingId == null) {
            throw new IllegalArgumentException("REAL_GDP series binding identifiers are required");
        }
    }
}
