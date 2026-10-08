package com.aira.api.demo;

import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;

class ScheduledOfficialDemoRefreshTests {
    @Test void skipsWithoutExplicitLiveModeOrKey() {
        var operation = mock(OfficialDemoBootstrapOperation.class);
        new ScheduledOfficialDemoRefresh(operation, "", "key", 2025).refresh();
        new ScheduledOfficialDemoRefresh(operation, "live", " ", 2025).refresh();
        verifyNoInteractions(operation);
    }

    @Test void runsPrepareForConfiguredBusinessYearWhenLive() {
        var operation = mock(OfficialDemoBootstrapOperation.class);
        when(operation.prepare(2025)).thenReturn(new OfficialDemoBootstrapOperation.Result(java.util.List.of(), 0, 0));
        new ScheduledOfficialDemoRefresh(operation, "live", "key", 2025).refresh();
        verify(operation).prepare(2025);
    }

    @Test void aFailureIsLoggedNotThrownSoTheNextScheduledRunStillHappens() {
        var operation = mock(OfficialDemoBootstrapOperation.class);
        when(operation.prepare(2025)).thenThrow(new IllegalStateException("OpenDART unavailable"));
        new ScheduledOfficialDemoRefresh(operation, "live", "key", 2025).refresh();
        verify(operation).prepare(2025);
    }
}
