package com.aira.api.market.news;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HttpGdeltMarketNewsClientTests {
    private final HttpGdeltMarketNewsClient client = new HttpGdeltMarketNewsClient(
            request -> { throw new AssertionError("network must not be called"); },
            new ObjectMapper(), URI.create("https://example.test/news"));

    @Test void buildsDateOrderedKoreanMarketMetadataQueryWithoutArticleBody() {
        String uri = client.buildUri().toString();
        assertTrue(uri.contains("KOSPI%20OR%20KOSDAQ"));
        assertTrue(uri.contains("sourcecountry%3Asouthkorea"));
        assertTrue(uri.contains("timespan=24h"));
        assertTrue(uri.contains("sort=datedesc"));
        assertFalse(uri.contains("fulltext"));
        assertEquals(java.time.Duration.ofSeconds(6), client.buildRequest().timeout().orElseThrow());
    }

    @Test void parsesUtf8MetadataDeduplicatesUrlsAndSkipsMalformedArticles() {
        String body = """
                {"articles":[
                  {"title":"&lt;시장&gt; 첫 기사","url":"https://news.example/a","domain":"News.Example","seendate":"20260921T020000Z","language":"Korean","sourcecountry":"South Korea","extra":"ignored"},
                  {"title":"중복 기사","url":"https://news.example/a","domain":"news.example","seendate":"20260921T010000Z","language":"Korean"},
                  {"title":"두 번째 기사","url":"https://other.example/b","domain":"other.example","seendate":"20260921T030000Z","language":"Korean"},
                  {"title":"본문 없는 메타데이터","url":"javascript:alert(1)","domain":"bad.example","seendate":"20260921T040000Z"},
                  {"title":"시간 없음","url":"https://bad.example/c","domain":"bad.example"}
                ]}
                """;
        var items = client.parse(body);
        assertEquals(2, items.size());
        assertEquals("두 번째 기사", items.get(0).title());
        assertEquals(OffsetDateTime.parse("2026-09-21T03:00:00Z"), items.get(0).seenAt());
        assertEquals("<시장> 첫 기사", items.get(1).title());
        assertEquals("news.example", items.get(1).domain());
        assertEquals("Korean", items.get(1).language());
        assertEquals("South Korea", items.get(1).sourceCountry());
    }

    @Test void malformedProviderShapeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> client.parse("{}"));
        assertThrows(IllegalArgumentException.class, () -> client.parse(""));
    }
}
