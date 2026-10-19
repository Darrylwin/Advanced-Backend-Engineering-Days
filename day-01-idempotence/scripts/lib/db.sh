#!/usr/bin/env bash
# Database helpers: psql runs inside the compose container, so nothing is installed locally.
# Expects ACCOUNT_ID, INITIAL_BALANCE_CENTS and SCRIPT_DIR to be defined by the caller.

COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/../../docker-compose.yml}"

# Unaligned, tuples-only output with '|' as field separator: easy to parse, no decoration.
psql_query() {
  docker compose -f "$COMPOSE_FILE" exec -T postgres \
    psql -U backend-engineer -d backend-engineering -X -q -A -t -F '|' -v ON_ERROR_STOP=1 -c "$1"
}

# Demo data lives here, not in the migrations: the schema stays free of fixtures.
reset_database() {
  psql_query "TRUNCATE payments, accounts RESTART IDENTITY;
              INSERT INTO accounts (id, balance_cents) VALUES (${ACCOUNT_ID}, ${INITIAL_BALANCE_CENTS});" > /dev/null
}

query_balance()       { psql_query "SELECT balance_cents FROM accounts WHERE id = ${ACCOUNT_ID}"; }
query_payment_count() { psql_query "SELECT count(*) FROM payments"; }
query_payments()      { psql_query "SELECT id, amount_cents, COALESCE(idempotency_key, '(none)') FROM payments ORDER BY id"; }