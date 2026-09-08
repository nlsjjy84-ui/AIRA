package com.aira.api.analysis.query;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class HistoricalAssessmentQueryTests {
    private JdbcTemplate jdbc;
    private HistoricalAssessmentQuery query;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:historical-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE event(id UUID PRIMARY KEY,status VARCHAR(24))");
        jdbc.execute("CREATE TABLE entity(id UUID PRIMARY KEY,entity_type VARCHAR(24),active BOOLEAN)");
        jdbc.execute("CREATE TABLE event_entity(event_id UUID,entity_id UUID)");
        jdbc.execute("CREATE TABLE event_evidence(event_id UUID,evidence_id UUID)");
        jdbc.execute("CREATE TABLE evidence(id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE assessment(id UUID PRIMARY KEY,event_id UUID,analysis_version VARCHAR(64),method VARCHAR(24),confidence VARCHAR(16),uncertainty VARCHAR(255),completed_at TIMESTAMP WITH TIME ZONE,supersedes_assessment_id UUID,status VARCHAR(24))");
        jdbc.execute("CREATE TABLE assessment_evidence(assessment_id UUID,evidence_id UUID)");
        query = new HistoricalAssessmentQuery(jdbc);
    }

    @Test
    void returnsTheExactAssessmentWithoutSelectingItsSuccessor() {
        UUID event = UUID.randomUUID();
        UUID predecessor = UUID.randomUUID();
        UUID successor = UUID.randomUUID();
        UUID evidence = UUID.randomUUID();
        addEligibleEvent(event, evidence);
        addAssessment(predecessor, event, null, evidence);
        addAssessment(successor, event, predecessor, evidence);

        var result = query.find(predecessor);

        assertEquals(predecessor, result.assessmentId());
        assertEquals(event, result.eventId());
        assertNull(result.supersedesAssessmentId());
        assertEquals(java.util.List.of(evidence), result.evidenceIds());
    }

    @Test
    void rejectsIncompleteOrNonCompletedAssessment() {
        assertThrows(HistoricalAssessmentNotFoundException.class,
                () -> query.find(UUID.randomUUID()));
    }

    private void addEligibleEvent(UUID event, UUID evidence) {
        jdbc.update("INSERT INTO event VALUES(?,'CONFIRMED')", event);
        UUID entity = UUID.randomUUID();
        jdbc.update("INSERT INTO entity VALUES(?,'COMPANY',true)", entity);
        jdbc.update("INSERT INTO event_entity VALUES(?,?)", event, entity);
        jdbc.update("INSERT INTO evidence VALUES(?)", evidence);
        jdbc.update("INSERT INTO event_evidence VALUES(?,?)", event, evidence);
    }

    private void addAssessment(UUID id, UUID event, UUID supersedes, UUID evidence) {
        jdbc.update("INSERT INTO assessment VALUES(?,?,'v1','RULE','MEDIUM','uncertainty',CURRENT_TIMESTAMP,?,'COMPLETED')",
                id, event, supersedes);
        jdbc.update("INSERT INTO assessment_evidence VALUES(?,?)", id, evidence);
    }
}
