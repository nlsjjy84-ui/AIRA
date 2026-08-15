package com.aira.api.auth.security;

import com.aira.api.auth.config.AuthProperties;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

public final class PasswordHasher {
    private final Argon2PasswordEncoder encoder;

    public PasswordHasher(AuthProperties.Argon2 properties) {
        this(
                required(properties.getSaltLength(), "salt length"),
                required(properties.getHashLength(), "hash length"),
                required(properties.getParallelism(), "parallelism"),
                required(properties.getMemoryKiB(), "memory"),
                required(properties.getIterations(), "iterations"));
    }

    public PasswordHasher(
            int saltLength,
            int hashLength,
            int parallelism,
            int memoryKiB,
            int iterations) {
        requirePositive(saltLength, "salt length");
        requirePositive(hashLength, "hash length");
        requirePositive(parallelism, "parallelism");
        requirePositive(memoryKiB, "memory");
        requirePositive(iterations, "iterations");
        this.encoder = new Argon2PasswordEncoder(
                saltLength, hashLength, parallelism, memoryKiB, iterations);
    }

    public String hash(String rawPassword) {
        validatePassword(rawPassword);
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || !encodedPassword.startsWith("$argon2id$")) {
            return false;
        }
        int length = rawPassword.codePointCount(0, rawPassword.length());
        if (length > AuthProperties.PASSWORD_MAX_LENGTH) {
            return false;
        }
        return encoder.matches(rawPassword, encodedPassword);
    }

    private static void validatePassword(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Password is required");
        }
        int length = rawPassword.codePointCount(0, rawPassword.length());
        if (length < AuthProperties.PASSWORD_MIN_LENGTH
                || length > AuthProperties.PASSWORD_MAX_LENGTH
                || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password does not satisfy the required policy");
        }
    }

    private static int required(Integer value, String name) {
        if (value == null) {
            throw new IllegalStateException("Argon2 " + name + " must be configured");
        }
        return value;
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException("Argon2 " + name + " must be positive");
        }
    }
}
