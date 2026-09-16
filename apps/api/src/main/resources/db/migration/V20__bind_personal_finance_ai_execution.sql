-- PF-4: bind private AI execution provenance to the owning AIRA user.
-- Existing market/event AI executions remain unchanged; this column is nullable for them.

ALTER TABLE ai_execution
    ADD COLUMN personal_finance_user_id uuid;

ALTER TABLE ai_execution
    ADD CONSTRAINT fk_ai_execution_personal_finance_user
    FOREIGN KEY (personal_finance_user_id)
    REFERENCES app_user (id) ON DELETE CASCADE;

ALTER TABLE ai_execution
    ADD CONSTRAINT ck_ai_execution_personal_finance_boundary CHECK (
        task_type <> 'PERSONAL_FINANCE_EXPLANATION'
        OR (
            personal_finance_user_id IS NOT NULL
            AND event_id IS NULL
            AND assessment_id IS NULL
        )
    );

CREATE INDEX ix_ai_execution_personal_finance_user_started
    ON ai_execution (personal_finance_user_id, started_at DESC)
    WHERE personal_finance_user_id IS NOT NULL;
