package com.aira.api.analysis.query;

import com.aira.api.analysis.dto.HistoricalAssessmentResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoricalAssessmentQuery {
    static final String ASSESSMENT_SQL = """
            SELECT a.id,a.event_id,a.analysis_version,a.method,a.confidence,a.uncertainty,
                   a.completed_at,a.supersedes_assessment_id
            FROM assessment a JOIN event ev ON ev.id=a.event_id
            WHERE a.id=? AND a.status='COMPLETED' AND ev.status='CONFIRMED'
              AND EXISTS (SELECT 1 FROM event_evidence eve WHERE eve.event_id=ev.id)
              AND EXISTS (SELECT 1 FROM event_entity ee JOIN entity en ON en.id=ee.entity_id
                          WHERE ee.event_id=ev.id AND en.entity_type='COMPANY' AND en.active=true)
              AND EXISTS (SELECT 1 FROM assessment_evidence ae WHERE ae.assessment_id=a.id)
            """;
    static final String EVIDENCE_SQL = """
            SELECT ae.evidence_id FROM assessment_evidence ae
            JOIN evidence e ON e.id=ae.evidence_id
            WHERE ae.assessment_id=? ORDER BY ae.evidence_id
            """;

    private final JdbcTemplate jdbc;

    public HistoricalAssessmentQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public HistoricalAssessmentResponse find(UUID assessmentId) {
        List<Header> matches = jdbc.query(ASSESSMENT_SQL, (rs, row) -> new Header(
                rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getObject(7, OffsetDateTime.class), rs.getObject(8, UUID.class)), assessmentId);
        if (matches.size() != 1) throw new HistoricalAssessmentNotFoundException();
        Header header = matches.getFirst();
        List<UUID> evidenceIds = jdbc.query(EVIDENCE_SQL,
                (rs, row) -> rs.getObject(1, UUID.class), assessmentId);
        if (evidenceIds.isEmpty()) throw new HistoricalAssessmentNotFoundException();
        return new HistoricalAssessmentResponse(header.assessmentId(), header.eventId(),
                header.analysisVersion(), header.method(), header.confidence(),
                header.uncertainty(), header.completedAt(), header.supersedesAssessmentId(),
                evidenceIds);
    }

    private record Header(UUID assessmentId, UUID eventId, String analysisVersion,
            String method, String confidence, String uncertainty, OffsetDateTime completedAt,
            UUID supersedesAssessmentId) {}
}
