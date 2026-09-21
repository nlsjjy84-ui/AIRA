package com.aira.api.market.krx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class KrxIndexRefreshOperationTests {
    @Test void resolvesBothMarketsBeforePersistingTheirExactSnapshots() {
        var resolver = mock(KrxLatestIndexTradingDayResolver.class);
        var persistence = mock(KrxPersistence.class);
        LocalDate kospiDate = LocalDate.of(2035, 1, 12);
        LocalDate kosdaqDate = LocalDate.of(2035, 1, 13);
        var kospi = KrxSnapshot.validated(KrxDataset.KOSPI_INDEX, kospiDate, List.of());
        var kosdaq = KrxSnapshot.validated(KrxDataset.KOSDAQ_INDEX, kosdaqDate, List.of());
        when(resolver.resolve(KrxDataset.KOSPI_INDEX)).thenReturn(kospi);
        when(resolver.resolve(KrxDataset.KOSDAQ_INDEX)).thenReturn(kosdaq);

        var result = new KrxIndexRefreshOperation(resolver, persistence).refreshLatest();

        assertEquals(kospiDate, result.kospiDate());
        assertEquals(kosdaqDate, result.kosdaqDate());
        var order = inOrder(resolver, persistence);
        order.verify(resolver).resolve(KrxDataset.KOSPI_INDEX);
        order.verify(resolver).resolve(KrxDataset.KOSDAQ_INDEX);
        order.verify(persistence).index(kospi);
        order.verify(persistence).index(kosdaq);
    }
}
