package com.aira.api.market.repository;

import com.aira.api.market.domain.Event;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EventRegistrationStore {
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public EventRegistrationStore(JdbcTemplate jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Event registerOrGetLocked(Event candidate) {
        entityManager.flush();
        // The existing unique index arbitrates first creation. The no-op update holds the
        // event row lock through observation, relation creation and the caller's commit.
        UUID id = jdbc.queryForObject("""
                INSERT INTO event(event_type,title,occurred_at,first_observed_at,last_observed_at,
                                  status,dedup_key,created_at,updated_at)
                VALUES ('EARNINGS',?,?,?,?,'CANDIDATE',?,?,?)
                ON CONFLICT (dedup_key) WHERE dedup_key IS NOT NULL
                DO UPDATE SET dedup_key=EXCLUDED.dedup_key
                RETURNING id
                """, UUID.class, candidate.getTitle(), candidate.getOccurredAt(),
                candidate.getFirstObservedAt(), candidate.getLastObservedAt(), candidate.getDedupKey(),
                candidate.getCreatedAt(), candidate.getUpdatedAt());
        Event event = entityManager.find(Event.class, id);
        // A caller may have loaded this event before waiting on the database lock.
        entityManager.refresh(event);
        return event;
    }
}
