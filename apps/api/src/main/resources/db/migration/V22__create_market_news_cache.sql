CREATE TABLE market_news_cache (
    provider VARCHAR(80) NOT NULL,
    original_url TEXT NOT NULL,
    title VARCHAR(500) NOT NULL,
    domain VARCHAR(255) NOT NULL,
    seen_at TIMESTAMPTZ NOT NULL,
    language VARCHAR(64),
    source_country VARCHAR(64),
    fetched_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (provider, original_url)
);

CREATE INDEX idx_market_news_cache_provider_fetched
    ON market_news_cache(provider, fetched_at DESC, seen_at DESC);
