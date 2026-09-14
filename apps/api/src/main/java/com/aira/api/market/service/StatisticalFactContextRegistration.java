package com.aira.api.market.service;

import com.aira.api.market.domain.StatisticalUnit;
import java.util.UUID;

public record StatisticalFactContextRegistration(
        UUID factId,
        UUID statisticalSeriesId,
        StatisticalUnit canonicalUnit) {

    public StatisticalFactContextRegistration {
        if (factId == null || statisticalSeriesId == null || canonicalUnit == null) {
            throw new IllegalArgumentException("Statistical fact context identity is required");
        }
    }
}
