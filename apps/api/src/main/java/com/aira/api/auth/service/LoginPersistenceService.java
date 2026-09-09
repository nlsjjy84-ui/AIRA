package com.aira.api.auth.service;

import com.aira.api.auth.exception.AuthenticationFailedException;
import com.aira.api.user.domain.AppUser;
import com.aira.api.user.domain.AuthenticationCredential;
import com.aira.api.user.domain.CredentialStatus;
import com.aira.api.user.domain.UserSession;
import com.aira.api.user.domain.UserStatus;
import com.aira.api.user.repository.AppUserRepository;
import com.aira.api.user.repository.AuthenticationCredentialRepository;
import com.aira.api.user.repository.UserSessionRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LoginPersistenceService {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final UserSessionRepository sessions;
    private final AppUserRepository users;
    private final AuthenticationCredentialRepository credentials;

    LoginPersistenceService(UserSessionRepository sessions, AppUserRepository users,
            AuthenticationCredentialRepository credentials) {
        this.sessions = sessions;
        this.users = users;
        this.credentials = credentials;
    }

    @Transactional
    public UserSession save(AppUser user, AuthenticationCredential credential, byte[] tokenHash) {
        String verifiedPasswordHash = credential.getPasswordHash();
        AppUser currentUser = users.findByIdForSession(user.getId())
                .orElseThrow(AuthenticationFailedException::new);
        AuthenticationCredential currentCredential = credentials.findByIdForSession(credential.getId())
                .orElseThrow(AuthenticationFailedException::new);
        entityManager.refresh(currentUser);
        entityManager.refresh(currentCredential);
        if (currentUser.getStatus() != UserStatus.ACTIVE
                || currentCredential.getStatus() != CredentialStatus.ACTIVE
                || !java.util.Objects.equals(verifiedPasswordHash, currentCredential.getPasswordHash())
                || !currentCredential.getUser().getId().equals(currentUser.getId())) {
            throw new AuthenticationFailedException();
        }
        return sessions.saveAndFlush(
                UserSession.create(currentUser, currentCredential, tokenHash, OffsetDateTime.now()));
    }
}
