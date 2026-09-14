package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HttpOpenDartPeriodWitnessClientTests {
    private static final String SENTINEL = "fake-sentinel-key-for-test";

    @Test
    void usesOfficialWitnessEndpointAndParsesOnlyPeriodFields() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"status":"000","message":"ignored","list":[{"corp_code":"00126380",
                "bsns_year":"2025","reprt_code":"11011","rcept_no":"20260331000123",
                "fs_div":"CFS","thstrm_dt":"2025.07.01 ~ 2025.12.31",
                "account_nm":"not used","thstrm_nm":"not used","thstrm_amount":"999"}]}
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            assertEquals("/api/fnlttSinglAcnt.json", request.uri().getPath());
            assertEquals("https", request.uri().getScheme());
            assertEquals("opendart.fss.or.kr", request.uri().getHost());
            assertTrue(request.uri().getQuery().contains("corp_code=00126380"));
            assertTrue(request.uri().getQuery().contains("bsns_year=2025"));
            assertTrue(request.uri().getQuery().contains("reprt_code=11011"));
            assertTrue(request.uri().getQuery().contains("crtfc_key=" + SENTINEL));
            return response;
        });
        var result = new HttpOpenDartPeriodWitnessClient(http, new ObjectMapper(), SENTINEL).fetch("00126380", 2025);
        assertEquals("2025.07.01 ~ 2025.12.31", result.list().getFirst().currentTerm());
        assertFalse(result.toString().contains(SENTINEL));
    }

    @Test
    void transportAndParsingErrorsNeverExposeRequestKeyOrRawResponse() throws Exception {
        HttpClient http = mock(HttpClient.class);
        var client = new HttpOpenDartPeriodWitnessClient(http, new ObjectMapper(), SENTINEL);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("crtfc_key=" + SENTINEL));
        var transport = assertThrows(OpenDartProviderException.class, () -> client.fetch("00126380", 2025));
        assertFalse(transport.toString().contains(SENTINEL));
        assertNull(transport.getCause());
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(SENTINEL);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var parsing = assertThrows(OpenDartProviderException.class, () -> client.fetch("00126380", 2025));
        assertFalse(parsing.toString().contains(SENTINEL));
        assertNull(parsing.getCause());
    }
}
