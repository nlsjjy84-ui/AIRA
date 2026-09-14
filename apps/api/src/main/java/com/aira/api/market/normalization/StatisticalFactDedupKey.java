package com.aira.api.market.normalization;

import com.aira.api.market.domain.StatisticalAdjustment;
import com.aira.api.market.domain.StatisticalFrequency;
import com.aira.api.market.domain.StatisticalMetric;
import com.aira.api.market.domain.StatisticalUnit;
import com.aira.api.market.domain.StatisticalValueKind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;

public final class StatisticalFactDedupKey {
    private static final String PREFIX = "AIRA|FACT|V1|STATISTICAL|";

    private StatisticalFactDedupKey() {}

    public static byte[] create(String subjectCanonicalKey,
            StatisticalMetric metric, StatisticalFrequency frequency,
            StatisticalAdjustment adjustment, StatisticalValueKind valueKind,
            StatisticalUnit unit, LocalDate periodStart, LocalDate periodEnd) {
        if (subjectCanonicalKey == null || subjectCanonicalKey.isBlank()
                || metric == null || frequency == null || adjustment == null
                || valueKind == null || unit == null || periodStart == null
                || periodEnd == null || periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("Statistical fact identity values are invalid");
        }
        String canonical = PREFIX + metric.name() + "|" + subjectCanonicalKey + "|"
                + frequency.name() + "|" + adjustment.name() + "|"
                + valueKind.name() + "|" + unit.name() + "|"
                + periodStart + "|" + periodEnd;
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
