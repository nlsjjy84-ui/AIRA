package com.aira.api.market.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.aira.api.market.dto.CanonicalDataState;
import com.aira.api.market.dto.EconomicIndicatorResponse;
import com.aira.api.market.dto.EconomicIndicatorResponse.ObservationView;
import com.aira.api.market.query.RealGdpIndicatorQuery;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EconomicIndicatorControllerTests {
    @Test void exposesOfficialObservationsWithoutAssessmentFields() throws Exception {
        RealGdpIndicatorQuery query = mock(RealGdpIndicatorQuery.class);
        UUID evidenceId = UUID.randomUUID();
        when(query.latest()).thenReturn(new EconomicIndicatorResponse(CanonicalDataState.AVAILABLE,
                null, "실질 국내총생산", "십억원", "한국은행 ECOS", List.of(
                new ObservationView("2025Q2", new BigDecimal("617220.6"), evidenceId),
                new ObservationView("2025Q1", new BigDecimal("614115.3"), evidenceId))));

        MockMvcBuilders.standaloneSetup(new EconomicIndicatorController(query)).build()
                .perform(get("/api/economic-indicators/real-gdp/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.seriesName").value("실질 국내총생산"))
                .andExpect(jsonPath("$.unitName").value("십억원"))
                .andExpect(jsonPath("$.observations[0].period").value("2025Q2"))
                .andExpect(jsonPath("$.observations[0].value").value(617220.6))
                .andExpect(jsonPath("$.observations[0].evidenceId").value(evidenceId.toString()))
                .andExpect(jsonPath("$.assessment").doesNotExist())
                .andExpect(jsonPath("$.recommendation").doesNotExist());
    }
}
