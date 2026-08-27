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

class OfficialEvidenceQueryTests {
    private static final UUID SOURCE = id(1);
    private static final UUID COMPANY = id(2);
    private JdbcTemplate jdbc;
    private OfficialEvidenceQuery query;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:official-evidence-"
                + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE source(id UUID PRIMARY KEY,name VARCHAR(200),source_type VARCHAR(32),canonical_domain VARCHAR(255))");
        jdbc.execute("CREATE TABLE evidence(id UUID PRIMARY KEY,source_id UUID,evidence_type VARCHAR(32),external_id VARCHAR(255),title VARCHAR(500),original_url VARCHAR(1000),published_at TIMESTAMP WITH TIME ZONE,collected_at TIMESTAMP WITH TIME ZONE,revision INT,locator VARCHAR(500),excerpt VARCHAR(1000))");
        jdbc.execute("CREATE TABLE entity(id UUID PRIMARY KEY,entity_type VARCHAR(32),active BOOLEAN)");
        jdbc.execute("CREATE TABLE fact(id UUID PRIMARY KEY,subject_entity_id UUID,predicate VARCHAR(64),status VARCHAR(32),value_number NUMERIC)");
        jdbc.execute("CREATE TABLE fact_assertion(fact_id UUID,evidence_id UUID)");
        jdbc.execute("CREATE TABLE event(id UUID PRIMARY KEY,status VARCHAR(32))");
        jdbc.execute("CREATE TABLE event_entity(event_id UUID,entity_id UUID)");
        jdbc.execute("CREATE TABLE event_evidence(event_id UUID,evidence_id UUID)");
        jdbc.execute("CREATE TABLE assessment(id UUID PRIMARY KEY,event_id UUID,supersedes_assessment_id UUID,status VARCHAR(24))");
        jdbc.execute("CREATE TABLE assessment_evidence(assessment_id UUID,evidence_id UUID)");
        jdbc.update("INSERT INTO source VALUES(?,?,'REGULATORY_FILING','official.example')",
                SOURCE, "Official Registry");
        jdbc.update("INSERT INTO entity VALUES(?,'COMPANY',true)", COMPANY);
        query = new OfficialEvidenceQuery(jdbc);
    }

    @Test
    void returnsCanonicalStoredEvidenceMetadataWhenReachableFromPublicFact() {
        UUID evidence = addEvidence(id(10), "DOC-10", "stored://document/10");
        UUID fact = id(20);
        jdbc.update("INSERT INTO fact VALUES(?,?,'REVENUE','SUPPORTED',100)", fact, COMPANY);
        jdbc.update("INSERT INTO fact_assertion VALUES(?,?)", fact, evidence);

        var detail = query.find(evidence);

        assertEquals(evidence, detail.evidenceId());
        assertEquals("DOC-10", detail.externalId());
        assertEquals("Stored title DOC-10", detail.title());
        assertEquals("stored://document/10", detail.originalUrl());
        assertEquals(OffsetDateTime.parse("2026-08-20T00:00:00Z"), detail.publishedAt());
        assertEquals(OffsetDateTime.parse("2026-08-21T00:00:00Z"), detail.collectedAt());
        assertEquals(3, detail.revision());
        assertEquals("Official Registry", detail.source().sourceName());
        assertEquals("REGULATORY_FILING", detail.source().sourceType());
        assertEquals("official.example", detail.source().canonicalDomain());
    }

    @Test
    void returnsEvidenceReachableFromConfirmedPublicEvent() {
        UUID evidence = addEvidence(id(11), "EVENT-11", "stored://event/11");
        UUID event = publicEvent(id(30));
        jdbc.update("INSERT INTO event_evidence VALUES(?,?)", event, evidence);
        assertEquals(evidence, query.find(evidence).evidenceId());
    }

    @Test
    void returnsOnlyEvidenceUsedByUniqueCurrentAssessmentOfPublicEvent() {
        UUID directEventEvidence = addEvidence(id(12), "EVENT-12", "stored://event/12");
        UUID assessmentEvidence = addEvidence(id(13), "ASSESS-13", "stored://assessment/13");
        UUID predecessorEvidence = addEvidence(id(14), "OLD-14", "stored://assessment/14");
        UUID event = publicEvent(id(31));
        jdbc.update("INSERT INTO event_evidence VALUES(?,?)", event, directEventEvidence);
        UUID predecessor = addAssessment(id(40), event, null);
        UUID terminal = addAssessment(id(41), event, predecessor);
        jdbc.update("INSERT INTO assessment_evidence VALUES(?,?)", predecessor, predecessorEvidence);
        jdbc.update("INSERT INTO assessment_evidence VALUES(?,?)", terminal, assessmentEvidence);

        assertEquals(assessmentEvidence, query.find(assessmentEvidence).evidenceId());
        assertThrows(OfficialEvidenceNotFoundException.class,
                () -> query.find(predecessorEvidence));
    }

    @Test
    void ambiguousAssessmentTerminalsDoNotCreatePublicReachability() {
        UUID direct = addEvidence(id(15), "EVENT-15", "stored://event/15");
        UUID internal = addEvidence(id(16), "AMBIG-16", "stored://assessment/16");
        UUID event = publicEvent(id(32));
        jdbc.update("INSERT INTO event_evidence VALUES(?,?)", event, direct);
        UUID first = addAssessment(id(42), event, null);
        addAssessment(id(43), event, null);
        jdbc.update("INSERT INTO assessment_evidence VALUES(?,?)", first, internal);
        assertThrows(OfficialEvidenceNotFoundException.class, () -> query.find(internal));
    }

    @Test
    void internalAndMissingEvidenceAreIndistinguishableNotFound() {
        UUID internal = addEvidence(id(17), "INTERNAL-17", "stored://internal/17");
        assertThrows(OfficialEvidenceNotFoundException.class, () -> query.find(internal));
        assertThrows(OfficialEvidenceNotFoundException.class, () -> query.find(id(999)));
    }

    @Test
    void nullOriginalUrlDoesNotHideOtherwisePublicEvidence() {
        UUID evidence = addEvidence(id(18), "NO-URL-18", null);
        UUID event = publicEvent(id(33));
        jdbc.update("INSERT INTO event_evidence VALUES(?,?)", event, evidence);
        assertNull(query.find(evidence).originalUrl());
    }

    @Test
    void repeatedPublicRelationsResolveToOneCanonicalEvidenceIdentity() {
        UUID evidence = addEvidence(id(19), "SHARED-19", "stored://shared/19");
        UUID event = publicEvent(id(34));
        jdbc.update("INSERT INTO event_evidence VALUES(?,?)", event, evidence);
        UUID fact = id(21);
        jdbc.update("INSERT INTO fact VALUES(?,?,'OPERATING_INCOME','SUPPORTED',200)", fact, COMPANY);
        jdbc.update("INSERT INTO fact_assertion VALUES(?,?)", fact, evidence);
        assertEquals(evidence, query.find(evidence).evidenceId());
    }

    private UUID addEvidence(UUID id, String externalId, String originalUrl) {
        jdbc.update("INSERT INTO evidence VALUES(?,?,'DISCLOSURE',?,?,?,'2026-08-20T00:00:00Z','2026-08-21T00:00:00Z',3,'section-1','stored excerpt')",
                id, SOURCE, externalId, "Stored title " + externalId, originalUrl);
        return id;
    }

    private UUID publicEvent(UUID id) {
        jdbc.update("INSERT INTO event VALUES(?,'CONFIRMED')", id);
        jdbc.update("INSERT INTO event_entity VALUES(?,?)", id, COMPANY);
        return id;
    }

    private UUID addAssessment(UUID id, UUID eventId, UUID supersedes) {
        jdbc.update("INSERT INTO assessment VALUES(?,?,?,'COMPLETED')", id, eventId, supersedes);
        return id;
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }
}
