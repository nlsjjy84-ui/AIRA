-- AIRA ERD v1: delivery records reuse common assessments and impacts.

CREATE TABLE alert (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    assessment_id uuid NOT NULL,
    impact_id uuid,
    policy_version varchar(64) NOT NULL,
    reason_code varchar(64) NOT NULL,
    dedup_key bytea NOT NULL,
    status varchar(16) NOT NULL,
    scheduled_at timestamptz,
    sent_at timestamptz,
    failure_code varchar(64),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_alert_user_dedup UNIQUE (user_id, dedup_key),
    CONSTRAINT fk_alert_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_assessment FOREIGN KEY (assessment_id)
        REFERENCES assessment (id) ON DELETE RESTRICT,
    CONSTRAINT fk_alert_impact FOREIGN KEY (impact_id)
        REFERENCES impact (id) ON DELETE RESTRICT,
    CONSTRAINT ck_alert_status CHECK (
        status IN ('CANDIDATE', 'PENDING', 'SENT', 'FAILED', 'SUPPRESSED')
    ),
    CONSTRAINT ck_alert_policy_not_blank CHECK (btrim(policy_version) <> ''),
    CONSTRAINT ck_alert_reason_not_blank CHECK (btrim(reason_code) <> ''),
    CONSTRAINT ck_alert_dedup_key CHECK (octet_length(dedup_key) > 0),
    CONSTRAINT ck_alert_sent_order CHECK (sent_at IS NULL OR sent_at >= created_at)
);

CREATE INDEX ix_alert_user_status_scheduled ON alert (user_id, status, scheduled_at);
CREATE INDEX ix_alert_assessment ON alert (assessment_id);

CREATE TABLE briefing (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    briefing_type varchar(24) NOT NULL,
    period_start timestamptz NOT NULL,
    period_end timestamptz NOT NULL,
    policy_version varchar(64) NOT NULL,
    status varchar(16) NOT NULL,
    title text NOT NULL,
    dedup_key bytea NOT NULL,
    generated_at timestamptz,
    delivered_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_briefing_user_dedup UNIQUE (user_id, dedup_key),
    CONSTRAINT uq_briefing_user_type_period UNIQUE (
        user_id, briefing_type, period_start, period_end
    ),
    CONSTRAINT fk_briefing_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_briefing_status CHECK (status IN ('BUILDING', 'READY', 'DELIVERED', 'FAILED')),
    CONSTRAINT ck_briefing_type_not_blank CHECK (btrim(briefing_type) <> ''),
    CONSTRAINT ck_briefing_policy_not_blank CHECK (btrim(policy_version) <> ''),
    CONSTRAINT ck_briefing_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_briefing_dedup_key CHECK (octet_length(dedup_key) > 0),
    CONSTRAINT ck_briefing_period CHECK (period_end > period_start),
    CONSTRAINT ck_briefing_generated_order CHECK (
        generated_at IS NULL OR generated_at >= created_at
    ),
    CONSTRAINT ck_briefing_delivered_order CHECK (
        delivered_at IS NULL OR delivered_at >= created_at
    )
);

CREATE TABLE briefing_item (
    briefing_id uuid NOT NULL,
    assessment_id uuid NOT NULL,
    impact_id uuid,
    display_order smallint NOT NULL,
    reason_code varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (briefing_id, assessment_id),
    CONSTRAINT uq_briefing_item_display_order UNIQUE (briefing_id, display_order),
    CONSTRAINT fk_briefing_item_briefing FOREIGN KEY (briefing_id)
        REFERENCES briefing (id) ON DELETE CASCADE,
    CONSTRAINT fk_briefing_item_assessment FOREIGN KEY (assessment_id)
        REFERENCES assessment (id) ON DELETE RESTRICT,
    CONSTRAINT fk_briefing_item_impact FOREIGN KEY (impact_id)
        REFERENCES impact (id) ON DELETE RESTRICT,
    CONSTRAINT ck_briefing_item_display_order CHECK (display_order > 0),
    CONSTRAINT ck_briefing_item_reason_not_blank CHECK (btrim(reason_code) <> '')
);

CREATE INDEX ix_briefing_item_assessment_briefing
    ON briefing_item (assessment_id, briefing_id);

