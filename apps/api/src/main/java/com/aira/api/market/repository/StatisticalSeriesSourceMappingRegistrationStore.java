package com.aira.api.market.repository;

import com.aira.api.market.service.StatisticalSeriesSourceMappingRegistration;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StatisticalSeriesSourceMappingRegistrationStore {
    private static final String REGISTER_SQL = """
            INSERT INTO statistical_series_source_mapping (
                statistical_series_id, source_id, provider_binding_key,
                provider_series_name, provider_item_name, provider_frequency_code,
                provider_unit_name, metadata_locator, active, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (source_id, provider_binding_key)
            DO UPDATE SET provider_binding_key = EXCLUDED.provider_binding_key
            WHERE statistical_series_source_mapping.statistical_series_id
                    IS NOT DISTINCT FROM EXCLUDED.statistical_series_id
              AND statistical_series_source_mapping.provider_series_name
                    IS NOT DISTINCT FROM EXCLUDED.provider_series_name
              AND statistical_series_source_mapping.provider_item_name
                    IS NOT DISTINCT FROM EXCLUDED.provider_item_name
              AND statistical_series_source_mapping.provider_frequency_code
                    IS NOT DISTINCT FROM EXCLUDED.provider_frequency_code
              AND statistical_series_source_mapping.provider_unit_name
                    IS NOT DISTINCT FROM EXCLUDED.provider_unit_name
              AND statistical_series_source_mapping.metadata_locator
                    IS NOT DISTINCT FROM EXCLUDED.metadata_locator
              AND statistical_series_source_mapping.active = true
            RETURNING id
            """;

    private final JdbcTemplate jdbc;

    public StatisticalSeriesSourceMappingRegistrationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID registerOrGetId(StatisticalSeriesSourceMappingRegistration registration) throws IllegalStateException {
        try {
            return jdbc.queryForObject(REGISTER_SQL, UUID.class,
                    registration.statisticalSeriesId(), registration.sourceId(),
                    registration.providerBindingKey(), registration.providerSeriesName(),
                    registration.providerItemName(), registration.providerFrequencyCode(),
                    registration.providerUnitName(), registration.metadataLocator());
        } catch (EmptyResultDataAccessException conflict) {
            throw new IllegalStateException(
                    "Provider binding conflicts with different statistical series metadata",
                    conflict);
        }
    }
}
