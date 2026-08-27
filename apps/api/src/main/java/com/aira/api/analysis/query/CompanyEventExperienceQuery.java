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
            SELECT ev.id, ev.event_type, ev.title, ev.occurred_at, ev.status,
                   e.id, s.name, e.external_id, e.original_url, e.title
            FROM event ev
            JOIN event_entity ee ON ee.event_id = ev.id AND ee.entity_id = ?
            JOIN event_evidence eve ON eve.event_id = ev.id
            JOIN evidence e ON e.id = eve.evidence_id
            JOIN source s ON s.id = e.source_id
            WHERE ev.status = 'CONFIRMED'
            ORDER BY ev.occurred_at DESC, ev.id, e.id
            """;
    static final String CURRENT_ASSESSMENT_SQL = """
            SELECT a.id, a.event_id, a.supersedes_assessment_id, predecessor.event_id,
                   a.importance, a.summary, a.confidence, a.uncertainty,a.time_horizon, a.method
            FROM assessment a
            JOIN assessment_evidence ae ON ae.assessment_id = a.id AND ae.evidence_id = ?
            LEFT JOIN assessment predecessor ON predecessor.id = a.supersedes_assessment_id
            WHERE a.event_id = ? AND a.status = 'COMPLETED'
            """;

    private final JdbcTemplate jdbc;

    public CompanyEventExperienceQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public CompanyEventExperienceResponse find(UUID companyId) {
        List<EventExperience> events = jdbc.query(EVENT_SQL, (rs, row) -> {
            UUID eventId = rs.getObject(1, UUID.class);
            UUID evidenceId = rs.getObject(6, UUID.class);
            AssessmentExperience assessment = selectCurrentAssessment(jdbc.query(
                    CURRENT_ASSESSMENT_SQL, (assessmentRs, assessmentRow) ->
                            new AssessmentCandidate(assessmentRs.getObject(1, UUID.class),
                                    assessmentRs.getObject(2, UUID.class),
                                    assessmentRs.getObject(3, UUID.class),
                                    assessmentRs.getObject(4, UUID.class),
                                    new AssessmentExperience(assessmentRs.getString(5),
                                            assessmentRs.getString(6), assessmentRs.getString(7),
                                            assessmentRs.getString(8), assessmentRs.getString(9),
                                            assessmentRs.getString(10))), evidenceId, eventId));
            return new EventExperience(eventId, rs.getString(2), rs.getString(3),
                    rs.getObject(4, java.time.OffsetDateTime.class), rs.getString(5), assessment,
                    new EvidenceExperience(rs.getString(7), rs.getString(8),
                            rs.getString(9), rs.getString(10)));
        }, companyId);
        return new CompanyEventExperienceResponse(companyId, events);
    }

    static AssessmentExperience selectCurrentAssessment(List<AssessmentCandidate> candidates) {
        AssessmentCandidate terminal = TerminalAssessmentSelector.select(candidates);
        return terminal == null ? null : terminal.assessment();
    }

    record AssessmentCandidate(UUID assessmentId, UUID eventId, UUID supersedesAssessmentId,
            UUID predecessorEventId, AssessmentExperience assessment)
            implements TerminalAssessmentSelector.Candidate {}
}
