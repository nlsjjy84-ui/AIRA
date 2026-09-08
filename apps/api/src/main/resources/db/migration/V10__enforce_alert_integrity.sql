-- The PostgreSQL preflight found no existing user_interest or alert rows.
-- Therefore no compatibility backfill is required: alert activation starts only
-- with an explicit post-migration false -> true transition.
ALTER TABLE user_interest
    ADD COLUMN alert_enabled_at timestamptz;

ALTER TABLE user_interest
    ADD CONSTRAINT ck_user_interest_alert_activation CHECK (
        (alert_enabled AND alert_enabled_at IS NOT NULL)
        OR (NOT alert_enabled AND alert_enabled_at IS NULL)
    );

ALTER TABLE alert
    ADD CONSTRAINT uq_alert_user_assessment_policy
        UNIQUE (user_id, assessment_id, policy_version),
    ADD CONSTRAINT ck_alert_sent_timestamp CHECK (
        status <> 'SENT' OR sent_at IS NOT NULL
    );

ALTER TABLE alert
    DROP CONSTRAINT ck_alert_dedup_key,
    ADD CONSTRAINT ck_alert_dedup_key CHECK (octet_length(dedup_key) = 32);

-- No additional candidate index is needed. Existing indexes cover the access path:
-- uq_user_interest_user_entity starts with user_id, event_entity is indexed by
-- (entity_id,event_id), ix_assessment_event_status_completed starts with event_id,
-- and uq_alert_user_assessment_policy directly supports exact Alert identity checks.
