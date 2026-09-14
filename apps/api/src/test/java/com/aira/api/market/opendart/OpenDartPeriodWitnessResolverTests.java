package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.*;
import static com.aira.api.market.opendart.OpenDartProviderException.Category.*;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenDartPeriodWitnessResolverTests {
    private static final String CORP = "00126380";
    private static final String RECEIPT = "20260331000123";
    private static final OpenDartAnnualCfsContext CONTEXT = new OpenDartAnnualCfsContext(CORP, 2025, "11011", "CFS");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-04-01T00:00:00Z");

    @ParameterizedTest
    @CsvSource({
            "2025.01.01 ~ 2025.12.31,2025-01-01,2025-12-31",
            "2024.04.01~2025.03.31,2024-04-01,2025-03-31",
            "2025.07.01 ~ 2025.09.30,2025-07-01,2025-09-30",
            "2024.02.29 ~ 2024.03.31,2024-02-29,2024-03-31"})
    void preservesExactDatesIncludingNonDecemberShortAndLeapYear(String term, String start, String end) {
        var resolved = resolve(term, "2025.12.31 현재");
        assertEquals(start, resolved.start().toString());
        assertEquals(end, resolved.end().toString());
        assertEquals("fnlttSinglAcnt:CFS:11011:" + RECEIPT, resolved.evidence().externalId());
        assertEquals(1, resolved.evidence().revision());
        assertTrue(resolved.locator().contains("fnlttSinglAcnt/CFS/11011/" + RECEIPT + "/thstrm_dt/"));
        assertTrue(resolved.locator().endsWith(start + "/" + end));
        assertFalse(resolved.evidence().originalUrl().contains("crtfc_key"));
    }

    @Test
    void safeWhitespaceRowOrderAndCollectionTimeDoNotChangeHash() {
        var one = resolve(" 2025.01.01\t~ 2025.12.31 ", "2025.12.31 현재");
        var two = OpenDartPeriodWitnessResolver.resolve(CONTEXT, RECEIPT,
                OpenDartWitnessFixtures.witness(CORP, RECEIPT, "2025.12.31현재", "2025.01.01~2025.12.31"), NOW.plusDays(1));
        assertArrayEquals(one.evidence().contentHash(), two.evidence().contentHash());
        assertEquals(one.locator(), two.locator());
        assertNotEquals(one.evidence().collectedAt(), two.evidence().collectedAt());
        assertFalse(java.util.Arrays.equals(one.evidence().contentHash(),
                resolve("2025.01.01~2025.12.31", "2025.12.30 현재").evidence().contentHash()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025.02.29 ~ 2025.12.31", "2025.12.31 ~ 2025.01.01",
            "2025.01.01", "제 13 기", "2025.1.1 ~ 2025.12.31", "2025.13.01 현재", "sentinel-secret"})
    void rejectsMalformedDatesReversedRangesAndUnsupportedNonemptyTerms(String term) {
        var failure = assertThrows(OpenDartProviderException.class, () -> resolve(term, "2025.01.01~2025.12.31"));
        assertEquals(PERIOD_WITNESS_MALFORMED, failure.category());
        assertFalse(failure.toString().contains(term));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "2025.12.31 현재"})
    void noDurationBlocksWithoutMonthOrTermNameFallback(String term) {
        assertEquals(PERIOD_WITNESS_MISSING, assertThrows(OpenDartProviderException.class,
                () -> resolve(term)).category());
    }

    @Test
    void missingResponseNoDataAndEmptyListAreBlocked() {
        for (var response : new OpenDartPeriodWitnessResponse[] {null,
                new OpenDartPeriodWitnessResponse("013", null),
                new OpenDartPeriodWitnessResponse("000", List.of())}) {
            assertEquals(PERIOD_WITNESS_MISSING, assertThrows(OpenDartProviderException.class,
                    () -> OpenDartPeriodWitnessResolver.resolve(CONTEXT, RECEIPT, response, NOW)).category());
        }
    }

    @Test
    void distinctPairsConflictButRepeatedSamePairPasses() {
        assertEquals(PERIOD_WITNESS_CONFLICT, assertThrows(OpenDartProviderException.class,
                () -> resolve("2025.01.01~2025.12.31", "2025.04.01~2025.12.31")).category());
        assertNotNull(resolve("2025.01.01~2025.12.31", "2025.01.01~2025.12.31"));
    }

    @ParameterizedTest
    @CsvSource({"99999999,2025,11011,20260331000123,CFS",
            "00126380,2024,11011,20260331000123,CFS",
            "00126380,2025,11012,20260331000123,CFS",
            "00126380,2025,11011,20260331000124,CFS",
            "00126380,2025,11011,invalid,CFS",
            "00126380,2025,11011,20260331000123,UNKNOWN"})
    void exactIdentityMismatchBlocks(String corp, String year, String report, String receipt, String division) {
        var response = new OpenDartPeriodWitnessResponse("000", List.of(new OpenDartPeriodWitnessResponse.Row(
                corp, year, report, receipt, division, "2025.01.01~2025.12.31")));
        assertEquals(PERIOD_WITNESS_IDENTITY_MISMATCH, assertThrows(OpenDartProviderException.class,
                () -> OpenDartPeriodWitnessResolver.resolve(CONTEXT, RECEIPT, response, NOW)).category());
    }

    @Test
    void ofsCannotSupplyThePeriodAndInvalidValueReceiptBlocks() {
        var ofs = new OpenDartPeriodWitnessResponse("000", List.of(new OpenDartPeriodWitnessResponse.Row(
                CORP, "2025", "11011", RECEIPT, "OFS", "2025.01.01~2025.12.31")));
        assertEquals(PERIOD_WITNESS_MISSING, assertThrows(OpenDartProviderException.class,
                () -> OpenDartPeriodWitnessResolver.resolve(CONTEXT, RECEIPT, ofs, NOW)).category());
        assertEquals(PERIOD_WITNESS_IDENTITY_MISMATCH, assertThrows(OpenDartProviderException.class,
                () -> OpenDartPeriodWitnessResolver.resolve(CONTEXT, "123", ofs, NOW)).category());
    }

    @Test
    void mixedOfsCannotChangeCfsPeriodOrHashButUnknownDivisionStillBlocks() {
        var cfs = OpenDartWitnessFixtures.witness(CORP, RECEIPT, "2025.04.01~2025.09.30").list().getFirst();
        var ofs = new OpenDartPeriodWitnessResponse.Row(CORP, "2025", "11011", RECEIPT,
                "OFS", "2025.01.01~2025.12.31");
        var mixed = OpenDartPeriodWitnessResolver.resolve(CONTEXT, RECEIPT,
                new OpenDartPeriodWitnessResponse("000", List.of(ofs, cfs)), NOW);
        assertEquals("2025-04-01", mixed.start().toString());
        assertArrayEquals(resolve("2025.04.01~2025.09.30").evidence().contentHash(), mixed.evidence().contentHash());
        var unexpected = new OpenDartPeriodWitnessResponse.Row(CORP, "2025", "11011", RECEIPT,
                "UNKNOWN", "2025.04.01~2025.09.30");
        assertEquals(PERIOD_WITNESS_IDENTITY_MISMATCH, assertThrows(OpenDartProviderException.class,
                () -> OpenDartPeriodWitnessResolver.resolve(CONTEXT, RECEIPT,
                        new OpenDartPeriodWitnessResponse("000", List.of(cfs, unexpected)), NOW)).category());
    }

    private OpenDartPeriodWitnessResolver.Resolved resolve(String... terms) {
        return OpenDartPeriodWitnessResolver.resolve(CONTEXT, RECEIPT,
                OpenDartWitnessFixtures.witness(CORP, RECEIPT, terms), NOW);
    }
}
