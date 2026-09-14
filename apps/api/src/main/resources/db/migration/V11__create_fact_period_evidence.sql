CREATE TABLE fact_period_evidence (
    fact_id uuid NOT NULL,
    evidence_id uuid NOT NULL,
    locator text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (fact_id, evidence_id),
    CONSTRAINT fk_fact_period_evidence_fact FOREIGN KEY (fact_id)
        REFERENCES fact (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fact_period_evidence_evidence FOREIGN KEY (evidence_id)
        REFERENCES evidence (id) ON DELETE RESTRICT,
    CONSTRAINT ck_fact_period_evidence_locator_not_blank CHECK (
        btrim(locator) <> '' AND locator ~ '[^[:space:]]'
    )
);

CREATE INDEX ix_fact_period_evidence_evidence_fact
    ON fact_period_evidence (evidence_id, fact_id);
