package com.aira.api.market.news;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcMarketNewsCacheStore implements MarketNewsCacheStore {
    private final JdbcTemplate jdbc;

    public JdbcMarketNewsCacheStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<MarketNewsFeedResponse> latest(String provider) {
        OffsetDateTime fetchedAt = jdbc.query("SELECT max(fetched_at) FROM market_news_cache WHERE provider=?",
                rs -> rs.next() ? rs.getObject(1, OffsetDateTime.class) : null, provider);
        if (fetchedAt == null) return Optional.empty();
        List<MarketNewsArticle> items = jdbc.query("""
                SELECT title, original_url, domain, seen_at, language, source_country
                FROM market_news_cache
                WHERE provider=? AND fetched_at=?
                ORDER BY seen_at DESC, original_url
                """, (rs, rowNum) -> new MarketNewsArticle(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class), rs.getString(5), rs.getString(6)),
                provider, fetchedAt);
        return Optional.of(new MarketNewsFeedResponse(provider, fetchedAt, false, List.copyOf(items)));
    }

    @Override
    @Transactional
    public void replace(String provider, OffsetDateTime fetchedAt, List<MarketNewsArticle> items) {
        jdbc.update("DELETE FROM market_news_cache WHERE provider=?", provider);
        for (MarketNewsArticle item : items) {
            jdbc.update("""
                    INSERT INTO market_news_cache(provider,original_url,title,domain,seen_at,language,source_country,fetched_at)
                    VALUES(?,?,?,?,?,?,?,?)
                    """, provider, item.originalUrl(), item.title(), item.domain(), item.seenAt(),
                    item.language(), item.sourceCountry(), fetchedAt);
        }
    }
}
