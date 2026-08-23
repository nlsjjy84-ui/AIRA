package com.aira.api.delivery.service;

import com.aira.api.delivery.dto.BriefingResponse;
import com.aira.api.delivery.dto.BriefingResponse.Item;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalBriefingService {
    static final String POLICY_VERSION = "interest-assessment-v1";
    private final JdbcTemplate jdbc;

    public PersonalBriefingService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public BriefingResponse getOrCreate(UUID userId) {
        List<Candidate> candidates = candidates(userId);
        if (candidates.isEmpty()) return BriefingResponse.empty();

        byte[] dedupKey = digest(candidates.stream().map(c -> c.assessmentId().toString()).toList());
        UUID briefingId = findByDedup(userId, dedupKey);
        if (briefingId == null) {
            briefingId = create(userId, candidates, dedupKey);
        }
        return findOwned(userId, briefingId);
    }

    @Transactional(readOnly = true)
    public BriefingResponse findOwned(UUID userId, UUID briefingId) {
        var headers = jdbc.query("""
                SELECT id,title,status,generated_at FROM briefing
                WHERE id=? AND user_id=?
                """, (rs, row) -> new Header(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getObject(4, OffsetDateTime.class)), briefingId, userId);
        if (headers.isEmpty()) throw new BriefingNotFoundException();
        Header header = headers.getFirst();
        List<Item> items = jdbc.query("""
                SELECT DISTINCT ON (bi.display_order) bi.display_order,en.id,en.canonical_name,ev.id,ev.event_type,ev.title,
                       ev.occurred_at,a.id,a.summary,a.uncertainty,a.importance,a.confidence,
                       s.name,e.external_id,e.original_url
                FROM briefing_item bi
                JOIN assessment a ON a.id=bi.assessment_id
                JOIN event ev ON ev.id=a.event_id
                JOIN event_entity ee ON ee.event_id=ev.id
                JOIN entity en ON en.id=ee.entity_id
                JOIN assessment_evidence ae ON ae.assessment_id=a.id
                JOIN evidence e ON e.id=ae.evidence_id
                JOIN source s ON s.id=e.source_id
                WHERE bi.briefing_id=?
                ORDER BY bi.display_order,e.id
                """, (rs, row) -> new Item(rs.getShort(1), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6),
                        rs.getObject(7, OffsetDateTime.class), rs.getObject(8, UUID.class),
                        rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                        rs.getString(13), rs.getString(14), rs.getString(15)), briefingId);
        return new BriefingResponse(header.id(), header.title(), header.status(), header.generatedAt(),
                items.stream().distinct().toList());
    }

    private List<Candidate> candidates(UUID userId) {
        return jdbc.query("""
                SELECT DISTINCT ON (a.id) a.id,ev.occurred_at,a.completed_at
                FROM user_interest ui
                JOIN event_entity ee ON ee.entity_id=ui.entity_id
                JOIN event ev ON ev.id=ee.event_id AND ev.status IN ('CANDIDATE','CONFIRMED')
                JOIN assessment a ON a.event_id=ev.id AND a.status='COMPLETED'
                JOIN assessment_evidence ae ON ae.assessment_id=a.id
                WHERE ui.user_id=?
                ORDER BY a.id,ev.occurred_at DESC
                """, (rs, row) -> new Candidate(rs.getObject(1, UUID.class),
                        rs.getObject(2, OffsetDateTime.class), rs.getObject(3, OffsetDateTime.class)), userId)
                .stream().sorted((a, b) -> {
                    int byTime = b.occurredAt().compareTo(a.occurredAt());
                    return byTime != 0 ? byTime : a.assessmentId().compareTo(b.assessmentId());
                }).toList();
    }

    private UUID create(UUID userId, List<Candidate> candidates, byte[] dedupKey) {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime end = start.plusSeconds(1);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO briefing(id,user_id,briefing_type,period_start,period_end,policy_version,
                    status,title,dedup_key,generated_at,created_at,updated_at)
                    VALUES(?,?,'ON_DEMAND',?,?,?,'READY','내 브리핑',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, id, userId, Timestamp.from(start.toInstant()), Timestamp.from(end.toInstant()),
                    POLICY_VERSION, dedupKey);
            short order = 1;
            for (Candidate candidate : candidates) {
                jdbc.update("""
                        INSERT INTO briefing_item(briefing_id,assessment_id,display_order,reason_code,created_at)
                        VALUES(?,?,?,'CURRENT_INTEREST',CURRENT_TIMESTAMP)
                        """, id, candidate.assessmentId(), order++);
            }
            return id;
        } catch (DuplicateKeyException race) {
            UUID existing = findByDedup(userId, dedupKey);
            if (existing != null) return existing;
            throw race;
        }
    }

    private UUID findByDedup(UUID userId, byte[] key) {
        var ids = jdbc.query("SELECT id FROM briefing WHERE user_id=? AND dedup_key=?",
                (rs, row) -> rs.getObject(1, UUID.class), userId, key);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private static byte[] digest(List<String> assessmentIds) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(POLICY_VERSION.getBytes(StandardCharsets.UTF_8));
            assessmentIds.forEach(id -> digest.update(id.getBytes(StandardCharsets.UTF_8)));
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private record Candidate(UUID assessmentId, OffsetDateTime occurredAt, OffsetDateTime completedAt) {}
    private record Header(UUID id, String title, String status, OffsetDateTime generatedAt) {}
}
