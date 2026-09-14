package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class EcosStatisticSearchRequestBuilderTests {
    private static final URI BASE = URI.create("https://example.invalid/api/");
    private static final String SECRET = "ECOS_SEARCH_SECRET+/";

    @Test
    void buildsConfirmedOfficialRealGdpPathExactly() {
        URI uri = EcosStatisticSearchRequestBuilder.build(
                BASE, SECRET, new EcosRealGdpSearchRequest(1, 10, "2026Q1", "2026Q2"));

        assertEquals(
                "https://example.invalid/api/StatisticSearch/ECOS_SEARCH_SECRET%2B%2F/"
                        + "json/kr/1/10/200Y104/Q/2026Q1/2026Q2/1400/?/?/?",
                uri.toString());
    }

    @Test
    void blankKeyFailsSanitized() {
        var error = assertThrows(EcosProviderException.class,
                () -> EcosStatisticSearchRequestBuilder.build(
                        BASE, " ", new EcosRealGdpSearchRequest(1, 1, "2026Q1", "2026Q1")));
        assertEquals(EcosProviderException.Category.AUTHENTICATION, error.category());
        assertFalse(error.toString().contains(SECRET));
    }
}
