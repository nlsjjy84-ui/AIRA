package com.aira.api.analysis.service;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import com.aira.api.market.repository.EventRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfficialEventAssessmentPreparationOperation {
    private final JdbcTemplate jdbc;
    private final RuleBasedEarningsAssessmentService assessments;
    private final EventRepository events;

    public OfficialEventAssessmentPreparationOperation(JdbcTemplate jdbc,
            RuleBasedEarningsAssessmentService assessments, EventRepository events) {
        this.jdbc = jdbc;
        this.assessments = assessments;
        this.events = events;
    }

    @Transactional
    public List<PreparedAssessment> prepare() {
        List<EventEvidencePair> pairs = jdbc.query("""
                SELECT DISTINCT ev.id, e.id, en.id, en.canonical_name
                FROM event ev
                JOIN event_entity ee ON ee.event_id = ev.id
                JOIN entity en ON en.id = ee.entity_id AND en.entity_type = 'COMPANY'
                JOIN event_evidence eve ON eve.event_id = ev.id
                JOIN evidence e ON e.id = eve.evidence_id
                JOIN source s ON s.id = e.source_id
                WHERE ev.event_type = 'EARNINGS'
                  AND ev.status IN ('CANDIDATE', 'CONFIRMED')
                  AND s.external_key = 'opendart'
                  AND e.status = 'ACTIVE'
                ORDER BY en.canonical_name
                """, (rs, row) -> new EventEvidencePair(rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getString(4)));
        return pairs.stream().map(pair -> {
            events.findById(pair.eventId()).orElseThrow().describeOfficialAnnualFiling(
                    pair.companyName(), OffsetDateTime.now(ZoneOffset.UTC));
            return new PreparedAssessment(
                    assessments.assess(pair.eventId(), pair.evidenceId()).getId(),
                    pair.eventId(), pair.evidenceId(), pair.companyId(), pair.companyName());
        }).toList();
    }

    private record EventEvidencePair(UUID eventId, UUID evidenceId,
            UUID companyId, String companyName) {}
    public record PreparedAssessment(UUID assessmentId, UUID eventId, UUID evidenceId,
            UUID companyId, String companyName) {}
}
