package com.aira.api.auth.security;

@FunctionalInterface
public interface VerificationTokenGenerator {
    String generate();
}
