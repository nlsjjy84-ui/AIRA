package com.aira.api.analysis.query;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CurrentAssessmentQueryTests {
    private JdbcTemplate jdbc;
    private HistoricalAssessmentQuery historical;
    private CurrentAssessmentQuery current;

    @BeforeEach void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:current-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE event(id UUID PRIMARY KEY,status VARCHAR(24))");
        jdbc.execute("CREATE TABLE entity(id UUID PRIMARY KEY,entity_type VARCHAR(24),active BOOLEAN)");
        jdbc.execute("CREATE TABLE event_entity(event_id UUID,entity_id UUID)");
        jdbc.execute("CREATE TABLE event_evidence(event_id UUID,evidence_id UUID)");
        jdbc.execute("CREATE TABLE evidence(id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE assessment(id UUID PRIMARY KEY,event_id UUID,analysis_version VARCHAR(64),method VARCHAR(24),confidence VARCHAR(16),uncertainty VARCHAR(255),completed_at TIMESTAMP WITH TIME ZONE,supersedes_assessment_id UUID,status VARCHAR(24))");
        jdbc.execute("CREATE TABLE assessment_evidence(assessment_id UUID,evidence_id UUID)");
        historical = new HistoricalAssessmentQuery(jdbc);
        current = new CurrentAssessmentQuery(jdbc, historical);
    }

    @Test void uniqueTerminalWinsEvenWhenPredecessorHasLaterTimestampAndHistoricalIdStaysExact() {
        UUID event = eligibleEvent();
        UUID old = UUID.randomUUID(), terminal = UUID.randomUUID();
        addAssessment(old, event, null, "2030-01-01T00:00:00Z");
        addAssessment(terminal, event, old, "2020-01-01T00:00:00Z");
        assertEquals(terminal, current.find(event).assessmentId());
        assertEquals(old, historical.find(old).assessmentId());
    }

    @Test void forkHasNoCurrentEvenWithDifferentTimestamps() {
        UUID event = eligibleEvent();
        UUID root = UUID.randomUUID();
        addAssessment(root, event, null, "2020-01-01T00:00:00Z");
        addAssessment(UUID.randomUUID(), event, root, "2021-01-01T00:00:00Z");
        addAssessment(UUID.randomUUID(), event, root, "2030-01-01T00:00:00Z");
        assertThrows(HistoricalAssessmentNotFoundException.class, () -> current.find(event));
    }

    private UUID eligibleEvent() {
        UUID event = UUID.randomUUID(), entity = UUID.randomUUID(), evidence = UUID.randomUUID();
        jdbc.update("INSERT INTO event VALUES(?,'CONFIRMED')", event);
        jdbc.update("INSERT INTO entity VALUES(?,'COMPANY',true)", entity);
        jdbc.update("INSERT INTO event_entity VALUES(?,?)", event, entity);
        jdbc.update("INSERT INTO evidence VALUES(?)", evidence);
        jdbc.update("INSERT INTO event_evidence VALUES(?,?)", event, evidence);
        return event;
    }
    private void addAssessment(UUID id, UUID event, UUID predecessor, String completedAt) {
        jdbc.update("INSERT INTO assessment VALUES(?,?,'v1','RULE','MEDIUM','uncertainty',?,?,'COMPLETED')",
                id, event, java.time.OffsetDateTime.parse(completedAt), predecessor);
        UUID evidence = jdbc.queryForObject("SELECT evidence_id FROM event_evidence WHERE event_id=?", UUID.class, event);
        jdbc.update("INSERT INTO assessment_evidence VALUES(?,?)", id, evidence);
    }
}
