package com.aira.api.auth.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aira.api.auth.security.SessionTokenHasher;
import com.aira.api.user.repository.SessionLogoutRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LogoutServiceTests {
    SessionTokenHasher hasher = new SessionTokenHasher();
    SessionLogoutRepository sessions = mock(SessionLogoutRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-16T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void hashesRawTokenAndRevokesOnlyByHash() {
        new LogoutService(hasher, sessions, clock).logout("opaque-token");

        ArgumentCaptor<byte[]> hash = ArgumentCaptor.forClass(byte[].class);
        verify(sessions).revokeForLogout(hash.capture(),
                eq(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
        assertArrayEquals(hasher.hash("opaque-token"), hash.getValue());
    }

    @Test
    void missingOrBlankTokenIsAnIdempotentNoOp() {
        LogoutService service = new LogoutService(hasher, sessions, clock);
        service.logout(null);
        service.logout("");
        service.logout("   ");
        verify(sessions, never()).revokeForLogout(any(), any());
    }
}
