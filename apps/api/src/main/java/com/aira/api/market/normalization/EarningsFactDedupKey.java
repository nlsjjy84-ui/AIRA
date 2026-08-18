package com.aira.api.market.normalization;

import com.aira.api.market.domain.FactPredicate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;

public final class EarningsFactDedupKey {
    private static final String PREFIX = "AIRA|FACT|V1|EARNINGS|";

    private EarningsFactDedupKey() {}

    public static byte[] create(FactPredicate predicate, String subjectCanonicalKey,
            LocalDate periodStart, LocalDate periodEnd, String currencyCode) {
        if (predicate == null || subjectCanonicalKey == null || subjectCanonicalKey.isBlank()
                || periodStart == null || periodEnd == null || periodStart.isAfter(periodEnd)
                || currencyCode == null || !currencyCode.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Fact dedup identity values are invalid");
        }
        String canonical = PREFIX + predicate.name() + "|" + subjectCanonicalKey + "|"
                + periodStart + "|" + periodEnd + "|" + currencyCode;
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
