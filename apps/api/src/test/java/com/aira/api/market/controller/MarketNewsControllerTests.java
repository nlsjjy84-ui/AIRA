package com.aira.api.market.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.aira.api.market.news.MarketNewsArticle;
import com.aira.api.market.news.MarketNewsFeedResponse;
import com.aira.api.market.news.MarketNewsUnavailableException;
import com.aira.api.market.news.RecentMarketNewsService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MarketNewsControllerTests {
    @Test void exposesMetadataLinksOnlyWithoutRecommendationOrAiFields() throws Exception {
        RecentMarketNewsService service = mock(RecentMarketNewsService.class);
        when(service.latest()).thenReturn(new MarketNewsFeedResponse("GDELT DOC 2.0",
                OffsetDateTime.parse("2026-09-21T02:00:00Z"), false, List.of(
                new MarketNewsArticle("시장 기사", "https://news.example/a", "news.example",
                        OffsetDateTime.parse("2026-09-21T01:30:00Z"), "Korean", "South Korea"))));
        MockMvcBuilders.standaloneSetup(new MarketNewsController(service)).build()
                .perform(get("/api/news/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("GDELT DOC 2.0"))
                .andExpect(jsonPath("$.items[0].title").value("시장 기사"))
                .andExpect(jsonPath("$.items[0].originalUrl").value("https://news.example/a"))
                .andExpect(jsonPath("$.items[0].domain").value("news.example"))
                .andExpect(jsonPath("$.items[0].body").doesNotExist())
                .andExpect(jsonPath("$.items[0].summary").doesNotExist())
                .andExpect(jsonPath("$.items[0].sentiment").doesNotExist())
                .andExpect(jsonPath("$.ranking").doesNotExist())
                .andExpect(jsonPath("$.recommendation").doesNotExist());
    }

    @Test void mapsProviderFailureToServiceUnavailable() throws Exception {
        RecentMarketNewsService service = mock(RecentMarketNewsService.class);
        when(service.latest()).thenThrow(new MarketNewsUnavailableException(new IllegalStateException("down")));
        MockMvcBuilders.standaloneSetup(new MarketNewsController(service)).build()
                .perform(get("/api/news/latest"))
                .andExpect(status().isServiceUnavailable());
    }
}
