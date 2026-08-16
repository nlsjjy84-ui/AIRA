package com.aira.api.auth.security;

public record RecoveryEmailLookup(String normalizedEmail, byte[] lookupHash) {
    public RecoveryEmailLookup { lookupHash = lookupHash.clone(); }
    @Override public byte[] lookupHash() { return lookupHash.clone(); }
}
