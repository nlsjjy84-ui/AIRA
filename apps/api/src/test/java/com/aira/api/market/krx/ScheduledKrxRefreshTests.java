package com.aira.api.market.krx;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ScheduledKrxRefreshTests {
    private static final String TARGETS = "KOSPI:000660";

    @Test void skipsEverythingWithoutAuthKey() {
        var index = mock(KrxIndexRefreshOperation.class);
        var stock = mock(KrxStockRefreshOperation.class);
        new ScheduledKrxRefresh(index, stock, "", "live", "live", TARGETS).refresh();
        verifyNoInteractions(index, stock);
    }

    @Test void skipsOperationsWhoseModeIsNotExplicitlyLive() {
        var index = mock(KrxIndexRefreshOperation.class);
        var stock = mock(KrxStockRefreshOperation.class);
        new ScheduledKrxRefresh(index, stock, "key", "", "dry", TARGETS).refresh();
        verifyNoInteractions(index, stock);
    }

    @Test void refreshesIndexAndStocksWhenLive() {
        var index = mock(KrxIndexRefreshOperation.class);
        var stock = mock(KrxStockRefreshOperation.class);
        when(index.refreshLatest()).thenReturn(new KrxIndexRefreshOperation.Result(
                LocalDate.of(2035, 1, 12), LocalDate.of(2035, 1, 12)));
        when(stock.refreshLatest(anyList())).thenReturn(new KrxStockRefreshOperation.Result(java.util.List.of()));
        new ScheduledKrxRefresh(index, stock, "key", "live", "live", TARGETS).refresh();
        verify(index).refreshLatest();
        verify(stock).refreshLatest(anyList());
    }

    @Test void aFailingIndexRefreshDoesNotStopTheStockRefresh() {
        var index = mock(KrxIndexRefreshOperation.class);
        var stock = mock(KrxStockRefreshOperation.class);
        when(index.refreshLatest()).thenThrow(new IllegalStateException("KRX unavailable"));
        when(stock.refreshLatest(anyList())).thenReturn(new KrxStockRefreshOperation.Result(java.util.List.of()));
        new ScheduledKrxRefresh(index, stock, "key", "live", "live", TARGETS).refresh();
        verify(stock).refreshLatest(anyList());
    }
}
