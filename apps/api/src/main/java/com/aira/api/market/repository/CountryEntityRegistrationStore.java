package com.aira.api.market.repository;

import com.aira.api.market.service.CountryEntityRegistration;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CountryEntityRegistrationStore {
    private static final String REGISTER_SQL = """
            INSERT INTO entity (
                entity_type, canonical_name, canonical_key,
                market_code, symbol, country_code,
                active, created_at, updated_at
            ) VALUES ('COUNTRY', ?, ?, NULL, NULL, ?, true,
                      CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (canonical_key)
            DO UPDATE SET canonical_key = EXCLUDED.canonical_key
            WHERE entity.entity_type = 'COUNTRY'
              AND entity.canonical_name = EXCLUDED.canonical_name
              AND entity.country_code = EXCLUDED.country_code
              AND entity.market_code IS NULL
              AND entity.symbol IS NULL
              AND entity.active = true
            RETURNING id
            """;

    private final JdbcTemplate jdbc;

    public CountryEntityRegistrationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID registerOrGetId(CountryEntityRegistration registration)
            throws IllegalStateException {
        try {
            return jdbc.queryForObject(REGISTER_SQL, UUID.class,
                    registration.canonicalName(),
                    registration.canonicalKey(),
                    registration.countryCode());
        } catch (EmptyResultDataAccessException | DataIntegrityViolationException conflict) {
            throw new IllegalStateException(
                    "Country identity conflicts with existing entity", conflict);
        }
    }
}
