-- AIRA ERD v1: minimal user data, Argon2id credentials, recovery, and opaque sessions.

CREATE TABLE app_user (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nickname varchar(20) NOT NULL,
    nickname_normalized varchar(20) NOT NULL,
    status varchar(16) NOT NULL,
    locale varchar(16),
    timezone varchar(64),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    CONSTRAINT uq_app_user_nickname_normalized UNIQUE (nickname_normalized),
    CONSTRAINT ck_app_user_nickname CHECK (
        nickname ~ '^[가-힣A-Za-z][가-힣A-Za-z0-9]{2,19}$'
    ),
    CONSTRAINT ck_app_user_nickname_normalized CHECK (
        nickname_normalized ~ '^[가-힣A-Za-z][가-힣A-Za-z0-9]{2,19}$'
    ),
    CONSTRAINT ck_app_user_nickname_normalized_length CHECK (
        char_length(nickname_normalized) BETWEEN 3 AND 20
    ),
    CONSTRAINT ck_app_user_nickname_reserved CHECK (
        lower(nickname_normalized) NOT IN ('admin', 'administrator', '관리자', '운영자')
    ),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DELETED')),
    CONSTRAINT ck_app_user_deleted_state CHECK (
        (status = 'DELETED' AND deleted_at IS NOT NULL)
        OR (status <> 'DELETED' AND deleted_at IS NULL)
    )
);

CREATE TABLE authentication_credential (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    password_hash text NOT NULL,
    password_changed_at timestamptz NOT NULL,
    status varchar(16) NOT NULL,
    failed_attempts integer NOT NULL DEFAULT 0,
    locked_until timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_authentication_credential_user UNIQUE (user_id),
    CONSTRAINT fk_authentication_credential_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_authentication_credential_hash_not_blank CHECK (btrim(password_hash) <> ''),
    CONSTRAINT ck_authentication_credential_argon2id CHECK (password_hash LIKE '$argon2id$%'),
    CONSTRAINT ck_authentication_credential_status CHECK (status IN ('ACTIVE', 'LOCKED', 'REVOKED')),
    CONSTRAINT ck_authentication_credential_attempts CHECK (failed_attempts >= 0)
);

CREATE TABLE recovery_email (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    purpose varchar(32) NOT NULL,
    email_ciphertext bytea NOT NULL,
    email_lookup_hash bytea NOT NULL,
    encryption_key_version smallint NOT NULL,
    verified_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    CONSTRAINT fk_recovery_email_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_recovery_email_purpose CHECK (purpose = 'ACCOUNT_RECOVERY'),
    CONSTRAINT ck_recovery_email_ciphertext CHECK (octet_length(email_ciphertext) > 0),
    CONSTRAINT ck_recovery_email_lookup_hash CHECK (octet_length(email_lookup_hash) > 0),
    CONSTRAINT ck_recovery_email_key_version CHECK (encryption_key_version > 0)
);

CREATE UNIQUE INDEX uq_recovery_email_active_user
    ON recovery_email (user_id)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_recovery_email_active_lookup_hash
    ON recovery_email (email_lookup_hash)
    WHERE deleted_at IS NULL;

CREATE TABLE recovery_email_verification (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    candidate_email_ciphertext bytea NOT NULL,
    candidate_email_lookup_hash bytea NOT NULL,
    encryption_key_version smallint NOT NULL,
    token_hash bytea NOT NULL,
    requested_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    verified_at timestamptz,
    invalidated_at timestamptz,
    CONSTRAINT uq_recovery_email_verification_token UNIQUE (token_hash),
    CONSTRAINT fk_recovery_email_verification_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_recovery_email_verification_ciphertext CHECK (
        octet_length(candidate_email_ciphertext) > 0
    ),
    CONSTRAINT ck_recovery_email_verification_lookup_hash CHECK (
        octet_length(candidate_email_lookup_hash) > 0
    ),
    CONSTRAINT ck_recovery_email_verification_token_hash CHECK (octet_length(token_hash) > 0),
    CONSTRAINT ck_recovery_email_verification_key_version CHECK (encryption_key_version > 0),
    CONSTRAINT ck_recovery_email_verification_expiry CHECK (expires_at > requested_at),
    CONSTRAINT ck_recovery_email_verification_state CHECK (
        verified_at IS NULL OR invalidated_at IS NULL
    )
);

