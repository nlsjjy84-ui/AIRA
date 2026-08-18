package com.aira.api.market.normalization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;

public final class EarningsEventDedupKey {
    private static final String PREFIX = "AIRA|EVENT|V1|EARNINGS|RESULTS_RELEASE|";

    private EarningsEventDedupKey() {}

    public static byte[] create(String subjectCanonicalKey, LocalDate reportingPeriodEnd) {
        if (subjectCanonicalKey == null || subjectCanonicalKey.isBlank()
                || reportingPeriodEnd == null) {
            throw new IllegalArgumentException("Earnings dedup identity values are required");
        }
        String canonicalIdentity = PREFIX + subjectCanonicalKey + "|" + reportingPeriodEnd;
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonicalIdentity.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
