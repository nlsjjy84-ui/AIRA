package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.krx.KrxClient;
import com.aira.api.market.krx.KrxDataset;
import com.aira.api.market.krx.KrxSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StockHistoryBackfillTests {
    // 2026-10-12(월) 09:00 KST
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void pricesSkipWeekendsAndAlreadyLoadedDaysAndDoNotMarkTodayEmpty() {
        KrxClient krx = mock(KrxClient.class);
        StockPriceStore store = mock(StockPriceStore.class);
        when(store.loadedDays(eq("KOSPI"), any())).thenReturn(Set.of(LocalDate.of(2026, 10, 9)));
        when(krx.fetch(any(), any())).thenAnswer(call -> {
            LocalDate date = call.getArgument(1);
            List<Map<String, String>> rows = date.equals(LocalDate.of(2026, 10, 12)) ? List.of()
                    : List.of(Map.of("BAS_DD", date.toString().replace("-", ""), "ISU_CD", "005930", "TDD_CLSPRC", "262000"));
            return KrxSnapshot.validated(KrxDataset.STK_DAILY, date, rows);
        });
        var backfill = new StockHistoryBackfill(krx, store, mock(DartStockClient.class), mock(FinancialHistoryStore.class),
                mock(StockCompanyLookup.class), true, "KEY", 6, 0, clock);
        int loaded = backfill.backfillPrices("KOSPI");
        // 10/6~10/12 중 평일: 6,7,8,9,12. 9일은 이미 받음, 12일은 아직 비어 있어 표시하지 않음 → 6,7,8
        assertEquals(3, loaded);
        verify(store, never()).saveDay(eq("KOSPI"), eq(LocalDate.of(2026, 10, 12)), anyList());
        verify(store, never()).saveDay(eq("KOSPI"), eq(LocalDate.of(2026, 10, 10)), anyList());
        verify(krx, never()).fetch(any(), eq(LocalDate.of(2026, 10, 9)));
    }

    @Test
    void fullDatabaseStillTakesRecentDaysByDroppingTheOldestDay() {
        KrxClient krx = mock(KrxClient.class);
        StockPriceStore store = mock(StockPriceStore.class);
        StockStorage storage = mock(StockStorage.class);
        when(storage.nearlyFull()).thenReturn(true);
        when(storage.softLimitMb()).thenReturn(400L);
        when(store.loadedDays(eq("KOSPI"), any())).thenReturn(Set.of());
        when(store.dropOldestDay("KOSPI")).thenReturn(LocalDate.of(2025, 10, 1));
        when(krx.fetch(any(), any())).thenAnswer(call -> {
            LocalDate date = call.getArgument(1);
            return KrxSnapshot.validated(KrxDataset.STK_DAILY, date,
                    List.of(Map.of("BAS_DD", date.toString().replace("-", ""), "ISU_CD", "005930", "TDD_CLSPRC", "262000")));
        });
        var backfill = new StockHistoryBackfill(krx, store, mock(DartStockClient.class), mock(FinancialHistoryStore.class),
                mock(StockCompanyLookup.class), true, "KEY", 30, 0, clock);
        backfill.setStorage(storage);
        int loaded = backfill.backfillPrices("KOSPI");
        // 열흘 안의 평일(10/2~10/12 중 평일 7일: 2·5·6·7·8·9·12)만 받고, 받을 때마다 가장 오래된 하루를 뺀다. 그 앞은 채우지 않는다.
        assertEquals(7, loaded);
        verify(store, times(7)).dropOldestDay("KOSPI");
        verify(store).saveDay(eq("KOSPI"), eq(LocalDate.of(2026, 10, 12)), anyList());
        verify(store, never()).saveDay(eq("KOSPI"), eq(LocalDate.of(2026, 10, 1)), anyList());
    }

    @Test
    void financialsAskHundredCompaniesAtATimeForTwoReportYears() {
        DartStockClient dart = mock(DartStockClient.class);
        FinancialHistoryStore store = mock(FinancialHistoryStore.class);
        StockCompanyLookup lookup = mock(StockCompanyLookup.class);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 150; i++) codes.add(String.format("%08d", i));
        when(lookup.allCorpCodes()).thenReturn(codes);
        when(store.loadedChunks(2022)).thenReturn(Set.of("00000000"));
        when(store.loadedChunks(2025)).thenReturn(Set.of());
        when(dart.multiAccounts(anyList(), anyInt())).thenReturn(List.of());
        var backfill = new StockHistoryBackfill(mock(KrxClient.class), mock(StockPriceStore.class), dart, store, lookup,
                true, "", 6, 0, clock);
        backfill.backfillFinancials();
        verify(dart, times(2)).multiAccounts(anyList(), eq(2025));
        verify(dart, times(1)).multiAccounts(anyList(), eq(2022));
        verify(store).saveChunk(eq(2022), eq("00000100"), anyList());
        verify(store, never()).saveChunk(eq(2022), eq("00000000"), anyList());
    }

    @Test
    void profilesSkipFreshOnesAndSaveTheRest() {
        DartStockClient dart = mock(DartStockClient.class);
        StockCompanyLookup lookup = mock(StockCompanyLookup.class);
        ProfileStore profiles = mock(ProfileStore.class);
        when(lookup.allCorpCodes()).thenReturn(List.of("00000001", "00000002", "00000003"));
        when(profiles.freshCorpCodes(any())).thenReturn(Set.of("00000002"));
        when(dart.company("00000001")).thenReturn(Map.of("induty_code", "264"));
        when(dart.company("00000003")).thenReturn(Map.of());
        var backfill = new StockHistoryBackfill(mock(KrxClient.class), mock(StockPriceStore.class), dart, mock(FinancialHistoryStore.class), lookup,
                true, "", 6, 0, clock);
        backfill.setProfiles(profiles);
        assertEquals(1, backfill.backfillProfiles());
        verify(dart, never()).company("00000002");
        verify(profiles).save(eq("00000001"), eq(Map.of("induty_code", "264")), any());
    }

    @Test
    void nothingRunsWithoutKeys() {
        KrxClient krx = mock(KrxClient.class);
        DartStockClient dart = mock(DartStockClient.class);
        when(dart.configured()).thenReturn(false);
        var backfill = new StockHistoryBackfill(krx, mock(StockPriceStore.class), dart, mock(FinancialHistoryStore.class),
                mock(StockCompanyLookup.class), true, "", 6, 0, clock);
        backfill.runOnce();
        verify(krx, never()).fetch(any(), any());
        verify(dart, never()).multiAccounts(anyList(), anyInt());
        assertTrue(true);
    }
}
