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
    /** 실패 직후에는 GDELT를 다시 두드리지 않는다(요청 제한 429를 피하고 화면이 오래 기다리지 않게). */
    static final Duration FAILURE_BACKOFF = Duration.ofMinutes(2);
    private OffsetDateTime lastFailureAt;

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

    public MarketNewsFeedResponse latest() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        MarketNewsFeedResponse cached = cacheStore.latest(PROVIDER).orElse(null);
        if (isFresh(cached, now)) return cached;
        synchronized (this) {
            now = OffsetDateTime.now(clock);
            cached = cacheStore.latest(PROVIDER).orElse(null);
            if (isFresh(cached, now)) return cached;
            return fetchOrFallBack(now, cached);
        }
    }

    /** 스케줄러가 부른다. 캐시 유효시간과 상관없이 새로 받아 저장하고, 실패해도 예외를 던지지 않는다. */
    public synchronized void refresh() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        try {
            fetchOrFallBack(now, cacheStore.latest(PROVIDER).orElse(null));
        } catch (MarketNewsUnavailableException unavailable) {
            // 실패 원인은 fetchOrFallBack 안에서 이미 로그로 남겼다.
        }
    }

    private boolean isFresh(MarketNewsFeedResponse cached, OffsetDateTime now) {
        return cached != null && now.isBefore(cached.fetchedAt().plus(ttl));
    }

    private MarketNewsFeedResponse fetchOrFallBack(OffsetDateTime now, MarketNewsFeedResponse cached) {
        if (lastFailureAt != null && now.isBefore(lastFailureAt.plus(FAILURE_BACKOFF))) {
            return fallBack(cached, null);
        }
        try {
            List<MarketNewsArticle> items = client.fetchRecentKoreanMarketNews().stream().limit(6).toList();
            MarketNewsFeedResponse response = new MarketNewsFeedResponse(PROVIDER, now, false, items);
            cacheStore.replace(PROVIDER, now, items);
            lastFailureAt = null;
            return response;
        } catch (RuntimeException failure) {
            lastFailureAt = now;
            log.warn("GDELT news fetch failed ({}); {}", failure.getMessage(),
                    cached != null ? "serving cached items" : "no cache available");
            return fallBack(cached, failure);
        }
    }

    private MarketNewsFeedResponse fallBack(MarketNewsFeedResponse cached, RuntimeException failure) {
        if (cached != null) return new MarketNewsFeedResponse(PROVIDER, cached.fetchedAt(), true, cached.items());
        throw new MarketNewsUnavailableException(failure);
    }
}
