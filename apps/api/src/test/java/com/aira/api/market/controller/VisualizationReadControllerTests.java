package com.aira.api.market.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.dto.*;
import com.aira.api.market.query.FinancialExactComparisonQuery;
import com.aira.api.market.query.KrxStoredSeriesQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VisualizationReadControllerTests {
    FinancialExactComparisonQuery financial = mock(FinancialExactComparisonQuery.class);
    KrxStoredSeriesQuery market = mock(KrxStoredSeriesQuery.class);
    UUID company = UUID.randomUUID(), security = UUID.randomUUID(), fact = UUID.randomUUID();

    @Test void routesOnlyCallerSelectedExactPeriodsToComparison() throws Exception {
        when(financial.find(eq(company), any(), any(), any())).thenAnswer(invocation ->
                new FinancialExactComparisonResponse(CanonicalDataState.NO_DATA, "B_FACTS_NOT_FOUND",
                        company, invocation.getArgument(1), invocation.getArgument(2), List.of()));
        MockMvcBuilders.standaloneSetup(new VisualizationReadController(financial, market)).build()
                .perform(get("/api/companies/{id}/financial-facts/compare", company)
                        .param("aStart", "2024-01-01").param("aEnd", "2024-12-31")
                        .param("aReceipt", "20250101000001").param("bStart", "2025-01-01")
                        .param("bEnd", "2025-12-31").param("bReceipt", "20260101000001")
                        .param("predicates", "REVENUE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("NO_DATA"))
                .andExpect(jsonPath("$.reason").value("B_FACTS_NOT_FOUND"))
                .andExpect(jsonPath("$.a.receipt").value("20250101000001"))
                .andExpect(jsonPath("$.b.receipt").value("20260101000001"));
    }

    @Test void exposesOrderedSeriesAndExplicitCurrentFactIdentity() throws Exception {
        var date = LocalDate.of(2035, 1, 2);
        when(market.find(security, FactPredicate.CLOSE_PRICE, date, date))
                .thenReturn(new KrxStoredSeriesResponse(CanonicalDataState.NO_DATA, "NO_STORED_OFFICIAL_OBSERVATIONS",
                        security, FactPredicate.CLOSE_PRICE, date, date, List.of()));
        when(market.previous(security, FactPredicate.CLOSE_PRICE, date, fact))
                .thenReturn(new KrxPreviousObservationResponse(CanonicalDataState.NO_DATA,
                        "EXACT_D_FACT_MISSING", security, FactPredicate.CLOSE_PRICE, date, fact,
                        null, null, null, null, null));
        var mvc = MockMvcBuilders.standaloneSetup(new VisualizationReadController(financial, market)).build();
        mvc.perform(get("/api/securities/{id}/market-series", security).param("predicate", "CLOSE_PRICE")
                        .param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("NO_DATA"));
        mvc.perform(get("/api/securities/{id}/market-previous", security).param("predicate", "CLOSE_PRICE")
                        .param("currentDate", date.toString()).param("currentFactId", fact.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("EXACT_D_FACT_MISSING"));
    }
}
