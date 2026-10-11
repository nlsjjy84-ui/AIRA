package com.aira.api.demo;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.market.opendart.PriorYearBackfill;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduledPriorYearBackfillTests {
    private static final class Fake implements PriorYearBackfill {
        final List<Integer> years = new ArrayList<>();
        final List<Result> answers;
        Fake(Result... answers) { this.answers = new ArrayList<>(List.of(answers)); }
        public Result run(int businessYear, int limit, long pauseMillis, Set<String> skip) {
            years.add(businessYear);
            return answers.isEmpty() ? new Result(0, 0, 0, 0, 0, StopReason.NONE) : answers.remove(0);
        }
    }

    private static ScheduledPriorYearBackfill scheduler(Fake fake, List<Integer> years, String mode) {
        return new ScheduledPriorYearBackfill(fake, mode, "key", years, 40, 0, Duration.ofHours(12), Runnable::run);
    }

    @Test
    void parsesYearsAndIgnoresJunk() {
        assertEquals(List.of(2024, 2023), ScheduledPriorYearBackfill.parseYears("2024, 2023, abc, 24, 2024"));
        assertEquals(List.of(), ScheduledPriorYearBackfill.parseYears(""));
        assertEquals(List.of(), ScheduledPriorYearBackfill.parseYears(null));
    }

    @Test
    void movesToNextYearOnlyWhenNothingIsPending() {
        Fake fake = new Fake(new PriorYearBackfill.Result(5, 5, 5, 0, 0, PriorYearBackfill.StopReason.NONE),
                new PriorYearBackfill.Result(0, 0, 0, 0, 0, PriorYearBackfill.StopReason.NONE),
                new PriorYearBackfill.Result(3, 3, 3, 0, 0, PriorYearBackfill.StopReason.NONE));
        ScheduledPriorYearBackfill s = scheduler(fake, List.of(2024, 2023), "live");
        s.tick();
        s.tick();
        s.tick();
        assertEquals(List.of(2024, 2024, 2023), fake.years);
    }

    @Test
    void doesNothingWithoutLiveModeOrYears() {
        Fake fake = new Fake();
        scheduler(fake, List.of(2024), "demo").tick();
        scheduler(fake, List.of(), "live").tick();
        assertTrue(fake.years.isEmpty());
    }

    @Test
    void pausesAfterRateLimit() {
        Fake fake = new Fake(new PriorYearBackfill.Result(9, 2, 1, 0, 0, PriorYearBackfill.StopReason.RATE_LIMIT));
        ScheduledPriorYearBackfill s = scheduler(fake, List.of(2024), "live");
        s.tick();
        s.tick();
        assertEquals(1, fake.years.size());
    }

    @Test
    void waitsAfterAllYearsAreDone() {
        Fake fake = new Fake();
        ScheduledPriorYearBackfill s = scheduler(fake, List.of(2024), "live");
        s.tick(); // nothing pending for 2024 -> index moves on
        s.tick(); // all years done -> long pause
        s.tick();
        assertEquals(1, fake.years.size());
    }

    @Test
    void providerFailureIsCaughtAndRetriedLater() {
        PriorYearBackfill broken = (year, limit, pause, skip) -> { throw new IllegalStateException("boom"); };
        ScheduledPriorYearBackfill s = new ScheduledPriorYearBackfill(broken, "live", "key", List.of(2024), 40, 0,
                Duration.ofHours(12), Runnable::run);
        s.tick();
        s.tick();
    }
}
