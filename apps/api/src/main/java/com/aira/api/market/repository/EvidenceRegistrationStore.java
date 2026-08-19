package com.aira.api.market.repository;

import com.aira.api.market.domain.Source;
import com.aira.api.market.ingestion.EvidenceRegistration;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EvidenceRegistrationStore {
    private static final String REGISTER_SQL = """
            INSERT INTO evidence (
                source_id, evidence_type, external_id, original_url, title, content_hash,
                locator, published_at, collected_at, revision, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
            ON CONFLICT (source_id, external_id, revision) WHERE external_id IS NOT NULL
            DO UPDATE SET external_id = EXCLUDED.external_id
            RETURNING id
            """;

    private final JdbcTemplate jdbc;

    public EvidenceRegistrationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID registerOrGetId(Source source, EvidenceRegistration registration) {
        return jdbc.queryForObject(REGISTER_SQL, UUID.class, source.getId(),
                registration.evidenceType().name(), registration.externalId(),
                registration.originalUrl(), registration.title(), registration.contentHash(),
                registration.locator(), registration.publishedAt(), registration.collectedAt(),
                registration.revision());
    }
}
