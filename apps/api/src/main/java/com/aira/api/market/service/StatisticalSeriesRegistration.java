package com.aira.api.market.service;

import com.aira.api.market.domain.StatisticalAdjustment;
import com.aira.api.market.domain.StatisticalFrequency;
import com.aira.api.market.domain.StatisticalMetric;
import com.aira.api.market.domain.StatisticalValueKind;
import java.util.UUID;

public record StatisticalSeriesRegistration(
        UUID subjectEntityId,
        StatisticalMetric metric,
        StatisticalFrequency frequency,
        StatisticalAdjustment adjustment,
        StatisticalValueKind valueKind) {

    public StatisticalSeriesRegistration {
        if (subjectEntityId == null || metric == null || frequency == null
                || adjustment == null || valueKind == null) {
            throw new IllegalArgumentException("Statistical series identity is required");
        }
    }
}
