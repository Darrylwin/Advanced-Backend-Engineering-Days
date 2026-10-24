CREATE TABLE accounts
(
    id            BIGINT PRIMARY KEY,
    balance_cents BIGINT NOT NULL,
    -- Only read by the optimistic strategy; the other mappings ignore it.
    version       BIGINT NOT NULL DEFAULT 0,
    -- Safety net for every strategy: whatever the application does, a balance can never go negative.
    CONSTRAINT ck_accounts_balance_non_negative CHECK (balance_cents >= 0)
);