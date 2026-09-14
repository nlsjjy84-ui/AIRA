package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aira.api.market.domain.EvidenceType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EcosRealGdpEvidenceSnapshotFactoryTests {
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-09-12T00:00:00Z");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-09-12T01:00:00Z");

    @Test
    void createsExactSecretFreeEvidenceRegistration() {
        var registration = EcosRealGdpEvidenceSnapshotFactory.create(
                result("2026Q1", "2026Q2", 1, 2,
                        observation("2026Q1", "596692.8"),
                        observation("2026Q2", "600474.9")), T1);

        assertEquals(EvidenceType.OFFICIAL_DATA, registration.evidenceType());
        assertEquals("BOK_ECOS:StatisticSearch:v1:kr:200Y104:Q:2026Q1:2026Q2:1400:-:-:-",
                registration.externalId());
        assertEquals("https://ecos.bok.or.kr/api/", registration.originalUrl());
        assertEquals("BOK ECOS official StatisticSearch: real GDP", registration.title());
        assertEquals("StatisticSearch/json/kr/200Y104/Q/2026Q1/2026Q2/1400/-/-/-",
                registration.locator());
        assertNull(registration.publishedAt());
        assertEquals(T1, registration.collectedAt());
        assertEquals(1, registration.revision());
        assertEquals(32, registration.contentHash().length);
        assertEquals("f5b2af763e00e587f8764cdc9fc1c5f0834ec56cc64963bfe57e5504e5c17821",
                java.util.HexFormat.of().formatHex(registration.contentHash()));
        org.junit.jupiter.api.Assertions.assertTrue(registration.externalId().length() <= 255);
        assertFalse(registration.externalId().contains("KEY"));
        assertFalse(registration.locator().contains("KEY"));
        assertFalse(registration.originalUrl().contains("KEY"));
    }

    @Test
    void hashIgnoresObservationOrderExecutionMetadataAndCollectionTime() {
        var q1 = observation("2026Q1", "596692.8");
        var q2 = observation("2026Q2", "600474.9");
        var first = EcosRealGdpEvidenceSnapshotFactory.create(
                result("2026Q1", "2026Q2", 1, 2, q1, q2), T1);
        var second = EcosRealGdpEvidenceSnapshotFactory.create(
                result("2026Q1", "2026Q2", 9, 18, q2, q1), T2);

        assertArrayEquals(first.contentHash(), second.contentHash());
        assertEquals(first.externalId(), second.externalId());
        assertEquals(first.locator(), second.locator());
        assertNotEquals(first.collectedAt(), second.collectedAt());
    }

    @Test
    void hashChangesWhenProviderDataChanges() {
        var first = EcosRealGdpEvidenceSnapshotFactory.create(
                result("2026Q1", "2026Q1", 1, 2,
                        observation("2026Q1", "596692.8")), T1);
        var changed = EcosRealGdpEvidenceSnapshotFactory.create(
                result("2026Q1", "2026Q1", 1, 2,
                        observation("2026Q1", "596692.9")), T1);

        assertFalse(java.util.Arrays.equals(first.contentHash(), changed.contentHash()));
    }

    @Test
    void nullAndEmptyProviderValuesProduceDifferentHashes() {
        var nullValue = EcosRealGdpEvidenceSnapshotFactory.create(
                result("2026Q1", "2026Q1", 1, 2,
                        observation("2026Q1", null)), T1);
        var emptyValue = EcosRealGdpEvidenceSnapshotFactory.create(
                result("2026Q1", "2026Q1", 1, 2,
                        observation("2026Q1", "")), T1);

        assertFalse(java.util.Arrays.equals(nullValue.contentHash(), emptyValue.contentHash()));
    }

    @Test
    void scopeIsPartOfIdentityAndContentHash() {
        var observation = observation("2026Q1", "596692.8");
        var narrow = EcosRealGdpEvidenceSnapshotFactory.create(
                result("2026Q1", "2026Q1", 1, 2, observation), T1);
        var wider = EcosRealGdpEvidenceSnapshotFactory.create(
                result("2026Q1", "2026Q2", 1, 2, observation), T1);

        assertNotEquals(narrow.externalId(), wider.externalId());
        assertFalse(java.util.Arrays.equals(narrow.contentHash(), wider.contentHash()));
    }

    @Test
    void rejectsObservationOutsideConfirmedContract() {
        var invalid = new EcosStatisticSearchObservation(
                "WRONG", EcosRealGdpContract.STAT_NAME,
                EcosRealGdpContract.ITEM_CODE1, EcosRealGdpContract.ITEM_NAME1,
                null, null, null, null, null, null,
                EcosRealGdpContract.UNIT_NAME, null,
                "2026Q1", "1", BigDecimal.ONE);

        assertThrows(IllegalArgumentException.class,
                () -> EcosRealGdpEvidenceSnapshotFactory.create(
                        result("2026Q1", "2026Q1", 1, 2, invalid), T1));
    }

    @Test
    void rejectsRawNumericDisagreement() {
        var invalid = new EcosStatisticSearchObservation(
                EcosRealGdpContract.STAT_CODE, EcosRealGdpContract.STAT_NAME,
                EcosRealGdpContract.ITEM_CODE1, EcosRealGdpContract.ITEM_NAME1,
                null, null, null, null, null, null,
                EcosRealGdpContract.UNIT_NAME, null,
                "2026Q1", "1.0", BigDecimal.ONE);

        assertThrows(IllegalArgumentException.class,
                () -> EcosRealGdpEvidenceSnapshotFactory.create(
                        result("2026Q1", "2026Q1", 1, 2, invalid), T1));
    }

    @Test
    void rejectsMissingCollectionContext() {
        var result = result("2026Q1", "2026Q1", 1, 2,
                observation("2026Q1", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> EcosRealGdpEvidenceSnapshotFactory.create(null, T1));
        assertThrows(IllegalArgumentException.class,
                () -> EcosRealGdpEvidenceSnapshotFactory.create(result, null));
    }

    private static EcosRealGdpObservationResult result(
            String startTime,
            String endTime,
            int pageRequests,
            int worstCaseHttpAttempts,
            EcosStatisticSearchObservation... observations) {
        return new EcosRealGdpObservationResult(
                startTime, endTime, quarterCount(startTime, endTime),
                observations.length, pageRequests, worstCaseHttpAttempts,
                List.of(observations));
    }

    private static long quarterCount(String startTime, String endTime) {
        return quarterOrdinal(endTime) - quarterOrdinal(startTime) + 1L;
    }

    private static long quarterOrdinal(String value) {
        long year = Long.parseLong(value.substring(0, 4));
        long quarter = value.charAt(5) - '0';
        return year * 4L + quarter;
    }

    private static EcosStatisticSearchObservation observation(String time, String raw) {
        BigDecimal numeric = raw == null || raw.isEmpty() ? null : new BigDecimal(raw);
        return new EcosStatisticSearchObservation(
                EcosRealGdpContract.STAT_CODE,
                EcosRealGdpContract.STAT_NAME,
                EcosRealGdpContract.ITEM_CODE1,
                EcosRealGdpContract.ITEM_NAME1,
                null, null, null, null, null, null,
                EcosRealGdpContract.UNIT_NAME,
                null,
                time,
                raw,
                numeric);
    }
}
