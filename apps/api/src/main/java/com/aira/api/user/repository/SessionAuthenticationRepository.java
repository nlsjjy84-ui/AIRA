package com.aira.api.user.repository;

import com.aira.api.auth.security.AiraPrincipal;
import java.util.Optional;

public interface SessionAuthenticationRepository {
    Optional<AiraPrincipal> authenticateAndTouch(byte[] tokenHash);
}
