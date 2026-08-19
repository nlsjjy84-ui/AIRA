package com.aira.api.market.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.query.CompanyFinancialPeriodView;
import com.aira.api.market.query.PublicCompanyCatalogException;
import com.aira.api.market.query.PublicCompanyCatalogQuery;
import com.aira.api.market.query.PublicCompanyView;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicCompanyCatalogControllerTests {
    private static final UUID COMPANY_ID = UUID.fromString(
            "5eafc0b5-c163-4cea-8dbd-131265004e95");
    private final PublicCompanyCatalogQuery query = mock(PublicCompanyCatalogQuery.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(query);
        mvc = MockMvcBuilders.standaloneSetup(new PublicCompanyCatalogController(query))
                .setControllerAdvice(new PublicCompanyCatalogExceptionHandler()).build();
    }

    @Test
    void returnsBrowsableCompaniesWithoutInternalIdentityFields() throws Exception {
        when(query.findCompanies()).thenReturn(List.of(
                new PublicCompanyView(COMPANY_ID, "삼성전자", "KR")));

        mvc.perform(get("/api/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].companyId").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.companies[0].canonicalName").value("삼성전자"))
                .andExpect(jsonPath("$.companies[0].countryCode").value("KR"))
                .andExpect(jsonPath("$.companies[0].canonicalKey").doesNotExist());
    }

    @Test
    void returnsExactPeriodsAndAvailablePredicates() throws Exception {
        when(query.findPeriods(COMPANY_ID)).thenReturn(List.of(new CompanyFinancialPeriodView(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                Set.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME))));

        mvc.perform(get("/api/companies/{companyId}/financial-periods", COMPANY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.periods[0].periodStart").value("2025-01-01"))
                .andExpect(jsonPath("$.periods[0].periodEnd").value("2025-12-31"))
                .andExpect(jsonPath("$.periods[0].predicates.length()").value(2));
    }

    @Test
    void distinguishesMalformedCompanyAndUnavailablePeriods() throws Exception {
        mvc.perform(get("/api/companies/not-a-uuid/financial-periods"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_PARAMETER"));

        when(query.findPeriods(COMPANY_ID)).thenThrow(new PublicCompanyCatalogException(
                PublicCompanyCatalogException.Category.COMPANY_NOT_FOUND, "missing"));
        mvc.perform(get("/api/companies/{companyId}/financial-periods", COMPANY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));

        org.mockito.Mockito.reset(query);
        when(query.findPeriods(COMPANY_ID)).thenThrow(new PublicCompanyCatalogException(
                PublicCompanyCatalogException.Category.PERIODS_NOT_FOUND, "missing"));
        mvc.perform(get("/api/companies/{companyId}/financial-periods", COMPANY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERIODS_NOT_FOUND"));
    }
}
