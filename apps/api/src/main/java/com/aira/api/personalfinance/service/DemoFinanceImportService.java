package com.aira.api.personalfinance.service;

import com.aira.api.personalfinance.dto.DemoFinanceImportResponse;
import com.aira.api.personalfinance.exception.FinanceConsentRequiredException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports deterministic sample finance data only after explicit DEMO_IMPORT consent.
 * This service never represents the sample as a live financial-institution connection.
 */
@Service
public class DemoFinanceImportService {
    public static final String PROVIDER_KEY = "AIRA_DEMO_V1";
    private static final String DISPLAY_NAME = "AIRA 데모 금융데이터";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DemoFinanceImportService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public DemoFinanceImportResponse importDemo(UUID userId) {
        if (userId == null) throw new FinanceConsentRequiredException();
        Consent consent = activeConsent(userId);
        ZoneId userZone = userZone(userId);
        OffsetDateTime now = OffsetDateTime.now(clock).atZoneSameInstant(userZone).toOffsetDateTime();
        UUID connectionId = upsertConnection(userId, consent, now);
        UUID checking = upsertAccount(userId, connectionId, "CHECKING",
                "생활계좌 (데모)", "checking", now);
        UUID card = upsertAccount(userId, connectionId, "CARD",
                "결제카드 (데모)", "card", now);

        YearMonth current = YearMonth.from(now);
        int inserted = 0;
        for (int offset = 2; offset >= 0; offset--) {
            inserted += importMonth(userId, checking, card, current.minusMonths(offset), now, userZone);
        }
        return new DemoFinanceImportResponse(connectionId, true, PROVIDER_KEY, 2, inserted,
                current.minusMonths(2).atDay(1), now.toLocalDate());
    }

    private Consent activeConsent(UUID userId) {
        List<Consent> rows = jdbc.query("""
                SELECT id,consented_at
                FROM personal_finance_consent
                WHERE user_id=? AND source_type='DEMO_IMPORT'
                  AND provider_key=? AND revoked_at IS NULL
                  AND allow_accounts=true AND allow_transactions=true
                """, (rs, row) -> new Consent(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, OffsetDateTime.class)), userId, PROVIDER_KEY);
        if (rows.isEmpty()) throw new FinanceConsentRequiredException();
        return rows.getFirst();
    }

    private UUID upsertConnection(UUID userId, Consent consent, OffsetDateTime now) {
        return jdbc.queryForObject("""
                INSERT INTO personal_finance_connection(
                    id,user_id,source_type,provider_key,display_name,status,
                    consented_at,last_synced_at,disconnected_at,created_at,updated_at,consent_id)
                VALUES (? ,?,'DEMO_IMPORT',?,?,'ACTIVE',?,?,NULL,?,?,?)
                ON CONFLICT (user_id,provider_key) DO UPDATE SET
                    display_name=EXCLUDED.display_name,status='ACTIVE',
                    consented_at=EXCLUDED.consented_at,last_synced_at=EXCLUDED.last_synced_at,
                    disconnected_at=NULL,updated_at=EXCLUDED.updated_at,
                    consent_id=EXCLUDED.consent_id
                RETURNING id
                """, UUID.class, UUID.randomUUID(), userId, PROVIDER_KEY, DISPLAY_NAME,
                consent.consentedAt(), now, now, now, consent.id());
    }

    private UUID upsertAccount(UUID userId, UUID connectionId, String accountType,
            String displayName, String logicalKey, OffsetDateTime now) {
        byte[] sourceHash = hash(PROVIDER_KEY + "|account|" + logicalKey);
        return jdbc.queryForObject("""
                INSERT INTO personal_finance_account(
                    id,user_id,connection_id,account_type,display_name,currency_code,
                    source_ref_hash,created_at,updated_at)
                VALUES (?,?,?,?,?,'KRW',?,?,?)
                ON CONFLICT (connection_id,source_ref_hash) DO UPDATE SET
                    display_name=EXCLUDED.display_name,
                    updated_at=EXCLUDED.updated_at
                RETURNING id
                """, UUID.class, UUID.randomUUID(), userId, connectionId, accountType,
                displayName, sourceHash, now, now);
    }

    private int importMonth(UUID userId, UUID checking, UUID card,
            YearMonth month, OffsetDateTime now, ZoneId userZone) {
        long age = java.time.temporal.ChronoUnit.MONTHS.between(month, YearMonth.from(now));
        BigDecimal food = switch ((int) age) {
            case 0 -> new BigDecimal("420000");
            case 1 -> new BigDecimal("350000");
            default -> new BigDecimal("300000");
        };
        int inserted = 0;
        inserted += insertTransaction(userId, checking, month, 1, "INCOME",
                new BigDecimal("3000000"), "급여 (데모)", "INCOME", "salary", now, userZone);
        inserted += insertTransaction(userId, checking, month, 2, "EXPENSE",
                new BigDecimal("800000"), "주거비 (데모)", "HOUSING", "housing", now, userZone);
        inserted += insertTransaction(userId, card, month, 3, "EXPENSE",
                food, "식비 합계 (데모)", "FOOD", "food", now, userZone);
        inserted += insertTransaction(userId, card, month, 4, "EXPENSE",
                new BigDecimal("120000"), "교통비 합계 (데모)", "TRANSPORT", "transport", now, userZone);
        inserted += insertTransaction(userId, card, month, 5, "EXPENSE",
                new BigDecimal("35000"), "정기구독 합계 (데모)", "SUBSCRIPTION", "subscription", now, userZone);
        return inserted;
    }

    private int insertTransaction(UUID userId, UUID accountId, YearMonth month,
            int preferredDay, String direction, BigDecimal amount, String merchant,
            String category, String logicalKey, OffsetDateTime now, ZoneId userZone) {
        OffsetDateTime occurredAt = occurredAt(month, preferredDay, now, userZone);
        byte[] sourceHash = hash(PROVIDER_KEY + "|transaction|" + month + "|" + logicalKey);
        return jdbc.update("""
                INSERT INTO personal_finance_transaction(
                    id,user_id,account_id,occurred_at,direction,amount,currency_code,
                    merchant_name,category,source_ref_hash,created_at)
                VALUES (?,?,?,?,?,?,'KRW',?,?,?,?)
                ON CONFLICT (account_id,source_ref_hash) DO NOTHING
                """, UUID.randomUUID(), userId, accountId, occurredAt, direction, amount,
                merchant, category, sourceHash, now);
    }

    private OffsetDateTime occurredAt(YearMonth month, int preferredDay, OffsetDateTime now, ZoneId userZone) {
        int day = Math.min(preferredDay, month.lengthOfMonth());
        if (month.equals(YearMonth.from(now))) {
            day = Math.min(day, now.getDayOfMonth());
        }
        return month.atDay(Math.max(day, 1)).atTime(12, 0).atZone(userZone).toOffsetDateTime();
    }

    private ZoneId userZone(UUID userId) {
        String configured = jdbc.queryForObject(
                "SELECT timezone FROM app_user WHERE id=?", String.class, userId);
        // AIRA currently targets Korea; an unset/invalid profile timezone falls back explicitly rather than UTC.
        if (configured == null || configured.isBlank()) return ZoneId.of("Asia/Seoul");
        try {
            return ZoneId.of(configured);
        } catch (java.time.DateTimeException invalid) {
            return ZoneId.of("Asia/Seoul");
        }
    }

    private static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record Consent(UUID id, OffsetDateTime consentedAt) {}
}
