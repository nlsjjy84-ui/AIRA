-- PF-SEC-1: step-up access and consent history for user-owned finance data.
-- A normal AIRA login is necessary but not sufficient for sensitive finance operations.

ALTER TABLE user_session
    ADD CONSTRAINT uq_user_session_id_user UNIQUE (id, user_id);

CREATE TABLE personal_finance_consent (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    source_type varchar(24) NOT NULL,
    provider_key varchar(64) NOT NULL,
    policy_version varchar(32) NOT NULL,
    allow_accounts boolean NOT NULL,
    allow_transactions boolean NOT NULL,
    consented_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_personal_finance_consent_identity
        UNIQUE (id, user_id, source_type, provider_key),
    CONSTRAINT fk_personal_finance_consent_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_personal_finance_consent_source
        CHECK (source_type IN ('DEMO_IMPORT','MYDATA_API')),
    CONSTRAINT ck_personal_finance_consent_provider CHECK (btrim(provider_key) <> ''),
    CONSTRAINT ck_personal_finance_consent_policy CHECK (btrim(policy_version) <> ''),    CONSTRAINT ck_personal_finance_consent_scope
        CHECK (allow_accounts OR allow_transactions),
    CONSTRAINT ck_personal_finance_consent_revoke_order
        CHECK (revoked_at IS NULL OR revoked_at >= consented_at)
);

CREATE UNIQUE INDEX uq_personal_finance_consent_active_provider
    ON personal_finance_consent (user_id, source_type, provider_key)
    WHERE revoked_at IS NULL;

ALTER TABLE personal_finance_connection
    ADD COLUMN consent_id uuid;

ALTER TABLE personal_finance_connection
    ADD CONSTRAINT fk_personal_finance_connection_consent
    FOREIGN KEY (consent_id, user_id, source_type, provider_key)
    REFERENCES personal_finance_consent (id, user_id, source_type, provider_key)
    ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_personal_finance_connection_consent
    ON personal_finance_connection (consent_id)
    WHERE consent_id IS NOT NULL;

-- Reauthentication failures are persisted so protection survives app restarts.
CREATE TABLE personal_finance_reauth_guard (
    user_id uuid PRIMARY KEY,
    failed_attempts integer NOT NULL DEFAULT 0,
    locked_until timestamptz,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_personal_finance_reauth_guard_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_personal_finance_reauth_guard_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT ck_personal_finance_reauth_guard_lock CHECK (
        locked_until IS NULL OR failed_attempts >= 5
    )
);

CREATE TABLE personal_finance_access_grant (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    session_id uuid NOT NULL,
    token_hash bytea NOT NULL,
    issued_at timestamptz NOT NULL,    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_personal_finance_access_grant_token UNIQUE (token_hash),
    CONSTRAINT fk_personal_finance_access_grant_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_personal_finance_access_grant_session_user
        FOREIGN KEY (session_id, user_id)
        REFERENCES user_session (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_personal_finance_access_grant_hash
        CHECK (octet_length(token_hash) > 0),
    CONSTRAINT ck_personal_finance_access_grant_expiry
        CHECK (expires_at = issued_at + interval '10 minutes'),
    CONSTRAINT ck_personal_finance_access_grant_revoke_order
        CHECK (revoked_at IS NULL OR revoked_at >= issued_at)
);

CREATE UNIQUE INDEX uq_personal_finance_access_grant_active_session
    ON personal_finance_access_grant (session_id)
    WHERE revoked_at IS NULL;

CREATE INDEX ix_personal_finance_access_grant_user_expiry
    ON personal_finance_access_grant (user_id, expires_at);
