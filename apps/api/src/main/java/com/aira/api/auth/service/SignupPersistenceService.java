package com.aira.api.auth.service;

import com.aira.api.user.domain.AppUser;
import com.aira.api.user.domain.AuthenticationCredential;
import com.aira.api.user.repository.AppUserRepository;
import com.aira.api.user.repository.AuthenticationCredentialRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SignupPersistenceService {
    private final AppUserRepository users;
    private final AuthenticationCredentialRepository credentials;

    SignupPersistenceService(AppUserRepository users, AuthenticationCredentialRepository credentials) {
        this.users = users;
        this.credentials = credentials;
    }

    @Transactional
    public AppUser save(String nickname, String normalizedNickname, String passwordHash) {
        OffsetDateTime now = OffsetDateTime.now();
        AppUser user = users.save(AppUser.create(nickname, normalizedNickname, now));
        credentials.save(AuthenticationCredential.create(user, passwordHash, now));
        users.flush();
        return user;
    }
}
