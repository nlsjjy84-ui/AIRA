package com.aira.api.auth.security;

import java.util.List;

public final class Argon2Benchmark {
    private static final int WARMUP_RUNS = 1;
    private static final int MEASURED_RUNS = 3;

    private record Candidate(int memoryKiB, int iterations, int parallelism) {}

    public static void main(String[] args) {
        List<Candidate> candidates = List.of(
                new Candidate(19_456, 2, 1),
                new Candidate(32_768, 3, 1),
                new Candidate(65_536, 3, 1),
                new Candidate(32_768, 3, 2));

        for (Candidate candidate : candidates) {
            PasswordHasher hasher = new PasswordHasher(
                    16, 32, candidate.parallelism(), candidate.memoryKiB(), candidate.iterations());
            for (int i = 0; i < WARMUP_RUNS; i++) {
                String encoded = hasher.hash(benchmarkPassword());
                if (!hasher.matches(benchmarkPassword(), encoded)) {
                    throw new IllegalStateException("Argon2 verification failed");
                }
            }

            long hashNanos = 0;
            long matchNanos = 0;
            for (int i = 0; i < MEASURED_RUNS; i++) {
                long started = System.nanoTime();
                String encoded = hasher.hash(benchmarkPassword());
                hashNanos += System.nanoTime() - started;

                started = System.nanoTime();
                if (!hasher.matches(benchmarkPassword(), encoded)) {
                    throw new IllegalStateException("Argon2 verification failed");
                }
                matchNanos += System.nanoTime() - started;
            }

            System.out.printf(
                    "ARGON2_CANDIDATE memoryKiB=%d iterations=%d parallelism=%d avgHashMs=%.2f avgMatchMs=%.2f%n",
                    candidate.memoryKiB(),
                    candidate.iterations(),
                    candidate.parallelism(),
                    nanosToMillis(hashNanos / MEASURED_RUNS),
                    nanosToMillis(matchNanos / MEASURED_RUNS));
        }
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static String benchmarkPassword() {
        return "benchmark-only-password-value";
    }

    private Argon2Benchmark() {}
}
