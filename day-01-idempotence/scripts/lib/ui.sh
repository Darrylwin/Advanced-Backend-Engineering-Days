#!/usr/bin/env bash
# Terminal rendering helpers. Sourced by demo.sh, never executed directly.

# Colors are disabled when the output is not a terminal or when NO_COLOR is set.
if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
  BOLD=$'\033[1m'; DIM=$'\033[2m'
  RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; CYAN=$'\033[36m'
  RESET=$'\033[0m'
else
  BOLD=''; DIM=''; RED=''; GREEN=''; YELLOW=''; CYAN=''; RESET=''
fi

readonly RULE_WIDTH=62

rule() {
  printf '%s' "$DIM"
  printf '─%.0s' $(seq 1 "$RULE_WIDTH")
  printf '%s\n' "$RESET"
}

banner() {
  printf '\n%s%s%s\n' "$BOLD" "$1" "$RESET"
  printf '%s%s%s\n' "$DIM" "$2" "$RESET"
  rule
}

step() { printf '\n%s%s%s\n' "$BOLD$CYAN" "$1" "$RESET"; }
note() { printf '%s%s%s\n' "$DIM" "$1" "$RESET"; }
ok()   { printf '%s✔ %s%s\n' "$BOLD$GREEN" "$1" "$RESET"; }
ko()   { printf '%s✘ %s%s\n' "$BOLD$RED" "$1" "$RESET"; }
die()  { printf '%s✘ %s%s\n' "$BOLD$RED" "$1" "$RESET" >&2; exit 1; }

# cell <color> <width> <text>: pads the plain text first, then colors it, so ANSI codes never break alignment.
cell() { printf '%s%-*s%s ' "$1" "$2" "$3" "$RESET"; }

# money <cents>: 100000 -> 1000.00
money() { printf '%d.%02d' $(( $1 / 100 )) $(( $1 % 100 )); }

require_tools() {
  local tool
  for tool in "$@"; do
    command -v "$tool" > /dev/null 2>&1 || die "Missing required tool: $tool"
  done
}