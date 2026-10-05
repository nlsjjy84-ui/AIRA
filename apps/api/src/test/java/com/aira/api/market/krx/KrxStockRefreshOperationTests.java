package com.aira.api.market.krx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KrxStockRefreshOperationTests {
    @Test
    void refreshesOnlyRequestedSecuritiesForBothSupportedMarkets() {
        var resolver = mock(KrxLatestCompletedTradingDayResolver.class);
        var client = mock(KrxClient.class);
        var persistence = mock(KrxPersistence.class);
        LocalDate kospiDate = LocalDate.of(2035, 1, 12);
        LocalDate kosdaqDate = LocalDate.of(2035, 1, 13);

        var kospiDaily = KrxSnapshot.validated(KrxDataset.STK_DAILY, kospiDate, List.of(
                Map.of("BAS_DD", "20350112", "ISU_CD", "000660", "TDD_CLSPRC", "100"),
                Map.of("BAS_DD", "20350112", "ISU_CD", "005930", "TDD_CLSPRC", "200")));
        var kospiBase = KrxSnapshot.validated(KrxDataset.STK_BASE, kospiDate, List.of(
                Map.of("ISU_CD", "KR7000660001", "ISU_SRT_CD", "000660", "ISU_NM", "SK hynix"),
                Map.of("ISU_CD", "KR7005930003", "ISU_SRT_CD", "005930", "ISU_NM", "Samsung")));

        var kosdaqDaily = KrxSnapshot.validated(KrxDataset.KSQ_DAILY, kosdaqDate, List.of(
                Map.of("BAS_DD", "20350113", "ISU_CD", "123456", "TDD_CLSPRC", "300"),
                Map.of("BAS_DD", "20350113", "ISU_CD", "654321", "TDD_CLSPRC", "400")));
        var kosdaqBase = KrxSnapshot.validated(KrxDataset.KSQ_BASE, kosdaqDate, List.of(
                Map.of("ISU_CD", "KR7123450000", "ISU_SRT_CD", "123456", "ISU_NM", "KQ One"),
                Map.of("ISU_CD", "KR7654320000", "ISU_SRT_CD", "654321", "ISU_NM", "KQ Two")));

        when(resolver.resolve("KOSPI")).thenReturn(kospiDaily);
        when(resolver.resolve("KOSDAQ")).thenReturn(kosdaqDaily);
        when(client.fetch(KrxDataset.STK_BASE, kospiDate)).thenReturn(kospiBase);
        when(client.fetch(KrxDataset.KSQ_BASE, kosdaqDate)).thenReturn(kosdaqBase);

        var operation = new KrxStockRefreshOperation(resolver, client, persistence);
        var result = operation.refreshLatest(List.of(
                new KrxStockRefreshOperation.Target("KOSPI", "000660"),
                new KrxStockRefreshOperation.Target("KOSDAQ", "123456")));

        assertEquals(2, result.markets().size());
        assertEquals(kospiDate, result.markets().get(0).tradingDate());
        assertEquals(kosdaqDate, result.markets().get(1).tradingDate());

        var captor = ArgumentCaptor.forClass(KrxPreparedPacket.class);
        verify(persistence, times(2)).stock(captor.capture());
        var packets = captor.getAllValues();
        assertEquals(List.of("000660"), packets.get(0).securities().stream()
                .map(KrxPreparedPacket.SecurityRow::shortCode).toList());
        assertEquals(List.of("123456"), packets.get(1).securities().stream()
                .map(KrxPreparedPacket.SecurityRow::shortCode).toList());
    }

    @Test
    void allTargetPersistsEverySecurityOfTheMarket() {
        var resolver = mock(KrxLatestCompletedTradingDayResolver.class);
        var client = mock(KrxClient.class);
        var persistence = mock(KrxPersistence.class);
        LocalDate date = LocalDate.of(2035, 1, 12);
        var daily = KrxSnapshot.validated(KrxDataset.STK_DAILY, date, List.of(
                Map.of("BAS_DD", "20350112", "ISU_CD", "000660", "TDD_CLSPRC", "100"),
                Map.of("BAS_DD", "20350112", "ISU_CD", "005930", "TDD_CLSPRC", "200")));
        var base = KrxSnapshot.validated(KrxDataset.STK_BASE, date, List.of(
                Map.of("ISU_CD", "KR7000660001", "ISU_SRT_CD", "000660", "ISU_NM", "SK hynix"),
                Map.of("ISU_CD", "KR7005930003", "ISU_SRT_CD", "005930", "ISU_NM", "Samsung")));
        when(resolver.resolve("KOSPI")).thenReturn(daily);
        when(client.fetch(KrxDataset.STK_BASE, date)).thenReturn(base);

        var operation = new KrxStockRefreshOperation(resolver, client, persistence);
        var result = operation.refreshLatest(List.of(new KrxStockRefreshOperation.Target("KOSPI", "all")));

        assertEquals(2, result.markets().get(0).securityCount());
        var captor = ArgumentCaptor.forClass(KrxPreparedPacket.class);
        verify(persistence).stock(captor.capture());
        assertEquals(List.of("000660", "005930"), captor.getValue().securities().stream()
                .map(KrxPreparedPacket.SecurityRow::shortCode).sorted().toList());
    }
}
