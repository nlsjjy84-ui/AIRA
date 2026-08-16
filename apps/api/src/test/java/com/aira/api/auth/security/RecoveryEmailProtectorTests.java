package com.aira.api.auth.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class RecoveryEmailProtectorTests {
    @Test
    void encryptsEmailAndCreatesSeparateDeterministicLookupHash() {
        RecoveryEmailProtector protector = new RecoveryEmailProtector(
                Base64.getEncoder().encodeToString(new byte[32]),
                Base64.getEncoder().encodeToString(filled((byte) 1)), (short) 1);
        ProtectedRecoveryEmail first = protector.protect(" User@Example.COM ");
        ProtectedRecoveryEmail second = protector.protect("user@example.com");

        assertNotEquals("user@example.com", new String(first.ciphertext()));
        assertFalse(java.util.Arrays.equals(first.ciphertext(), second.ciphertext()));
        assertArrayEquals(first.lookupHash(), second.lookupHash());
        assertEquals("user@example.com", first.normalizedEmail());
        assertEquals(1, first.keyVersion());
    }

    @Test
    void rejectsReusingEncryptionKeyAsLookupKey() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        assertThrows(IllegalStateException.class,
                () -> new RecoveryEmailProtector(key, key, (short) 1));
    }

    private static byte[] filled(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}
