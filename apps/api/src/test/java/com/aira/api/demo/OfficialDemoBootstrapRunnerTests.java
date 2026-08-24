package com.aira.api.demo;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class OfficialDemoBootstrapRunnerTests {
    @Test
    void refusesAnyModeOtherThanExplicitLiveMode() {
        var bootstrap = mock(OfficialDemoBootstrapOperation.class);
        var runner = new OfficialDemoBootstrapRunner(bootstrap, "", "key", 2025);

        assertThrows(IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments()));
        verifyNoInteractions(bootstrap);
    }

    @Test
    void refusesLiveModeWithoutApiKey() {
        var bootstrap = mock(OfficialDemoBootstrapOperation.class);
        var runner = new OfficialDemoBootstrapRunner(bootstrap, "live", "", 2025);

        assertThrows(IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments()));
        verifyNoInteractions(bootstrap);
    }

    @Test
    void runsWithExplicitLiveModeAndApiKey() {
        var bootstrap = mock(OfficialDemoBootstrapOperation.class);
        when(bootstrap.prepare(2025)).thenReturn(
                new OfficialDemoBootstrapOperation.Result(List.of(), 0, 0));
        var runner = new OfficialDemoBootstrapRunner(bootstrap, "live", "configured-key", 2025);

        runner.run(new DefaultApplicationArguments());

        verify(bootstrap).prepare(2025);
    }
}
