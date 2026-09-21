package com.aira.api.market.krx;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KrxLatestIndexTradingDayResolverTests {
    @Test void discoversLatestNonEmptyOfficialIndexSnapshotWithoutCalendarGuessing() {
        LocalDate expected = LocalDate.of(2035, 1, 12);
        KrxClient client = (dataset, date) -> date.equals(expected)
                ? KrxSnapshot.validated(dataset, date, List.of(row(date, dataset.market())))
                : KrxSnapshot.validated(dataset, date, List.of());
        var resolver = new KrxLatestIndexTradingDayResolver(client,
                Clock.fixed(Instant.parse("2035-01-14T01:00:00Z"), ZoneOffset.UTC));
        assertEquals(expected, resolver.resolve(KrxDataset.KOSPI_INDEX).date());
    }

    @Test void rejectsNonIndexDataset() {
        KrxClient client = (dataset, date) -> KrxSnapshot.validated(dataset, date, List.of());
        var resolver = new KrxLatestIndexTradingDayResolver(client, Clock.systemUTC());
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(KrxDataset.STK_DAILY));
    }

    private static Map<String, String> row(LocalDate date, String market) {
        return Map.of("BAS_DD", date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                "IDX_CLSS", market, "IDX_NM", market, "CLSPRC_IDX", "1000.00",
                "CMPPREVDD_IDX", "-1.00", "FLUC_RT", "-0.10");
    }
}
