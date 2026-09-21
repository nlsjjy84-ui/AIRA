package com.aira.api.market.news;

import static org.junit.jupiter.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class MarketNewsCacheStorePostgresTests {
    @Autowired MarketNewsCacheStore store;

    @Test void persistedMetadataSurvivesReadAndReplaceWithoutArticleBody() {
        String provider = "TEST-NEWS-" + UUID.randomUUID();
        OffsetDateTime firstFetchedAt = OffsetDateTime.parse("2099-01-01T00:00:00Z");
        store.replace(provider, firstFetchedAt, List.of(
                article("첫 기사", "https://news.example/a", "2099-01-01T00:01:00Z"),
                article("두 번째 기사", "https://news.example/b", "2099-01-01T00:02:00Z")));

        var first = store.latest(provider).orElseThrow();
        assertEquals(firstFetchedAt, first.fetchedAt());
        assertEquals(List.of("두 번째 기사", "첫 기사"), first.items().stream().map(MarketNewsArticle::title).toList());

        OffsetDateTime secondFetchedAt = OffsetDateTime.parse("2099-01-01T01:00:00Z");
        store.replace(provider, secondFetchedAt,
                List.of(article("새 기사", "https://news.example/c", "2099-01-01T00:59:00Z")));
        var second = store.latest(provider).orElseThrow();
        assertEquals(secondFetchedAt, second.fetchedAt());
        assertEquals(1, second.items().size());
        assertEquals("새 기사", second.items().getFirst().title());
    }

    private static MarketNewsArticle article(String title, String url, String seenAt) {
        return new MarketNewsArticle(title, url, URI_DOMAIN, OffsetDateTime.parse(seenAt), "Korean", "South Korea");
    }

    private static final String URI_DOMAIN = "news.example";
}
