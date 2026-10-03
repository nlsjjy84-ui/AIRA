package com.aira.api.market.service;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers colloquial/legacy names (e.g. "현대차" for 현대자동차) against an entity so
 * search can match on them in addition to the entity's official canonical_name.
 */
@Service
public class EntityAliasRegistryService {
    private final JdbcTemplate jdbc;

    public EntityAliasRegistryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void registerOrReuse(UUID entityId, String alias) {
        if (entityId == null) {
            throw new IllegalArgumentException("Entity identifier is required");
        }
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Alias is required");
        }
        jdbc.update("""
                INSERT INTO entity_alias (entity_id, alias)
                VALUES (?, ?)
                ON CONFLICT (entity_id, alias) DO NOTHING
                """, entityId, alias.strip());
    }
}
