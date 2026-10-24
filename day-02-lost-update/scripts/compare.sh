#!/usr/bin/env bash
# Day 2 demo: lost update, and three ways to fix it.
#
# For each strategy (Spring profile), the script starts the application, fires concurrent
# withdrawals at one account, stops the application, then reads the real balance from the database.
#
# Usage: ./scripts/compare.sh [strategy ...]
#        strategies: naive optimistic pessimistic atomic (default: all four)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

readonly PORT="${PORT:-8082}"
readonly BASE_URL="http://localhost:${PORT}"
readonly ACCOUNT_ID=1
readonly INITIAL_BALANCE_CENTS=100000
readonly AMOUNT_CENTS=1000
readonly CONCURRENT_REQUESTS=20
readonly STARTUP_TIMEOUT_SECONDS=90
readonly PAYLOAD="{\"amountCents\":${AMOUNT_CENTS}}"
readonly LABEL_WIDTH=27

# shellcheck source=lib/ui.sh
source "$SCRIPT_DIR/lib/ui.sh"
# shellcheck source=lib/db.sh
source "$SCRIPT_DIR/lib/db.sh"

TMP_DIR="$(mktemp -d)"
APP_PID=""

# Column widths (in characters) of the table currently being drawn.
BOX_WIDTHS=()

stop_app() {
  if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" 2> /dev/null; then
    kill "$APP_PID" 2> /dev/null || true
    wait "$APP_PID" 2> /dev/null || true
  fi
  APP_PID=""
}

