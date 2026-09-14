package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class HttpEcosMetadataClientTests {
    private static final String SECRET = "ECOS_SECRET_SENTINEL+/";
    private static final URI TEST_BASE = URI.create("https://example.invalid/api/");

    @Test
    void buildsOfficialStatisticItemListPathAndReturnsRawBody() {
        var captured = new AtomicReference<HttpRequest>();
        HttpEcosMetadataClient.Sender sender = request -> {
            captured.set(request);
            return response(request, 200, "{\"raw\":true}");
        };
        var client = new HttpEcosMetadataClient(sender, SECRET, TEST_BASE);
        var raw = client.fetch(new EcosRequestDescriptor(1, 1000, "601Y002"));

        assertEquals(200, raw.statusCode());
        assertEquals("{\"raw\":true}", raw.body());
        assertEquals(
                "/api/StatisticItemList/ECOS_SECRET_SENTINEL%2B%2F/json/kr/1/1000/601Y002/",
                captured.get().uri().getRawPath());
        assertEquals("GET", captured.get().method());
    }

    @Test
    void blankKeyFailsBeforeAnySend() {
        var called = new AtomicBoolean(false);
        HttpEcosMetadataClient.Sender sender = request -> {
            called.set(true);
            return response(request, 200, "{}");
        };
        var client = new HttpEcosMetadataClient(sender, " ", TEST_BASE);

        var error = assertThrows(EcosProviderException.class,
                () -> client.fetch(new EcosRequestDescriptor(1, 1000, "601Y002")));

        assertEquals(EcosProviderException.Category.AUTHENTICATION, error.category());
        assertFalse(called.get());
        assertFalse(error.toString().contains(SECRET));
    }

    @Test
    void transportFailureDoesNotExposeSecretBearingRequest() {
        HttpEcosMetadataClient.Sender sender = request -> {
            throw new IOException("transport failed for " + request.uri());
        };
        var client = new HttpEcosMetadataClient(sender, SECRET, TEST_BASE);

        var error = assertThrows(EcosProviderException.class,
                () -> client.fetch(new EcosRequestDescriptor(1, 1000, "601Y002")));

        assertEquals(EcosProviderException.Category.TRANSPORT, error.category());
        assertFalse(error.getMessage().contains(SECRET));
        assertFalse(error.toString().contains("example.invalid"));
        assertFalse(client.toString().contains(SECRET));
    }

    @Test
    void non200ResponseIsSanitizedProviderFailure() {
        HttpEcosMetadataClient.Sender sender = request -> response(request, 503, "provider down");
        var client = new HttpEcosMetadataClient(sender, SECRET, TEST_BASE);

        var error = assertThrows(EcosProviderException.class,
                () -> client.fetch(new EcosRequestDescriptor(1, 1000, "601Y002")));

        assertEquals(EcosProviderException.Category.PROVIDER_FAILURE, error.category());
        assertEquals("ECOS StatisticItemList returned HTTP 503", error.getMessage());
        assertFalse(error.toString().contains(SECRET));
    }

    @Test
    void emptyBodyIsMalformedResponseWithoutSecretLeak() {
        for (String body : new String[] {null, "", "   "}) {
            HttpEcosMetadataClient.Sender sender = request -> response(request, 200, body);
            var client = new HttpEcosMetadataClient(sender, SECRET, TEST_BASE);

            var error = assertThrows(EcosProviderException.class,
                    () -> client.fetch(new EcosRequestDescriptor(1, 1000, "601Y002")));

            assertEquals(EcosProviderException.Category.MALFORMED_RESPONSE, error.category());
            assertFalse(error.toString().contains(SECRET));
        }
    }

    @Test
    void credentialEchoInBodyIsBlockedBeforeRawBoundary() {
        HttpEcosMetadataClient.Sender sender = request ->
                response(request, 200, "{\"echo\":\"" + SECRET + "\"}");
        var client = new HttpEcosMetadataClient(sender, SECRET, TEST_BASE);

        var error = assertThrows(EcosProviderException.class,
                () -> client.fetch(new EcosRequestDescriptor(1, 1000, "601Y002")));

        assertEquals(EcosProviderException.Category.MALFORMED_RESPONSE, error.category());
        assertFalse(error.toString().contains(SECRET));
    }

    @Test
    void applicationRateLimitIsDecodedBeforeRawBoundary() {
        HttpEcosMetadataClient.Sender sender = request -> response(request, 200,
                "{\"RESULT\":{\"CODE\":\"ERROR-602\",\"MESSAGE\":\"limited\"}}");
        var client = new HttpEcosMetadataClient(sender, SECRET, TEST_BASE);

        var error = assertThrows(EcosProviderException.class,
                () -> client.fetch(new EcosRequestDescriptor(1, 10, "200Y104")));

        assertEquals(EcosProviderException.Category.RATE_LIMIT, error.category());
        assertEquals("ERROR-602", error.providerCode());
    }

    @Test
    void interruptedSendRestoresInterruptAndDoesNotLeakSecret() {
        HttpEcosMetadataClient.Sender sender = request -> {
            throw new InterruptedException("interrupted " + request.uri());
        };
        var client = new HttpEcosMetadataClient(sender, SECRET, TEST_BASE);
        try {
            var error = assertThrows(EcosProviderException.class,
                    () -> client.fetch(new EcosRequestDescriptor(1, 1000, "601Y002")));
            assertEquals(EcosProviderException.Category.TRANSPORT, error.category());
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(error.toString().contains(SECRET));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void applicationRateLimitIsNeverRetried() {
        AtomicInteger sends = new AtomicInteger();
        HttpEcosMetadataClient.Sender sender = request -> {
            sends.incrementAndGet();
            return response(request, 200,
                    "{\"RESULT\":{\"CODE\":\"ERROR-602\",\"MESSAGE\":\"limited\"}}");
        };
        var client = new HttpEcosMetadataClient(sender, SECRET, TEST_BASE);

        var error = assertThrows(EcosProviderException.class,
                () -> client.fetch(new EcosRequestDescriptor(1, 10, "200Y104")));

        assertEquals(EcosProviderException.Category.RATE_LIMIT, error.category());
        assertEquals(1, sends.get());
    }

    private static HttpResponse<String> response(HttpRequest request, int status, String body) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return status; }
            @Override public HttpRequest request() { return request; }
            @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (name, value) -> true);
            }
            @Override public String body() { return body; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return request.uri(); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
