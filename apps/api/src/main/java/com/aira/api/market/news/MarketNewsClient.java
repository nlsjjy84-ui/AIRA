package com.aira.api.market.news;

import java.util.List;

public interface MarketNewsClient {
    List<MarketNewsArticle> fetchRecentKoreanMarketNews();
}
