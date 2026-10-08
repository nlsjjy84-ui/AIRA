package com.aira.api.auth.security;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.auth.config.AuthProperties;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class AuthSecurityCoreTests {
    private static final Pattern URL_SAFE = Pattern.compile("^[A-Za-z0-9_-]+$");

    @Test
    void normalizesNicknameWithNfkcAndAsciiCaseFold() {
        NicknameNormalizer normalizer = new NicknameNormalizer(new AuthProperties());
        assertEquals("AIRAuser", normalizer.normalizeDisplay("ＡＩＲＡuser"));
        assertEquals("aira123", normalizer.normalize("AIRA123"));
        assertEquals("테스트1", normalizer.normalize("테스트1"));
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("1Aira"));
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("관리자"));
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("ab cd"));
    }

    @Test
    void blocksDefaultAndExternallyConfiguredReservedNicknames() {
        AuthProperties defaults = new AuthProperties();
        NicknameNormalizer defaultNormalizer = new NicknameNormalizer(defaults);
        assertThrows(IllegalArgumentException.class, () -> defaultNormalizer.normalize("ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> defaultNormalizer.normalize("관리자"));

        AuthProperties customized = new AuthProperties();
        customized.setReservedNicknames(List.of("admin", "AiraTeam"));
        NicknameNormalizer customizedNormalizer = new NicknameNormalizer(customized);
        assertThrows(IllegalArgumentException.class, () -> customizedNormalizer.normalize("AIRATEAM"));
    }

    @Test
    void hashesAndMatchesWithArgon2id() {
        PasswordHasher hasher = new PasswordHasher(16, 32, 1, 19_456, 2);
        String encoded = hasher.hash(testPassword());

        assertTrue(encoded.startsWith("$argon2id$"));
        assertTrue(hasher.matches(testPassword(), encoded));
        assertFalse(hasher.matches("different-password-value", encoded));
    }

    @Test
    void enforcesPasswordLengthAndBlankPolicy() {
        PasswordHasher hasher = new PasswordHasher(16, 32, 1, 19_456, 2);
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("short-pw"));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("               "));
        assertDoesNotThrow(() -> hasher.hash("123456789012345"));
    }

    @Test
    void matchesCurrentMaximumAndDoesNotEnforceCurrentMinimum() {
        PasswordHasher hasher = new PasswordHasher(16, 32, 1, 19_456, 2);
        String maximumPassword = "x".repeat(72);
        String maximumHash = hasher.hash(maximumPassword);
        assertTrue(hasher.matches(maximumPassword, maximumHash));

        String legacyPassword = "legacy-short";
        Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
        String legacyHash = encoder.encode(legacyPassword);
        assertTrue(hasher.matches(legacyPassword, legacyHash));
    }

    @Test
    void rejectsOverMaximumBeforeInvokingArgon2() {
        PasswordHasher hasher = new PasswordHasher(16, 32, 1, 19_456, 2);
        assertFalse(hasher.matches("x".repeat(73), "$argon2id$malformed"));
    }

    @Test
    void createsAndHashesOpaqueSessionTokens() {
        SessionTokenGenerator generator = new SessionTokenGenerator();
        SessionTokenHasher hasher = new SessionTokenHasher();
        String first = generator.generate();
        String second = generator.generate();

        assertEquals(32, Base64.getUrlDecoder().decode(first).length);
        assertEquals(43, first.length());
        assertTrue(URL_SAFE.matcher(first).matches());
        assertFalse(first.contains("="));
        assertArrayEquals(hasher.hash(first), hasher.hash(first));
        assertFalse(java.util.Arrays.equals(hasher.hash(first), hasher.hash(second)));
    }

    private static String testPassword() {
        return "correct horse battery staple";
    }
}
