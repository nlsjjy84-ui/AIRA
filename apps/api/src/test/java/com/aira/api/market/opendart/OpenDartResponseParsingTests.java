package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenDartResponseParsingTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesMinimalOfficialShapeFixture() throws Exception {
        String fixture = """
                {"status":"000","message":"정상","list":[{
                  "rcept_no":"20260331000123","bsns_year":"2025",
                  "reprt_code":"11011","fs_div":"CFS","sj_div":"IS",
                  "account_id":"ifrs_Revenue","account_nm":"수익(매출액)",
                  "thstrm_dt":"2025.01.01 ~ 2025.12.31",
                  "thstrm_amount":"1,234","currency":"KRW",
                  "thstrm_add_amount":"9,999","frmtrm_amount":"888"
                }]}
                """;

        var response = mapper.readValue(fixture, OpenDartFinancialResponse.class);

        assertEquals("000", response.status());
        assertEquals("ifrs_Revenue", response.list().getFirst().accountId());
        assertEquals("1,234", response.list().getFirst().currentTermAmount());
    }

    @Test
    void missingApiKeyFailsWithoutLeakingSecret() {
        var client = new HttpOpenDartAnnualCfsClient(
                java.net.http.HttpClient.newHttpClient(), mapper, "");
        var failure = assertThrows(OpenDartProviderException.class,
                () -> client.fetch("00126380", 2025));
        assertEquals(OpenDartProviderException.Category.AUTHENTICATION, failure.category());
        assertFalse(failure.getMessage().contains("crtfc_key"));
    }

    @Test
    void requestPolicyConstantsAreAnnualAndCfsOnly() {
        assertEquals("https://opendart.fss.or.kr/api/fnlttSinglAcntAll.json",
                HttpOpenDartAnnualCfsClient.ENDPOINT);
        assertEquals("11011", HttpOpenDartAnnualCfsClient.ANNUAL_REPORT_CODE);
        assertEquals("CFS", HttpOpenDartAnnualCfsClient.CFS);
    }
}
