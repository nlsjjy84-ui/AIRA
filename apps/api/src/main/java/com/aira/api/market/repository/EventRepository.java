package com.aira.api.market.repository;

import com.aira.api.market.domain.Event;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, UUID> {
    Optional<Event> findByDedupKey(byte[] dedupKey);
}
