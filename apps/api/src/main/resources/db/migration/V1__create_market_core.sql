-- AIRA ERD v1: public market facts and event core.
-- gen_random_uuid() is built into supported modern PostgreSQL versions and
-- avoids adding an extension solely for UUID defaults.

CREATE TABLE source (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type varchar(32) NOT NULL,
    name varchar(200) NOT NULL,
    canonical_domain varchar(255),
    external_key varchar(200),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_source_type CHECK (
        source_type IN ('NEWS', 'REGULATOR', 'EXCHANGE', 'COMPANY_IR', 'GOVERNMENT', 'OTHER')
    ),
    CONSTRAINT ck_source_name_not_blank CHECK (btrim(name) <> '')
);

CREATE UNIQUE INDEX uq_source_type_external_key
    ON source (source_type, external_key)
    WHERE external_key IS NOT NULL;

CREATE TABLE evidence (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id uuid NOT NULL,
    evidence_type varchar(32) NOT NULL,
    external_id varchar(255),
    original_url text NOT NULL,
    title text,
    content_hash bytea NOT NULL,
    locator text,
    excerpt text,
    published_at timestamptz,
    collected_at timestamptz NOT NULL,
    revision integer NOT NULL DEFAULT 1,
    status varchar(24) NOT NULL,
    CONSTRAINT fk_evidence_source FOREIGN KEY (source_id)
        REFERENCES source (id) ON DELETE RESTRICT,
    CONSTRAINT ck_evidence_type CHECK (
        evidence_type IN ('ARTICLE', 'DISCLOSURE', 'IR', 'PRESS_RELEASE', 'OFFICIAL_DATA', 'OTHER')
    ),
    CONSTRAINT ck_evidence_status CHECK (
        status IN ('ACTIVE', 'UPDATED', 'RETRACTED', 'UNAVAILABLE')
    ),
    CONSTRAINT ck_evidence_revision CHECK (revision >= 1),
    CONSTRAINT ck_evidence_url_not_blank CHECK (btrim(original_url) <> ''),
    CONSTRAINT ck_evidence_content_hash_not_empty CHECK (octet_length(content_hash) > 0)
);

CREATE UNIQUE INDEX uq_evidence_source_external_revision
    ON evidence (source_id, external_id, revision)
    WHERE external_id IS NOT NULL;

CREATE INDEX ix_evidence_content_hash ON evidence (content_hash);
CREATE INDEX ix_evidence_source_published_at ON evidence (source_id, published_at DESC);
CREATE INDEX ix_evidence_collected_at ON evidence (collected_at DESC);

CREATE TABLE event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type varchar(32) NOT NULL,
    title text NOT NULL,
    occurred_at timestamptz,
    occurred_until timestamptz,
    first_observed_at timestamptz NOT NULL,
    last_observed_at timestamptz NOT NULL,
    status varchar(24) NOT NULL,
    dedup_key bytea,
    superseded_by_event_id uuid,
    merge_reason text,
    merged_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_event_superseded_by FOREIGN KEY (superseded_by_event_id)
        REFERENCES event (id) ON DELETE RESTRICT,
    CONSTRAINT ck_event_type CHECK (
        event_type IN ('EARNINGS', 'DISCLOSURE', 'BUSINESS', 'GOVERNANCE', 'POLICY_REGULATION', 'RISK', 'MARKET')
    ),
    CONSTRAINT ck_event_status CHECK (
        status IN ('CANDIDATE', 'CONFIRMED', 'MERGED', 'DISCARDED')
    ),
    CONSTRAINT ck_event_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_event_observed_order CHECK (last_observed_at >= first_observed_at),
    CONSTRAINT ck_event_occurrence_order CHECK (
        occurred_at IS NULL OR occurred_until IS NULL OR occurred_until >= occurred_at
    ),
    CONSTRAINT ck_event_not_self_superseded CHECK (
        superseded_by_event_id IS NULL OR superseded_by_event_id <> id
    ),
    CONSTRAINT ck_event_dedup_key_not_empty CHECK (
        dedup_key IS NULL OR octet_length(dedup_key) > 0
    )
);

CREATE UNIQUE INDEX uq_event_dedup_key ON event (dedup_key) WHERE dedup_key IS NOT NULL;
CREATE INDEX ix_event_type_occurred_at ON event (event_type, occurred_at DESC);
CREATE INDEX ix_event_status_last_observed ON event (status, last_observed_at DESC);
CREATE INDEX ix_event_superseded_by ON event (superseded_by_event_id);

CREATE TABLE event_evidence (
    event_id uuid NOT NULL,
    evidence_id uuid NOT NULL,
    relation_type varchar(24) NOT NULL,
    linked_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, evidence_id),
    CONSTRAINT fk_event_evidence_event FOREIGN KEY (event_id)
        REFERENCES event (id) ON DELETE RESTRICT,
    CONSTRAINT fk_event_evidence_evidence FOREIGN KEY (evidence_id)
        REFERENCES evidence (id) ON DELETE RESTRICT,
    CONSTRAINT ck_event_evidence_relation_not_blank CHECK (btrim(relation_type) <> '')
);

CREATE INDEX ix_event_evidence_evidence_event ON event_evidence (evidence_id, event_id);

CREATE TABLE entity (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type varchar(24) NOT NULL,
    canonical_name varchar(300) NOT NULL,
    canonical_key varchar(300) NOT NULL,
    market_code varchar(32),
    symbol varchar(64),
    country_code char(2),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_entity_canonical_key UNIQUE (canonical_key),
    CONSTRAINT ck_entity_type CHECK (
        entity_type IN ('COMPANY', 'SECURITY', 'INDUSTRY', 'MARKET', 'COUNTRY', 'COMMODITY')
    ),
    CONSTRAINT ck_entity_name_not_blank CHECK (btrim(canonical_name) <> ''),
    CONSTRAINT ck_entity_key_not_blank CHECK (btrim(canonical_key) <> ''),
    CONSTRAINT ck_entity_country_code CHECK (
        country_code IS NULL OR country_code ~ '^[A-Z]{2}$'
    )
);

CREATE UNIQUE INDEX uq_entity_type_market_symbol
    ON entity (entity_type, market_code, symbol)
    WHERE market_code IS NOT NULL AND symbol IS NOT NULL;

CREATE TABLE event_entity (
    event_id uuid NOT NULL,
    entity_id uuid NOT NULL,
    relation_type varchar(24) NOT NULL,
    relevance varchar(16) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, entity_id),
    CONSTRAINT fk_event_entity_event FOREIGN KEY (event_id)
        REFERENCES event (id) ON DELETE RESTRICT,
    CONSTRAINT fk_event_entity_entity FOREIGN KEY (entity_id)
        REFERENCES entity (id) ON DELETE RESTRICT,
    CONSTRAINT ck_event_entity_relation_not_blank CHECK (btrim(relation_type) <> ''),
    CONSTRAINT ck_event_entity_relevance CHECK (relevance IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX ix_event_entity_entity_event ON event_entity (entity_id, event_id);

