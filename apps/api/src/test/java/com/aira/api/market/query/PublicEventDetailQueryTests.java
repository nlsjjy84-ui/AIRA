package com.aira.api.market.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PublicEventDetailQueryTests {
    private static final UUID EVENT = id(10);
    private static final UUID OTHER_EVENT = id(20);
    private static final UUID COMPANY_A = id(30);
    private static final UUID COMPANY_B = id(31);
    private JdbcTemplate jdbc;
    private PublicEventDetailQuery query;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:event-detail-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE entity(id UUID PRIMARY KEY,canonical_name VARCHAR(255),entity_type VARCHAR(32),active BOOLEAN)");
        jdbc.execute("CREATE TABLE event(id UUID PRIMARY KEY,event_type VARCHAR(64),title VARCHAR(500),occurred_at TIMESTAMP WITH TIME ZONE,status VARCHAR(32))");
        jdbc.execute("CREATE TABLE source(id UUID PRIMARY KEY,name VARCHAR(255))");
        jdbc.execute("CREATE TABLE evidence(id UUID PRIMARY KEY,source_id UUID,external_id VARCHAR(255),title VARCHAR(500),original_url VARCHAR(1000),published_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE event_entity(event_id UUID,entity_id UUID)");
        jdbc.execute("CREATE TABLE event_evidence(event_id UUID,evidence_id UUID)");
        jdbc.execute("CREATE TABLE assessment(id UUID PRIMARY KEY,event_id UUID,supersedes_assessment_id UUID,summary VARCHAR(500),uncertainty VARCHAR(500),confidence VARCHAR(16),importance VARCHAR(16),time_horizon VARCHAR(24),method VARCHAR(24),analysis_version VARCHAR(64),status VARCHAR(24))");
        jdbc.execute("CREATE TABLE assessment_evidence(assessment_id UUID,evidence_id UUID)");
        jdbc.update("INSERT INTO source VALUES(?,?)", id(40), "Official Source");
        addCompany(COMPANY_B, "회사 B", true);
        addCompany(COMPANY_A, "회사 A", true);
        addEvent(EVENT, "CONFIRMED", "Event factual title");
        addEvidence(id(50), "E-50", "stored://official/50");
        jdbc.update("INSERT INTO event_evidence VALUES(?,?)", EVENT, id(50));
        jdbc.update("INSERT INTO event_entity VALUES(?,?)", EVENT, COMPANY_B);
        jdbc.update("INSERT INTO event_entity VALUES(?,?)", EVENT, COMPANY_A);
        query = new PublicEventDetailQuery(jdbc);
    }

    @Test
    void returnsEligibleFactualEventAllCompaniesAndAllDirectEvidenceDeterministically() {
        addEvidence(id(51), "E-51", "stored://official/51");
        jdbc.update("INSERT INTO event_evidence VALUES(?,?)", EVENT, id(51));

        var detail = query.find(EVENT);

        assertEquals("Event factual title", detail.title());
        assertEquals(java.util.List.of(COMPANY_A, COMPANY_B),
                detail.companies().stream().map(company -> company.companyId()).toList());
        assertEquals(java.util.List.of(id(50), id(51)),
                detail.eventEvidence().stream().map(evidence -> evidence.evidenceId()).toList());
        assertEquals("stored://official/51", detail.eventEvidence().get(1).originalUrl());
        assertNull(detail.assessment());
    }

    @Test
    void hidesCandidateMissingAndOtherwiseIneligibleEventsBehindNotFound() {
        UUID candidate = id(11);
        addEvent(candidate, "CANDIDATE", "internal candidate");
        jdbc.update("INSERT INTO event_entity VALUES(?,?)", candidate, COMPANY_A);
        jdbc.update("INSERT INTO event_evidence VALUES(?,?)", candidate, id(50));
        assertThrows(PublicEventNotFoundException.class, () -> query.find(candidate));
        assertThrows(PublicEventNotFoundException.class, () -> query.find(id(999)));

        UUID noEvidence = id(12);
        addEvent(noEvidence, "CONFIRMED", "no evidence");
        jdbc.update("INSERT INTO event_entity VALUES(?,?)", noEvidence, COMPANY_A);
        assertThrows(PublicEventNotFoundException.class, () -> query.find(noEvidence));

        UUID inactiveCompany = id(32);
        addCompany(inactiveCompany, "inactive", false);
        UUID inactiveOnly = id(13);
        addEvent(inactiveOnly, "CONFIRMED", "inactive company only");
        jdbc.update("INSERT INTO event_entity VALUES(?,?)", inactiveOnly, inactiveCompany);
        jdbc.update("INSERT INTO event_evidence VALUES(?,?)", inactiveOnly, id(50));
        assertThrows(PublicEventNotFoundException.class, () -> query.find(inactiveOnly));
    }

    @Test
    void exposesUniqueTerminalAssessmentAndOnlyItsRelatedEvidence() {
        UUID predecessor = id(60);
        UUID terminal = id(61);
        addAssessment(predecessor, EVENT, null, "predecessor");
        addAssessment(terminal, EVENT, predecessor, "terminal");
        addEvidence(id(52), "A-52", "stored://assessment/52");
        jdbc.update("INSERT INTO assessment_evidence VALUES(?,?)", terminal, id(52));

        var detail = query.find(EVENT);

        assertEquals(terminal, detail.assessment().assessmentId());
        assertEquals("terminal", detail.assessment().summary());
        assertEquals(java.util.List.of(id(52)), detail.assessment().evidence().stream()
                .map(evidence -> evidence.evidenceId()).toList());
        assertEquals(java.util.List.of(id(50)), detail.eventEvidence().stream()
                .map(evidence -> evidence.evidenceId()).toList());
    }

    @Test
    void leavesAssessmentAbsentForMultipleTerminalsWithoutChoosingByUuid() {
        addAssessment(id(60), EVENT, null, "first");
        addAssessment(id(61), EVENT, null, "second");
        assertNull(query.find(EVENT).assessment());
    }

    @Test
    void leavesAssessmentAbsentForCrossEventSupersession() {
        addEvent(OTHER_EVENT, "CONFIRMED", "other");
        addAssessment(id(60), OTHER_EVENT, null, "other predecessor");
        addAssessment(id(61), EVENT, id(60), "invalid successor");
        assertNull(query.find(EVENT).assessment());
    }

    @Test
    void permitsSameEvidenceIdentityInBothRolesOnlyWhenBothRelationsExist() {
        addAssessment(id(60), EVENT, null, "current");
        jdbc.update("INSERT INTO assessment_evidence VALUES(?,?)", id(60), id(50));
        var detail = query.find(EVENT);
        assertEquals(id(50), detail.eventEvidence().getFirst().evidenceId());
        assertEquals(id(50), detail.assessment().evidence().getFirst().evidenceId());
    }

    private void addCompany(UUID id, String name, boolean active) {
        jdbc.update("INSERT INTO entity VALUES(?,?,'COMPANY',?)", id, name, active);
    }

    private void addEvent(UUID id, String status, String title) {
        jdbc.update("INSERT INTO event VALUES(?,'EARNINGS',?,'2026-08-25T00:00:00Z',?)",
                id, title, status);
    }

    private void addEvidence(UUID id, String externalId, String originalUrl) {
        jdbc.update("INSERT INTO evidence VALUES(?,?,?,?,?,?)", id, PublicEventDetailQueryTests.id(40),
                externalId, "Evidence " + externalId, originalUrl,
                OffsetDateTime.parse("2026-08-24T00:00:00Z"));
    }

    private void addAssessment(UUID id, UUID eventId, UUID supersedes, String summary) {
        jdbc.update("INSERT INTO assessment VALUES(?,?,?,?,?,'MEDIUM','HIGH','UNSPECIFIED','RULE','v1','COMPLETED')",
                id, eventId, supersedes, summary, "uncertainty");
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }
}
