package com.aira.api.auth.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aira.auth")
public class AuthProperties {
    public static final int PASSWORD_MIN_LENGTH = 10;
    public static final int PASSWORD_MAX_LENGTH = 72;
    public static final int SESSION_TOKEN_BYTES = 32;
    public static final int REQUEST_BODY_MAX_BYTES = 8 * 1024;

    private final Argon2 argon2 = new Argon2();
    private final Session session = new Session();
    private final RecoveryEmail recoveryEmail = new RecoveryEmail();
    private final Resend resend = new Resend();
    private List<String> reservedNicknames = new ArrayList<>(
            List.of("admin", "administrator", "관리자", "운영자"));

    public Argon2 getArgon2() {
        return argon2;
    }

    public Session getSession() {
        return session;
    }

    public RecoveryEmail getRecoveryEmail() { return recoveryEmail; }
    public Resend getResend() { return resend; }

    public List<String> getReservedNicknames() {
        return List.copyOf(reservedNicknames);
    }

    public void setReservedNicknames(List<String> reservedNicknames) {
        this.reservedNicknames = reservedNicknames == null
                ? new ArrayList<>()
                : new ArrayList<>(reservedNicknames);
    }

    public static class Argon2 {
        private Integer saltLength;
        private Integer hashLength;
        private Integer parallelism;
        private Integer memoryKiB;
        private Integer iterations;

        public Integer getSaltLength() { return saltLength; }
        public void setSaltLength(Integer saltLength) { this.saltLength = saltLength; }
        public Integer getHashLength() { return hashLength; }
        public void setHashLength(Integer hashLength) { this.hashLength = hashLength; }
        public Integer getParallelism() { return parallelism; }
        public void setParallelism(Integer parallelism) { this.parallelism = parallelism; }
        public Integer getMemoryKiB() { return memoryKiB; }
        public void setMemoryKiB(Integer memoryKiB) { this.memoryKiB = memoryKiB; }
        public Integer getIterations() { return iterations; }
        public void setIterations(Integer iterations) { this.iterations = iterations; }
    }

    public static class Session {
        private boolean cookieSecure = true;
        private String cookieSameSite = "Lax";

        public boolean isCookieSecure() { return cookieSecure; }
        public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
        public String getCookieSameSite() { return cookieSameSite; }
        public void setCookieSameSite(String cookieSameSite) { this.cookieSameSite = cookieSameSite; }
    }

    public static class RecoveryEmail {
        private String encryptionKey;
        private String lookupKey;
        private short keyVersion = 1;
        public String getEncryptionKey() { return encryptionKey; }
        public void setEncryptionKey(String value) { encryptionKey = value; }
        public String getLookupKey() { return lookupKey; }
        public void setLookupKey(String value) { lookupKey = value; }
        public short getKeyVersion() { return keyVersion; }
        public void setKeyVersion(short value) { keyVersion = value; }
    }

    public static class Resend {
        private String apiKey;
        private String from;
        private String publicBaseUrl;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String value) { apiKey = value; }
        public String getFrom() { return from; }
        public void setFrom(String value) { from = value; }
        public String getPublicBaseUrl() { return publicBaseUrl; }
        public void setPublicBaseUrl(String value) { publicBaseUrl = value; }
    }
}
