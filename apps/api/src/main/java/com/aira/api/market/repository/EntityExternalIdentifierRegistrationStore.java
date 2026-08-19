package com.aira.api.market.repository;

import com.aira.api.market.service.ExternalIdentifierRegistration;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EntityExternalIdentifierRegistrationStore {
    private static final String REGISTER_SQL = """
            INSERT INTO entity_external_identifier (
                entity_id, namespace, identifier_type, identifier_value, created_at
            ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (namespace, identifier_type, identifier_value)
            DO UPDATE SET identifier_value = EXCLUDED.identifier_value
            RETURNING id, entity_id
            """;

    private final JdbcTemplate jdbc;

    public EntityExternalIdentifierRegistrationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public StoredExternalIdentifier registerOrGet(ExternalIdentifierRegistration registration) {
        var key = registration.identifier();
        return jdbc.queryForObject(REGISTER_SQL, (result, rowNumber) ->
                        new StoredExternalIdentifier(
                                result.getObject("id", UUID.class),
                                result.getObject("entity_id", UUID.class)),
                registration.entityId(), key.namespace(), key.identifierType(),
                key.identifierValue());
    }

    public record StoredExternalIdentifier(UUID id, UUID entityId) {}
}
