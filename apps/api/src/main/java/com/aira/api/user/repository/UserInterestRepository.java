package com.aira.api.user.repository;

import com.aira.api.user.domain.UserInterest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInterestRepository extends JpaRepository<UserInterest, UUID> {}
