package com.aira.api.user.repository;

import com.aira.api.user.domain.UserSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {}
