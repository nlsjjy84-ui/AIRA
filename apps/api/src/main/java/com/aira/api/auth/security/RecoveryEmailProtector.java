package com.aira.api.auth.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class RecoveryEmailProtector {
    private static final int IV_BYTES = 12;
    private final byte[] encryptionKey;
    private final byte[] lookupKey;
    private final short keyVersion;
    private final SecureRandom random = new SecureRandom();

    public RecoveryEmailProtector(String encryptionKeyBase64, String lookupKeyBase64, short keyVersion) {
        this.encryptionKey = decodeKey(encryptionKeyBase64, "encryption");
        this.lookupKey = decodeKey(lookupKeyBase64, "lookup");
        if (MessageDigest.isEqual(this.encryptionKey, this.lookupKey)) {
            throw new IllegalStateException("Recovery email encryption and lookup keys must be different");
        }
        if (keyVersion <= 0) throw new IllegalArgumentException("Recovery email key version must be positive");
        this.keyVersion = keyVersion;
    }

    public ProtectedRecoveryEmail protect(String email) {
        String normalized = normalize(email);
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return new ProtectedRecoveryEmail(normalized, ciphertext,
                    lookupHash(normalized), keyVersion);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("Recovery email protection is unavailable", impossible);
        }
    }

    public RecoveryEmailLookup lookup(String email) {
        String normalized = normalize(email);
        return new RecoveryEmailLookup(normalized, lookupHash(normalized));
    }

    private byte[] lookupHash(String normalized) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(lookupKey, "HmacSHA256"));
            return mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("Recovery email lookup protection is unavailable", impossible);
        }
    }

    private static String normalize(String email) {
        if (email == null) throw new IllegalArgumentException("Recovery email is required");
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 254 || !normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Recovery email is invalid");
        }
        return normalized;
    }

    private static byte[] decodeKey(String encoded, String name) {
        try {
            byte[] key = Base64.getDecoder().decode(encoded == null ? "" : encoded);
            if (key.length != 32) throw new IllegalArgumentException();
            return key;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Recovery email " + name + " key must be a Base64 256-bit key");
        }
    }
}
