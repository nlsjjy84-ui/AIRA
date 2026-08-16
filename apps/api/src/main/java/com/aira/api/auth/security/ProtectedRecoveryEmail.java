package com.aira.api.auth.security;

public record ProtectedRecoveryEmail(
        String normalizedEmail, byte[] ciphertext, byte[] lookupHash, short keyVersion) {
    public ProtectedRecoveryEmail {
        ciphertext = ciphertext.clone();
        lookupHash = lookupHash.clone();
    }

    @Override public byte[] ciphertext() { return ciphertext.clone(); }
    @Override public byte[] lookupHash() { return lookupHash.clone(); }
}
