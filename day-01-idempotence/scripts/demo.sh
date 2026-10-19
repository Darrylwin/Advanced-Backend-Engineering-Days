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

# Column widths (in characters) of the table currently being drawn.
BOX_WIDTHS=()

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

# --- Table rendering --------------------------------------------------------

# Prints a horizontal bar of $1 box-drawing characters.
hbar() {
  local count="$1" bar="" i
  for ((i = 0; i < count; i++)); do
    bar+='─'
  done
  printf '%s' "$bar"
}

# box_rule <left> <middle> <right>: draws a border line sized to BOX_WIDTHS.
box_rule() {
  local left="$1" middle="$2" right="$3" i last=$(( ${#BOX_WIDTHS[@]} - 1 ))

  printf '  %s%s' "$DIM" "$left"
  for i in "${!BOX_WIDTHS[@]}"; do
    hbar $(( BOX_WIDTHS[i] + 2 ))
    if (( i < last )); then
      printf '%s' "$middle"
    fi
  done
  printf '%s%s\n' "$right" "$RESET"
}

# box_row <color> <text> [<color> <text> ...]: one table row, one pair per column.
box_row() {
  local i=0 color text

  printf '  %s│%s' "$DIM" "$RESET"
  while (( $# )); do
    color="$1"; text="$2"; shift 2
    printf ' %s%-*s%s %s│%s' "$color" "${BOX_WIDTHS[i]}" "$text" "$RESET" "$DIM" "$RESET"
    i=$(( i + 1 ))
  done
  printf '\n'
}

# label_value <label> <value>: aligned key/value line used in the summary blocks.
label_value() {
  printf '  %s%-16s%s %s\n' "$DIM" "$1" "$RESET" "$2"
}

# --- Responses table --------------------------------------------------------

print_responses_header() {
  BOX_WIDTHS=(3 6 9 18 14 8)

  box_rule '┌' '┬' '┐'
  box_row "$BOLD" "#" "$BOLD" "HTTP" "$BOLD" "Replayed" "$BOLD" "Payment" "$BOLD" "Balance after" "$BOLD" "Time"
  box_rule '├' '┼' '┤'
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

  box_row \
    "$BOLD" "$n" \
    "$BOLD$status_color" "$status" \
    "$replayed_color" "$replayed" \
    "$RESET" "$payment" \
    "$RESET" "$balance" \
    "$DIM" "$elapsed"
}

print_responses_footer() {
  box_rule '└' '┴' '┘'
}

# --- Database table ---------------------------------------------------------

print_database_rows() {
  BOX_WIDTHS=(5 9 16)

  box_rule '┌' '┬' '┐'
  box_row "$BOLD" "id" "$BOLD" "amount" "$BOLD" "idempotency_key"
  box_rule '├' '┼' '┤'

  local id amount stored_key
  while IFS='|' read -r id amount stored_key; do
    if [[ "$stored_key" == "(none)" ]]; then
      box_row "$RESET" "$id" "$RESET" "$(money "$amount")" "$RED" "$stored_key"
    else
      box_row "$RESET" "$id" "$RESET" "$(money "$amount")" "$CYAN" "${stored_key:0:8}…"
    fi
  done < <(query_payments)

  box_rule '└' '┴' '┘'
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
  label_value "server profile" "$BOLD$mode_color$mode$RESET"

  step "1. Initial state"
  label_value "account" "#$ACCOUNT_ID"
  label_value "balance" "$BOLD$(money "$(query_balance)")$RESET"
  label_value "payments" "$(query_payment_count)"

  step "2. The double click"
  note "  Two identical requests, fired at the same instant, with the same Idempotency-Key."
  printf '\n'
  label_value "request" "${BOLD}POST${RESET} /api/v1/payments"
  label_value "body" "$PAYLOAD"
  label_value "Idempotency-Key" "$CYAN$key$RESET"
  printf '\n'

  local pid_1 pid_2
  send_payment 1 "$key" & pid_1=$!
  send_payment 2 "$key" & pid_2=$!
  wait "$pid_1" || die "Request 1 failed. Is the server still up?"
  wait "$pid_2" || die "Request 2 failed. Is the server still up?"

  print_responses_header
  print_response_row 1
  print_response_row 2
  print_responses_footer

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