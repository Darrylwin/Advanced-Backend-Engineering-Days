#!/usr/bin/env bash
# Database helpers: psql runs inside the compose container, so nothing is installed locally.
# Expects ACCOUNT_ID, INITIAL_BALANCE_CENTS and SCRIPT_DIR to be defined by the caller.

COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/../../docker-compose.yml}"
DB_NAME="${DB_NAME:-day02}"

# psql_query <sql> [database]: unaligned, tuples-only output, easy to parse.
psql_query() {
  docker compose -f "$COMPOSE_FILE" exec -T postgres \
    psql -U backend-engineer -d "${2:-$DB_NAME}" -X -q -A -t -F '|' -v ON_ERROR_STOP=1 -c "$1"
}

# One database per day keeps the Flyway histories of the different days apart.
ensure_database() {
  local exists
  exists="$(psql_query "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" postgres)"
  if [[ "$exists" != "1" ]]; then
    psql_query "CREATE DATABASE ${DB_NAME}" postgres > /dev/null
  fi
}

# Demo data lives here, not in the migrations: the schema stays free of fixtures.
reset_database() {
  psql_query "TRUNCATE accounts;
              INSERT INTO accounts (id, balance_cents) VALUES (${ACCOUNT_ID}, ${INITIAL_BALANCE_CENTS});" > /dev/null
}

query_balance() { psql_query "SELECT balance_cents FROM accounts WHERE id = ${ACCOUNT_ID}"; }