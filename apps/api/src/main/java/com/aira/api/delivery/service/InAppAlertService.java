package com.aira.api.delivery.service;

import com.aira.api.delivery.dto.AlertResponse;
import com.aira.api.delivery.dto.AlertResponse.Item;
import com.aira.api.delivery.dto.RelatedCompany;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InAppAlertService {
    static final String POLICY = "interest-new-event-v1";
    static final String CANDIDATE_SQL = """
            WITH interested_event AS (
                SELECT ev.id AS event_id,MIN(ui.updated_at) AS activated_at
                FROM user_interest ui
                JOIN event_entity ee ON ee.entity_id=ui.entity_id
                JOIN event ev ON ev.id=ee.event_id AND ev.status='CONFIRMED'
                WHERE ui.user_id=? AND ui.alert_enabled=true
                  AND NOT EXISTS (
                      SELECT 1 FROM alert existing
                      JOIN assessment existing_assessment
                        ON existing_assessment.id=existing.assessment_id
                      WHERE existing.user_id=ui.user_id
                        AND existing_assessment.event_id=ev.id
                  )
                GROUP BY ev.id
            )
            SELECT ie.event_id,a.id,a.supersedes_assessment_id,predecessor.event_id,
                   a.completed_at>=ie.activated_at,
                   EXISTS (
                       SELECT 1 FROM assessment_evidence ae
                       JOIN evidence e ON e.id=ae.evidence_id
                       JOIN source s ON s.id=e.source_id
                       WHERE ae.assessment_id=a.id
                   ) AS evidence_backed
            FROM interested_event ie
            JOIN assessment a ON a.event_id=ie.event_id AND a.status='COMPLETED'
            LEFT JOIN assessment predecessor ON predecessor.id=a.supersedes_assessment_id
            ORDER BY ie.event_id,a.id
            """;
    static final String USER_VISIBLE_SQL = """
            SELECT alert_id,event_id,event_title,event_type,
                   occurred_at,assessment_id,summary,uncertainty,source_name,evidence_external_id,
                   evidence_original_url,created_at,sent_at
            FROM (
                SELECT DISTINCT ON (al.id) al.id AS alert_id,ev.id AS event_id,
                       ev.title AS event_title,ev.event_type,ev.occurred_at,
                       a.id AS assessment_id,a.summary,a.uncertainty,s.name AS source_name,
                       e.external_id AS evidence_external_id,
                       e.original_url AS evidence_original_url,al.created_at,al.sent_at,e.id AS evidence_id
                FROM alert al JOIN assessment a ON a.id=al.assessment_id
                JOIN event ev ON ev.id=a.event_id
                JOIN assessment_evidence ae ON ae.assessment_id=a.id
                JOIN evidence e ON e.id=ae.evidence_id JOIN source s ON s.id=e.source_id
                WHERE al.user_id=? AND al.status='SENT'
                ORDER BY al.id,e.id
            ) visible
            ORDER BY sent_at DESC,alert_id ASC
            """;
    static final String COMPANIES_SQL = """
            SELECT en.id,en.canonical_name FROM event_entity ee
            JOIN entity en ON en.id=ee.entity_id
            WHERE ee.event_id=? AND en.entity_type='COMPANY' AND en.active=true
            ORDER BY en.id ASC
            """;

    private final JdbcTemplate jdbc;
    public InAppAlertService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public AlertResponse reconcile(UUID userId) {
        List<Candidate> candidates = selectCandidates(jdbc.query(CANDIDATE_SQL,
                (rs,row)->new AssessmentCandidate(rs.getObject(1,UUID.class),
                        rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),
                        rs.getObject(4,UUID.class),rs.getBoolean(5),rs.getBoolean(6)), userId));
        for (Candidate candidate : candidates) {
            jdbc.update("""
                    INSERT INTO alert(id,user_id,assessment_id,policy_version,reason_code,dedup_key,status,
                    sent_at,created_at,updated_at) VALUES(?,?,?,?,'NEW_ASSESSMENT',?,'SENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id,dedup_key) DO NOTHING
                    """, UUID.randomUUID(), userId, candidate.assessmentId(), POLICY,
                    digest(candidate.eventId()));
        }
        return findAll(userId);
    }

    static List<Candidate> selectCandidates(List<AssessmentCandidate> assessments) {
        Map<UUID, List<AssessmentCandidate>> byEvent = assessments.stream().distinct().collect(
                Collectors.groupingBy(AssessmentCandidate::eventId, LinkedHashMap::new,
                        Collectors.toList()));
        return byEvent.entrySet().stream().map(entry -> {
            AssessmentCandidate terminal = com.aira.api.analysis.query.TerminalAssessmentSelector
                    .select(entry.getValue());
            return terminal != null && terminal.evidenceBacked()
                    && terminal.eligibleAfterActivation()
                    ? new Candidate(entry.getKey(), terminal.assessmentId()) : null;
        }).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional(readOnly=true)
    public AlertResponse findAll(UUID userId) {
        List<RawItem> rawItems = jdbc.query(USER_VISIBLE_SQL, (rs,row)->new RawItem(
                rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),
                rs.getString(4),rs.getObject(5,java.time.OffsetDateTime.class),
                rs.getObject(6,UUID.class),rs.getString(7),rs.getString(8),rs.getString(9),
                rs.getString(10),rs.getString(11),rs.getObject(12,java.time.OffsetDateTime.class),
                rs.getObject(13,java.time.OffsetDateTime.class)), userId);
        return new AlertResponse(rawItems.stream().map(raw -> new Item(raw.alertId(),
                companies(raw.eventId()), raw.eventId(), raw.eventTitle(), raw.eventType(),
                raw.occurredAt(), raw.assessmentId(), raw.summary(), raw.uncertainty(),
                raw.sourceName(), raw.evidenceExternalId(), raw.evidenceOriginalUrl(),
                raw.createdAt(), raw.sentAt())).toList());
    }

    private List<RelatedCompany> companies(UUID eventId) {
        return RelatedCompanyOrder.normalize(jdbc.query(COMPANIES_SQL, (rs, row) ->
                new RelatedCompany(rs.getObject(1, UUID.class), rs.getString(2)), eventId));
    }

    @Transactional(readOnly=true)
    public Item findOwned(UUID userId, UUID alertId) {
        return findAll(userId).alerts().stream().filter(item->item.alertId().equals(alertId)).findFirst()
                .orElseThrow(BriefingNotFoundException::new);
    }

    static byte[] digest(UUID eventId) {
        try { return MessageDigest.getInstance("SHA-256").digest(
                (POLICY+eventId).getBytes(StandardCharsets.UTF_8)); }
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }

    record AssessmentCandidate(UUID eventId, UUID assessmentId, UUID supersedesAssessmentId,
            UUID predecessorEventId, boolean eligibleAfterActivation, boolean evidenceBacked)
            implements com.aira.api.analysis.query.TerminalAssessmentSelector.Candidate {}
    record Candidate(UUID eventId, UUID assessmentId) {}
    private record RawItem(UUID alertId, UUID eventId, String eventTitle, String eventType,
            java.time.OffsetDateTime occurredAt, UUID assessmentId, String summary,
            String uncertainty, String sourceName, String evidenceExternalId,
            String evidenceOriginalUrl, java.time.OffsetDateTime createdAt,
            java.time.OffsetDateTime sentAt) {}
}
