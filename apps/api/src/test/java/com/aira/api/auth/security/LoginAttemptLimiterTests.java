package com.aira.api.auth.security;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.auth.exception.LoginTemporarilyLockedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTests {
    static final class MovingClock extends Clock {
        Instant now = Instant.parse("2026-10-30T00:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void plus(Duration d) { now = now.plus(d); }
    }

    @Test
    void locksAfterFiveFailuresAndTellsWhenToRetry() {
        MovingClock clock = new MovingClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES - 1; i++) {
            limiter.recordFailure("airauser");
            assertDoesNotThrow(() -> limiter.checkAllowed("airauser"));
        }
        limiter.recordFailure("airauser");
        LoginTemporarilyLockedException locked =
                assertThrows(LoginTemporarilyLockedException.class, () -> limiter.checkAllowed("airauser"));
        assertEquals(15 * 60, locked.getRetryAfterSeconds());
        assertDoesNotThrow(() -> limiter.checkAllowed("someoneelse"), "other nicknames are not affected");
    }

    @Test
    void unlocksWhenTheWindowPassesAndSuccessClearsFailures() {
        MovingClock clock = new MovingClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES; i++) limiter.recordFailure("airauser");
        clock.plus(LoginAttemptLimiter.WINDOW.plusSeconds(1));
        assertDoesNotThrow(() -> limiter.checkAllowed("airauser"));

        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES - 1; i++) limiter.recordFailure("other");
        limiter.recordSuccess("other");
        limiter.recordFailure("other");
        assertDoesNotThrow(() -> limiter.checkAllowed("other"));
    }

    @Test
    void keepsMemoryBounded() {
        MovingClock clock = new MovingClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
        for (int i = 0; i < LoginAttemptLimiter.MAX_KEYS + 50; i++) limiter.recordFailure("user" + i);
        assertTrue(limiter.trackedKeys() <= LoginAttemptLimiter.MAX_KEYS);
    }
}
