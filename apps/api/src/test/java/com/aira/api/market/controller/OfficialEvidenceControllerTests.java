package com.aira.api.market.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aira.api.market.dto.OfficialEvidenceResponse;
import com.aira.api.market.query.OfficialEvidenceNotFoundException;
import com.aira.api.market.query.OfficialEvidenceQuery;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OfficialEvidenceControllerTests {
    private final OfficialEvidenceQuery query = mock(OfficialEvidenceQuery.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(query);
        mvc = MockMvcBuilders.standaloneSetup(new OfficialEvidenceController(query)).build();
    }

    @Test
    void exposesStoredCanonicalEvidenceAndSourceMetadata() throws Exception {
        UUID id = UUID.randomUUID();
        when(query.find(id)).thenReturn(new OfficialEvidenceResponse(id, "DISCLOSURE", "EXT",
                "Stored title", "stored://url", OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                OffsetDateTime.parse("2026-08-21T00:00:00Z"), 2, "section", "excerpt",
                new OfficialEvidenceResponse.Source(UUID.randomUUID(), "Official", "REGULATORY_FILING",
                        "official.example")));
        mvc.perform(get("/api/evidence/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceId").value(id.toString()))
                .andExpect(jsonPath("$.originalUrl").value("stored://url"))
                .andExpect(jsonPath("$.publishedAt").value("2026-08-20T00:00:00Z"))
                .andExpect(jsonPath("$.collectedAt").value("2026-08-21T00:00:00Z"))
                .andExpect(jsonPath("$.source.sourceName").value("Official"));
    }

    @Test
    void mapsMissingAndInternalEvidenceToNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(query.find(id)).thenThrow(new OfficialEvidenceNotFoundException());
        mvc.perform(get("/api/evidence/{id}", id)).andExpect(status().isNotFound());
    }
}
