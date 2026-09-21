package com.aira.api.market.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.aira.api.market.dto.CanonicalDataState;
import com.aira.api.market.dto.MarketIndexBoardResponse;
import com.aira.api.market.dto.MarketIndexBoardResponse.IndexView;
import com.aira.api.market.query.MarketIndexQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MarketIndexControllerTests {
    @Test void exposesLatestOfficialIndexBoardWithoutRecommendationFields() throws Exception {
        MarketIndexQuery query = mock(MarketIndexQuery.class);
        UUID evidenceId = UUID.randomUUID();
        when(query.latest()).thenReturn(new MarketIndexBoardResponse(List.of(
                new IndexView("KOSPI", CanonicalDataState.AVAILABLE, null, LocalDate.of(2026, 9, 18),
                        new BigDecimal("3123.45"), new BigDecimal("-12.30"), new BigDecimal("-0.39"),
                        evidenceId, "KRX Data Marketplace Open API", "https://example.test/kospi"),
                new IndexView("KOSDAQ", CanonicalDataState.NO_DATA, "NO_INDEX_FACTS",
                        null, null, null, null, null, null, null))));

        MockMvcBuilders.standaloneSetup(new MarketIndexController(query)).build()
                .perform(get("/api/market-indices/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indices[0].marketCode").value("KOSPI"))
                .andExpect(jsonPath("$.indices[0].state").value("AVAILABLE"))
                .andExpect(jsonPath("$.indices[0].tradingDate").value("2026-09-18"))
                .andExpect(jsonPath("$.indices[0].close").value(3123.45))
                .andExpect(jsonPath("$.indices[0].changeRate").value(-0.39))
                .andExpect(jsonPath("$.indices[0].evidenceId").value(evidenceId.toString()))
                .andExpect(jsonPath("$.indices[1].state").value("NO_DATA"))
                .andExpect(jsonPath("$.recommendation").doesNotExist())
                .andExpect(jsonPath("$.ranking").doesNotExist());
    }
}
