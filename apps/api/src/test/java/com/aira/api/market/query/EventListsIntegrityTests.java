package com.aira.api.market.query;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.analysis.query.CompanyEventExperienceQuery;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class EventListsIntegrityTests {
    private JdbcTemplate jdbc;
    private PublicEventFeedQuery feed;
    private CompanyEventExperienceQuery companyEvents;
    private final UUID company = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:event-lists-"
                + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE event(id UUID PRIMARY KEY,event_type VARCHAR,title VARCHAR,occurred_at TIMESTAMP WITH TIME ZONE,status VARCHAR)");
        jdbc.execute("CREATE TABLE entity(id UUID PRIMARY KEY,canonical_name VARCHAR,entity_type VARCHAR,active BOOLEAN)");
        jdbc.execute("CREATE TABLE event_entity(event_id UUID,entity_id UUID,PRIMARY KEY(event_id,entity_id))");
        jdbc.execute("CREATE TABLE source(id UUID PRIMARY KEY,name VARCHAR)");
        jdbc.execute("CREATE TABLE evidence(id UUID PRIMARY KEY,source_id UUID,external_id VARCHAR,original_url VARCHAR,title VARCHAR,revision INT)");
        jdbc.execute("CREATE TABLE event_evidence(event_id UUID,evidence_id UUID,PRIMARY KEY(event_id,evidence_id))");
        jdbc.execute("CREATE TABLE assessment(id UUID PRIMARY KEY,event_id UUID,supersedes_assessment_id UUID,importance VARCHAR,summary VARCHAR,confidence VARCHAR,uncertainty VARCHAR,time_horizon VARCHAR,method VARCHAR,status VARCHAR)");
        jdbc.execute("CREATE TABLE assessment_evidence(assessment_id UUID,evidence_id UUID)");
        jdbc.update("INSERT INTO entity VALUES(?,'A','COMPANY',true),(?,'B','COMPANY',true)", company, other);
        jdbc.update("INSERT INTO source VALUES(?,'Official')", company);
        feed = new PublicEventFeedQuery(jdbc);
        companyEvents = new CompanyEventExperienceQuery(jdbc);
    }

    @Test
    void oneEventPreservesMultipleCompaniesEvidenceAndRevisionsWithoutMultiplyingRows() {
        UUID event = addEvent(null);
        var result = feed.findRecentEvents();
        assertEquals(1, result.size());
        assertEquals(event, result.getFirst().eventId());
        assertEquals(List.of(company, other).stream().sorted().toList(), result.getFirst().companies()
                .stream().map(c -> c.companyId()).sorted().toList());
        var experiences = companyEvents.find(company).events();
        assertEquals(1, experiences.size());
        assertNull(experiences.getFirst().assessment());
        assertNull(experiences.getFirst().occurredAt());
        assertEquals(3, experiences.getFirst().evidence().size());
        assertEquals(3, experiences.getFirst().evidence().stream().map(e -> e.evidenceId()).distinct().count());
        assertEquals(List.of(1, 1, 2), experiences.getFirst().evidence().stream()
                .map(e -> e.revision()).sorted().toList());
        assertTrue(experiences.getFirst().evidence().stream().allMatch(e -> e.originalUrl() != null));
    }

    @Test
    void limitCountsEventsAndNullOccurrenceSortsLastWithStableIdentity() {
        UUID unknown = addEvent(null);
        for (int i = 0; i < 51; i++) addEvent("2026-03-31T00:00:00Z");
        var result = feed.findRecentEvents();
        assertEquals(50, result.size());
        assertEquals(50, result.stream().map(PublicEventView::eventId).distinct().count());
        assertTrue(result.stream().allMatch(e -> e.companies().size() == 2));
        assertFalse(result.stream().anyMatch(e -> e.eventId().equals(unknown)));
        var experiences = companyEvents.find(company).events();
        assertEquals(52, experiences.size());
        assertEquals(unknown, experiences.getLast().eventId());
        assertEquals(experiences.stream().limit(50).map(e -> e.eventId()).toList(),
                result.stream().map(PublicEventView::eventId).toList());
    }

    private UUID addEvent(String occurredAt) {
        UUID event = UUID.randomUUID();
        jdbc.update("INSERT INTO event VALUES(?,'EARNINGS','neutral',?,'CONFIRMED')", event,
                occurredAt == null ? null : java.time.OffsetDateTime.parse(occurredAt));
        jdbc.update("INSERT INTO event_entity VALUES(?,?),(?,?)", event, company, event, other);
        for (int i = 0; i < 3; i++) {
            UUID evidence = UUID.randomUUID();
            jdbc.update("INSERT INTO evidence VALUES(?,?,?,?,?,?)", evidence, company,
                    i < 2 ? "same-filing" : "other-filing", "https://official.test/" + i, "filing", i == 1 ? 2 : 1);
            jdbc.update("INSERT INTO event_evidence VALUES(?,?)", event, evidence);
        }
        return event;
    }
}
