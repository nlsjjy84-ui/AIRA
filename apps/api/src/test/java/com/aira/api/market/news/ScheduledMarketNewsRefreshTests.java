package com.aira.api.market.news;

import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;

class ScheduledMarketNewsRefreshTests {
    @Test void refreshDelegatesToNewsService() {
        RecentMarketNewsService service = mock(RecentMarketNewsService.class);
        new ScheduledMarketNewsRefresh(service).refresh();
        verify(service).refresh();
    }

    @Test void refreshSwallowsUnexpectedFailure() {
        RecentMarketNewsService service = mock(RecentMarketNewsService.class);
        doThrow(new IllegalStateException("boom")).when(service).refresh();
        new ScheduledMarketNewsRefresh(service).refresh();
        verify(service).refresh();
    }
}
