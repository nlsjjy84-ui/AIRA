package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

class OpenDartLiveSmokeTests {
    @Test
    @EnabledIfEnvironmentVariable(named = "OPENDART_LIVE_SMOKE", matches = "true")
    void readsOneAnnualCfsDisclosureFromOfficialEndpoint() {
        String apiKey = System.getenv("OPENDART_API_KEY");
        var client = new HttpOpenDartAnnualCfsClient(
                java.net.http.HttpClient.newHttpClient(), new ObjectMapper(), apiKey);

        var response = client.fetch("00126380", 2025);

        assertEquals("000", response.status());
        var annualCfsRows = response.list().stream()
                .filter(row -> "2025".equals(row.businessYear()))
                .filter(row -> "11011".equals(row.reportCode()))
                .filter(row -> "CFS".equals(row.financialStatementDivision()))
                .toList();
        assertTrue(annualCfsRows.stream().anyMatch(row -> "ifrs_Revenue".equals(row.accountId())));
        assertTrue(annualCfsRows.stream()
                .anyMatch(row -> "dart_OperatingIncomeLoss".equals(row.accountId())));
    }
}
