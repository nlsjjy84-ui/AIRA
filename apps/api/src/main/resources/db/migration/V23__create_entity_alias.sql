CREATE TABLE entity_alias (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id uuid NOT NULL,
    alias varchar(300) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_entity_alias_entity FOREIGN KEY (entity_id)
        REFERENCES entity (id) ON DELETE CASCADE,
    CONSTRAINT ck_entity_alias_not_blank CHECK (btrim(alias) <> ''),
    CONSTRAINT uq_entity_alias UNIQUE (entity_id, alias)
);

CREATE INDEX ix_entity_alias_alias_lower ON entity_alias (lower(alias));
