package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class EcosRequestExecutionGuardTests {
    @Test
    void retriesOnly502503504OnceAndWaitsFiveHundredMilliseconds() {
        for (int status : List.of(502, 503, 504)) {
            AtomicLong clock = new AtomicLong(1_000_000_000L);
            List<Long> sleeps = new ArrayList<>();
            var guard = new EcosRequestExecutionGuard(
                    clock::get,
                    millis -> {
                        sleeps.add(millis);
                        clock.addAndGet(millis * 1_000_000L);
                    },
                    EcosRequestExecutionGuard.MIN_INTERVAL);
            AtomicInteger sends = new AtomicInteger();

            HttpResponse<String> result = guard.execute(
                    () -> response(sends.incrementAndGet() == 1 ? status : 200),
                    "Test");

            assertEquals(200, result.statusCode());
            assertEquals(2, sends.get());
            assertEquals(List.of(500L), sleeps);
        }
    }

    @Test
    void neverRetriesOtherHttpStatuses() {
        for (int status : List.of(400, 401, 404, 429, 500, 501)) {
            var guard = immediateGuard();
            AtomicInteger sends = new AtomicInteger();

            var error = assertThrows(EcosProviderException.class,
                    () -> guard.execute(() -> {
                        sends.incrementAndGet();
                        return response(status);
                    }, "Test"));

            assertEquals(EcosProviderException.Category.PROVIDER_FAILURE, error.category());
            assertEquals(1, sends.get());
        }
    }

    @Test
    void retriesOnlyTimeoutAndExplicitConnectionReset() throws Exception {
        assertRetried(new HttpTimeoutException("timed out"));
        assertRetried(new SocketException("Connection reset"));
        assertRetried(new SocketException("An existing connection was forcibly closed"));

        assertNotRetried(new IOException("generic failure"));
        assertNotRetried(new SocketException("Connection refused"));
    }

    @Test
    void neverExceedsTwoAttempts() {
        var guard = immediateGuard();
        AtomicInteger sends = new AtomicInteger();

        var error = assertThrows(EcosProviderException.class,
                () -> guard.execute(() -> {
                    sends.incrementAndGet();
                    return response(503);
                }, "Test"));

        assertEquals(EcosProviderException.Category.PROVIDER_FAILURE, error.category());
        assertEquals(EcosRequestExecutionGuard.MAX_ATTEMPTS, sends.get());
    }

    @Test
    void spacesIndependentSuccessfulCallsByFiveHundredMilliseconds() {
        AtomicLong clock = new AtomicLong(2_000_000_000L);
        List<Long> sleeps = new ArrayList<>();
        var guard = new EcosRequestExecutionGuard(
                clock::get,
                millis -> {
                    sleeps.add(millis);
                    clock.addAndGet(millis * 1_000_000L);
                },
                EcosRequestExecutionGuard.MIN_INTERVAL);

        guard.execute(() -> response(200), "First");
        guard.execute(() -> response(200), "Second");

        assertEquals(List.of(500L), sleeps);
    }

    @Test
    void serializesConcurrentCalls() throws Exception {
        var guard = immediateGuard();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var task = (java.util.concurrent.Callable<HttpResponse<String>>) () -> {
                start.await();
                return guard.execute(() -> {
                    int current = active.incrementAndGet();
                    maxActive.accumulateAndGet(current, Math::max);
                    try {
                        Thread.sleep(75);
                        return response(200);
                    } finally {
                        active.decrementAndGet();
                    }
                }, "Concurrent");
            };

            var first = pool.submit(task);
            var second = pool.submit(task);
            start.countDown();
            assertEquals(200, first.get(2, TimeUnit.SECONDS).statusCode());
            assertEquals(200, second.get(2, TimeUnit.SECONDS).statusCode());
            assertEquals(1, maxActive.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private static void assertRetried(IOException firstFailure) throws Exception {
        var guard = immediateGuard();
        AtomicInteger sends = new AtomicInteger();
        HttpResponse<String> result = guard.execute(() -> {
            if (sends.incrementAndGet() == 1) throw firstFailure;
            return response(200);
        }, "Test");
        assertEquals(200, result.statusCode());
        assertEquals(2, sends.get());
    }

    private static void assertNotRetried(IOException firstFailure) {
        var guard = immediateGuard();
        AtomicInteger sends = new AtomicInteger();
        var error = assertThrows(EcosProviderException.class,
                () -> guard.execute(() -> {
                    sends.incrementAndGet();
                    throw firstFailure;
                }, "Test"));
        assertEquals(EcosProviderException.Category.TRANSPORT, error.category());
        assertEquals(1, sends.get());
    }

    private static EcosRequestExecutionGuard immediateGuard() {
        return new EcosRequestExecutionGuard(System::nanoTime, millis -> {}, Duration.ZERO);
    }

    private static HttpResponse<String> response(int status) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.invalid/")).GET().build();
        return new HttpResponse<>() {
            @Override public int statusCode() { return status; }
            @Override public HttpRequest request() { return request; }
            @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (n, v) -> true); }
            @Override public String body() { return "{}"; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return request.uri(); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
