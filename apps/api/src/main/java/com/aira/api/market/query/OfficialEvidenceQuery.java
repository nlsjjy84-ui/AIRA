package com.aira.api.market.query;

import com.aira.api.market.dto.OfficialEvidenceResponse;
import com.aira.api.market.dto.OfficialEvidenceResponse.Source;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfficialEvidenceQuery {
    static final String EVIDENCE_SQL = """
            SELECT e.id,e.evidence_type,e.external_id,e.title,e.original_url,e.published_at,
                   e.collected_at,e.revision,e.locator,e.excerpt,
                   s.id,s.name,s.source_type,s.canonical_domain
            FROM evidence e JOIN source s ON s.id=e.source_id WHERE e.id=?
            """;
    static final String FACT_REACHABLE_SQL = """
            SELECT count(*) FROM fact_assertion fa JOIN fact f ON f.id=fa.fact_id
            JOIN entity en ON en.id=f.subject_entity_id
            WHERE fa.evidence_id=? AND f.status='SUPPORTED' AND f.value_number IS NOT NULL
              AND ((en.entity_type='COMPANY' AND f.predicate IN ('REVENUE','OPERATING_INCOME'))
                   OR (en.entity_type='MARKET' AND f.predicate IN ('INDEX_CLOSE','INDEX_CHANGE','INDEX_CHANGE_RATE'))
                   OR (en.entity_type='COUNTRY' AND f.predicate='REAL_GDP'))
            """;
    static final String EVENT_REACHABLE_SQL = """
            SELECT count(*) FROM event_evidence eve JOIN event ev ON ev.id=eve.event_id
            WHERE eve.evidence_id=? AND ev.status='CONFIRMED'
              AND EXISTS (SELECT 1 FROM event_evidence required WHERE required.event_id=ev.id)
              AND EXISTS (SELECT 1 FROM event_entity ee JOIN entity en ON en.id=ee.entity_id
                          WHERE ee.event_id=ev.id AND en.entity_type='COMPANY' AND en.active=true)
            """;
    static final String HISTORICAL_ASSESSMENT_REACHABLE_SQL = """
            SELECT count(*) FROM assessment_evidence ae
            JOIN assessment a ON a.id=ae.assessment_id
            JOIN event ev ON ev.id=a.event_id
            WHERE ae.evidence_id=? AND a.status='COMPLETED' AND ev.status='CONFIRMED'
              AND EXISTS (SELECT 1 FROM event_evidence eve WHERE eve.event_id=ev.id)
              AND EXISTS (SELECT 1 FROM event_entity ee JOIN entity en ON en.id=ee.entity_id
                          WHERE ee.event_id=ev.id AND en.entity_type='COMPANY' AND en.active=true)
            """;

    private final JdbcTemplate jdbc;

    public OfficialEvidenceQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public OfficialEvidenceResponse find(UUID evidenceId) {
        if (!isReachable(evidenceId)) throw new OfficialEvidenceNotFoundException();
        List<OfficialEvidenceResponse> evidence = jdbc.query(EVIDENCE_SQL, (rs, row) ->
                new OfficialEvidenceResponse(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getObject(6, OffsetDateTime.class),
                        rs.getObject(7, OffsetDateTime.class), rs.getInt(8), rs.getString(9),
                        rs.getString(10), new Source(rs.getObject(11, UUID.class), rs.getString(12),
                                rs.getString(13), rs.getString(14))), evidenceId);
        if (evidence.isEmpty()) throw new OfficialEvidenceNotFoundException();
        return evidence.getFirst();
    }

    private boolean isReachable(UUID evidenceId) {
        if (count(FACT_REACHABLE_SQL, evidenceId) > 0 || count(EVENT_REACHABLE_SQL, evidenceId) > 0) {
            return true;
        }
        // HistoricalAssessmentQuery exposes these exact IDs even after supersession.
        // Their original evidence must remain inspectable without selecting Current.
        return count(HISTORICAL_ASSESSMENT_REACHABLE_SQL, evidenceId) > 0;
    }

    private int count(String sql, Object... arguments) {
        Integer count = jdbc.queryForObject(sql, Integer.class, arguments);
        return count == null ? 0 : count;
    }

}
