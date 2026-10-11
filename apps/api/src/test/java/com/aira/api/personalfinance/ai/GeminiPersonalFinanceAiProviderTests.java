package com.aira.api.personalfinance.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GeminiPersonalFinanceAiProviderTests {
    private static final String SECRET = "gemini-test-secret-sentinel";
    private static final URI URL = URI.create("https://example.invalid/v1beta/models/test-model:generateContent");

    @Test
    void sendsOnlyAggregateInputAndParsesGenerateContentOutput() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"candidates":[{"finishReason":"STOP","content":{"parts":[
                {"text":"식비 지출이 전월보다 증가했습니다. 원인은 집계값만으로 확정할 수 없습니다."}]}}],
                "usageMetadata":{"promptTokenCount":41,"candidatesTokenCount":22}}
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            assertEquals(SECRET, request.headers().firstValue("x-goog-api-key").orElseThrow());
            assertFalse(request.uri().toString().contains(SECRET));
            String body = readBody(request);
            assertTrue(body.contains("\"contents\""));
            assertTrue(body.contains("FOOD"));
            assertTrue(body.contains("150000"));
            assertFalse(body.contains("userId"));
            assertFalse(body.matches("(?s).*\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b.*"));
            assertFalse(body.contains(SECRET));
            return response;
        });

        var provider = new GeminiPersonalFinanceAiProvider(true, SECRET, URL, "test-model", http, new ObjectMapper());
        assertTrue(provider.available());
        assertEquals("GEMINI_GENERATE_CONTENT", provider.providerKey());
        var result = provider.explain(input());

        assertTrue(result.text().contains("원인은"));
        assertEquals(41, result.inputTokens());
        assertEquals(22, result.outputTokens());
    }

    @Test
    void providerFailuresDoNotExposeApiKeyOrRawResponse() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("raw-" + SECRET);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var provider = new GeminiPersonalFinanceAiProvider(true, SECRET, URL, "test-model", http, new ObjectMapper());

        var error = assertThrows(AiProviderException.class, () -> provider.explain(input()));
        assertEquals("HTTP_500", error.code());
        assertFalse(error.toString().contains(SECRET));
        assertNull(error.getCause());
    }

    @Test
    void truncatedOrEmptyAnswersAreRejectedAndMissingKeyDisablesProvider() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"식비\"}]}}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var provider = new GeminiPersonalFinanceAiProvider(true, SECRET, URL, "test-model", http, new ObjectMapper());
        assertEquals("INCOMPLETE_RESPONSE",
                assertThrows(AiProviderException.class, () -> provider.explain(input())).code());

        var noKey = new GeminiPersonalFinanceAiProvider(true, "", URL, "test-model", http, new ObjectMapper());
        assertFalse(noKey.available());
        assertEquals("PROVIDER_DISABLED",
                assertThrows(AiProviderException.class, () -> noKey.explain(input())).code());
    }

    private static AiExplanationInput input() {
        return new AiExplanationInput(
                "2026-09", "2026-08", "KRW",
                new BigDecimal("300000"), new BigDecimal("200000"), new BigDecimal("100000"),
                List.of(new AiExplanationInput.CategoryChange(
                        "FOOD", new BigDecimal("150000"), new BigDecimal("90000"), new BigDecimal("60000"))));
    }

    private static String readBody(HttpRequest request) throws Exception {
        var publisher = request.bodyPublisher().orElseThrow();
        var bytes = new java.io.ByteArrayOutputStream();
        var done = new java.util.concurrent.CompletableFuture<Void>();
        publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer item) {
                byte[] copy = new byte[item.remaining()];
                item.get(copy);
                bytes.writeBytes(copy);
            }
            public void onError(Throwable throwable) { done.completeExceptionally(throwable); }
            public void onComplete() { done.complete(null); }
        });
        done.get();
        return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
