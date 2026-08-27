package com.aira.api.delivery.service;

import com.aira.api.delivery.dto.BriefingResponse;
import com.aira.api.delivery.dto.BriefingResponse.Item;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalBriefingService {
    static final String POLICY_VERSION = "interest-assessment-v1";
    static final String BRIEFING_TYPE = "ON_DEMAND";
    static final String USER_LOCK_SQL = "SELECT id FROM app_user WHERE id=? FOR UPDATE";
    static final String HEADER_SQL = """
            SELECT id,title,status,briefing_type,period_start,period_end,generated_at FROM briefing
            WHERE id=? AND user_id=?
            """;
    static final String ITEM_SQL = """
            SELECT DISTINCT ON (bi.display_order) bi.display_order,en.id,en.canonical_name,ev.id,
                   ev.event_type,ev.title,ev.occurred_at,a.id,a.summary,a.uncertainty,
                   a.importance,a.confidence,s.name,e.external_id,e.original_url
            FROM briefing_item bi
            JOIN assessment a ON a.id=bi.assessment_id
            JOIN event ev ON ev.id=a.event_id
            JOIN event_entity ee ON ee.event_id=ev.id
            JOIN entity en ON en.id=ee.entity_id
            JOIN assessment_evidence ae ON ae.assessment_id=a.id
            JOIN evidence e ON e.id=ae.evidence_id
            JOIN source s ON s.id=e.source_id WHERE bi.briefing_id=?
            ORDER BY bi.display_order,e.id
            """;
    static final String CANDIDATE_SQL = """
            SELECT a.id,ev.occurred_at,a.completed_at
            FROM user_interest ui
            JOIN entity en ON en.id=ui.entity_id AND en.entity_type='COMPANY'
            JOIN event_entity ee ON ee.entity_id=ui.entity_id
            JOIN event ev ON ev.id=ee.event_id AND ev.status='CONFIRMED'
            JOIN assessment a ON a.event_id=ev.id AND a.status='COMPLETED'
            WHERE ui.user_id=?
              AND EXISTS (SELECT 1 FROM assessment_evidence ae WHERE ae.assessment_id=a.id)
              AND a.completed_at>GREATEST(?,ui.created_at)
              AND a.completed_at<=?
            ORDER BY ev.occurred_at DESC NULLS LAST,a.id ASC
            """;
    static final Comparator<Candidate> CANDIDATE_ORDER =
            Comparator.comparing(Candidate::occurredAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Candidate::assessmentId);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PersonalBriefingService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    PersonalBriefingService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public BriefingResponse getOrCreate(UUID userId) {
        lockUser(userId);
        OffsetDateTime cutoff = OffsetDateTime.now(clock);
        OffsetDateTime periodStart = periodStart(userId, cutoff);
        if (periodStart == null) {
            return BriefingResponse.empty(
                    BRIEFING_TYPE, null, cutoff, OffsetDateTime.now(clock));
        }
        if (!periodStart.isBefore(cutoff)) {
            return BriefingResponse.empty(
                    BRIEFING_TYPE, periodStart, cutoff, OffsetDateTime.now(clock));
        }

        List<Candidate> candidates = candidates(userId, periodStart, cutoff);
        if (candidates.isEmpty()) {
            return BriefingResponse.empty(
                    BRIEFING_TYPE, periodStart, cutoff, OffsetDateTime.now(clock));
        }

        byte[] dedupKey = digest(periodStart, cutoff,
                candidates.stream().map(candidate -> candidate.assessmentId().toString()).toList());
        UUID briefingId = findByDedup(userId, dedupKey);
        if (briefingId == null) {
            briefingId = create(userId, periodStart, cutoff, candidates, dedupKey);
        }
        return findOwned(userId, briefingId);
    }

    @Transactional(readOnly = true)
    public BriefingResponse findOwned(UUID userId, UUID briefingId) {
        var headers = jdbc.query(HEADER_SQL,
                (rs, row) -> new Header(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getObject(5, OffsetDateTime.class),
                        rs.getObject(6, OffsetDateTime.class), rs.getObject(7, OffsetDateTime.class)),
                briefingId, userId);
        if (headers.isEmpty()) throw new BriefingNotFoundException();
        Header header = headers.getFirst();
        List<Item> items = jdbc.query(ITEM_SQL,
                (rs, row) -> new Item(rs.getShort(1), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6),
                        rs.getObject(7, OffsetDateTime.class), rs.getObject(8, UUID.class),
                        rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                        rs.getString(13), rs.getString(14), rs.getString(15)), briefingId);
        return new BriefingResponse(header.id(), header.title(), header.status(), header.briefingType(),
                header.periodStart(), header.periodEnd(), header.generatedAt(),
                items.stream().distinct().toList());
    }

    private void lockUser(UUID userId) {
        jdbc.queryForObject(USER_LOCK_SQL, UUID.class, userId);
    }

    private OffsetDateTime periodStart(UUID userId, OffsetDateTime cutoff) {
        List<OffsetDateTime> previous = jdbc.query("""
                SELECT period_end FROM briefing
                WHERE user_id=? AND briefing_type=? AND status IN ('READY','DELIVERED')
                  AND period_end<=?
                ORDER BY period_end DESC LIMIT 1
                """, (rs, row) -> rs.getObject(1, OffsetDateTime.class), userId, BRIEFING_TYPE,
                Timestamp.from(cutoff.toInstant()));
        List<OffsetDateTime> earliestInterest = jdbc.query("""
                SELECT min(ui.created_at) FROM user_interest ui
                JOIN entity en ON en.id=ui.entity_id AND en.entity_type='COMPANY'
                WHERE ui.user_id=?
                HAVING count(*)>0
                """, (rs, row) -> rs.getObject(1, OffsetDateTime.class), userId);
        return selectPeriodStart(previous, earliestInterest);
    }

    static OffsetDateTime selectPeriodStart(List<OffsetDateTime> previous,
            List<OffsetDateTime> earliestInterest) {
        if (!previous.isEmpty()) return previous.getFirst();
        return earliestInterest.isEmpty() ? null : earliestInterest.getFirst();
    }

    private List<Candidate> candidates(UUID userId, OffsetDateTime periodStart,
            OffsetDateTime cutoff) {
        return jdbc.query(CANDIDATE_SQL, (rs, row) -> new Candidate(rs.getObject(1, UUID.class),
                        rs.getObject(2, OffsetDateTime.class), rs.getObject(3, OffsetDateTime.class)),
                userId, Timestamp.from(periodStart.toInstant()), Timestamp.from(cutoff.toInstant()))
                .stream().sorted(CANDIDATE_ORDER).toList();
    }

    private UUID create(UUID userId, OffsetDateTime periodStart, OffsetDateTime periodEnd,
            List<Candidate> candidates, byte[] dedupKey) {
        UUID id = UUID.randomUUID();
        OffsetDateTime generatedAt = OffsetDateTime.now(clock);
        try {
            jdbc.update("""
                    INSERT INTO briefing(id,user_id,briefing_type,period_start,period_end,policy_version,
                    status,title,dedup_key,generated_at,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,'READY','내 브리핑',?,?,?,?)
                    """, id, userId, BRIEFING_TYPE, Timestamp.from(periodStart.toInstant()),
                    Timestamp.from(periodEnd.toInstant()), POLICY_VERSION, dedupKey,
                    Timestamp.from(generatedAt.toInstant()), Timestamp.from(generatedAt.toInstant()),
                    Timestamp.from(generatedAt.toInstant()));
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

    static byte[] digest(OffsetDateTime periodStart, OffsetDateTime periodEnd,
            List<String> assessmentIds) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, BRIEFING_TYPE);
            update(digest, POLICY_VERSION);
            update(digest, periodStart.toInstant().toString());
            update(digest, periodEnd.toInstant().toString());
            assessmentIds.forEach(id -> update(digest, id));
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    record Candidate(UUID assessmentId, OffsetDateTime occurredAt, OffsetDateTime completedAt) {}
    private record Header(UUID id, String title, String status, String briefingType,
            OffsetDateTime periodStart, OffsetDateTime periodEnd, OffsetDateTime generatedAt) {}
}
