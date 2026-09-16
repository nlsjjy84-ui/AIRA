package com.aira.api.personalfinance.service;

import com.aira.api.personalfinance.domain.FinanceConnectionSource;
import com.aira.api.personalfinance.dto.FinanceConsentRequest;
import com.aira.api.personalfinance.dto.FinanceConsentResponse;
import com.aira.api.personalfinance.exception.FinanceConsentAlreadyActiveException;
import com.aira.api.personalfinance.exception.FinanceConsentNotFoundException;
import com.aira.api.personalfinance.exception.InvalidFinanceConsentException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only consent history for personal-finance data access.
 * Active consent is revoked explicitly; it is never silently overwritten.
 */
@Service
public class FinanceConsentService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public FinanceConsentService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public FinanceConsentResponse create(UUID userId, FinanceConsentRequest request) {
        if (userId == null || request == null || request.sourceType() == null
                || request.providerKey() == null || request.providerKey().isBlank()
                || request.policyVersion() == null || request.policyVersion().isBlank()
                || (!request.allowAccounts() && !request.allowTransactions())) {
            throw new InvalidFinanceConsentException();
        }
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);
        try {
            jdbc.update("""
                    INSERT INTO personal_finance_consent(
                        id,user_id,source_type,provider_key,policy_version,
                        allow_accounts,allow_transactions,consented_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, id, userId, request.sourceType().name(), request.providerKey().trim(),
                    request.policyVersion().trim(), request.allowAccounts(),
                    request.allowTransactions(), now);
        } catch (DataIntegrityViolationException conflict) {
            throw new FinanceConsentAlreadyActiveException();
        }
        return findOwned(userId, id);
    }

    @Transactional(readOnly = true)
    public List<FinanceConsentResponse> findAll(UUID userId) {
        if (userId == null) throw new InvalidFinanceConsentException();
        return jdbc.query("""
                SELECT id,source_type,provider_key,policy_version,
                       allow_accounts,allow_transactions,consented_at,revoked_at
                FROM personal_finance_consent
                WHERE user_id=?
                ORDER BY consented_at DESC, id DESC
                """, (rs, row) -> map(rs), userId);
    }

    @Transactional
    public void revoke(UUID userId, UUID consentId) {
        if (userId == null || consentId == null) throw new FinanceConsentNotFoundException();
        OffsetDateTime now = OffsetDateTime.now(clock);
        int changed = jdbc.update("""
                UPDATE personal_finance_consent
                SET revoked_at=?
                WHERE id=? AND user_id=? AND revoked_at IS NULL
                """, now, consentId, userId);
        if (changed == 0) throw new FinanceConsentNotFoundException();

        // Consent withdrawal immediately stops any connection created from that consent.
        jdbc.update("""
                UPDATE personal_finance_connection
                SET status='DISCONNECTED',
                    disconnected_at=GREATEST(?, consented_at),
                    updated_at=GREATEST(?, consented_at)
                WHERE consent_id=? AND user_id=? AND status='ACTIVE'
                """, now, now, consentId, userId);
    }

    @Transactional(readOnly = true)
    public FinanceConsentResponse findOwned(UUID userId, UUID consentId) {
        var rows = jdbc.query("""
                SELECT id,source_type,provider_key,policy_version,
                       allow_accounts,allow_transactions,consented_at,revoked_at
                FROM personal_finance_consent
                WHERE id=? AND user_id=?
                """, (rs, row) -> map(rs), consentId, userId);
        if (rows.isEmpty()) throw new FinanceConsentNotFoundException();
        return rows.getFirst();
    }

    private static FinanceConsentResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FinanceConsentResponse(
                rs.getObject("id", UUID.class),
                FinanceConnectionSource.valueOf(rs.getString("source_type")),
                rs.getString("provider_key"), rs.getString("policy_version"),
                rs.getBoolean("allow_accounts"), rs.getBoolean("allow_transactions"),
                rs.getObject("consented_at", OffsetDateTime.class),
                rs.getObject("revoked_at", OffsetDateTime.class));
    }
}