cleanup() {
  stop_app
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

# Same as die, but first erases the in-place progress line so the error starts on a clean line.
fail() {
  clear_progress
  die "$@"
}

build_application() {
  local mvn_cmd="mvn"
  if [[ -x "$PROJECT_DIR/mvnw" ]]; then
    mvn_cmd="$PROJECT_DIR/mvnw"
  fi

  progress "  building the application..."
  if ! (cd "$PROJECT_DIR" && "$mvn_cmd" -q -DskipTests package > "$TMP_DIR/build.log" 2>&1); then
    clear_progress
    cat "$TMP_DIR/build.log" >&2
    die "Build failed."
  fi
  clear_progress
}

locate_jar() {
  local jars=("$PROJECT_DIR"/target/*.jar)
  [[ -f "${jars[0]}" ]] || die "No jar found in target/."
  printf '%s' "${jars[0]}"
}

start_app() {
  local strategy="$1" jar="$2"
  java -jar "$jar" --spring.profiles.active="$strategy" --server.port="$PORT" \
    > "$TMP_DIR/$strategy.log" 2>&1 &
  APP_PID=$!
}

wait_until_up() {
  local strategy="$1" waited=0 mode
  until curl -fs "$BASE_URL/actuator/health" > /dev/null 2>&1; do
    if ! kill -0 "$APP_PID" 2> /dev/null; then
      clear_progress
      tail -n 30 "$TMP_DIR/$strategy.log" >&2
      die "The application stopped while starting ($strategy)."
    fi
    if (( waited >= STARTUP_TIMEOUT_SECONDS )); then
      fail "Startup timeout ($strategy)."
    fi
    sleep 1
    waited=$(( waited + 1 ))
  done

  mode="$(curl -fs "$BASE_URL/actuator/info" | jq -r '.mode // "unknown"')"
  [[ "$mode" == "$strategy" ]] || fail "Profile mismatch: expected '$strategy', the server reports '$mode'."
}

# Fires the withdrawals from separate processes. Only the curl processes are awaited: a bare
# "wait" would also wait for the application, which runs in the background of this shell.
fire_requests() {
  local strategy="$1" results="$TMP_DIR/$1.results" pid i
  local pids=()
  : > "$results"

  for i in $(seq 1 "$CONCURRENT_REQUESTS"); do
    curl -s -o /dev/null -X POST "$BASE_URL/api/v1/accounts/$ACCOUNT_ID/withdrawals" \
      -H 'Content-Type: application/json' -d "$PAYLOAD" \
      -w '%{http_code} %{time_total}\n' >> "$results" &
    pids+=($!)
  done

  for pid in "${pids[@]}"; do
    wait "$pid" || true
  done
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

# label_value <label> <value>: aligned key/value line used in the summary block.
label_value() {
  printf '  %s%-*s%s %s\n' "$DIM" "$LABEL_WIDTH" "$1" "$RESET" "$2"
}

# legend <color> <term> <description>: one line of the column legend.
legend() {
  printf '  %s%-8s%s  %s%s%s\n' "$BOLD$1" "$2" "$RESET" "$DIM" "$3" "$RESET"
}

print_header() {
  # The last column holds one symbol per request, so its width follows CONCURRENT_REQUESTS.
  BOX_WIDTHS=(11 8 8 7 8 4 8 "$CONCURRENT_REQUESTS")

  box_rule '┌' '┬' '┐'
  box_row \
    "$BOLD" "Strategy" \
    "$BOLD" "Accepted" \
    "$BOLD" "Rejected" \
    "$BOLD" "Balance" \
    "$BOLD" "Expected" \
    "$BOLD" "Lost" \
    "$BOLD" "Slowest" \
    "$BOLD" "Outcomes"
  box_rule '├' '┼' '┤'
}

print_footer() {
  box_rule '└' '┴' '┘'
}

print_row() {
  local strategy="$1" results="$TMP_DIR/$1.results"
  local accepted rejected other slowest_ms code strip="" strip_length=0 padding
  local balance expected lost verdict_color

  read -r accepted rejected other slowest_ms < <(
    awk '{ if ($1 == 204) a++; else if ($1 == 409) r++; else o++; if ($2 > m) m = $2 }
         END { printf "%d %d %d %.0f\n", a, r, o, m * 1000 }' "$results"
  )

  while read -r code _; do
    case "$code" in
      204) strip+="${GREEN}✔${RESET}" ;;
      409) strip+="${YELLOW}✘${RESET}" ;;
      *)   strip+="${RED}?${RESET}" ;;
    esac
    strip_length=$(( strip_length + 1 ))
  done < "$results"

  # The strip contains ANSI codes, so printf cannot pad it: pad with the plain symbol count instead.
  padding=$(( CONCURRENT_REQUESTS - strip_length ))
  if (( padding > 0 )); then
    strip+="$(printf '%*s' "$padding" '')"
  fi

  balance="$(query_balance)"
  expected=$(( INITIAL_BALANCE_CENTS - accepted * AMOUNT_CENTS ))
  lost=$(( (balance - expected) / AMOUNT_CENTS ))

  verdict_color="$GREEN"
  if [[ "$lost" -ne 0 || "$other" -ne 0 ]]; then
    verdict_color="$BOLD$RED"
  fi

  clear_progress
  box_row \
    "$BOLD" "$strategy" \
    "$GREEN" "$accepted" \
    "$YELLOW" "$rejected" \
    "$verdict_color" "$(money "$balance")" \
    "$RESET" "$(money "$expected")" \
    "$verdict_color" "$lost" \
    "$RESET" "${slowest_ms} ms" \
    "$RESET" "$strip"
}

run_strategy() {
  local strategy="$1" jar="$2"

  progress "  ${strategy}: starting..."
  start_app "$strategy" "$jar"
  wait_until_up "$strategy"
  reset_database

  progress "  ${strategy}: ${CONCURRENT_REQUESTS} concurrent withdrawals..."
  fire_requests "$strategy"
  stop_app

  print_row "$strategy"
}

main() {
  require_tools curl jq docker java

  local strategies=("$@")
  if [[ ${#strategies[@]} -eq 0 ]]; then
    strategies=(naive optimistic pessimistic atomic)
  fi

  if ! ensure_database; then
    die "PostgreSQL is not reachable. Run 'docker compose up -d' from the repository root."
  fi

  banner "DAY 2 · LOST UPDATE" \
    "${CONCURRENT_REQUESTS} concurrent withdrawals of $(money "$AMOUNT_CENTS") from the same account"
  label_value "starting balance" "$BOLD$(money "$INITIAL_BALANCE_CENTS")$RESET"
  label_value "expected if nothing is lost" \
    "$BOLD$(money $(( INITIAL_BALANCE_CENTS - CONCURRENT_REQUESTS * AMOUNT_CENTS )))$RESET"
  printf '\n'

  build_application
  local jar strategy
  jar="$(locate_jar)"

  print_header
  for strategy in "${strategies[@]}"; do
    run_strategy "$strategy" "$jar"
  done
  print_footer

  printf '\n'
  legend "$GREEN" "Accepted" "requests answered with 204: the client believes the debit happened."
  legend "$YELLOW" "Rejected" "requests refused with 409 to keep the balance correct."
  legend "$RED" "Lost" "accepted debits that never reached the balance."
  printf '\n'
}

main "$@"