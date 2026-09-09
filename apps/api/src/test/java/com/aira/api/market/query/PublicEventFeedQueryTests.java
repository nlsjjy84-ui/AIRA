package com.aira.api.market.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PublicEventFeedQueryTests {
    private static final UUID COMPANY = id(100);
    private JdbcTemplate jdbc;
    private PublicEventFeedQuery query;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:event-feed-" + UUID.randomUUID() +
                        ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE entity(id UUID PRIMARY KEY,canonical_name VARCHAR(255)," +
                "entity_type VARCHAR(32),active BOOLEAN)");
        jdbc.execute("CREATE TABLE event(id UUID PRIMARY KEY,event_type VARCHAR(64)," +
                "title VARCHAR(500),occurred_at TIMESTAMP WITH TIME ZONE,status VARCHAR(32))");
        jdbc.execute("CREATE TABLE evidence(id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE event_entity(event_id UUID,entity_id UUID)");
        jdbc.execute("CREATE TABLE event_evidence(event_id UUID,evidence_id UUID)");
        jdbc.execute("CREATE TABLE assessment(id UUID PRIMARY KEY,event_id UUID,summary VARCHAR(500))");
        jdbc.update("INSERT INTO entity VALUES(?,?,'COMPANY',true)", COMPANY, "삼성전자");
        query = new PublicEventFeedQuery(jdbc);
    }

    @Test
    void includesOnlyConfirmedEvidenceBackedCompanyEventsAndUsesFactualTitle() {
        UUID included = id(1);
        addEvent(included, "CONFIRMED", "공식 사실 제목", "2026-08-25T00:00:00Z", true, true);
        jdbc.update("INSERT INTO assessment VALUES(?,?,?)", id(900), included,
                "Assessment summary must not replace the title");
        addEvent(id(2), "CANDIDATE", "candidate", "2026-08-26T00:00:00Z", true, true);
        addEvent(id(3), "CONFIRMED", "no evidence", "2026-08-27T00:00:00Z", true, false);
        addEvent(id(4), "CONFIRMED", "no company", "2026-08-28T00:00:00Z", false, true);

        var events = query.findRecentEvents();

        assertEquals(1, events.size());
        assertEquals(included, events.getFirst().eventId());
        assertEquals(COMPANY, events.getFirst().companies().getFirst().companyId());
        assertEquals("삼성전자", events.getFirst().companies().getFirst().companyName());
        assertEquals("공식 사실 제목", events.getFirst().title());
        assertFalse(PublicEventFeedQuery.EVENT_FEED_SQL.contains("assessment"));
    }

    @Test
    void ordersNewestOccurrenceFirstThenByStableEventIdentity() {
        UUID older = id(3);
        UUID sameTimeHigh = id(2);
        UUID sameTimeLow = id(1);
        addEvent(older, "CONFIRMED", "older", "2026-08-20T00:00:00Z", true, true);
        addEvent(sameTimeHigh, "CONFIRMED", "high", "2026-08-21T00:00:00Z", true, true);
        addEvent(sameTimeLow, "CONFIRMED", "low", "2026-08-21T00:00:00Z", true, true);

        assertEquals(java.util.List.of(sameTimeLow, sameTimeHigh, older),
                query.findRecentEvents().stream().map(PublicEventView::eventId).toList());
        assertTrue(PublicEventFeedQuery.EVENT_FEED_SQL.contains(
                "ORDER BY ev.occurred_at DESC NULLS LAST,ev.id ASC"));
    }

    @Test
    void emptyQualifiedDatasetReturnsARegularEmptyList() {
        assertTrue(query.findRecentEvents().isEmpty());
        assertEquals(50, PublicEventFeedQuery.MAX_EVENTS);
    }

    private void addEvent(UUID eventId, String status, String title, String occurredAt,
            boolean companyLink, boolean evidenceLink) {
        jdbc.update("INSERT INTO event VALUES(?,'EARNINGS',?,?,?)", eventId, title,
                OffsetDateTime.parse(occurredAt), status);
        if (companyLink) {
            jdbc.update("INSERT INTO event_entity VALUES(?,?)", eventId, COMPANY);
        }
        if (evidenceLink) {
            UUID evidenceId = UUID.randomUUID();
            jdbc.update("INSERT INTO evidence VALUES(?)", evidenceId);
            jdbc.update("INSERT INTO event_evidence VALUES(?,?)", eventId, evidenceId);
        }
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }
}
