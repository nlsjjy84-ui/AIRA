package com.aira.api.auth.service;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.auth.security.SessionTokenHasher;
import com.aira.api.user.repository.SessionAuthenticationRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionAuthenticationService {
    private final SessionTokenHasher tokenHasher;
    private final SessionAuthenticationRepository sessions;

    public SessionAuthenticationService(
            SessionTokenHasher tokenHasher, SessionAuthenticationRepository sessions) {
        this.tokenHasher = tokenHasher;
        this.sessions = sessions;
    }

    @Transactional
    public Optional<AiraPrincipal> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        return sessions.authenticateAndTouch(tokenHasher.hash(rawToken));
    }
}
