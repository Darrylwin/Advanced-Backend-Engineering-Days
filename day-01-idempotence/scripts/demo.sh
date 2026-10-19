#!/usr/bin/env bash
# Day 1 demo: a double click on "Pay".
#
# The same script runs against both server profiles. The server reports its own
# profile on /actuator/info, so the only thing that changes is the outcome.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

readonly BASE_URL="${BASE_URL:-http://localhost:8080}"
readonly ACCOUNT_ID=1
readonly INITIAL_BALANCE_CENTS=100000
readonly AMOUNT_CENTS=10000
readonly PAYLOAD="{\"accountId\":${ACCOUNT_ID},\"amountCents\":${AMOUNT_CENTS}}"

# shellcheck source=lib/ui.sh
source "$SCRIPT_DIR/lib/ui.sh"
# shellcheck source=lib/db.sh
source "$SCRIPT_DIR/lib/db.sh"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

new_idempotency_key() {
  if command -v uuidgen > /dev/null 2>&1; then
    uuidgen | tr '[:upper:]' '[:lower:]'
  elif command -v openssl > /dev/null 2>&1; then
    openssl rand -hex 16
  elif [ -r /dev/urandom ]; then
    head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n'
  else
    printf '%s-%s-%s' "$(date +%s)" "$(date +%N 2>/dev/null || echo $RANDOM)" "$RANDOM"
  fi
}

detect_server_mode() {
  local mode
  if ! mode="$(curl -fs --max-time 3 "$BASE_URL/actuator/info" | jq -r '.mode // "unknown"')"; then
    die "Cannot reach $BASE_URL. Start the application with the 'broken' or 'fixed' profile first."
  fi
  printf '%s' "$mode"
}

# Sends one payment request; headers, body and duration are kept under $TMP_DIR/<n>.*
send_payment() {
  local n="$1" key="$2"
  curl -sS -X POST "$BASE_URL/api/v1/payments" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $key" \
    -d "$PAYLOAD" \
    -D "$TMP_DIR/$n.headers" -o "$TMP_DIR/$n.body" \
    -w '%{time_total}' > "$TMP_DIR/$n.time"
}

print_response_row() {
  local n="$1" status replayed payment balance elapsed status_color replayed_color

  status="$(head -n1 "$TMP_DIR/$n.headers" | awk '{ print $2 }')"
  elapsed="$(awk -v t="$(cat "$TMP_DIR/$n.time")" 'BEGIN { printf "%.0f ms", t * 1000 }')"

  if grep -qi '^idempotent-replayed: true' "$TMP_DIR/$n.headers"; then
    replayed="yes"; replayed_color="$CYAN"
  else
    replayed="no"; replayed_color="$DIM"
  fi

  if [[ "$status" == 2* ]]; then
    status_color="$GREEN"
    payment="#$(jq -r '.paymentId' "$TMP_DIR/$n.body")"
    balance="$(money "$(jq -r '.balanceAfterCents' "$TMP_DIR/$n.body")")"
  else
    status_color="$YELLOW"
    payment="$(jq -r '.code // "error"' "$TMP_DIR/$n.body")"
    balance="-"
  fi

  printf '  '
  cell "$RESET" 3 "$n"
  cell "$status_color" 5 "$status"
  cell "$replayed_color" 9 "$replayed"
  cell "$RESET" 9 "$payment"
  cell "$RESET" 14 "$balance"
  printf '%s\n' "$elapsed"
}

print_database_rows() {
  printf '  '
  cell "$DIM" 5 "id"
  cell "$DIM" 9 "amount"
  printf '%sidempotency_key%s\n' "$DIM" "$RESET"

  local id amount stored_key
  while IFS='|' read -r id amount stored_key; do
    printf '  '
    cell "$RESET" 5 "$id"
    cell "$RESET" 9 "$(money "$amount")"
    if [[ "$stored_key" == "(none)" ]]; then
      printf '%s%s%s\n' "$RED" "$stored_key" "$RESET"
    else
      printf '%s…\n' "${stored_key:0:8}"
    fi
  done < <(query_payments)
}

main() {
  require_tools curl jq docker

  local mode key mode_color
  mode="$(detect_server_mode)"
  reset_database
  key="$(new_idempotency_key)"

  mode_color="$GREEN"
  if [[ "$mode" == "broken" ]]; then
    mode_color="$RED"
  fi

  banner "DAY 1 · IDEMPOTENCE" "A client double-clicks \"Pay\". What does the server do?"
  printf '  %-16s %s%s%s\n' "server profile" "$BOLD$mode_color" "$mode" "$RESET"

  step "1. Initial state"
  printf '  account #%s   balance %s%s%s   payments %s\n' \
    "$ACCOUNT_ID" "$BOLD" "$(money "$(query_balance)")" "$RESET" "$(query_payment_count)"

  step "2. The double click"
  note "  Two identical requests, fired at the same instant, with the same Idempotency-Key."
  printf '  %sPOST%s /api/v1/payments   %s\n' "$BOLD" "$RESET" "$PAYLOAD"
  printf '  %sIdempotency-Key:%s %s\n\n' "$BOLD" "$RESET" "$key"

  local pid_1 pid_2
  send_payment 1 "$key" & pid_1=$!
  send_payment 2 "$key" & pid_2=$!
  wait "$pid_1" || die "Request 1 failed. Is the server still up?"
  wait "$pid_2" || die "Request 2 failed. Is the server still up?"

  printf '  '
  cell "$DIM" 3 "#"
  cell "$DIM" 5 "HTTP"
  cell "$DIM" 9 "Replayed"
  cell "$DIM" 9 "Payment"
  cell "$DIM" 14 "Balance after"
  printf '%sTime%s\n' "$DIM" "$RESET"
  print_response_row 1
  print_response_row 2

  step "3. What the database says"
  print_database_rows

  step "4. Verdict"
  local balance count expected
  balance="$(query_balance)"
  count="$(query_payment_count)"
  expected=$(( INITIAL_BALANCE_CENTS - AMOUNT_CENTS ))

  if [[ "$count" -eq 1 && "$balance" -eq "$expected" ]]; then
    ok "One intent, one debit. Balance is $(money "$balance"), as expected."
  else
    ko "Double charge: expected $(money "$expected"), got $(money "$balance") which means $count payments for a single intent."
  fi
  printf '\n'
}

main "$@"