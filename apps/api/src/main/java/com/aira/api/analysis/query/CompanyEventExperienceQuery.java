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
    private final JdbcTemplate jdbc;

    public CompanyEventExperienceQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public CompanyEventExperienceResponse find(UUID companyId) {
        List<EventExperience> events = jdbc.query("""
                SELECT ev.id, ev.event_type, ev.title, ev.occurred_at, ev.status,
                       a.importance, a.summary, a.confidence, a.uncertainty,
                       a.time_horizon, a.method, s.name, e.external_id,
                       e.original_url, e.title
                FROM event ev
                JOIN event_entity ee ON ee.event_id = ev.id AND ee.entity_id = ?
                JOIN event_evidence eve ON eve.event_id = ev.id
                JOIN evidence e ON e.id = eve.evidence_id
                JOIN source s ON s.id = e.source_id
                JOIN assessment a ON a.event_id = ev.id AND a.status = 'COMPLETED'
                JOIN assessment_evidence ae ON ae.assessment_id = a.id
                                              AND ae.evidence_id = e.id
                WHERE ev.status IN ('CANDIDATE', 'CONFIRMED')
                ORDER BY ev.occurred_at DESC, ev.id
                """, (rs, row) -> new EventExperience(
                        rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getObject(4, java.time.OffsetDateTime.class), rs.getString(5),
                        new AssessmentExperience(rs.getString(6), rs.getString(7),
                                rs.getString(8), rs.getString(9), rs.getString(10),
                                rs.getString(11)),
                        new EvidenceExperience(rs.getString(12), rs.getString(13),
                                rs.getString(14), rs.getString(15))), companyId);
        return new CompanyEventExperienceResponse(companyId, events);
    }
}
