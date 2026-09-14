package com.aira.api.market.krx;

import static org.junit.jupiter.api.Assertions.*;
import com.aira.api.market.domain.FactPredicate;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KrxPacketTests {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 11);
    @Test void discoversMarketDateFromOfficialNonEmptySnapshotNotTargetPresence() {
        var calls = new java.util.ArrayList<LocalDate>();
        KrxClient client = (dataset, date) -> {
            calls.add(date);
            return KrxSnapshot.validated(dataset, date, date.equals(DATE)
                    ? List.of(Map.of("BAS_DD", "20260911", "ISU_CD", "not-the-target", "TDD_CLSPRC", "1000"))
                    : List.of());
        };
        var clock = java.time.Clock.fixed(java.time.Instant.parse("2026-09-12T01:00:00Z"),
                java.time.ZoneOffset.UTC);
        var found = new KrxLatestCompletedTradingDayResolver(client, clock).resolve("KOSPI");
        assertEquals(DATE, found.date());
        assertEquals(List.of(DATE.plusDays(1), DATE), calls);
    }
    @Test void malformedOfficialCandidateBlocksInsteadOfSkippingToEarlierDate() {
        KrxClient client = (dataset, date) -> KrxSnapshot.validated(dataset, date,
                List.of(Map.of("BAS_DD", date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                        "ISU_CD", "000001", "TDD_CLSPRC", "bad")));
        var clock = java.time.Clock.fixed(java.time.Instant.parse("2026-09-11T01:00:00Z"),
                java.time.ZoneOffset.UTC);
        assertThrows(IllegalArgumentException.class,
                () -> new KrxLatestCompletedTradingDayResolver(client, clock).resolve("KOSPI"));
    }
    @Test void fullSnapshotHashIgnoresRowOrderButIncludesUnpromotedFields() {
        var first = Map.of("BAS_DD", "20260911", "ISU_CD", "000001", "ISU_NM", "One");
        var second = Map.of("BAS_DD", "20260911", "ISU_CD", "000002", "ISU_NM", "Two");
        var a = KrxSnapshot.validated(KrxDataset.STK_DAILY, DATE, List.of(first, second));
        var b = KrxSnapshot.validated(KrxDataset.STK_DAILY, DATE, List.of(second, first));
        assertArrayEquals(a.hash(), b.hash());
        var changed = KrxSnapshot.validated(KrxDataset.STK_DAILY, DATE,
                List.of(Map.of("BAS_DD", "20260911", "ISU_CD", "000001", "ISU_NM", "Renamed"), second));
        assertFalse(java.util.Arrays.equals(a.hash(), changed.hash()));
    }
    @Test void exactCodeJoinAndMissingMetricPolicy() {
        var base = KrxSnapshot.validated(KrxDataset.STK_BASE, DATE,
                List.of(Map.of("ISU_CD", "KR7000000001", "ISU_SRT_CD", "000001", "ISU_NM", "One")));
        var daily = KrxSnapshot.validated(KrxDataset.STK_DAILY, DATE,
                List.of(Map.of("BAS_DD", "20260911", "ISU_CD", "000001", "TDD_CLSPRC", "0",
                        "TDD_OPNPRC", "-", "ACC_TRDVOL", "12")));
        var packet = KrxPreparedPacket.stock(base, daily);
        assertEquals(2, packet.values().size());
        assertTrue(packet.values().stream().anyMatch(v -> v.predicate() == FactPredicate.CLOSE_PRICE && v.value().signum() == 0));
        assertThrows(IllegalArgumentException.class, () -> KrxPreparedPacket.stock(base,
                KrxSnapshot.validated(KrxDataset.STK_DAILY, DATE,
                        List.of(Map.of("BAS_DD", "20260911", "ISU_CD", "000001", "TDD_CLSPRC", "bad")))));
        assertThrows(IllegalArgumentException.class, () -> KrxPreparedPacket.stock(base,
                KrxSnapshot.validated(KrxDataset.STK_DAILY, DATE,
                        List.of(Map.of("BAS_DD", "20260911", "ISU_CD", "999999")))));
    }
    @Test void parserRequiresDocumentedBlockAndPreservesNoValueToken() {
        var client = new HttpKrxClient(java.net.http.HttpClient.newHttpClient(),
                new tools.jackson.databind.ObjectMapper(), "synthetic");
        var snapshot = client.parse(KrxDataset.STK_DAILY, DATE, """
                {"OutBlock_1":[{"BAS_DD":"20260911","ISU_CD":"000001","TDD_CLSPRC":"-"}]}
                """);
        assertEquals("-", snapshot.rows().getFirst().get("TDD_CLSPRC"));
        assertThrows(IllegalArgumentException.class, () -> client.parse(KrxDataset.STK_DAILY, DATE, "{}"));
        assertThrows(IllegalArgumentException.class, () -> client.parse(KrxDataset.STK_DAILY, DATE,
                "{\"OutBlock_1\":[{\"BAS_DD\":\"20260910\",\"ISU_CD\":\"000001\"}]}"));
    }
}
