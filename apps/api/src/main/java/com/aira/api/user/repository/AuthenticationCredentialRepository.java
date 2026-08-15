package com.aira.api.user.repository;

import com.aira.api.user.domain.AuthenticationCredential;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthenticationCredentialRepository
        extends JpaRepository<AuthenticationCredential, UUID> {
    Optional<AuthenticationCredential> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select credential from AuthenticationCredential credential where credential.id = :id")
    Optional<AuthenticationCredential> findByIdForSession(@Param("id") UUID id);
}
