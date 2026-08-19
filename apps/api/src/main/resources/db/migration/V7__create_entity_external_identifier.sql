CREATE TABLE entity_external_identifier (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id uuid NOT NULL,
    namespace varchar(64) NOT NULL,
    identifier_type varchar(64) NOT NULL,
    identifier_value varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_entity_external_identifier_entity FOREIGN KEY (entity_id)
        REFERENCES entity (id) ON DELETE RESTRICT,
    CONSTRAINT ck_entity_external_identifier_namespace_not_blank
        CHECK (btrim(namespace) <> ''),
    CONSTRAINT ck_entity_external_identifier_type_not_blank
        CHECK (btrim(identifier_type) <> ''),
    CONSTRAINT ck_entity_external_identifier_value_not_blank
        CHECK (btrim(identifier_value) <> ''),
    CONSTRAINT uq_entity_external_identifier
        UNIQUE (namespace, identifier_type, identifier_value)
);

CREATE INDEX ix_entity_external_identifier_entity_namespace_type
    ON entity_external_identifier (entity_id, namespace, identifier_type);
