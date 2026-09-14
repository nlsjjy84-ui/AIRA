package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HttpEcosRealGdpStatisticSearchClientTests {
    private static final String SECRET = "ECOS_SEARCH_SECRET+/";
    private static final URI TEST_BASE = URI.create("https://example.invalid/api/");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void localFakeServerReceivesConfirmedPathAndParsesStrictResponse() throws Exception {
        var captured = new AtomicReference<String>();
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/", exchange -> {
            captured.set(exchange.getRequestURI().toString());
            byte[] bytes = validBody().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/");
            HttpClient http = HttpClient.newHttpClient();
            HttpEcosRealGdpStatisticSearchClient.Sender sender = request ->
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var client = new HttpEcosRealGdpStatisticSearchClient(
                    sender, objectMapper, "LOCAL_SENTINEL", base);

            var page = client.fetch(new EcosRealGdpSearchRequest(1, 10, "2026Q1", "2026Q2"));
            assertEquals(2, page.totalCount());
            assertEquals(2, page.observations().size());
            assertEquals(
                    "/api/StatisticSearch/LOCAL_SENTINEL/json/kr/1/10/"
                            + "200Y104/Q/2026Q1/2026Q2/1400/?/?/?",
                    captured.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void blankKeyFailsBeforeSend() {
        var called = new AtomicBoolean(false);
        HttpEcosRealGdpStatisticSearchClient.Sender sender = request -> {
            called.set(true);
            return response(request, 200, validBody());
        };
        var client = new HttpEcosRealGdpStatisticSearchClient(
                sender, objectMapper, " ", TEST_BASE);

        var error = assertThrows(EcosProviderException.class,
                () -> client.fetch(new EcosRealGdpSearchRequest(1, 1, "2026Q1", "2026Q1")));
        assertEquals(EcosProviderException.Category.AUTHENTICATION, error.category());
        assertFalse(called.get());
    }

    @Test
    void transportAndHttpFailuresAreSanitized() {
        HttpEcosRealGdpStatisticSearchClient.Sender broken = request -> {
            throw new IOException("failed " + request.uri());
        };
        var transportClient = new HttpEcosRealGdpStatisticSearchClient(
                broken, objectMapper, SECRET, TEST_BASE);
        var transport = assertThrows(EcosProviderException.class,
                () -> transportClient.fetch(new EcosRealGdpSearchRequest(1, 1, "2026Q1", "2026Q1")));
        assertEquals(EcosProviderException.Category.TRANSPORT, transport.category());
        assertFalse(transport.toString().contains(SECRET));
        assertFalse(transport.toString().contains("example.invalid"));

        HttpEcosRealGdpStatisticSearchClient.Sender unavailable = request ->
                response(request, 503, "provider down");
        var httpClient = new HttpEcosRealGdpStatisticSearchClient(
                unavailable, objectMapper, SECRET, TEST_BASE);
        var http = assertThrows(EcosProviderException.class,
                () -> httpClient.fetch(new EcosRealGdpSearchRequest(1, 1, "2026Q1", "2026Q1")));
        assertEquals(EcosProviderException.Category.PROVIDER_FAILURE, http.category());
        assertEquals("ECOS StatisticSearch returned HTTP 503", http.getMessage());
    }

    @Test
    void emptyCredentialEchoAndMalformedBodyAreBlocked() {
        var emptyClient = new HttpEcosRealGdpStatisticSearchClient(
                request -> response(request, 200, " "), objectMapper, SECRET, TEST_BASE);
        var empty = assertThrows(EcosProviderException.class,
                () -> emptyClient.fetch(new EcosRealGdpSearchRequest(1, 1, "2026Q1", "2026Q1")));
        assertEquals(EcosProviderException.Category.MALFORMED_RESPONSE, empty.category());

        var echoClient = new HttpEcosRealGdpStatisticSearchClient(
                request -> response(request, 200, "{\"echo\":\"" + SECRET + "\"}"),
                objectMapper, SECRET, TEST_BASE);
        var echo = assertThrows(EcosProviderException.class,
                () -> echoClient.fetch(new EcosRealGdpSearchRequest(1, 1, "2026Q1", "2026Q1")));
        assertEquals(EcosProviderException.Category.MALFORMED_RESPONSE, echo.category());
        assertFalse(echo.toString().contains(SECRET));

        var malformedClient = new HttpEcosRealGdpStatisticSearchClient(
                request -> response(request, 200, "{\"unexpected\":{}}"),
                objectMapper, SECRET, TEST_BASE);
        var malformed = assertThrows(EcosProviderException.class,
                () -> malformedClient.fetch(new EcosRealGdpSearchRequest(1, 1, "2026Q1", "2026Q1")));
        assertEquals(EcosProviderException.Category.MALFORMED_RESPONSE, malformed.category());
    }

    @Test
    void applicationNoDataIsDecodedBeforeStrictParser() {
        var client = new HttpEcosRealGdpStatisticSearchClient(
                request -> response(request, 200,
                        "{\"RESULT\":{\"CODE\":\"INFO-200\",\"MESSAGE\":\"no data\"}}"),
                objectMapper, SECRET, TEST_BASE);

        var error = assertThrows(EcosProviderException.class,
                () -> client.fetch(new EcosRealGdpSearchRequest(1, 1, "2026Q1", "2026Q1")));

        assertEquals(EcosProviderException.Category.NO_DATA, error.category());
        assertEquals("INFO-200", error.providerCode());
    }

    @Test
    void interruptedSendRestoresInterrupt() {
        HttpEcosRealGdpStatisticSearchClient.Sender sender = request -> {
            throw new InterruptedException("interrupted " + request.uri());
        };
        var client = new HttpEcosRealGdpStatisticSearchClient(
                sender, objectMapper, SECRET, TEST_BASE);
        try {
            var error = assertThrows(EcosProviderException.class,
                    () -> client.fetch(new EcosRealGdpSearchRequest(1, 1, "2026Q1", "2026Q1")));
            assertEquals(EcosProviderException.Category.TRANSPORT, error.category());
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(error.toString().contains(SECRET));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void noDataApplicationResultIsNeverRetried() {
        AtomicInteger sends = new AtomicInteger();
        HttpEcosRealGdpStatisticSearchClient.Sender sender = request -> {
            sends.incrementAndGet();
            return response(request, 200,
                    "{\"RESULT\":{\"CODE\":\"INFO-200\",\"MESSAGE\":\"no data\"}}");
        };
        var client = new HttpEcosRealGdpStatisticSearchClient(
                sender, objectMapper, SECRET, TEST_BASE);

        var error = assertThrows(EcosProviderException.class,
                () -> client.fetch(new EcosRealGdpSearchRequest(1, 1, "2026Q1", "2026Q1")));

        assertEquals(EcosProviderException.Category.NO_DATA, error.category());
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

    private static String validBody() {
        return "{\"StatisticSearch\":{\"list_total_count\":2,\"row\":["
                + row("2026Q1", "596692.8") + ","
                + row("2026Q2", "600474.9") + "]}}";
    }

    private static String row(String time, String value) {
        return "{"
                + "\"STAT_CODE\":\"200Y104\","
                + "\"STAT_NAME\":\"2.1.2.1.2. 경제활동별 GDP 및 GNI(계절조정, 실질, 분기)\","
                + "\"ITEM_CODE1\":\"1400\","
                + "\"ITEM_NAME1\":\"국내총생산(시장가격, GDP)\","
                + "\"ITEM_CODE2\":null,\"ITEM_NAME2\":null,"
                + "\"ITEM_CODE3\":null,\"ITEM_NAME3\":null,"
                + "\"ITEM_CODE4\":null,\"ITEM_NAME4\":null,"
                + "\"UNIT_NAME\":\"십억원\",\"WGT\":null,"
                + "\"TIME\":\"" + time + "\","
                + "\"DATA_VALUE\":\"" + value + "\"}";
    }
}
