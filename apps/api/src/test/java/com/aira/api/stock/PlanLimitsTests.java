package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanLimitsTests {
    static class MovingClock extends Clock {
        Instant now;
        MovingClock(Instant now) { this.now = now; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }

    @Test
    void offBeforeLaunchOnlyCountsUsage() {
        var limits = new PlanLimits(false, new MovingClock(Instant.parse("2026-10-12T03:00:00Z")));
        UUID user = UUID.randomUUID();
        for (int i = 0; i < 8; i++) assertTrue(limits.useAi(user));
        assertEquals(8, limits.aiUsedToday(user));
        assertEquals(PlanLimits.OPEN_NOTES, limits.notesLimit(user));
        assertEquals(PlanLimits.OPEN_ALERTS, limits.alertsLimit(user));
        @SuppressWarnings("unchecked") var ai = (Map<String, Object>) limits.usage(user, 3, 1).get("ai");
        assertEquals(8, ai.get("used"));
        assertEquals(5, ai.get("freeLimit"));
    }

    @Test
    void whenOnFreeUsersGetFiveAiCallsPerKoreanDay() {
        var clock = new MovingClock(Instant.parse("2026-10-12T03:00:00Z"));
        var limits = new PlanLimits(true, clock);
        UUID user = UUID.randomUUID();
        for (int i = 0; i < 5; i++) assertTrue(limits.useAi(user));
        assertFalse(limits.useAi(user), "sixth call of the day is refused");
        assertEquals(5, limits.aiUsedToday(user));
        assertEquals(PlanLimits.FREE_NOTES, limits.notesLimit(user));
        assertEquals(PlanLimits.FREE_ALERTS, limits.alertsLimit(user));
        clock.now = Instant.parse("2026-10-12T15:30:00Z"); // 한국 시간 다음 날 00:30
        assertTrue(limits.useAi(user), "a new Korean day resets the count");
    }
}
