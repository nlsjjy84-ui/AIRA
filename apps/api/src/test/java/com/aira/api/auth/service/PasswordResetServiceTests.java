package com.aira.api.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aira.api.auth.email.EmailSender;
import com.aira.api.auth.exception.InvalidPasswordResetTokenException;
import com.aira.api.auth.security.*;
import com.aira.api.user.repository.PasswordResetStore;
import java.time.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PasswordResetServiceTests {
    Clock clock = Clock.fixed(Instant.parse("2026-08-16T08:00:00Z"), ZoneOffset.UTC);
    RecoveryEmailProtector protector = new RecoveryEmailProtector(
            Base64.getEncoder().encodeToString(new byte[32]),
            Base64.getEncoder().encodeToString(filled((byte) 1)), (short) 1);
    PasswordResetStore store = mock(PasswordResetStore.class);
    FakeSender sender = new FakeSender();
    PasswordHasher passwords = mock(PasswordHasher.class);

    @Test
    void knownEmailStoresOnlyTokenHashForExactlyThirtyMinutesAndSendsRawToken() {
        when(store.create(any(), any(), any(), any())).thenReturn(true);
        PasswordResetService service = service();

        service.request("User@example.com");

        OffsetDateTime now = OffsetDateTime.now(clock);
        verify(store).create(any(), aryEq(new SessionTokenHasher().hash("raw-reset-token")),
                eq(now), eq(now.plusMinutes(30)));
        assertEquals("user@example.com", sender.email);
        assertEquals("raw-reset-token", sender.token);
    }

    @Test
    void unknownEmailReturnsIdenticallyWithoutSendingToken() {
        when(store.create(any(), any(), any(), any())).thenReturn(false);
        assertDoesNotThrow(() -> service().request("unknown@example.com"));
        assertNull(sender.token);
    }

    @Test
    void confirmHashesPasswordAndConsumesTokenAtomically() {
        when(passwords.hash("new-password-value")).thenReturn("$argon2id$new-hash");
        when(store.consumeAndReset(any(), anyString(), any())).thenReturn(true);

        service().confirm("raw-reset-token", "new-password-value");

        verify(store).consumeAndReset(
                aryEq(new SessionTokenHasher().hash("raw-reset-token")),
                eq("$argon2id$new-hash"), eq(OffsetDateTime.now(clock)));
    }

    @Test
    void reusedExpiredOrUnknownTokenUsesOneGenericFailure() {
        when(passwords.hash(anyString())).thenReturn("$argon2id$new-hash");
        when(store.consumeAndReset(any(), anyString(), any())).thenReturn(false);
        assertThrows(InvalidPasswordResetTokenException.class,
                () -> service().confirm("invalid-token", "new-password-value"));
    }

    private PasswordResetService service() {
        return new PasswordResetService(protector, () -> "raw-reset-token",
                new SessionTokenHasher(), passwords, store, sender, clock);
    }

    static final class FakeSender implements EmailSender {
        String email;
        String token;
        public void sendRecoveryEmailVerification(String email, String rawToken) {}
        public void sendPasswordReset(String email, String rawToken) {
            this.email = email;
            this.token = rawToken;
        }
    }

    private static byte[] filled(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}
