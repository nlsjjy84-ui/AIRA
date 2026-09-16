package com.aira.api.personalfinance.service;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases user-owned personal-finance data without deleting the AIRA account.
 * Explicit user erasure is the exception to the append-only consent-history rule.
 */
@Service
public class PersonalFinanceDataDeletionService {
    private final JdbcTemplate jdbc;

    public PersonalFinanceDataDeletionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void deleteAll(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("Finance data owner is required");

        // Child-first deletion keeps ownership/FK constraints enabled throughout the erase.
        jdbc.update("DELETE FROM monthly_budget WHERE user_id=?", userId);
        jdbc.update("DELETE FROM personal_finance_transaction WHERE user_id=?", userId);
        jdbc.update("DELETE FROM personal_finance_account WHERE user_id=?", userId);
        jdbc.update("DELETE FROM personal_finance_connection WHERE user_id=?", userId);
        jdbc.update("DELETE FROM personal_finance_consent WHERE user_id=?", userId);
        jdbc.update("DELETE FROM personal_finance_access_grant WHERE user_id=?", userId);
        jdbc.update("DELETE FROM personal_finance_reauth_guard WHERE user_id=?", userId);
    }
}
