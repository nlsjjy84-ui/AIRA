package com.aira.api.market.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aira.api.market.query.PublicEventFeedQuery;
import com.aira.api.market.query.PublicEventView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicEventControllerTests {
    private final PublicEventFeedQuery query = mock(PublicEventFeedQuery.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(query);
        mvc = MockMvcBuilders.standaloneSetup(new PublicEventController(query)).build();
    }

    @Test
    void exposesFactualEventAndNavigationIdentityWithoutAssessmentFields() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(query.findRecentEvents()).thenReturn(List.of(new PublicEventView(eventId, companyId,
                "삼성전자", "EARNINGS", "공식 사실 제목",
                OffsetDateTime.parse("2026-08-25T00:00:00Z"))));

        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.events[0].companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.events[0].companyName").value("삼성전자"))
                .andExpect(jsonPath("$.events[0].eventType").value("EARNINGS"))
                .andExpect(jsonPath("$.events[0].title").value("공식 사실 제목"))
                .andExpect(jsonPath("$.events[0].occurredAt").value("2026-08-25T00:00:00Z"))
                .andExpect(jsonPath("$.events[0].assessment").doesNotExist());
    }

    @Test
    void emptyFeedIsARegularSuccessfulResponse() throws Exception {
        when(query.findRecentEvents()).thenReturn(List.of());

        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isEmpty());
    }
}
