package com.aira.api.delivery.service;

import com.aira.api.delivery.dto.AlertResponse;
import com.aira.api.delivery.dto.AlertResponse.Item;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InAppAlertService {
    static final String POLICY = "interest-new-assessment-v1";
    private final JdbcTemplate jdbc;
    public InAppAlertService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public AlertResponse reconcile(UUID userId) {
        List<UUID> candidates = jdbc.query("""
                SELECT DISTINCT a.id FROM user_interest ui
                JOIN event_entity ee ON ee.entity_id=ui.entity_id
                JOIN event ev ON ev.id=ee.event_id AND ev.status IN ('CANDIDATE','CONFIRMED')
                JOIN assessment a ON a.event_id=ev.id AND a.status='COMPLETED'
                JOIN assessment_evidence ae ON ae.assessment_id=a.id
                JOIN evidence e ON e.id=ae.evidence_id JOIN source s ON s.id=e.source_id
                WHERE ui.user_id=? AND ui.alert_enabled=true AND a.completed_at>=ui.updated_at
                ORDER BY a.id
                """, (rs,row)->rs.getObject(1,UUID.class), userId);
        for (UUID assessmentId : candidates) {
            jdbc.update("""
                    INSERT INTO alert(id,user_id,assessment_id,policy_version,reason_code,dedup_key,status,
                    sent_at,created_at,updated_at) VALUES(?,?,?,?,'NEW_ASSESSMENT',?,'SENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id,dedup_key) DO NOTHING
                    """, UUID.randomUUID(), userId, assessmentId, POLICY, digest(assessmentId));
        }
        return findAll(userId);
    }

    @Transactional(readOnly=true)
    public AlertResponse findAll(UUID userId) {
        return new AlertResponse(jdbc.query("""
                SELECT DISTINCT ON (al.id) al.id,en.id,en.canonical_name,ev.id,ev.title,ev.event_type,
                ev.occurred_at,a.id,a.summary,a.uncertainty,s.name,e.external_id,e.original_url,al.created_at,e.id
                FROM alert al JOIN assessment a ON a.id=al.assessment_id JOIN event ev ON ev.id=a.event_id
                JOIN event_entity ee ON ee.event_id=ev.id JOIN entity en ON en.id=ee.entity_id
                JOIN assessment_evidence ae ON ae.assessment_id=a.id JOIN evidence e ON e.id=ae.evidence_id
                JOIN source s ON s.id=e.source_id WHERE al.user_id=?
                ORDER BY al.id,e.id
                """, (rs,row)->new Item(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),
                        rs.getObject(4,UUID.class),rs.getString(5),rs.getString(6),rs.getObject(7,java.time.OffsetDateTime.class),
                        rs.getObject(8,UUID.class),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),
                        rs.getString(13),rs.getObject(14,java.time.OffsetDateTime.class)), userId));
    }

    @Transactional(readOnly=true)
    public Item findOwned(UUID userId, UUID alertId) {
        return findAll(userId).alerts().stream().filter(item->item.alertId().equals(alertId)).findFirst()
                .orElseThrow(BriefingNotFoundException::new);
    }

    private static byte[] digest(UUID id) {
        try { return MessageDigest.getInstance("SHA-256").digest((POLICY+id).getBytes(StandardCharsets.UTF_8)); }
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
