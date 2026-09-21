package com.aira.api.market.news;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketNewsCacheStore {
    Optional<MarketNewsFeedResponse> latest(String provider);
    void replace(String provider, OffsetDateTime fetchedAt, List<MarketNewsArticle> items);
}
