package com.aira.api.market.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class CompanyFinancialFactsApiPostgresE2ETests {
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup
                        .SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void anonymousClientReadsExistingSamsungFactsThroughHttpBoundary() throws Exception {
        mvc.perform(get("/api/companies/5eafc0b5-c163-4cea-8dbd-131265004e95/financial-facts")
                        .param("periodStart", "2025-01-01")
                        .param("periodEnd", "2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facts.length()").value(2))
                .andExpect(jsonPath("$.facts[?(@.predicate == 'REVENUE')].value")
                        .value(333605938000000L))
                .andExpect(jsonPath("$.facts[?(@.predicate == 'OPERATING_INCOME')].value")
                        .value(43601051000000L))
                .andExpect(jsonPath("$.facts[0].currency").value("KRW"))
                .andExpect(jsonPath("$.facts[0].periodStart").value("2025-01-01"))
                .andExpect(jsonPath("$.facts[0].periodEnd").value("2025-12-31"))
                .andExpect(jsonPath("$.facts[0].sourceName").value("OpenDART"))
                .andExpect(jsonPath("$.facts[0].evidenceExternalId").value("20260310002820"))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    String first = com.jayway.jsonpath.JsonPath.read(
                            body, "$.facts[0].evidenceId");
                    String second = com.jayway.jsonpath.JsonPath.read(
                            body, "$.facts[1].evidenceId");
                    assertEquals(first, second);
                });
    }
}
