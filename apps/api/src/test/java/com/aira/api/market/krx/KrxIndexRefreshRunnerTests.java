package com.aira.api.market.krx;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class KrxIndexRefreshRunnerTests {
    @Test void refusesAnyModeOtherThanExplicitLive() {
        var operation = mock(KrxIndexRefreshOperation.class);
        var runner = new KrxIndexRefreshRunner(operation, "", "configured-key");
        assertThrows(IllegalStateException.class, () -> runner.run(new DefaultApplicationArguments()));
        verifyNoInteractions(operation);
    }

    @Test void refusesLiveModeWithoutAuthKey() {
        var operation = mock(KrxIndexRefreshOperation.class);
        var runner = new KrxIndexRefreshRunner(operation, "live", "");
        assertThrows(IllegalStateException.class, () -> runner.run(new DefaultApplicationArguments()));
        verifyNoInteractions(operation);
    }

    @Test void runsOnlyWithExplicitLiveModeAndAuthKey() {
        var operation = mock(KrxIndexRefreshOperation.class);
        when(operation.refreshLatest()).thenReturn(new KrxIndexRefreshOperation.Result(
                LocalDate.of(2035, 1, 12), LocalDate.of(2035, 1, 12)));
        var runner = new KrxIndexRefreshRunner(operation, "live", "configured-key");
        runner.run(new DefaultApplicationArguments());
        verify(operation).refreshLatest();
    }
}
