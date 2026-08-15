package com.aira.api.user.repository;

import com.aira.api.user.domain.RecoveryEmail;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryEmailRepository extends JpaRepository<RecoveryEmail, UUID> {
    Optional<RecoveryEmail> findByUserIdAndDeletedAtIsNull(UUID userId);
}
