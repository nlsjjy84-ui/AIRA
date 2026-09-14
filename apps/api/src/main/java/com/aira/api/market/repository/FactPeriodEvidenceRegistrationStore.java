package com.aira.api.market.repository;

import com.aira.api.market.domain.FactPeriodEvidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FactPeriodEvidenceRegistrationStore {
    private final JdbcTemplate jdbc;

    public FactPeriodEvidenceRegistrationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void registerOrReuse(FactPeriodEvidence link) {
        if (link == null) throw new IllegalArgumentException("Period evidence link is required");
        int written = jdbc.update("""
                INSERT INTO fact_period_evidence(fact_id,evidence_id,locator,created_at)
                VALUES (?,?,?,?)
                ON CONFLICT (fact_id,evidence_id)
                DO UPDATE SET locator=EXCLUDED.locator
                WHERE fact_period_evidence.locator=EXCLUDED.locator
                """, link.getId().getFactId(), link.getId().getEvidenceId(),
                link.getLocator(), link.getCreatedAt());
        if (written != 1) {
            throw new IllegalStateException("Period evidence link conflicts with a different locator");
        }
    }
}
