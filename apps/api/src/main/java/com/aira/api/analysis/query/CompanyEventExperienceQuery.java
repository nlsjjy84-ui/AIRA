package com.aira.api.analysis.query;

import com.aira.api.analysis.dto.CompanyEventExperienceResponse;
import com.aira.api.analysis.dto.CompanyEventExperienceResponse.AssessmentExperience;
import com.aira.api.analysis.dto.CompanyEventExperienceResponse.EventExperience;
import com.aira.api.analysis.dto.CompanyEventExperienceResponse.EvidenceExperience;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyEventExperienceQuery {
    static final String EVENT_SQL = """
            SELECT ev.id, ev.event_type, ev.title, ev.occurred_at, ev.status
            FROM event ev
            WHERE ev.status = 'CONFIRMED'
              AND EXISTS (SELECT 1 FROM event_entity ee WHERE ee.event_id=ev.id AND ee.entity_id=?)
              AND EXISTS (SELECT 1 FROM event_evidence eve JOIN evidence e ON e.id=eve.evidence_id
                          JOIN source s ON s.id=e.source_id WHERE eve.event_id=ev.id)
            ORDER BY ev.occurred_at DESC NULLS LAST, ev.id
            """;
    static final String CURRENT_ASSESSMENT_SQL = """
            SELECT a.id, a.event_id, a.supersedes_assessment_id, predecessor.event_id,
                   a.importance, a.summary, a.confidence, a.uncertainty,a.time_horizon, a.method,
                   EXISTS (SELECT 1 FROM assessment_evidence ae
                           JOIN evidence evidence ON evidence.id=ae.evidence_id
                           JOIN source source ON source.id=evidence.source_id
                           WHERE ae.assessment_id=a.id)
            FROM assessment a
            LEFT JOIN assessment predecessor ON predecessor.id = a.supersedes_assessment_id
            WHERE a.event_id = ? AND a.status = 'COMPLETED'
            """;

    private final JdbcTemplate jdbc;

    public CompanyEventExperienceQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public CompanyEventExperienceResponse find(UUID companyId) {
        List<EventExperience> events = jdbc.query(EVENT_SQL, (rs, row) -> {
            UUID eventId = rs.getObject(1, UUID.class);
            AssessmentExperience assessment = selectCurrentAssessment(jdbc.query(
                    CURRENT_ASSESSMENT_SQL, (assessmentRs, assessmentRow) ->
                            new AssessmentCandidate(assessmentRs.getObject(1, UUID.class),
                                    assessmentRs.getObject(2, UUID.class),
                                    assessmentRs.getObject(3, UUID.class),
                                    assessmentRs.getObject(4, UUID.class),
                                    new AssessmentExperience(assessmentRs.getString(5),
                                            assessmentRs.getString(6), assessmentRs.getString(7),
                                            assessmentRs.getString(8), assessmentRs.getString(9),
                                            assessmentRs.getString(10)),
                                    assessmentRs.getBoolean(11)), eventId));
            return new EventExperience(eventId, rs.getString(2), rs.getString(3),
                    rs.getObject(4, java.time.OffsetDateTime.class), rs.getString(5), assessment,
                    jdbc.query("""
                            SELECT e.id,s.name,e.external_id,e.original_url,e.title,e.revision
                            FROM event_evidence eve JOIN evidence e ON e.id=eve.evidence_id
                            JOIN source s ON s.id=e.source_id WHERE eve.event_id=? ORDER BY e.id
                            """, (e, index) -> new EvidenceExperience(e.getObject(1, UUID.class),
                            e.getString(2), e.getString(3), e.getString(4), e.getString(5), e.getInt(6)), eventId));
        }, companyId);
        return new CompanyEventExperienceResponse(companyId, events);
    }

    static AssessmentExperience selectCurrentAssessment(List<AssessmentCandidate> candidates) {
        AssessmentCandidate terminal = TerminalAssessmentSelector.select(candidates);
        return terminal == null || !terminal.evidenceBacked() ? null : terminal.assessment();
    }

    record AssessmentCandidate(UUID assessmentId, UUID eventId, UUID supersedesAssessmentId,
            UUID predecessorEventId, AssessmentExperience assessment, boolean evidenceBacked)
            implements TerminalAssessmentSelector.Candidate {}
}
