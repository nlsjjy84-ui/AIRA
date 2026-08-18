package com.aira.api.market.repository;

import com.aira.api.market.domain.Fact;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactRepository extends JpaRepository<Fact, UUID> {
    Optional<Fact> findByDedupKey(byte[] dedupKey);
}
