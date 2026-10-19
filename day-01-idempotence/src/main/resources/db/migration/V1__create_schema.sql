CREATE TABLE accounts
(
    id            BIGINT PRIMARY KEY,
    balance_cents BIGINT NOT NULL,
    CONSTRAINT ck_accounts_balance_non_negative CHECK (balance_cents >= 0)
);

CREATE TABLE payments
(
    id                  BIGSERIAL PRIMARY KEY,
    account_id          BIGINT      NOT NULL REFERENCES accounts (id),
    amount_cents        BIGINT      NOT NULL CHECK (amount_cents > 0),
    balance_after_cents BIGINT      NOT NULL,
    idempotency_key     VARCHAR(100),
    request_fingerprint VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL,
    -- Safety net: even if the application logic has a gap, the database refuses a second
    -- payment with the same key. NULLs are never equal, so payments without a key are allowed.
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX ix_payments_account_id ON payments (account_id);