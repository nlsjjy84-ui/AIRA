package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class OpenDartCompanyDirectoryClientTests {
    @Test
    void usesOnlyOfficialDirectoryEndpoint() {
        assertEquals("https://opendart.fss.or.kr/api/corpCode.xml",
                HttpOpenDartCompanyDirectoryClient.ENDPOINT);
    }

    @Test
    void missingKeyFailsWithoutSecretOrUrlExposure() {
        var client = new HttpOpenDartCompanyDirectoryClient(HttpClient.newHttpClient(),
                new OpenDartCompanyDirectoryParser(), "");

        var failure = assertThrows(OpenDartProviderException.class, client::fetch);

        assertEquals(OpenDartProviderException.Category.AUTHENTICATION, failure.category());
        assertFalse(failure.getMessage().contains("crtfc_key"));
        assertFalse(failure.getMessage().contains("corpCode.xml"));
    }
}
