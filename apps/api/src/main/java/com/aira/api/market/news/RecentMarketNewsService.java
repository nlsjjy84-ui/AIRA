package com.aira.api.market.news;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RecentMarketNewsService {
    private static final Logger log = LoggerFactory.getLogger(RecentMarketNewsService.class);
    static final String PROVIDER = "GDELT DOC 2.0";
    private final MarketNewsClient client;
    private final MarketNewsCacheStore cacheStore;
    private final Clock clock;
    private final Duration ttl;

    @Autowired
    public RecentMarketNewsService(MarketNewsClient client, MarketNewsCacheStore cacheStore,
            @Value("${aira.news.gdelt.cache-minutes:10}") long cacheMinutes) {
        this(client, cacheStore, Clock.systemUTC(), Duration.ofMinutes(Math.max(1, cacheMinutes)));
    }

    RecentMarketNewsService(MarketNewsClient client, MarketNewsCacheStore cacheStore,
            Clock clock, Duration ttl) {
        if (client == null || cacheStore == null || clock == null || ttl == null || ttl.isZero() || ttl.isNegative())
            throw new IllegalArgumentException("News service dependencies are required");
        this.client = client;
        this.cacheStore = cacheStore;
        this.clock = clock;
        this.ttl = ttl;
    }

    public synchronized MarketNewsFeedResponse latest() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        MarketNewsFeedResponse cached = cacheStore.latest(PROVIDER).orElse(null);
        if (cached != null && now.isBefore(cached.fetchedAt().plus(ttl))) return cached;
        try {
            List<MarketNewsArticle> items = client.fetchRecentKoreanMarketNews().stream().limit(6).toList();
            MarketNewsFeedResponse response = new MarketNewsFeedResponse(PROVIDER, now, false, items);
            cacheStore.replace(PROVIDER, now, items);
            return response;
        } catch (RuntimeException failure) {
            log.warn("GDELT news fetch failed ({}); {}", failure.getMessage(),
                    cached != null ? "serving cached items" : "no cache available");
            if (cached != null) return new MarketNewsFeedResponse(PROVIDER,
                    cached.fetchedAt(), true, cached.items());
            throw new MarketNewsUnavailableException(failure);
        }
    }
}
