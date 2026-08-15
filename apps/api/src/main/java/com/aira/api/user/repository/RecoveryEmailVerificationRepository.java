package com.aira.api.user.repository;

import com.aira.api.user.domain.RecoveryEmailVerification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryEmailVerificationRepository
        extends JpaRepository<RecoveryEmailVerification, UUID> {}
