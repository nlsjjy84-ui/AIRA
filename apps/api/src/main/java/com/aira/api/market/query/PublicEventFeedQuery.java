package com.aira.api.market.query;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicEventFeedQuery {
    static final int MAX_EVENTS = 50;
    static final String EVENT_FEED_SQL = """
            SELECT ev.id,en.id,en.canonical_name,ev.event_type,ev.title,ev.occurred_at
            FROM event ev
            JOIN event_entity ee ON ee.event_id=ev.id
            JOIN entity en ON en.id=ee.entity_id
                          AND en.entity_type='COMPANY' AND en.active=true
            WHERE ev.status='CONFIRMED'
              AND EXISTS (SELECT 1 FROM event_evidence eve WHERE eve.event_id=ev.id)
            ORDER BY ev.occurred_at DESC NULLS LAST,ev.id ASC,en.id ASC
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    public PublicEventFeedQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PublicEventView> findRecentEvents() {
        return jdbc.query(EVENT_FEED_SQL, (rs, row) -> new PublicEventView(
                rs.getObject(1, java.util.UUID.class),
                rs.getObject(2, java.util.UUID.class), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getObject(6, java.time.OffsetDateTime.class)), MAX_EVENTS);
    }
}
