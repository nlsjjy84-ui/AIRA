package com.aira.api.market.news;

import java.time.OffsetDateTime;

public record MarketNewsArticle(String title, String originalUrl, String domain,
        OffsetDateTime seenAt, String language, String sourceCountry) {}
