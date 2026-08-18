-- AIRA Source Registry v1: contextual authority scopes for existing sources.

CREATE TABLE source_authority_scope (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id uuid NOT NULL,
    scope_type varchar(48) NOT NULL,
    authority_role varchar(32) NOT NULL,
    subject_entity_id uuid,
    jurisdiction_entity_id uuid,
    basis_url text NOT NULL,
    verified_at timestamptz NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_source_authority_scope_source FOREIGN KEY (source_id)
        REFERENCES source (id) ON DELETE RESTRICT,
    CONSTRAINT fk_source_authority_scope_subject FOREIGN KEY (subject_entity_id)
        REFERENCES entity (id) ON DELETE RESTRICT,
    CONSTRAINT fk_source_authority_scope_jurisdiction FOREIGN KEY (jurisdiction_entity_id)
        REFERENCES entity (id) ON DELETE RESTRICT,
    CONSTRAINT ck_source_authority_scope_type CHECK (
        scope_type IN (
            'COMPANY_REPORTED_RESULTS',
            'COMPANY_OFFICIAL_STATEMENT',
            'REGULATORY_DISCLOSURE',
            'LISTING_STATUS',
            'MARKET_TRADING_DATA',
            'MONETARY_POLICY_DECISION',
            'OFFICIAL_ECONOMIC_STATISTICS'
        )
    ),
    CONSTRAINT ck_source_authority_scope_role CHECK (
        authority_role IN (
            'ORIGINATOR',
            'OFFICIAL_OPERATOR',
            'OFFICIAL_REPOSITORY',
            'DECISION_AUTHORITY'
        )
    ),
    CONSTRAINT ck_source_authority_scope_basis_url_not_blank CHECK (btrim(basis_url) <> '')
);

CREATE UNIQUE INDEX uq_source_authority_scope_context
    ON source_authority_scope (
        source_id,
        scope_type,
        authority_role,
        subject_entity_id,
        jurisdiction_entity_id
    ) NULLS NOT DISTINCT;

CREATE INDEX ix_source_authority_scope_subject
    ON source_authority_scope (subject_entity_id)
    WHERE subject_entity_id IS NOT NULL;
CREATE INDEX ix_source_authority_scope_jurisdiction
    ON source_authority_scope (jurisdiction_entity_id)
    WHERE jurisdiction_entity_id IS NOT NULL;
