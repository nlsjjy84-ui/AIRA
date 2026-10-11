package com.aira.api.demo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aira.api.market.opendart.OfficialAllCompaniesPreparationOperation;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ScheduledAllCompaniesRefreshTests {
    private final OfficialAllCompaniesPreparationOperation operation =
            mock(OfficialAllCompaniesPreparationOperation.class);

    private ScheduledAllCompaniesRefresh refresh(String mode, String key) {
        return new ScheduledAllCompaniesRefresh(operation, mode, key, 2025, 100, 0, Duration.ofHours(6), Runnable::run);
    }

    private static OfficialAllCompaniesPreparationOperation.Result result(int attempted,
            OfficialAllCompaniesPreparationOperation.StopReason stop) {
        return new OfficialAllCompaniesPreparationOperation.Result(attempted, attempted, attempted, 0, 0, stop);
    }

    @Test
    void doesNothingWithoutLiveModeAndKey() {
        refresh("", "key").tick();
        refresh("live", " ").tick();
        verifyNoInteractions(operation);
    }

    @Test
    void runsABatchWhenLive() {
        when(operation.run(anyInt(), anyInt(), anyLong(), any())).thenReturn(
                result(10, OfficialAllCompaniesPreparationOperation.StopReason.NONE));
        var refresh = refresh("live", "key");
        refresh.tick();
        refresh.tick();
        verify(operation, times(2)).run(anyInt(), anyInt(), anyLong(), any());
    }

    @Test
    void pausesAfterTheDailyLimitSoNoMoreCallsAreWasted() {
        when(operation.run(anyInt(), anyInt(), anyLong(), any())).thenReturn(
                result(1, OfficialAllCompaniesPreparationOperation.StopReason.RATE_LIMIT));
        var refresh = refresh("live", "key");
        refresh.tick();
        refresh.tick();
        verify(operation, times(1)).run(anyInt(), anyInt(), anyLong(), any());
    }

    @Test
    void pausesWhenNothingIsPendingAndSurvivesAFailure() {
        when(operation.run(anyInt(), anyInt(), anyLong(), any())).thenReturn(
                result(0, OfficialAllCompaniesPreparationOperation.StopReason.NONE));
        var idle = refresh("live", "key");
        idle.tick();
        idle.tick();
        verify(operation, times(1)).run(anyInt(), anyInt(), anyLong(), any());

        var failing = mock(OfficialAllCompaniesPreparationOperation.class);
        when(failing.run(anyInt(), anyInt(), anyLong(), any())).thenThrow(new IllegalStateException("down"));
        new ScheduledAllCompaniesRefresh(failing, "live", "key", 2025, 100, 0, Duration.ofHours(6), Runnable::run).tick();
        verify(failing, times(1)).run(anyInt(), anyInt(), anyLong(), any());
    }
}
