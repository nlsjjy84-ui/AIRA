package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScheduledEcosRefreshTests {
    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul"));
    }

    @Test void skipsWithoutApiKey() {
        var op = mock(EcosRealGdpIngestionOperation.class);
        new ScheduledEcosRefresh(op, "", 8, at("2026-10-08T01:00:00Z")).refresh();
        verifyNoInteractions(op);
    }

    @Test void requestsTheLatestEightQuartersEndingThisQuarter() {
        var op = mock(EcosRealGdpIngestionOperation.class);
        when(op.ingest(any(), any())).thenReturn(
                new EcosRealGdpFactIngestionResult(UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID())));
        new ScheduledEcosRefresh(op, "key", 8, at("2026-10-08T01:00:00Z")).refresh();
        var scope = ArgumentCaptor.forClass(EcosRealGdpObservationScope.class);
        verify(op).ingest(scope.capture(), any());
        assertEquals("2025Q1", scope.getValue().startTime());
        assertEquals("2026Q4", scope.getValue().endTime());
        assertEquals(8, scope.getValue().quarterCount());
    }

    @Test void crossesTheYearBoundaryCorrectly() {
        var refresh = new ScheduledEcosRefresh(mock(EcosRealGdpIngestionOperation.class), "key", 3, at("2026-01-15T00:00:00Z"));
        assertEquals("2025Q3", refresh.scope().startTime());
        assertEquals("2026Q1", refresh.scope().endTime());
    }

    @Test void aProviderFailureIsLoggedNotThrown() {
        var op = mock(EcosRealGdpIngestionOperation.class);
        when(op.ingest(any(), any())).thenThrow(new IllegalStateException("ECOS unavailable"));
        new ScheduledEcosRefresh(op, "key", 8, at("2026-10-08T01:00:00Z")).refresh();
        verify(op).ingest(any(), any());
    }
}
