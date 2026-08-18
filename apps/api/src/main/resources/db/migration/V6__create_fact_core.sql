CREATE TABLE fact (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_entity_id uuid NOT NULL,
    event_id uuid,
    predicate varchar(32) NOT NULL,
    status varchar(24) NOT NULL,
    value_type varchar(16) NOT NULL,
    value_number numeric,
    value_text text,
    value_boolean boolean,
    value_date date,
    value_timestamp timestamptz,
    currency_code char(3),
    period_start date,
    period_end date,
    as_of_at timestamptz,
    dedup_key bytea NOT NULL,
    status_reason varchar(40),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fact_subject_entity FOREIGN KEY (subject_entity_id)
        REFERENCES entity (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fact_event FOREIGN KEY (event_id)
        REFERENCES event (id) ON DELETE RESTRICT,
    CONSTRAINT ck_fact_predicate CHECK (
        predicate IN ('REVENUE', 'OPERATING_INCOME')
    ),
    CONSTRAINT ck_fact_status CHECK (
        status IN ('SUPPORTED', 'CONFLICTING', 'UNKNOWN')
    ),
    CONSTRAINT ck_fact_value_type CHECK (
        value_type IN ('NUMBER', 'TEXT', 'BOOLEAN', 'DATE', 'TIMESTAMP')
    ),
    CONSTRAINT ck_fact_status_reason CHECK (
        status_reason IS NULL OR status_reason IN (
            'ASSERTED_VALUE_CONFLICT', 'INSUFFICIENT_EVIDENCE'
        )
    ),
    CONSTRAINT ck_fact_currency_code CHECK (
        currency_code IS NULL OR currency_code ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_fact_period_pair CHECK (
        (period_start IS NULL AND period_end IS NULL)
        OR (period_start IS NOT NULL AND period_end IS NOT NULL)
    ),
    CONSTRAINT ck_fact_period_order CHECK (
        period_start IS NULL OR period_start <= period_end
    ),
    CONSTRAINT ck_fact_period_or_as_of CHECK (
        period_start IS NULL OR as_of_at IS NULL
    ),
    CONSTRAINT ck_fact_resolved_value CHECK (
        (
            status = 'SUPPORTED'
            AND status_reason IS NULL
            AND num_nonnulls(
                value_number, value_text, value_boolean, value_date, value_timestamp
            ) = 1
            AND (
                (value_type = 'NUMBER' AND value_number IS NOT NULL)
                OR (value_type = 'TEXT' AND value_text IS NOT NULL)
                OR (value_type = 'BOOLEAN' AND value_boolean IS NOT NULL)
                OR (value_type = 'DATE' AND value_date IS NOT NULL)
                OR (value_type = 'TIMESTAMP' AND value_timestamp IS NOT NULL)
            )
        )
        OR (
            status IN ('CONFLICTING', 'UNKNOWN')
            AND status_reason IS NOT NULL
            AND num_nonnulls(
                value_number, value_text, value_boolean, value_date, value_timestamp
            ) = 0
        )
    ),
    CONSTRAINT ck_fact_status_reason_semantics CHECK (
        (status = 'SUPPORTED' AND status_reason IS NULL)
        OR (status = 'CONFLICTING' AND status_reason = 'ASSERTED_VALUE_CONFLICT')
        OR (status = 'UNKNOWN' AND status_reason = 'INSUFFICIENT_EVIDENCE')
    ),
    CONSTRAINT ck_fact_earnings_number CHECK (
        predicate NOT IN ('REVENUE', 'OPERATING_INCOME')
        OR (value_type = 'NUMBER' AND currency_code IS NOT NULL)
    ),
    CONSTRAINT ck_fact_dedup_key_length CHECK (octet_length(dedup_key) = 32)
);

CREATE UNIQUE INDEX uq_fact_dedup_key ON fact (dedup_key);

CREATE TABLE fact_assertion (
    fact_id uuid NOT NULL,
    evidence_id uuid NOT NULL,
    locator text NOT NULL,
    value_type varchar(16) NOT NULL,
    value_number numeric,
    value_text text,
    value_boolean boolean,
    value_date date,
    value_timestamp timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (fact_id, evidence_id),
    CONSTRAINT fk_fact_assertion_fact FOREIGN KEY (fact_id)
        REFERENCES fact (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fact_assertion_evidence FOREIGN KEY (evidence_id)
        REFERENCES evidence (id) ON DELETE RESTRICT,
    CONSTRAINT ck_fact_assertion_locator_not_blank CHECK (btrim(locator) <> ''),
    CONSTRAINT ck_fact_assertion_value_type CHECK (
        value_type IN ('NUMBER', 'TEXT', 'BOOLEAN', 'DATE', 'TIMESTAMP')
    ),
    CONSTRAINT ck_fact_assertion_value CHECK (
        num_nonnulls(
            value_number, value_text, value_boolean, value_date, value_timestamp
        ) = 1
        AND (
            (value_type = 'NUMBER' AND value_number IS NOT NULL)
            OR (value_type = 'TEXT' AND value_text IS NOT NULL)
            OR (value_type = 'BOOLEAN' AND value_boolean IS NOT NULL)
            OR (value_type = 'DATE' AND value_date IS NOT NULL)
            OR (value_type = 'TIMESTAMP' AND value_timestamp IS NOT NULL)
        )
    )
);

CREATE INDEX ix_fact_assertion_evidence_fact
    ON fact_assertion (evidence_id, fact_id);
