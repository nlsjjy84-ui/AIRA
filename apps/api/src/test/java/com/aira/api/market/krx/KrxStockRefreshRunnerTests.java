package com.aira.api.market.krx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class KrxStockRefreshRunnerTests {
    @Test
    void refusesAnyModeOtherThanExplicitLive() {
        var operation = mock(KrxStockRefreshOperation.class);
        var runner = new KrxStockRefreshRunner(operation, "", "configured-key", "KOSPI:000660");
        assertThrows(IllegalStateException.class, () -> runner.run(new DefaultApplicationArguments()));
        verifyNoInteractions(operation);
    }

    @Test
    void refusesLiveModeWithoutAuthKey() {
        var operation = mock(KrxStockRefreshOperation.class);
        var runner = new KrxStockRefreshRunner(operation, "live", "", "KOSPI:000660");
        assertThrows(IllegalStateException.class, () -> runner.run(new DefaultApplicationArguments()));
        verifyNoInteractions(operation);
    }

    @Test
    void parsesDeterministicTargetsAndRunsOperation() {
        var operation = mock(KrxStockRefreshOperation.class);
        var result = new KrxStockRefreshOperation.Result(List.of(
                new KrxStockRefreshOperation.MarketResult("KOSPI",
                        LocalDate.of(2035, 1, 12), 2, 16)));
        when(operation.refreshLatest(anyList())).thenReturn(result);

        var runner = new KrxStockRefreshRunner(operation, "live", "configured-key",
                "KOSPI:000660, KOSPI:035420, KOSPI:000660");
        runner.run(new DefaultApplicationArguments());

        var expected = List.of(
                new KrxStockRefreshOperation.Target("KOSPI", "000660"),
                new KrxStockRefreshOperation.Target("KOSPI", "035420"));
        verify(operation).refreshLatest(expected);
    }

    @Test
    void rejectsMalformedTargetConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> KrxStockRefreshRunner.parseTargets("KOSPI-000660"));
        assertThrows(IllegalArgumentException.class,
                () -> KrxStockRefreshRunner.parseTargets("KONEX:000660"));
        assertEquals(1, KrxStockRefreshRunner.parseTargets("kosdaq:123456").size());
    }
}
