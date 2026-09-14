package com.aira.api.market.repository;

import com.aira.api.market.service.StatisticalSeriesRegistration;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StatisticalSeriesRegistrationStore {
    private static final String REGISTER_SQL = """
            INSERT INTO statistical_series (
                subject_entity_id, metric, frequency, adjustment, value_kind,
                active, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (subject_entity_id, metric, frequency, adjustment, value_kind)
            DO UPDATE SET subject_entity_id = EXCLUDED.subject_entity_id
            WHERE statistical_series.active = true
            RETURNING id
            """;

    private final JdbcTemplate jdbc;

    public StatisticalSeriesRegistrationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID registerOrGetId(StatisticalSeriesRegistration registration) throws IllegalStateException {
        try {
            return jdbc.queryForObject(REGISTER_SQL, UUID.class,
                    registration.subjectEntityId(), registration.metric().name(),
                    registration.frequency().name(), registration.adjustment().name(),
                    registration.valueKind().name());
        } catch (EmptyResultDataAccessException conflict) {
            throw new IllegalStateException("Statistical series identity is inactive", conflict);
        }
    }
}
