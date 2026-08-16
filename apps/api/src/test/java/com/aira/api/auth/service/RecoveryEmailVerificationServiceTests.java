package com.aira.api.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

import com.aira.api.auth.email.EmailSender;
import com.aira.api.auth.security.*;
import com.aira.api.user.repository.RecoveryEmailVerificationStore;
import java.time.*;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecoveryEmailVerificationServiceTests {
    @Test
    void storesOnlyProtectedEmailAndTokenHashThenSendsRawValues() {
        RecoveryEmailVerificationStore store = mock(RecoveryEmailVerificationStore.class);
        FakeSender sender = new FakeSender();
        Clock clock = Clock.fixed(Instant.parse("2026-08-16T08:00:00Z"), ZoneOffset.UTC);
        RecoveryEmailProtector protector = new RecoveryEmailProtector(
                Base64.getEncoder().encodeToString(new byte[32]),
                Base64.getEncoder().encodeToString(filled((byte) 1)), (short) 1);
        RecoveryEmailVerificationService service = new RecoveryEmailVerificationService(
                protector, () -> "raw-verification-token", new SessionTokenHasher(), store, sender, clock);

        UUID userId = UUID.randomUUID();
        service.request(userId, "User@example.com");

        verify(store).create(eq(userId), any(), any(), eq((short) 1),
                aryEq(new SessionTokenHasher().hash("raw-verification-token")),
                eq(OffsetDateTime.now(clock)), eq(OffsetDateTime.now(clock).plusMinutes(30)));
        assertEquals("user@example.com", sender.email);
        assertEquals("raw-verification-token", sender.token);
    }

    @Test
    void confirmHashesRawTokenAndReturnsOnlyGenericOutcome() {
        RecoveryEmailVerificationStore store = mock(RecoveryEmailVerificationStore.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-16T08:00:00Z"), ZoneOffset.UTC);
        when(store.confirm(any(), any())).thenReturn(true);
        RecoveryEmailVerificationService service = new RecoveryEmailVerificationService(
                mock(RecoveryEmailProtector.class), () -> "unused", new SessionTokenHasher(), store,
                new FakeSender(), clock);

        assertTrue(service.confirm("raw-verification-token"));
        verify(store).confirm(aryEq(new SessionTokenHasher().hash("raw-verification-token")),
                eq(OffsetDateTime.now(clock)));
    }

    static final class FakeSender implements EmailSender {
        String email;
        String token;
        public void sendRecoveryEmailVerification(String email, String rawToken) {
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
