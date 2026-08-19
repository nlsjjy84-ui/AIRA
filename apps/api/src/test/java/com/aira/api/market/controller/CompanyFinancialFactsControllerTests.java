package com.aira.api.market.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.query.CompanyFinancialFactView;
import com.aira.api.market.query.CompanyFinancialFactsQuery;
import com.aira.api.market.query.CompanyFinancialFactsQueryException;
import com.aira.api.market.query.CompanyFinancialFactsQueryInput;
import com.aira.api.market.query.CompanyFinancialFactsResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CompanyFinancialFactsControllerTests {
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID EVIDENCE_ID = UUID.randomUUID();
    private static final String PATH = "/api/companies/" + COMPANY_ID + "/financial-facts";
    private final CompanyFinancialFactsQuery query = mock(CompanyFinancialFactsQuery.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CompanyFinancialFactsController(query))
                .setControllerAdvice(new CompanyFinancialFactsExceptionHandler()).build();
    }

    @Test
    void returnsExactPeriodFactsWithPreciseValuesAndSharedEvidence() throws Exception {
        when(query.find(any())).thenReturn(result());

        mvc.perform(get(PATH).param("periodStart", "2025-01-01")
                        .param("periodEnd", "2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.facts[0].value").value(333605938000000L))
                .andExpect(jsonPath("$.facts[1].value").value(43601051000000L))
                .andExpect(jsonPath("$.facts[0].currency").value("KRW"))
                .andExpect(jsonPath("$.facts[0].periodStart").value("2025-01-01"))
                .andExpect(jsonPath("$.facts[0].periodEnd").value("2025-12-31"))
                .andExpect(jsonPath("$.facts[0].publishedAt").doesNotExist())
                .andExpect(jsonPath("$.facts[0].collectedAt").value("2026-08-19T07:00:00Z"))
                .andExpect(jsonPath("$.facts[0].sourceName").value("OpenDART"))
                .andExpect(jsonPath("$.facts[0].evidenceId").value(EVIDENCE_ID.toString()))
                .andExpect(jsonPath("$.facts[1].evidenceId").value(EVIDENCE_ID.toString()))
                .andExpect(jsonPath("$.facts[0].evidenceExternalId").value("20260310002820"))
                .andExpect(jsonPath("$.facts[0].evidenceOriginalUrl")
                        .value("https://dart.example/filing"));

        ArgumentCaptor<CompanyFinancialFactsQueryInput> input =
                ArgumentCaptor.forClass(CompanyFinancialFactsQueryInput.class);
        verify(query).find(input.capture());
        org.junit.jupiter.api.Assertions.assertEquals(Set.of(), input.getValue().predicates());
    }

    @Test
    void passesOnlyExactPredicateStringsWithoutNormalization() throws Exception {
        when(query.find(any())).thenReturn(result());
        mvc.perform(get(PATH).param("periodStart", "2025-01-01")
                        .param("periodEnd", "2025-12-31")
                        .param("predicates", "REVENUE", "OPERATING_INCOME"))
                .andExpect(status().isOk());
        ArgumentCaptor<CompanyFinancialFactsQueryInput> input =
                ArgumentCaptor.forClass(CompanyFinancialFactsQueryInput.class);
        verify(query).find(input.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                Set.of("REVENUE", "OPERATING_INCOME"), input.getValue().predicates());
    }

    @Test
    void rejectsMalformedUuidDateAndReversedPeriod() throws Exception {
        mvc.perform(get("/api/companies/not-a-uuid/financial-facts")
                        .param("periodStart", "2025-01-01").param("periodEnd", "2025-12-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_PARAMETER"));
        mvc.perform(get(PATH).param("periodStart", "not-a-date")
                        .param("periodEnd", "2025-12-31"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(PATH).param("periodStart", "2025-12-31")
                        .param("periodEnd", "2025-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXACT_PERIOD"));
    }

    @Test
    void mapsQueryErrorsToApprovedStatuses() throws Exception {
        assertError(CompanyFinancialFactsQueryException.Category.UNSUPPORTED_PREDICATE,
                400, "UNSUPPORTED_PREDICATE");
        assertError(CompanyFinancialFactsQueryException.Category.COMPANY_NOT_FOUND,
                404, "COMPANY_NOT_FOUND");
        assertError(CompanyFinancialFactsQueryException.Category.FACTS_NOT_FOUND,
                404, "FACTS_NOT_FOUND");
        assertError(CompanyFinancialFactsQueryException.Category.INCONSISTENT_PROVENANCE,
                409, "INCONSISTENT_PROVENANCE");
    }

    private void assertError(CompanyFinancialFactsQueryException.Category category,
            int status, String code) throws Exception {
        org.mockito.Mockito.reset(query);
        when(query.find(any())).thenThrow(new CompanyFinancialFactsQueryException(category, "failure"));
        mvc.perform(get(PATH).param("periodStart", "2025-01-01")
                        .param("periodEnd", "2025-12-31"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().is(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    private CompanyFinancialFactsResult result() {
        OffsetDateTime collected = OffsetDateTime.parse("2026-08-19T07:00:00Z");
        return new CompanyFinancialFactsResult(COMPANY_ID, List.of(
                view(FactPredicate.REVENUE, "333605938000000", collected),
                view(FactPredicate.OPERATING_INCOME, "43601051000000", collected)));
    }

    private CompanyFinancialFactView view(
            FactPredicate predicate, String value, OffsetDateTime collected) {
        return new CompanyFinancialFactView(COMPANY_ID, predicate, new BigDecimal(value), "KRW",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null, collected,
                "OpenDART", EVIDENCE_ID, "20260310002820", "https://dart.example/filing");
    }
}
