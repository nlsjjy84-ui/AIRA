package com.aira.api.market.news;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RecentMarketNewsServiceTests {
    @Test void capsFeedAtSixAndCachesFreshProviderResult() {
        AtomicInteger calls = new AtomicInteger();
        MarketNewsClient client = () -> {
            calls.incrementAndGet();
            return java.util.stream.IntStream.range(0, 8)
                    .mapToObj(i -> article(i, "https://news.example/" + i)).toList();
        };
        var service = new RecentMarketNewsService(client,
                Clock.fixed(Instant.parse("2026-09-21T02:00:00Z"), ZoneOffset.UTC), Duration.ofMinutes(10));
        assertEquals(6, service.latest().items().size());
        assertEquals(6, service.latest().items().size());
        assertEquals(1, calls.get());
        assertFalse(service.latest().stale());
    }

    @Test void returnsMarkedStaleCacheWhenRefreshFails() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-21T02:00:00Z"));
        AtomicInteger calls = new AtomicInteger();
        MarketNewsClient client = () -> {
            if (calls.getAndIncrement() == 0) return List.of(article(1, "https://news.example/1"));
            throw new IllegalStateException("provider down");
        };
        var service = new RecentMarketNewsService(client, clock, Duration.ofMinutes(10));
        var fresh = service.latest();
        clock.advance(Duration.ofMinutes(11));
        var stale = service.latest();
        assertFalse(fresh.stale());
        assertTrue(stale.stale());
        assertEquals(fresh.fetchedAt(), stale.fetchedAt());
        assertEquals(fresh.items(), stale.items());
        assertEquals(2, calls.get());
    }

    @Test void firstProviderFailureBecomesServiceUnavailable() {
        var service = new RecentMarketNewsService(() -> { throw new IllegalStateException("down"); },
                Clock.systemUTC(), Duration.ofMinutes(10));
        assertThrows(MarketNewsUnavailableException.class, service::latest);
    }

    private static MarketNewsArticle article(int i, String url) {
        return new MarketNewsArticle("기사 " + i, url, "news.example",
                OffsetDateTime.parse("2026-09-21T02:00:00Z").minusMinutes(i), "Korean", "South Korea");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
