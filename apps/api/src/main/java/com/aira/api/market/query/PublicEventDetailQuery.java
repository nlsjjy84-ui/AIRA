package com.aira.api.market.query;

import com.aira.api.analysis.query.TerminalAssessmentSelector;
import com.aira.api.market.dto.PublicEventDetailResponse;
import com.aira.api.market.dto.PublicEventDetailResponse.Assessment;
import com.aira.api.market.dto.PublicEventDetailResponse.Company;
import com.aira.api.market.dto.PublicEventDetailResponse.Evidence;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicEventDetailQuery {
    static final String EVENT_SQL = """
            SELECT ev.id,ev.event_type,ev.title,ev.occurred_at
            FROM event ev
            WHERE ev.id=? AND ev.status='CONFIRMED'
              AND EXISTS (SELECT 1 FROM event_evidence eve WHERE eve.event_id=ev.id)
              AND EXISTS (SELECT 1 FROM event_entity ee JOIN entity en ON en.id=ee.entity_id
                          WHERE ee.event_id=ev.id AND en.entity_type='COMPANY' AND en.active=true)
            """;
    static final String COMPANY_SQL = """
            SELECT en.id,en.canonical_name FROM event_entity ee
            JOIN entity en ON en.id=ee.entity_id
            WHERE ee.event_id=? AND en.entity_type='COMPANY' AND en.active=true
            ORDER BY en.id ASC
            """;
    static final String EVENT_EVIDENCE_SQL = """
            SELECT e.id,s.name,e.external_id,e.title,e.original_url,e.published_at
            FROM event_evidence eve JOIN evidence e ON e.id=eve.evidence_id
            JOIN source s ON s.id=e.source_id WHERE eve.event_id=? ORDER BY e.id ASC
            """;
    static final String ASSESSMENT_SQL = """
            SELECT a.id,a.event_id,a.supersedes_assessment_id,predecessor.event_id,
                   a.summary,a.uncertainty,a.confidence,a.importance,a.time_horizon,a.method,
                   a.analysis_version
            FROM assessment a LEFT JOIN assessment predecessor ON predecessor.id=a.supersedes_assessment_id
            WHERE a.event_id=? AND a.status='COMPLETED'
            """;
    static final String ASSESSMENT_EVIDENCE_SQL = """
            SELECT e.id,s.name,e.external_id,e.title,e.original_url,e.published_at
            FROM assessment_evidence ae JOIN evidence e ON e.id=ae.evidence_id
            JOIN source s ON s.id=e.source_id WHERE ae.assessment_id=? ORDER BY e.id ASC
            """;

    private final JdbcTemplate jdbc;

    public PublicEventDetailQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PublicEventDetailResponse find(UUID eventId) {
        List<EventHeader> headers = jdbc.query(EVENT_SQL, (rs, row) -> new EventHeader(
                rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getObject(4, OffsetDateTime.class)), eventId);
        if (headers.isEmpty()) throw new PublicEventNotFoundException();
        EventHeader header = headers.getFirst();
        List<Company> companies = jdbc.query(COMPANY_SQL, (rs, row) ->
                new Company(rs.getObject(1, UUID.class), rs.getString(2)), eventId);
        List<Evidence> eventEvidence = evidence(EVENT_EVIDENCE_SQL, eventId);
        Assessment assessment = currentAssessment(eventId);
        return new PublicEventDetailResponse(header.eventId(), header.eventType(), header.title(),
                header.occurredAt(), companies, eventEvidence, assessment);
    }

    private Assessment currentAssessment(UUID eventId) {
        List<AssessmentCandidate> candidates = jdbc.query(ASSESSMENT_SQL, (rs, row) ->
                new AssessmentCandidate(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getString(10), rs.getString(11)), eventId);
        AssessmentCandidate current = TerminalAssessmentSelector.select(candidates);
        if (current == null) return null;
        List<Evidence> usedEvidence = evidence(ASSESSMENT_EVIDENCE_SQL, current.assessmentId());
        if (usedEvidence.isEmpty()) return null;
        return new Assessment(current.assessmentId(), current.summary(),
                current.uncertainty(), current.confidence(), current.importance(),
                current.timeHorizon(), current.method(), current.analysisVersion(),
                usedEvidence);
    }

    private List<Evidence> evidence(String sql, UUID ownerId) {
        return jdbc.query(sql, (rs, row) -> new Evidence(rs.getObject(1, UUID.class),
                rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getObject(6, OffsetDateTime.class)), ownerId);
    }

    private record EventHeader(UUID eventId, String eventType, String title,
            OffsetDateTime occurredAt) {}
    record AssessmentCandidate(UUID assessmentId, UUID eventId, UUID supersedesAssessmentId,
            UUID predecessorEventId, String summary, String uncertainty, String confidence,
            String importance, String timeHorizon, String method, String analysisVersion)
            implements TerminalAssessmentSelector.Candidate {}
}
