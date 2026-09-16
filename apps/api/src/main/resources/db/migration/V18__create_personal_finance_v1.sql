-- Personal Finance v1: user-owned finance data stays separate from public canonical Fact/Evidence.

CREATE TABLE personal_finance_connection (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    source_type varchar(24) NOT NULL,
    provider_key varchar(64) NOT NULL,
    display_name varchar(80) NOT NULL,
    status varchar(16) NOT NULL,
    consented_at timestamptz NOT NULL,
    last_synced_at timestamptz,
    disconnected_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_personal_finance_connection_user_provider UNIQUE (user_id, provider_key),
    CONSTRAINT uq_personal_finance_connection_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_personal_finance_connection_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_personal_finance_connection_source CHECK (source_type IN ('DEMO_IMPORT','MYDATA_API')),
    CONSTRAINT ck_personal_finance_connection_status CHECK (status IN ('ACTIVE','DISCONNECTED')),
    CONSTRAINT ck_personal_finance_connection_provider CHECK (btrim(provider_key) <> ''),
    CONSTRAINT ck_personal_finance_connection_display CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_personal_finance_connection_sync_order CHECK (last_synced_at IS NULL OR last_synced_at >= consented_at),
    CONSTRAINT ck_personal_finance_connection_disconnect_order CHECK (disconnected_at IS NULL OR disconnected_at >= consented_at),
    CONSTRAINT ck_personal_finance_connection_state CHECK (
        (status='ACTIVE' AND disconnected_at IS NULL) OR (status='DISCONNECTED' AND disconnected_at IS NOT NULL)
    )
);

CREATE INDEX ix_personal_finance_connection_user_status
    ON personal_finance_connection (user_id, status);

CREATE TABLE personal_finance_account (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    account_type varchar(16) NOT NULL,
    display_name varchar(80) NOT NULL,
    currency_code varchar(3) NOT NULL,
    source_ref_hash bytea NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_personal_finance_account_source UNIQUE (connection_id, source_ref_hash),
    CONSTRAINT uq_personal_finance_account_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_personal_finance_account_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_personal_finance_account_connection_user FOREIGN KEY (connection_id, user_id)
        REFERENCES personal_finance_connection (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_personal_finance_account_type CHECK (account_type IN ('CHECKING','SAVINGS','CARD')),
    CONSTRAINT ck_personal_finance_account_display CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_personal_finance_account_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_personal_finance_account_source_hash CHECK (octet_length(source_ref_hash) > 0)
);

CREATE INDEX ix_personal_finance_account_user
    ON personal_finance_account (user_id, created_at);

CREATE TABLE personal_finance_transaction (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    account_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL,
    direction varchar(16) NOT NULL,
    amount numeric(19,2) NOT NULL,
    currency_code varchar(3) NOT NULL,
    merchant_name varchar(120),
    category varchar(24) NOT NULL,
    source_ref_hash bytea NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_personal_finance_transaction_source UNIQUE (account_id, source_ref_hash),
    CONSTRAINT fk_personal_finance_transaction_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_personal_finance_transaction_account_user FOREIGN KEY (account_id, user_id)
        REFERENCES personal_finance_account (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_personal_finance_transaction_direction CHECK (direction IN ('INCOME','EXPENSE')),
    CONSTRAINT ck_personal_finance_transaction_amount CHECK (amount > 0),
    CONSTRAINT ck_personal_finance_transaction_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_personal_finance_transaction_merchant CHECK (merchant_name IS NULL OR btrim(merchant_name) <> ''),
    CONSTRAINT ck_personal_finance_transaction_category CHECK (category IN (
        'INCOME','HOUSING','FOOD','TRANSPORT','SHOPPING','HEALTH','EDUCATION',
        'LEISURE','SUBSCRIPTION','FINANCE','TRANSFER','OTHER')),
    CONSTRAINT ck_personal_finance_transaction_source_hash CHECK (octet_length(source_ref_hash) > 0)
);

CREATE INDEX ix_personal_finance_transaction_user_time
    ON personal_finance_transaction (user_id, occurred_at DESC);
CREATE INDEX ix_personal_finance_transaction_user_category_time
    ON personal_finance_transaction (user_id, category, occurred_at DESC);

CREATE TABLE monthly_budget (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    budget_month date NOT NULL,
    category varchar(24) NOT NULL,
    amount numeric(19,2) NOT NULL,
    currency_code varchar(3) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_monthly_budget_user_month_category_currency UNIQUE (user_id, budget_month, category, currency_code),
    CONSTRAINT fk_monthly_budget_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_monthly_budget_month CHECK (budget_month = date_trunc('month', budget_month)::date),
    CONSTRAINT ck_monthly_budget_category CHECK (category IN (
        'TOTAL','HOUSING','FOOD','TRANSPORT','SHOPPING','HEALTH','EDUCATION',
        'LEISURE','SUBSCRIPTION','FINANCE','OTHER')),
    CONSTRAINT ck_monthly_budget_amount CHECK (amount > 0),
    CONSTRAINT ck_monthly_budget_currency CHECK (currency_code ~ '^[A-Z]{3}$')
);

CREATE INDEX ix_monthly_budget_user_month
    ON monthly_budget (user_id, budget_month DESC);
