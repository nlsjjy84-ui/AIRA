package com.aira.api.market.query;

import java.util.List;
import java.util.UUID;
import com.aira.api.market.dto.PublicEventResponse.Company;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicEventFeedQuery {
    static final int MAX_EVENTS = 50;
    static final String EVENT_FEED_SQL = """
            SELECT ev.id,ev.event_type,ev.title,ev.occurred_at
            FROM event ev
            WHERE ev.status='CONFIRMED'
              AND EXISTS (SELECT 1 FROM event_entity ee JOIN entity en ON en.id=ee.entity_id
                          WHERE ee.event_id=ev.id AND en.entity_type='COMPANY' AND en.active=true)
              AND EXISTS (SELECT 1 FROM event_evidence eve WHERE eve.event_id=ev.id)
            ORDER BY ev.occurred_at DESC NULLS LAST,ev.id ASC
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    public PublicEventFeedQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PublicEventView> findRecentEvents() {
        // Limit event headers, then load every eligible company for each selected event.
        return jdbc.query(EVENT_FEED_SQL, (rs, row) -> new PublicEventView(
                rs.getObject(1, UUID.class), companies(rs.getObject(1, UUID.class)),
                rs.getString(2), rs.getString(3),
                rs.getObject(4, java.time.OffsetDateTime.class)), MAX_EVENTS);
    }

    private List<Company> companies(UUID eventId) {
        return jdbc.query("""
                SELECT en.id,en.canonical_name FROM event_entity ee JOIN entity en ON en.id=ee.entity_id
                WHERE ee.event_id=? AND en.entity_type='COMPANY' AND en.active=true ORDER BY en.id
                """, (rs, row) -> new Company(rs.getObject(1, UUID.class), rs.getString(2)), eventId);
    }
}
