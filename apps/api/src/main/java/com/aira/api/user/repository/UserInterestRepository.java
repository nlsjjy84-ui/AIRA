package com.aira.api.user.repository;

import com.aira.api.user.domain.UserInterest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface UserInterestRepository extends JpaRepository<UserInterest, UUID> {
    java.util.Optional<UserInterest> findByUser_IdAndMarketEntity_Id(UUID userId, UUID entityId);
    @EntityGraph(attributePaths = "marketEntity")
    List<UserInterest> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUser_IdAndMarketEntity_Id(UUID userId, UUID entityId);

    long deleteByUser_IdAndMarketEntity_Id(UUID userId, UUID entityId);
}
