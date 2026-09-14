package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HttpOpenDartFilingPageClientTests {
    private static final String SENTINEL = "filing-discovery-secret-sentinel";

    @Test
    void buildsOfficialListRequestAndPreservesCallerScope() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"status":"000","message":"정상","page_no":2,"page_count":100,
                 "total_count":0,"total_page":0,"list":[]}
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            assertEquals("https", request.uri().getScheme());
            assertEquals("opendart.fss.or.kr", request.uri().getHost());
            assertEquals("/api/list.json", request.uri().getPath());
            String query = request.uri().getQuery();
            assertTrue(query.contains("crtfc_key=" + SENTINEL));
            assertTrue(query.contains("corp_code=00126380"));
            assertTrue(query.contains("last_reprt_at=N"));
            assertTrue(query.contains("sort=date"));
            assertTrue(query.contains("sort_mth=asc"));
            assertTrue(query.contains("page_no=2"));
            assertTrue(query.contains("page_count=100"));
            assertTrue(query.contains("bgn_de=20250101"));
            assertTrue(query.contains("end_de=20251231"));
            assertTrue(query.contains("pblntf_ty=A"));
            assertTrue(query.contains("pblntf_detail_ty=A001"));
            return response;
        });
        var request = new OpenDartFilingDiscoveryRequest("00126380",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "A", "A001");
        var page = new HttpOpenDartFilingPageClient(http, new ObjectMapper(), SENTINEL).fetch(request, 2);
        assertEquals(2, page.pageNumber());
    }

    @Test
    void omitsOptionalFiltersWhenCallerDidNotSupplyThem() {
        HttpClient http = mock(HttpClient.class);
        var client = new HttpOpenDartFilingPageClient(http, new ObjectMapper(), SENTINEL);
        var request = new OpenDartFilingDiscoveryRequest("00126380", null, null, null, null);

        String query = client.buildUri(request, 1).getQuery();

        assertFalse(query.contains("bgn_de="));
        assertFalse(query.contains("end_de="));
        assertFalse(query.contains("pblntf_ty="));
        assertFalse(query.contains("pblntf_detail_ty="));
        assertTrue(query.contains("corp_code=00126380"));
    }

    @Test
    void transportAndParsingFailuresNeverExposeApiKeyOrRawPayload() throws Exception {
        HttpClient http = mock(HttpClient.class);
        var client = new HttpOpenDartFilingPageClient(http, new ObjectMapper(), SENTINEL);
        var request = new OpenDartFilingDiscoveryRequest("00126380", null, null, null, null);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("crtfc_key=" + SENTINEL));

        var transport = assertThrows(OpenDartProviderException.class, () -> client.fetch(request, 1));
        assertFalse(transport.toString().contains(SENTINEL));
        assertNull(transport.getCause());

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(SENTINEL);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        var parsing = assertThrows(OpenDartProviderException.class, () -> client.fetch(request, 1));
        assertFalse(parsing.toString().contains(SENTINEL));
        assertNull(parsing.getCause());
    }
}
