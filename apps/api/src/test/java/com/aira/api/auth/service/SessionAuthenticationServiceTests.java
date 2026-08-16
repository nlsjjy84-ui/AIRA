package com.aira.api.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.auth.security.SessionTokenHasher;
import com.aira.api.user.repository.SessionAuthenticationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionAuthenticationServiceTests {
    @Test
    void hashesRawTokenAndReturnsMinimalPrincipal() {
        SessionTokenHasher hasher = mock(SessionTokenHasher.class);
        SessionAuthenticationRepository sessions = mock(SessionAuthenticationRepository.class);
        byte[] hash = new byte[] {1, 2, 3};
        AiraPrincipal principal = new AiraPrincipal(UUID.randomUUID(), "AiraUser");
        when(hasher.hash("opaque-token")).thenReturn(hash);
        when(sessions.authenticateAndTouch(hash)).thenReturn(Optional.of(principal));

        Optional<AiraPrincipal> result = new SessionAuthenticationService(hasher, sessions)
                .authenticate("opaque-token");

        assertEquals(Optional.of(principal), result);
        verify(sessions).authenticateAndTouch(hash);
        assertFalse(principal.toString().contains("opaque-token"));
    }

    @Test
    void invalidSessionReturnsEmptyWithoutExposingToken() {
        SessionTokenHasher hasher = mock(SessionTokenHasher.class);
        SessionAuthenticationRepository sessions = mock(SessionAuthenticationRepository.class);
        byte[] hash = new byte[] {4, 5, 6};
        when(hasher.hash("invalid-token")).thenReturn(hash);
        when(sessions.authenticateAndTouch(hash)).thenReturn(Optional.empty());

        assertTrue(new SessionAuthenticationService(hasher, sessions)
                .authenticate("invalid-token").isEmpty());
    }
}
