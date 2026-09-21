package com.aira.api.market.news;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RecentMarketNewsService {
    static final String PROVIDER = "GDELT DOC 2.0";
    private final MarketNewsClient client;
    private final Clock clock;
    private final Duration ttl;
    private volatile Cache cache;

    @Autowired
    public RecentMarketNewsService(MarketNewsClient client,
            @Value("${aira.news.gdelt.cache-minutes:10}") long cacheMinutes) {
        this(client, Clock.systemUTC(), Duration.ofMinutes(Math.max(1, cacheMinutes)));
    }

    RecentMarketNewsService(MarketNewsClient client, Clock clock, Duration ttl) {
        if (client == null || clock == null || ttl == null || ttl.isZero() || ttl.isNegative())
            throw new IllegalArgumentException("News service dependencies are required");
        this.client = client;
        this.clock = clock;
        this.ttl = ttl;
    }

    public MarketNewsFeedResponse latest() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Cache current = cache;
        if (current != null && now.isBefore(current.expiresAt())) return current.response();
        synchronized (this) {
            now = OffsetDateTime.now(clock);
            current = cache;
            if (current != null && now.isBefore(current.expiresAt())) return current.response();
            try {
                List<MarketNewsArticle> items = client.fetchRecentKoreanMarketNews().stream().limit(6).toList();
                MarketNewsFeedResponse response = new MarketNewsFeedResponse(PROVIDER, now, false, items);
                cache = new Cache(response, now.plus(ttl));
                return response;
            } catch (RuntimeException failure) {
                if (current != null) {
                    MarketNewsFeedResponse stale = new MarketNewsFeedResponse(PROVIDER,
                            current.response().fetchedAt(), true, current.response().items());
                    cache = new Cache(stale, now.plus(Duration.ofMinutes(1)));
                    return stale;
                }
                throw new MarketNewsUnavailableException(failure);
            }
        }
    }

    private record Cache(MarketNewsFeedResponse response, OffsetDateTime expiresAt) {}
}
