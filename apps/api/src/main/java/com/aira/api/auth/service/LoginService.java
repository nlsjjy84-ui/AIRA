package com.aira.api.auth.service;

import com.aira.api.auth.dto.LoginRequest;
import com.aira.api.auth.dto.LoginResponse;
import com.aira.api.auth.exception.AuthenticationFailedException;
import com.aira.api.auth.exception.InvalidLoginRequestException;
import com.aira.api.auth.security.NicknameNormalizer;
import com.aira.api.auth.security.PasswordHasher;
import com.aira.api.auth.security.SessionTokenGenerator;
import com.aira.api.auth.security.SessionTokenHasher;
import com.aira.api.user.domain.AppUser;
import com.aira.api.user.domain.AuthenticationCredential;
import com.aira.api.user.domain.CredentialStatus;
import com.aira.api.user.domain.UserStatus;
import com.aira.api.user.repository.AppUserRepository;
import com.aira.api.user.repository.AuthenticationCredentialRepository;
import org.springframework.stereotype.Service;

@Service
public class LoginService {
    private static final String DUMMY_PASSWORD = "Aira authentication dummy password";
    private final AppUserRepository users;
    private final AuthenticationCredentialRepository credentials;
    private final NicknameNormalizer nicknameNormalizer;
    private final PasswordHasher passwordHasher;
    private final SessionTokenGenerator tokenGenerator;
    private final SessionTokenHasher tokenHasher;
    private final LoginPersistenceService persistence;
    private final String dummyPasswordHash;

    public LoginService(AppUserRepository users, AuthenticationCredentialRepository credentials,
            NicknameNormalizer nicknameNormalizer, PasswordHasher passwordHasher,
            SessionTokenGenerator tokenGenerator, SessionTokenHasher tokenHasher,
            LoginPersistenceService persistence) {
        this.users = users;
        this.credentials = credentials;
        this.nicknameNormalizer = nicknameNormalizer;
        this.passwordHasher = passwordHasher;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.persistence = persistence;
        this.dummyPasswordHash = passwordHasher.hash(DUMMY_PASSWORD);
    }

    public LoginResult login(LoginRequest request) {
        String normalizedNickname = normalize(request.nickname());
        validatePassword(request.password());
        AppUser user = users.findByNicknameNormalized(normalizedNickname).orElse(null);
        AuthenticationCredential credential = user == null
                ? null
                : credentials.findByUserId(user.getId()).orElse(null);
        String storedHash = credential == null ? dummyPasswordHash : credential.getPasswordHash();
        boolean matches = passwordHasher.matches(request.password(), storedHash);
        if (!matches || user == null || credential == null
                || user.getStatus() != UserStatus.ACTIVE
                || credential.getStatus() != CredentialStatus.ACTIVE) {
            throw new AuthenticationFailedException();
        }
        String rawToken = tokenGenerator.generate();
        persistence.save(user, credential, tokenHasher.hash(rawToken));
        return new LoginResult(LoginResponse.from(user), rawToken);
    }

    private String normalize(String nickname) {
        try {
            return nicknameNormalizer.normalize(nickname);
        } catch (IllegalArgumentException exception) {
            throw new InvalidLoginRequestException("nickname");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.isBlank()) throw new InvalidLoginRequestException("password");
        if (password.codePointCount(0, password.length()) > 72) {
            throw new InvalidLoginRequestException("password");
        }
    }
}
