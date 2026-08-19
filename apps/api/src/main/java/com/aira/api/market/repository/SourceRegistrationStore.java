package com.aira.api.market.repository;

import com.aira.api.market.service.SourceRegistration;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SourceRegistrationStore {
    private static final String REGISTER_SQL = """
            INSERT INTO source (
                source_type, external_key, name, canonical_domain, active, created_at, updated_at
            ) VALUES (?, ?, ?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (source_type, external_key) WHERE external_key IS NOT NULL
            DO UPDATE SET external_key = EXCLUDED.external_key
            RETURNING id
            """;

    private final JdbcTemplate jdbc;

    public SourceRegistrationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID registerOrGetId(SourceRegistration registration) {
        return jdbc.queryForObject(REGISTER_SQL, UUID.class,
                registration.sourceType().name(), registration.externalKey(),
                registration.name(), registration.canonicalDomain());
    }
}
