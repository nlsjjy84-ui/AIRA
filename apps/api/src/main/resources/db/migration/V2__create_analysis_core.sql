-- AIRA ERD v1: analysis, entity-specific impact, and AI execution metadata.

CREATE TABLE assessment (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL,
    analysis_version varchar(64) NOT NULL,
    method varchar(24) NOT NULL,
    importance varchar(16) NOT NULL,
    summary text NOT NULL,
    confidence varchar(16) NOT NULL,
    uncertainty text,
    time_horizon varchar(24) NOT NULL,
    status varchar(24) NOT NULL,
    input_fingerprint bytea NOT NULL,
    completed_at timestamptz,
    supersedes_assessment_id uuid,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assessment_event FOREIGN KEY (event_id)
        REFERENCES event (id) ON DELETE RESTRICT,
    CONSTRAINT fk_assessment_supersedes FOREIGN KEY (supersedes_assessment_id)
        REFERENCES assessment (id) ON DELETE RESTRICT,
    CONSTRAINT ck_assessment_method CHECK (method IN ('RULE', 'AI', 'HYBRID', 'HUMAN_REVIEW')),
    CONSTRAINT ck_assessment_importance CHECK (importance IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_assessment_confidence CHECK (confidence IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_assessment_time_horizon CHECK (
        time_horizon IN ('IMMEDIATE', 'SHORT_TERM', 'MEDIUM_TERM', 'LONG_TERM', 'UNSPECIFIED')
    ),
    CONSTRAINT ck_assessment_status CHECK (
        status IN ('DRAFT', 'COMPLETED', 'REVIEW_REQUIRED', 'SUPERSEDED', 'REJECTED')
    ),
    CONSTRAINT ck_assessment_version_not_blank CHECK (btrim(analysis_version) <> ''),
    CONSTRAINT ck_assessment_summary_not_blank CHECK (btrim(summary) <> ''),
    CONSTRAINT ck_assessment_fingerprint_not_empty CHECK (octet_length(input_fingerprint) > 0),
    CONSTRAINT ck_assessment_not_self_superseded CHECK (
        supersedes_assessment_id IS NULL OR supersedes_assessment_id <> id
    )
);

ALTER TABLE assessment
    ADD CONSTRAINT uq_assessment_event_version_input
    UNIQUE (event_id, analysis_version, input_fingerprint);

CREATE INDEX ix_assessment_event_status_completed
    ON assessment (event_id, status, completed_at DESC);

CREATE TABLE assessment_evidence (
    assessment_id uuid NOT NULL,
    evidence_id uuid NOT NULL,
    usage_type varchar(24) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (assessment_id, evidence_id),
    CONSTRAINT fk_assessment_evidence_assessment FOREIGN KEY (assessment_id)
        REFERENCES assessment (id) ON DELETE RESTRICT,
    CONSTRAINT fk_assessment_evidence_evidence FOREIGN KEY (evidence_id)
        REFERENCES evidence (id) ON DELETE RESTRICT,
    CONSTRAINT ck_assessment_evidence_usage_not_blank CHECK (btrim(usage_type) <> '')
);

CREATE INDEX ix_assessment_evidence_evidence_assessment
    ON assessment_evidence (evidence_id, assessment_id);

CREATE TABLE impact (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id uuid NOT NULL,
    entity_id uuid NOT NULL,
    sequence_no smallint NOT NULL,
    factor text NOT NULL,
    transmission_path text NOT NULL,
    rationale text NOT NULL,
    direction varchar(16) NOT NULL,
    time_horizon varchar(24) NOT NULL,
    confidence varchar(16) NOT NULL,
    uncertainty text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_impact_assessment FOREIGN KEY (assessment_id)
        REFERENCES assessment (id) ON DELETE RESTRICT,
    CONSTRAINT fk_impact_entity FOREIGN KEY (entity_id)
        REFERENCES entity (id) ON DELETE RESTRICT,
    CONSTRAINT uq_impact_assessment_entity_sequence UNIQUE (assessment_id, entity_id, sequence_no),
    CONSTRAINT ck_impact_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_impact_factor_not_blank CHECK (btrim(factor) <> ''),
    CONSTRAINT ck_impact_path_not_blank CHECK (btrim(transmission_path) <> ''),
    CONSTRAINT ck_impact_rationale_not_blank CHECK (btrim(rationale) <> ''),
    CONSTRAINT ck_impact_direction CHECK (
        direction IN ('POSITIVE', 'NEGATIVE', 'NEUTRAL', 'MIXED', 'UNKNOWN')
    ),
    CONSTRAINT ck_impact_time_horizon CHECK (
        time_horizon IN ('IMMEDIATE', 'SHORT_TERM', 'MEDIUM_TERM', 'LONG_TERM', 'UNSPECIFIED')
    ),
    CONSTRAINT ck_impact_confidence CHECK (confidence IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX ix_impact_entity_horizon ON impact (entity_id, time_horizon);
CREATE INDEX ix_impact_assessment ON impact (assessment_id);

CREATE TABLE ai_execution (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_key varchar(64) NOT NULL,
    model_key varchar(128) NOT NULL,
    task_type varchar(64) NOT NULL,
    prompt_version varchar(64) NOT NULL,
    event_id uuid,
    assessment_id uuid,
    input_fingerprint bytea NOT NULL,
    cache_key bytea NOT NULL,
    cache_hit boolean NOT NULL DEFAULT false,
    reused_execution_id uuid,
    input_tokens bigint,
    output_tokens bigint,
    estimated_cost numeric(18,8),
    actual_cost numeric(18,8),
    currency_code char(3),
    latency_ms integer,
    status varchar(16) NOT NULL,
    error_code varchar(64),
    started_at timestamptz NOT NULL,
    completed_at timestamptz,
    CONSTRAINT fk_ai_execution_event FOREIGN KEY (event_id)
        REFERENCES event (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_execution_assessment FOREIGN KEY (assessment_id)
        REFERENCES assessment (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_execution_reused FOREIGN KEY (reused_execution_id)
        REFERENCES ai_execution (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_execution_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'CACHE_HIT')),
    CONSTRAINT ck_ai_execution_provider_not_blank CHECK (btrim(provider_key) <> ''),
    CONSTRAINT ck_ai_execution_model_not_blank CHECK (btrim(model_key) <> ''),
    CONSTRAINT ck_ai_execution_task_not_blank CHECK (btrim(task_type) <> ''),
    CONSTRAINT ck_ai_execution_prompt_not_blank CHECK (btrim(prompt_version) <> ''),
    CONSTRAINT ck_ai_execution_input_fingerprint CHECK (octet_length(input_fingerprint) > 0),
    CONSTRAINT ck_ai_execution_cache_key CHECK (octet_length(cache_key) > 0),
    CONSTRAINT ck_ai_execution_input_tokens CHECK (input_tokens IS NULL OR input_tokens >= 0),
    CONSTRAINT ck_ai_execution_output_tokens CHECK (output_tokens IS NULL OR output_tokens >= 0),
    CONSTRAINT ck_ai_execution_estimated_cost CHECK (estimated_cost IS NULL OR estimated_cost >= 0),
    CONSTRAINT ck_ai_execution_actual_cost CHECK (actual_cost IS NULL OR actual_cost >= 0),
    CONSTRAINT ck_ai_execution_latency CHECK (latency_ms IS NULL OR latency_ms >= 0),
    CONSTRAINT ck_ai_execution_currency CHECK (currency_code IS NULL OR currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_ai_execution_time_order CHECK (completed_at IS NULL OR completed_at >= started_at),
    CONSTRAINT ck_ai_execution_not_self_reused CHECK (
        reused_execution_id IS NULL OR reused_execution_id <> id
    ),
    CONSTRAINT ck_ai_execution_cache_reuse CHECK (
        (cache_hit = false AND reused_execution_id IS NULL)
        OR (cache_hit = true AND reused_execution_id IS NOT NULL)
    )
);

CREATE INDEX ix_ai_execution_cache_status_completed
    ON ai_execution (cache_key, status, completed_at DESC);
CREATE INDEX ix_ai_execution_success_cache
    ON ai_execution (cache_key, completed_at DESC)
    WHERE status = 'SUCCEEDED';
CREATE INDEX ix_ai_execution_task_started ON ai_execution (task_type, started_at DESC);
CREATE INDEX ix_ai_execution_provider_model_started
    ON ai_execution (provider_key, model_key, started_at DESC);
CREATE INDEX ix_ai_execution_assessment ON ai_execution (assessment_id);
CREATE INDEX ix_ai_execution_reused ON ai_execution (reused_execution_id);

