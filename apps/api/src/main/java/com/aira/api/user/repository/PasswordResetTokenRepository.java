package com.aira.api.user.repository;

import com.aira.api.user.domain.PasswordResetToken;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {}
