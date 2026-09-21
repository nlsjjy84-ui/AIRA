package com.aira.api.market.news;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RecentMarketNewsServiceTests {
    @Test void returnsFreshPersistedCacheWithoutCallingProvider() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-21T02:00:00Z"));
        MemoryStore store = new MemoryStore();
        store.replace(RecentMarketNewsService.PROVIDER, OffsetDateTime.parse("2026-09-21T01:55:00Z"),
                List.of(article(1, "https://news.example/1")));
        AtomicInteger calls = new AtomicInteger();
        MarketNewsClient client = () -> { calls.incrementAndGet(); return List.of(); };
        var service = new RecentMarketNewsService(client, store, clock, Duration.ofMinutes(10));

        var result = service.latest();

        assertEquals(1, result.items().size());
        assertEquals(0, calls.get());
        assertFalse(result.stale());
    }

    @Test void refreshesExpiredCacheCapsAtSixAndPersistsResult() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-21T02:00:00Z"));
        MemoryStore store = new MemoryStore();
        store.replace(RecentMarketNewsService.PROVIDER, OffsetDateTime.parse("2026-09-21T01:40:00Z"),
                List.of(article(99, "https://old.example/99")));
        AtomicInteger calls = new AtomicInteger();
        MarketNewsClient client = () -> {
            calls.incrementAndGet();
            return java.util.stream.IntStream.range(0, 8)
                    .mapToObj(i -> article(i, "https://news.example/" + i)).toList();
        };
        var service = new RecentMarketNewsService(client, store, clock, Duration.ofMinutes(10));

        var result = service.latest();

        assertEquals(6, result.items().size());
        assertEquals(1, calls.get());
        assertEquals(6, store.latest(RecentMarketNewsService.PROVIDER).orElseThrow().items().size());
        assertFalse(result.stale());
    }

    @Test void returnsMarkedStalePersistedCacheWhenRefreshFails() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-21T02:00:00Z"));
        MemoryStore store = new MemoryStore();
        OffsetDateTime fetchedAt = OffsetDateTime.parse("2026-09-21T01:30:00Z");
        store.replace(RecentMarketNewsService.PROVIDER, fetchedAt,
                List.of(article(1, "https://news.example/1")));
        var service = new RecentMarketNewsService(() -> { throw new IllegalStateException("429"); },
                store, clock, Duration.ofMinutes(10));

        var stale = service.latest();

        assertTrue(stale.stale());
        assertEquals(fetchedAt, stale.fetchedAt());
        assertEquals(1, stale.items().size());
    }

    @Test void firstProviderFailureWithoutPersistedCacheBecomesServiceUnavailable() {
        var service = new RecentMarketNewsService(() -> { throw new IllegalStateException("down"); },
                new MemoryStore(), Clock.systemUTC(), Duration.ofMinutes(10));
        assertThrows(MarketNewsUnavailableException.class, service::latest);
    }

    private static MarketNewsArticle article(int i, String url) {
        return new MarketNewsArticle("기사 " + i, url, "news.example",
                OffsetDateTime.parse("2026-09-21T02:00:00Z").minusMinutes(i), "Korean", "South Korea");
    }

    private static final class MemoryStore implements MarketNewsCacheStore {
        private MarketNewsFeedResponse value;
        @Override public Optional<MarketNewsFeedResponse> latest(String provider) { return Optional.ofNullable(value); }
        @Override public void replace(String provider, OffsetDateTime fetchedAt, List<MarketNewsArticle> items) {
            value = new MarketNewsFeedResponse(provider, fetchedAt, false, List.copyOf(new ArrayList<>(items)));
        }
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
