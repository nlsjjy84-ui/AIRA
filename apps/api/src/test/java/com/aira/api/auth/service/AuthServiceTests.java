package com.aira.api.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aira.api.auth.config.AuthProperties;
import com.aira.api.auth.dto.SignupRequest;
import com.aira.api.auth.dto.SignupResponse;
import com.aira.api.auth.exception.InvalidSignupRequestException;
import com.aira.api.auth.exception.NicknameAlreadyExistsException;
import com.aira.api.auth.security.NicknameNormalizer;
import com.aira.api.auth.security.PasswordHasher;
import com.aira.api.user.domain.AppUser;
import com.aira.api.user.repository.AppUserRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class AuthServiceTests {
    AppUserRepository users = mock(AppUserRepository.class);
    PasswordHasher hasher = mock(PasswordHasher.class);
    SignupPersistenceService persistence = mock(SignupPersistenceService.class);
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, new NicknameNormalizer(new AuthProperties()), hasher, persistence);
        when(hasher.hash(anyString())).thenReturn("$argon2id$hash");
        when(persistence.save(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> AppUser.create(invocation.getArgument(0), invocation.getArgument(1), OffsetDateTime.now()));
    }

    @Test
    void normalizesBeforeDuplicateQuery() {
        when(users.existsByNicknameNormalized("airauser")).thenReturn(true);
        assertThrows(NicknameAlreadyExistsException.class,
                () -> service.signup(new SignupRequest("AIRAUSER", "x".repeat(15))));
        verify(users).existsByNicknameNormalized("airauser");
        verifyNoInteractions(persistence);
    }

    @Test
    void nfkcEquivalentNicknameIsDuplicate() {
        when(users.existsByNicknameNormalized("airauser")).thenReturn(true);
        assertThrows(NicknameAlreadyExistsException.class,
                () -> service.signup(new SignupRequest("ＡＩＲＡuser", "x".repeat(15))));
    }

    @Test
    void persistsNfkcDisplayNicknameAndLowercaseNormalizedNickname() {
        SignupResponse response = service.signup(
                new SignupRequest("ＡＩＲＡuser", "x".repeat(15)));

        verify(persistence).save("AIRAuser", "airauser", "$argon2id$hash");
        assertEquals("AIRAuser", response.user().nickname());
    }

    @Test
    void rejectsNicknameThatIsInvalidAfterNfkc() {
        assertThrows(InvalidSignupRequestException.class,
                () -> service.signup(new SignupRequest("１Aira", "x".repeat(15))));
        verifyNoInteractions(persistence);
    }

    @Test
    void enforcesPasswordCodePointBoundaries() {
        assertThrows(InvalidSignupRequestException.class, () -> signupWith("😀".repeat(14)));
        assertDoesNotThrow(() -> signupWith("😀".repeat(15)));
        assertDoesNotThrow(() -> signupWith("😀".repeat(72)));
        assertThrows(InvalidSignupRequestException.class, () -> signupWith("😀".repeat(73)));
        assertThrows(InvalidSignupRequestException.class, () -> signupWith(" ".repeat(15)));
    }

    @Test
    void requestToStringRedactsPassword() {
        SignupRequest request = new SignupRequest("AiraUser", "secret-password-value");
        assertFalse(request.toString().contains(request.password()));
        assertTrue(request.toString().contains("<redacted>"));
    }

    @Test
    void convertsConcurrentNicknameUniqueViolationToConflict() {
        when(persistence.save(anyString(), anyString(), anyString())).thenThrow(
                new DataIntegrityViolationException(
                        "constraint uq_app_user_nickname_normalized", new RuntimeException("duplicate")));
        assertThrows(NicknameAlreadyExistsException.class,
                () -> service.signup(new SignupRequest("AiraUser", "x".repeat(15))));
    }

    private void signupWith(String password) {
        service.signup(new SignupRequest("AiraUser", password));
    }
}
