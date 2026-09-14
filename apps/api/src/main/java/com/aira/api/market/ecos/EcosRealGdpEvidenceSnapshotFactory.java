package com.aira.api.market.ecos;

import com.aira.api.market.domain.EvidenceType;
import com.aira.api.market.ingestion.EvidenceRegistration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Comparator;

final class EcosRealGdpEvidenceSnapshotFactory {
    private static final String HASH_VERSION = "bok-ecos-statisticsearch-real-gdp-v1";
    private static final String API_ROOT = "https://ecos.bok.or.kr/api/";
    private static final String TITLE = "BOK ECOS official StatisticSearch: real GDP";
    private static final int REVISION = 1;

    private EcosRealGdpEvidenceSnapshotFactory() {
    }

    static EvidenceRegistration create(
            EcosRealGdpObservationResult result,
            OffsetDateTime collectedAt) {
        if (result == null) throw new IllegalArgumentException("result must not be null");
        if (collectedAt == null) throw new IllegalArgumentException("collectedAt must not be null");
        validateResult(result);
        return new EvidenceRegistration(
                EvidenceType.OFFICIAL_DATA,
                externalId(result),
                API_ROOT,
                TITLE,
                contentHash(result),
                locator(result),
                null,
                collectedAt,
                REVISION);
    }

    private static void validateResult(EcosRealGdpObservationResult result) {
        long expectedQuarters = quarterOrdinal(result.endTime())
                - quarterOrdinal(result.startTime()) + 1L;
        if (expectedQuarters < 1 || result.requestedQuarters() != expectedQuarters) {
            throw new IllegalArgumentException("observation scope counters are inconsistent");
        }
        if (result.totalCount() > expectedQuarters) {
            throw new IllegalArgumentException("observation total exceeds requested scope");
        }
        var times = new java.util.HashSet<String>();
        for (EcosStatisticSearchObservation observation : result.observations()) {
            validateObservation(result, observation, times);
        }
    }

    private static void validateObservation(
            EcosRealGdpObservationResult result,
            EcosStatisticSearchObservation observation,
            java.util.Set<String> times) {
        if (observation == null
                || !EcosRealGdpContract.STAT_CODE.equals(observation.statCode())
                || !EcosRealGdpContract.STAT_NAME.equals(observation.statName())
                || !EcosRealGdpContract.ITEM_CODE1.equals(observation.itemCode1())
                || !EcosRealGdpContract.ITEM_NAME1.equals(observation.itemName1())
                || !EcosRealGdpContract.UNIT_NAME.equals(observation.unitName())) {
            throw new IllegalArgumentException("observation does not match REAL_GDP contract");
        }
        if (observation.itemCode2() != null || observation.itemName2() != null
                || observation.itemCode3() != null || observation.itemName3() != null
                || observation.itemCode4() != null || observation.itemName4() != null
                || observation.weight() != null) {
            throw new IllegalArgumentException("observation dimensions do not match REAL_GDP contract");
        }
        long time = quarterOrdinal(observation.time());
        if (time < quarterOrdinal(result.startTime()) || time > quarterOrdinal(result.endTime())) {
            throw new IllegalArgumentException("observation TIME is outside evidence scope");
        }
        if (!times.add(observation.time())) {
            throw new IllegalArgumentException("duplicate observation TIME in evidence snapshot");
        }
        validateDataValue(observation);
    }

    private static void validateDataValue(EcosStatisticSearchObservation observation) {
        String raw = observation.rawDataValue();
        java.math.BigDecimal numeric = observation.numericValue();
        if (raw == null || raw.isEmpty()) {
            if (numeric != null) {
                throw new IllegalArgumentException("blank DATA_VALUE must not have numeric value");
            }
            return;
        }
        if (!raw.matches("-?[0-9]+(?:\\.[0-9]+)?")) {
            throw new IllegalArgumentException("DATA_VALUE is not a strict decimal string");
        }
        java.math.BigDecimal parsed = new java.math.BigDecimal(raw);
        if (numeric == null || !parsed.equals(numeric)) {
            throw new IllegalArgumentException("DATA_VALUE raw and numeric forms disagree");
        }
    }

    private static long quarterOrdinal(String value) {
        if (value == null || !value.matches("[0-9]{4}Q[1-4]")) {
            throw new IllegalArgumentException("quarter must match YYYYQn");
        }
        long year = Long.parseLong(value.substring(0, 4));
        long quarter = value.charAt(5) - '0';
        return year * 4L + quarter;
    }

    // AIRA deterministic request identity; this is not a provider-native document ID.
    private static String externalId(EcosRealGdpObservationResult result) {
        return String.join(":",
                "BOK_ECOS", "StatisticSearch", "v1", "kr",
                EcosRealGdpContract.STAT_CODE,
                EcosRealGdpContract.CYCLE,
                result.startTime(), result.endTime(),
                EcosRealGdpContract.ITEM_CODE1,
                "-", "-", "-");
    }

    private static String locator(EcosRealGdpObservationResult result) {
        return String.join("/",
                "StatisticSearch", "json", "kr",
                EcosRealGdpContract.STAT_CODE,
                EcosRealGdpContract.CYCLE,
                result.startTime(), result.endTime(),
                EcosRealGdpContract.ITEM_CODE1,
                "-", "-", "-");
    }

    private static byte[] contentHash(EcosRealGdpObservationResult result) {
        StringBuilder canonical = new StringBuilder(HASH_VERSION).append('\n');
        appendValue(canonical, "StatisticSearch");
        appendValue(canonical, "json");
        appendValue(canonical, "kr");
        appendValue(canonical, EcosRealGdpContract.STAT_CODE);
        appendValue(canonical, EcosRealGdpContract.CYCLE);
        appendValue(canonical, result.startTime());
        appendValue(canonical, result.endTime());
        appendValue(canonical, EcosRealGdpContract.ITEM_CODE1);
        appendValue(canonical, null);
        appendValue(canonical, null);
        appendValue(canonical, null);
        appendValue(canonical, Long.toString(result.totalCount()));
        canonical.append('\n');
        result.observations().stream()
                .sorted(Comparator.comparing(EcosStatisticSearchObservation::time))
                .forEach(observation -> {
                    appendValue(canonical, observation.statCode());
                    appendValue(canonical, observation.statName());
                    appendValue(canonical, observation.itemCode1());
                    appendValue(canonical, observation.itemName1());
                    appendValue(canonical, observation.itemCode2());
                    appendValue(canonical, observation.itemName2());
                    appendValue(canonical, observation.itemCode3());
                    appendValue(canonical, observation.itemName3());
                    appendValue(canonical, observation.itemCode4());
                    appendValue(canonical, observation.itemName4());
                    appendValue(canonical, observation.unitName());
                    appendValue(canonical, observation.weight());
                    appendValue(canonical, observation.time());
                    appendValue(canonical, observation.rawDataValue());
                    canonical.append('\n');
                });
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void appendValue(StringBuilder target, String value) {
        if (value == null) {
            target.append("-1:");
        } else {
            target.append(value.length()).append(':').append(value);
        }
    }
}
