package com.aira.api.auth.service;

import com.aira.api.auth.dto.SignupRequest;
import com.aira.api.auth.dto.SignupResponse;
import com.aira.api.auth.exception.InvalidSignupRequestException;
import com.aira.api.auth.exception.NicknameAlreadyExistsException;
import com.aira.api.auth.security.NicknameNormalizer;
import com.aira.api.auth.security.PasswordHasher;
import com.aira.api.user.domain.AppUser;
import com.aira.api.user.repository.AppUserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final String NICKNAME_CONSTRAINT = "uq_app_user_nickname_normalized";
    private final AppUserRepository users;
    private final NicknameNormalizer nicknameNormalizer;
    private final PasswordHasher passwordHasher;
    private final SignupPersistenceService persistence;

    public AuthService(AppUserRepository users, NicknameNormalizer nicknameNormalizer,
            PasswordHasher passwordHasher, SignupPersistenceService persistence) {
        this.users = users;
        this.nicknameNormalizer = nicknameNormalizer;
        this.passwordHasher = passwordHasher;
        this.persistence = persistence;
    }

    public SignupResponse signup(SignupRequest request) {
        String nickname = normalizeDisplay(request.nickname());
        String normalized = normalize(nickname);
        validatePassword(request.password());
        if (users.existsByNicknameNormalized(normalized)) {
            throw new NicknameAlreadyExistsException();
        }
        String hash = passwordHasher.hash(request.password());
        try {
            AppUser user = persistence.save(nickname, normalized, hash);
            return SignupResponse.from(user);
        } catch (DataIntegrityViolationException exception) {
            if (isNicknameConflict(exception)) throw new NicknameAlreadyExistsException();
            throw exception;
        }
    }

    private String normalizeDisplay(String nickname) {
        try {
            return nicknameNormalizer.normalizeDisplay(nickname);
        } catch (IllegalArgumentException exception) {
            throw new InvalidSignupRequestException("nickname");
        }
    }

    private String normalize(String nickname) {
        try {
            return nicknameNormalizer.normalize(nickname);
        } catch (IllegalArgumentException exception) {
            throw new InvalidSignupRequestException("nickname");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.isBlank()) throw new InvalidSignupRequestException("password");
        int length = password.codePointCount(0, password.length());
        if (length < 15 || length > 72) throw new InvalidSignupRequestException("password");
    }

    private static boolean isNicknameConflict(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException constraint
                    && NICKNAME_CONSTRAINT.equalsIgnoreCase(constraint.getConstraintName())) return true;
            if (current.getMessage() != null
                    && current.getMessage().toLowerCase(java.util.Locale.ROOT).contains(NICKNAME_CONSTRAINT)) return true;
        }
        return false;
    }
}
