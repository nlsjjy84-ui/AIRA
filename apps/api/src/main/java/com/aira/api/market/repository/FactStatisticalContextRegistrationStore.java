package com.aira.api.market.repository;

import com.aira.api.market.service.StatisticalFactContextRegistration;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FactStatisticalContextRegistrationStore {
    private static final String REGISTER_SQL = """
            INSERT INTO fact_statistical_context (
                fact_id, statistical_series_id, canonical_unit, created_at
            ) VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (fact_id)
            DO UPDATE SET fact_id = EXCLUDED.fact_id
            WHERE fact_statistical_context.statistical_series_id
                    IS NOT DISTINCT FROM EXCLUDED.statistical_series_id
              AND fact_statistical_context.canonical_unit
                    IS NOT DISTINCT FROM EXCLUDED.canonical_unit
            RETURNING fact_id
            """;

    private final JdbcTemplate jdbc;

    public FactStatisticalContextRegistrationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    public UUID registerOrGetFactId(StatisticalFactContextRegistration registration)
            throws IllegalStateException {
        try {
            return jdbc.queryForObject(REGISTER_SQL, UUID.class,
                    registration.factId(),
                    registration.statisticalSeriesId(),
                    registration.canonicalUnit().name());
        } catch (EmptyResultDataAccessException conflict) {
            throw new IllegalStateException(
                    "Fact statistical context conflicts with existing series or unit",
                    conflict);
        }
    }
}
