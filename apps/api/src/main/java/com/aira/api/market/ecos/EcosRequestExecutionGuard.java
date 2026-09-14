package com.aira.api.market.ecos;

import java.io.IOException;
import java.net.SocketException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

@Component
public final class EcosRequestExecutionGuard {
    static final int MAX_ATTEMPTS = 2;
    static final Duration MIN_INTERVAL = Duration.ofMillis(500);

    @FunctionalInterface
    interface Attempt {
        HttpResponse<String> send() throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final ReentrantLock lock = new ReentrantLock(true);
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private final long minIntervalNanos;
    private long nextAllowedNanos;

    public EcosRequestExecutionGuard() {
        this(System::nanoTime, Thread::sleep, MIN_INTERVAL);
    }

    EcosRequestExecutionGuard(
            LongSupplier nanoTime, Sleeper sleeper, Duration minInterval) {
        if (nanoTime == null) throw new IllegalArgumentException("nanoTime must not be null");
        if (sleeper == null) throw new IllegalArgumentException("sleeper must not be null");
        if (minInterval == null || minInterval.isNegative()) {
            throw new IllegalArgumentException("minInterval must not be negative");
        }
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
        this.minIntervalNanos = minInterval.toNanos();
    }

    HttpResponse<String> execute(Attempt attempt, String operation) {
        if (attempt == null) throw new IllegalArgumentException("attempt must not be null");
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation must not be blank");
        }

        lock.lock();
        try {
            return executeLocked(attempt, operation);
        } finally {
            lock.unlock();
        }
    }

    private boolean hasPreviousAttempt;

    private HttpResponse<String> executeLocked(Attempt attempt, String operation) {
        for (int attemptNumber = 1; attemptNumber <= MAX_ATTEMPTS; attemptNumber++) {
            awaitTurn(operation);
            HttpResponse<String> response;
            try {
                response = attempt.send();
            } catch (InterruptedException exception) {
                markAttemptFinished();
                Thread.currentThread().interrupt();
                throw transport(operation, "request was interrupted");
            } catch (IOException exception) {
                markAttemptFinished();
                if (attemptNumber < MAX_ATTEMPTS && retryable(exception)) {
                    continue;
                }
                throw transport(operation, "request failed");
            }
            markAttemptFinished();

            int status = response.statusCode();
            if (attemptNumber < MAX_ATTEMPTS && retryableStatus(status)) {
                continue;
            }
            if (status != 200) {
                throw new EcosProviderException(
                        EcosProviderException.Category.PROVIDER_FAILURE,
                        "ECOS " + operation + " returned HTTP " + status);
            }
            return response;
        }
        throw new IllegalStateException("ECOS retry loop exhausted unexpectedly");
    }

    private void awaitTurn(String operation) {
        if (!hasPreviousAttempt || minIntervalNanos == 0) return;
        long remainingNanos = nextAllowedNanos - nanoTime.getAsLong();
        if (remainingNanos <= 0) return;
        long millis = Math.max(1, (remainingNanos + 999_999L) / 1_000_000L);
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw transport(operation, "wait was interrupted");
        }
    }

    private void markAttemptFinished() {
        hasPreviousAttempt = true;
        nextAllowedNanos = nanoTime.getAsLong() + minIntervalNanos;
    }

    private static boolean retryableStatus(int status) {
        return status == 502 || status == 503 || status == 504;
    }

    private static boolean retryable(IOException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof HttpTimeoutException) return true;
            if (current instanceof SocketException socketException) {
                String message = socketException.getMessage();
                if (message != null) {
                    String normalized = message.toLowerCase(Locale.ROOT);
                    if (normalized.contains("reset") || normalized.contains("forcibly closed")) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static EcosProviderException transport(String operation, String detail) {
        return new EcosProviderException(
                EcosProviderException.Category.TRANSPORT,
                "ECOS " + operation + " " + detail);
    }
}
