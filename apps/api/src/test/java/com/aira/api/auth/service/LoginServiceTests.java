package com.aira.api.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aira.api.auth.config.AuthProperties;
import com.aira.api.auth.dto.LoginRequest;
import com.aira.api.auth.exception.AuthenticationFailedException;
import com.aira.api.auth.exception.InvalidLoginRequestException;
import com.aira.api.auth.security.NicknameNormalizer;
import com.aira.api.auth.security.PasswordHasher;
import com.aira.api.auth.security.SessionTokenGenerator;
import com.aira.api.auth.security.SessionTokenHasher;
import com.aira.api.user.domain.AppUser;
import com.aira.api.user.domain.AuthenticationCredential;
import com.aira.api.user.repository.AppUserRepository;
import com.aira.api.user.repository.AuthenticationCredentialRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginServiceTests {
    AppUserRepository users = mock(AppUserRepository.class);
    AuthenticationCredentialRepository credentials = mock(AuthenticationCredentialRepository.class);
    PasswordHasher hasher = mock(PasswordHasher.class);
    SessionTokenGenerator tokenGenerator = mock(SessionTokenGenerator.class);
    SessionTokenHasher tokenHasher = mock(SessionTokenHasher.class);
    LoginPersistenceService persistence = mock(LoginPersistenceService.class);
    LoginService service;
    AppUser user;
    AuthenticationCredential credential;

    @BeforeEach
    void setUp() {
        when(hasher.hash(anyString())).thenReturn("$argon2id$dummy");
        service = new LoginService(users, credentials, new NicknameNormalizer(new AuthProperties()),
                hasher, tokenGenerator, tokenHasher, persistence);
        user = AppUser.create("AiraUser", "airauser", OffsetDateTime.now());
        credential = AuthenticationCredential.create(user, "$argon2id$stored", OffsetDateTime.now());
        when(users.findByNicknameNormalized(anyString())).thenReturn(Optional.of(user));
        when(credentials.findByUserId(any())).thenReturn(Optional.of(credential));
        when(hasher.matches(anyString(), eq("$argon2id$stored"))).thenReturn(true);
        when(tokenGenerator.generate()).thenReturn("opaque-token");
        when(tokenHasher.hash("opaque-token")).thenReturn(new byte[] {1, 2, 3});
    }

    @Test
    void logsInEnglishKoreanAndNfkcEquivalentNicknames() {
        service.login(new LoginRequest("AiraUser", "correct-password-value"));
        verify(users).findByNicknameNormalized("airauser");

        clearInvocations(users, credentials, hasher, tokenGenerator, tokenHasher, persistence);
        service.login(new LoginRequest("ＡＩＲＡuser", "correct-password-value"));
        verify(users).findByNicknameNormalized("airauser");

        AppUser korean = AppUser.create("테스트1", "테스트1", OffsetDateTime.now());
        AuthenticationCredential koreanCredential =
                AuthenticationCredential.create(korean, "$argon2id$stored", OffsetDateTime.now());
        when(users.findByNicknameNormalized("테스트1")).thenReturn(Optional.of(korean));
        when(credentials.findByUserId(any())).thenReturn(Optional.of(koreanCredential));
        service.login(new LoginRequest("테스트1", "correct-password-value"));
        verify(users).findByNicknameNormalized("테스트1");
    }

    @Test
    void createsSessionOnlyAfterSuccessfulAuthentication() {
        LoginResult result = service.login(new LoginRequest("AiraUser", "correct-password-value"));

        assertEquals("AiraUser", result.response().user().nickname());
        assertEquals("opaque-token", result.rawSessionToken());
        assertFalse(result.toString().contains(result.rawSessionToken()));
        verify(persistence).save(user, credential, new byte[] {1, 2, 3});
    }

    @Test
    void unknownNicknameAndWrongPasswordUseSameFailureWithoutSession() {
        when(users.findByNicknameNormalized("unknownuser")).thenReturn(Optional.empty());
        assertThrows(AuthenticationFailedException.class,
                () -> service.login(new LoginRequest("UnknownUser", "correct-password-value")));
        verify(hasher).matches("correct-password-value", "$argon2id$dummy");
        verifyNoInteractions(persistence);

        clearInvocations(hasher, persistence);
        when(hasher.matches("wrong-password-value", "$argon2id$stored")).thenReturn(false);
        assertThrows(AuthenticationFailedException.class,
                () -> service.login(new LoginRequest("AiraUser", "wrong-password-value")));
        verifyNoInteractions(persistence);
    }

    @Test
    void rejectsInvalidNicknameBlankPasswordAndOverlongPassword() {
        assertThrows(InvalidLoginRequestException.class,
                () -> service.login(new LoginRequest("1Aira", "correct-password-value")));
        assertThrows(InvalidLoginRequestException.class,
                () -> service.login(new LoginRequest("AiraUser", "   ")));
        assertThrows(InvalidLoginRequestException.class,
                () -> service.login(new LoginRequest("AiraUser", "x".repeat(73))));
        verifyNoInteractions(persistence);
    }

    @Test
    void loginRequestToStringRedactsPassword() {
        LoginRequest request = new LoginRequest("AiraUser", "correct-password-value");
        assertFalse(request.toString().contains(request.password()));
        assertTrue(request.toString().contains("<redacted>"));
    }
}
