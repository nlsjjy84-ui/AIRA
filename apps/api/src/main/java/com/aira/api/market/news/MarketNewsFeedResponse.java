package com.aira.api.market.news;

import java.time.OffsetDateTime;
import java.util.List;

public record MarketNewsFeedResponse(String provider, OffsetDateTime fetchedAt,
        boolean stale, List<MarketNewsArticle> items) {}
