CREATE TABLE statistical_series (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_entity_id uuid NOT NULL,
    metric varchar(32) NOT NULL,
    frequency varchar(16) NOT NULL,
    adjustment varchar(32) NOT NULL,
    value_kind varchar(16) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_statistical_series_subject FOREIGN KEY (subject_entity_id)
        REFERENCES entity (id) ON DELETE RESTRICT,
    CONSTRAINT ck_statistical_series_metric CHECK (metric IN ('REAL_GDP')),
    CONSTRAINT ck_statistical_series_frequency CHECK (frequency IN ('QUARTERLY')),
    CONSTRAINT ck_statistical_series_adjustment CHECK (
        adjustment IN ('SEASONALLY_ADJUSTED')
    ),
    CONSTRAINT ck_statistical_series_value_kind CHECK (value_kind IN ('LEVEL'))
);

CREATE UNIQUE INDEX uq_statistical_series_identity
    ON statistical_series (
        subject_entity_id, metric, frequency, adjustment, value_kind
    );

CREATE TABLE statistical_series_source_mapping (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    statistical_series_id uuid NOT NULL,
    source_id uuid NOT NULL,
    provider_binding_key varchar(512) NOT NULL,
    provider_series_name text NOT NULL,
    provider_item_name text NOT NULL,
    provider_frequency_code varchar(32) NOT NULL,
    provider_unit_name varchar(100) NOT NULL,
    metadata_locator text NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_statistical_series_mapping_series FOREIGN KEY (statistical_series_id)
        REFERENCES statistical_series (id) ON DELETE RESTRICT,
    CONSTRAINT fk_statistical_series_mapping_source FOREIGN KEY (source_id)
        REFERENCES source (id) ON DELETE RESTRICT,
    CONSTRAINT ck_statistical_series_mapping_binding_key CHECK (
        provider_binding_key = btrim(provider_binding_key) AND btrim(provider_binding_key) <> ''
    ),
    CONSTRAINT ck_statistical_series_mapping_series_name CHECK (
        provider_series_name = btrim(provider_series_name) AND btrim(provider_series_name) <> ''
    ),
    CONSTRAINT ck_statistical_series_mapping_item_name CHECK (
        provider_item_name = btrim(provider_item_name) AND btrim(provider_item_name) <> ''
    )
);
ALTER TABLE statistical_series_source_mapping
    ADD CONSTRAINT ck_statistical_series_mapping_frequency_code CHECK (
        provider_frequency_code = btrim(provider_frequency_code) AND btrim(provider_frequency_code) <> ''
    ),
    ADD CONSTRAINT ck_statistical_series_mapping_unit_name CHECK (
        provider_unit_name = btrim(provider_unit_name) AND btrim(provider_unit_name) <> ''
    ),
    ADD CONSTRAINT ck_statistical_series_mapping_metadata_locator CHECK (
        metadata_locator = btrim(metadata_locator) AND btrim(metadata_locator) <> ''
    );

CREATE UNIQUE INDEX uq_statistical_series_source_binding
    ON statistical_series_source_mapping (source_id, provider_binding_key);

CREATE INDEX ix_statistical_series_mapping_series
    ON statistical_series_source_mapping (statistical_series_id, source_id);
