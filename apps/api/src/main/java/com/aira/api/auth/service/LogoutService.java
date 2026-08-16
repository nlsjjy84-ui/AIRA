package com.aira.api.auth.service;

import com.aira.api.auth.security.SessionTokenHasher;
import com.aira.api.user.repository.SessionLogoutRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService {
    private final SessionTokenHasher tokenHasher;
    private final SessionLogoutRepository sessions;
    private final Clock clock;

    public LogoutService(SessionTokenHasher tokenHasher, SessionLogoutRepository sessions, Clock clock) {
        this.tokenHasher = tokenHasher;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        sessions.revokeForLogout(tokenHasher.hash(rawToken), OffsetDateTime.now(clock));
    }
}
