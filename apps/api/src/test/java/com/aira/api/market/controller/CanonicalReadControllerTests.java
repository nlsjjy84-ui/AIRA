package com.aira.api.market.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.aira.api.analysis.dto.HistoricalAssessmentResponse;
import com.aira.api.analysis.query.CurrentAssessmentQuery;
import com.aira.api.analysis.query.HistoricalAssessmentNotFoundException;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.dto.*;
import com.aira.api.market.krx.*;
import com.aira.api.market.query.*;
import com.aira.api.market.repository.FactRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CanonicalReadControllerTests {
    CanonicalEntitySearchQuery search = mock(CanonicalEntitySearchQuery.class);
    FinancialHistoricalExactQuery historical = mock(FinancialHistoricalExactQuery.class);
    FinancialCurrentQuery financialCurrent = mock(FinancialCurrentQuery.class);
    CurrentAssessmentQuery assessment = mock(CurrentAssessmentQuery.class);
    KrxCurrentQuery market = mock(KrxCurrentQuery.class);
    FactRepository facts = mock(FactRepository.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    MockMvc mvc;
    UUID company = UUID.randomUUID(), event = UUID.randomUUID(), security = UUID.randomUUID();
    @BeforeEach void setup() { mvc = MockMvcBuilders.standaloneSetup(new CanonicalReadController(
            search, historical, financialCurrent, assessment, market, facts, jdbc)).build(); }

    @Test void searchReturnsTypedCanonicalIdentityWithoutRecommendationFields() throws Exception {
        when(search.find("Same")).thenReturn(new EntitySearchResponse(CanonicalDataState.AVAILABLE,
                List.of(new EntitySearchResponse.Item(company,
                        com.aira.api.market.domain.EntityType.COMPANY, "COMPANY:" + company,
                        "Same", null, null),
                        new EntitySearchResponse.Item(security,
                                com.aira.api.market.domain.EntityType.SECURITY, "SECURITY:" + security,
                                "Same", "KOSPI", "000001"))));
        mvc.perform(get("/api/search").param("query", "Same"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entities.length()").value(2))
                .andExpect(jsonPath("$.entities[0].entityType").value("COMPANY"))
                .andExpect(jsonPath("$.entities[1].entityType").value("SECURITY"));
    }

    @Test void financialExactAndCurrentRemainExplicitSeparateSelections() throws Exception {
        var fact = new CompanyFinancialFactView(company, FactPredicate.REVENUE, new BigDecimal("100"),
                "KRW", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), "OpenDART", UUID.randomUUID(),
                "20260101000001", "https://opendart.fss.or.kr/api/fnlttSinglAcnt.json");
        var result = new CompanyFinancialFactsResult(company, List.of(fact));
        when(historical.find(eq(company), any(), any(), eq("20260101000001"), any())).thenReturn(result);
        when(financialCurrent.find(any())).thenReturn(result);
        String path = "/api/companies/" + company + "/financial-facts/";
        for (String selection : List.of("exact", "current")) {
            mvc.perform(get(path + selection).param("periodStart", "2025-01-01")
                            .param("periodEnd", "2025-12-31").param("receipt", "20260101000001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.selection").value(selection.equals("exact")
                            ? "HISTORICAL_EXACT" : "EXPLICIT_FINANCIAL_CURRENT"))
                    .andExpect(jsonPath("$.state").value("PARTIAL"))
                    .andExpect(jsonPath("$.receipt").value("20260101000001"))
                    .andExpect(jsonPath("$.value.facts[0].evidenceId").exists());
        }
        verify(historical).find(eq(company), any(), any(), eq("20260101000001"), any());
        verify(financialCurrent).find(any());
    }

    @Test void graphCurrentAndExactMarketMissHaveDifferentStates() throws Exception {
        UUID a2 = UUID.randomUUID(), a1 = UUID.randomUUID();
        when(assessment.find(event)).thenReturn(new HistoricalAssessmentResponse(a2, event,
                "v2", "RULE", "HIGH", "Known", OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                a1, List.of(UUID.randomUUID())));
        mvc.perform(get("/api/assessments/current").param("eventId", event.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selection").value("SUPERSESSION_TERMINAL"))
                .andExpect(jsonPath("$.value.assessmentId").value(a2.toString()))
                .andExpect(jsonPath("$.value.supersedesAssessmentId").value(a1.toString()));
        when(market.find(security, FactPredicate.CLOSE_PRICE)).thenThrow(new KrxCurrentExactMissException());
        mvc.perform(get("/api/securities/{id}/market-current", security).param("predicate", "CLOSE_PRICE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("NO_DATA"))
                .andExpect(jsonPath("$.targetType").value("SECURITY"));
    }

    @Test void absentAssessmentIsNotSynthesized() throws Exception {
        when(assessment.find(event)).thenThrow(new HistoricalAssessmentNotFoundException());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(event))).thenReturn(0);
        mvc.perform(get("/api/assessments/current").param("eventId", event.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("NO_DATA"))
                .andExpect(jsonPath("$.value").doesNotExist());
    }
}
