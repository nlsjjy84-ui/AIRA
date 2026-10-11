package com.aira.api.assist;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GeminiReadingAssistModelTests {
    private static final String SECRET = "gemini-assist-secret-sentinel";
    private static final URI URL = URI.create("https://example.invalid/v1beta/models/test:generateContent");

    @Test
    void returnsTheCandidateTextAndSendsTheKeyOnlyInTheHeader() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"매출은 12.04조 원입니다.\"}]}}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            assertEquals(SECRET, request.headers().firstValue("x-goog-api-key").orElseThrow());
            assertFalse(request.uri().toString().contains(SECRET));
            return response;
        });
        var model = new GeminiReadingAssistModel(true, SECRET, URL, "test", http, new ObjectMapper());
        assertEquals("매출은 12.04조 원입니다.", model.complete("prompt"));
    }

    @Test
    void failuresDoNotExposeTheKeyAndDisabledModelRefuses() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("raw-" + SECRET);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var model = new GeminiReadingAssistModel(true, SECRET, URL, "test", http, new ObjectMapper());
        var error = assertThrows(IllegalStateException.class, () -> model.complete("prompt"));
        assertEquals("HTTP_500", error.getMessage());
        assertFalse(error.toString().contains(SECRET));

        var off = new GeminiReadingAssistModel(false, SECRET, URL, "test", http, new ObjectMapper());
        assertFalse(off.available());
        assertThrows(IllegalStateException.class, () -> off.complete("prompt"));
    }
}
