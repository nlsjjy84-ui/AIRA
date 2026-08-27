package com.aira.api.delivery.service;

import com.aira.api.delivery.dto.AlertResponse;
import com.aira.api.delivery.dto.AlertResponse.Item;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                   a.completed_at>=ie.activated_at
            FROM interested_event ie
            JOIN assessment a ON a.event_id=ie.event_id AND a.status='COMPLETED'
            LEFT JOIN assessment predecessor ON predecessor.id=a.supersedes_assessment_id
            WHERE EXISTS (
                SELECT 1 FROM assessment_evidence ae
                JOIN evidence e ON e.id=ae.evidence_id
                JOIN source s ON s.id=e.source_id
                WHERE ae.assessment_id=a.id
              )
            ORDER BY ie.event_id,a.id
            """;
    static final String USER_VISIBLE_SQL = """
            SELECT alert_id,company_id,company_name,event_id,event_title,event_type,
                   occurred_at,assessment_id,summary,uncertainty,source_name,evidence_external_id,
                   evidence_original_url,created_at,sent_at
            FROM (
                SELECT DISTINCT ON (al.id) al.id AS alert_id,en.id AS company_id,
                       en.canonical_name AS company_name,ev.id AS event_id,
                       ev.title AS event_title,ev.event_type,ev.occurred_at,
                       a.id AS assessment_id,a.summary,a.uncertainty,s.name AS source_name,
                       e.external_id AS evidence_external_id,
                       e.original_url AS evidence_original_url,al.created_at,al.sent_at,e.id AS evidence_id
                FROM alert al JOIN assessment a ON a.id=al.assessment_id
                JOIN event ev ON ev.id=a.event_id JOIN event_entity ee ON ee.event_id=ev.id
                JOIN entity en ON en.id=ee.entity_id
                JOIN assessment_evidence ae ON ae.assessment_id=a.id
                JOIN evidence e ON e.id=ae.evidence_id JOIN source s ON s.id=e.source_id
                WHERE al.user_id=? AND al.status='SENT'
                ORDER BY al.id,e.id
            ) visible
            ORDER BY sent_at DESC,alert_id ASC
            """;

    private final JdbcTemplate jdbc;
    public InAppAlertService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public AlertResponse reconcile(UUID userId) {
        List<Candidate> candidates = selectCandidates(jdbc.query(CANDIDATE_SQL,
                (rs,row)->new AssessmentCandidate(rs.getObject(1,UUID.class),
                        rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),
                        rs.getObject(4,UUID.class),rs.getBoolean(5)), userId));
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
            List<AssessmentCandidate> eventAssessments = entry.getValue();
            if (eventAssessments.stream().anyMatch(candidate ->
                    candidate.supersedesAssessmentId() != null
                            && !candidate.eventId().equals(candidate.predecessorEventId()))) {
                return null;
            }
            Set<UUID> superseded = eventAssessments.stream()
                    .filter(candidate -> candidate.supersedesAssessmentId() != null)
                    .map(AssessmentCandidate::supersedesAssessmentId)
                    .collect(Collectors.toSet());
            List<AssessmentCandidate> terminal = eventAssessments.stream()
                    .filter(candidate -> !superseded.contains(candidate.assessmentId()))
                    .toList();
            return terminal.size() == 1 && terminal.getFirst().eligibleAfterActivation()
                    ? new Candidate(entry.getKey(), terminal.getFirst().assessmentId()) : null;
        }).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional(readOnly=true)
    public AlertResponse findAll(UUID userId) {
        return new AlertResponse(jdbc.query(USER_VISIBLE_SQL,
                (rs,row)->new Item(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),
                        rs.getObject(4,UUID.class),rs.getString(5),rs.getString(6),rs.getObject(7,java.time.OffsetDateTime.class),
                        rs.getObject(8,UUID.class),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),
                        rs.getString(13),rs.getObject(14,java.time.OffsetDateTime.class),
                        rs.getObject(15,java.time.OffsetDateTime.class)), userId));
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
            UUID predecessorEventId, boolean eligibleAfterActivation) {}
    record Candidate(UUID eventId, UUID assessmentId) {}
}