CREATE INDEX ix_recovery_email_verification_user_expiry
    ON recovery_email_verification (user_id, expires_at);
CREATE INDEX ix_recovery_email_verification_candidate_hash
    ON recovery_email_verification (candidate_email_lookup_hash);

CREATE TABLE password_reset_token (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    recovery_email_id uuid NOT NULL,
    token_hash bytea NOT NULL,
    requested_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    invalidated_at timestamptz,
    invalid_reason varchar(32),
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_recovery_email FOREIGN KEY (recovery_email_id)
        REFERENCES recovery_email (id) ON DELETE CASCADE,
    CONSTRAINT ck_password_reset_token_hash CHECK (octet_length(token_hash) > 0),
    CONSTRAINT ck_password_reset_expiry CHECK (
        expires_at = requested_at + interval '30 minutes'
    ),
    CONSTRAINT ck_password_reset_state CHECK (used_at IS NULL OR invalidated_at IS NULL),
    CONSTRAINT ck_password_reset_used_order CHECK (used_at IS NULL OR used_at >= requested_at),
    CONSTRAINT ck_password_reset_invalidated_order CHECK (
        invalidated_at IS NULL OR invalidated_at >= requested_at
    )
);

CREATE INDEX ix_password_reset_recovery_expiry
    ON password_reset_token (recovery_email_id, expires_at);

CREATE TABLE user_session (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    credential_id uuid NOT NULL,
    token_hash bytea NOT NULL,
    rotated_from_session_id uuid,
    issued_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    idle_expires_at timestamptz NOT NULL,
    absolute_expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    revoke_reason varchar(32),
    CONSTRAINT uq_user_session_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_user_session_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_session_credential FOREIGN KEY (credential_id)
        REFERENCES authentication_credential (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_session_rotated_from FOREIGN KEY (rotated_from_session_id)
        REFERENCES user_session (id) ON DELETE SET NULL,
    CONSTRAINT ck_user_session_token_hash CHECK (octet_length(token_hash) > 0),
    CONSTRAINT ck_user_session_seen_order CHECK (last_seen_at >= issued_at),
    CONSTRAINT ck_user_session_idle_policy CHECK (
        idle_expires_at = LEAST(last_seen_at + interval '30 minutes', absolute_expires_at)
    ),
    CONSTRAINT ck_user_session_absolute_policy CHECK (
        absolute_expires_at = issued_at + interval '12 hours'
    ),
    CONSTRAINT ck_user_session_revoke_pair CHECK (
        (revoked_at IS NULL AND revoke_reason IS NULL)
        OR (revoked_at IS NOT NULL AND revoke_reason IS NOT NULL)
    ),
    CONSTRAINT ck_user_session_revoke_reason CHECK (
        revoke_reason IS NULL OR revoke_reason IN (
            'LOGOUT', 'EXPIRED', 'ROTATED', 'PASSWORD_RESET', 'SECURITY', 'ADMIN'
        )
    ),
    CONSTRAINT ck_user_session_not_self_rotated CHECK (
        rotated_from_session_id IS NULL OR rotated_from_session_id <> id
    )
);

CREATE UNIQUE INDEX uq_user_session_rotated_from
    ON user_session (rotated_from_session_id)
    WHERE rotated_from_session_id IS NOT NULL;
CREATE INDEX ix_user_session_user_absolute ON user_session (user_id, absolute_expires_at);
CREATE INDEX ix_user_session_active_idle
    ON user_session (user_id, idle_expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE user_interest (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    entity_id uuid NOT NULL,
    interest_level varchar(16),
    alert_enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_interest_user_entity UNIQUE (user_id, entity_id),
    CONSTRAINT fk_user_interest_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_interest_entity FOREIGN KEY (entity_id)
        REFERENCES entity (id) ON DELETE RESTRICT,
    CONSTRAINT ck_user_interest_level CHECK (
        interest_level IS NULL OR interest_level IN ('LOW', 'MEDIUM', 'HIGH')
    )
);

CREATE INDEX ix_user_interest_entity_user ON user_interest (entity_id, user_id);
