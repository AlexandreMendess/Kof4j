#!/usr/bin/env bash
# R1 boundary gate (IMPLEMENTATION-UNIVERSAL-PLATFORM §15 R1 / D-UNIVERSAL):
# "core -> base stdlib -> platform -> official packages -> interop" as a
# machine-checked invariant. Two checks, one ledger file:
#   scripts/stdlib_boundary.txt   lines: "<namespace> <layer> [note...]"
# Layers: base-stdlib | platform | official-package | interop | excluded
#   (excluded = the matched token is NOT a namespace — noise; each one needs
#    a justification note) | documented-only (spec surface without a code
#    literal; checked for existence, not for sources).
# Hard-deny (heavy domain, never base stdlib, AGENTS.md invariant 1):
# ml bio hpc data sci cloud infra game.
# New namespaces must enter the ledger with a layer — the ledger is the
# decision; the gate forces the decision to exist (R6: never silent).
# Exit: 0 clean · 1 violation or drift. --selftest proves the gate bites.
set -euo pipefail
cd "$(dirname "$0")/.."

HARD_DENY="ml bio hpc data sci cloud infra game"
LEDGER="${R1_LEDGER:-scripts/stdlib_boundary.txt}"
# R5 promotions (D4-A): namespaces promoted to `stable`. Empty = zero promotions
# (every namespace is born `experimental`). A promotion adds the ns here AND a
# registry row in docs/backend-parity.md §Stability (R5 DoD) — the gate denies a
# `stable` tier that is not pinned here, and a pin that is not `stable` (drift).
STABLE_ALLOWLIST=""
# bare (non-`kof.`-prefixed) stdlib surfaces dispatched by identifier —
# each MUST have a ledger line; adding one here without a ledger entry fails.
BARE_SURFACES="json"
SCAN_ROOTS=(kof-compiler/src/main kof-script/src/main kof-c-compiler/src/main kof-cli/src/main)

scan_ns() {
  grep -rhoE '"kof\.[a-z0-9]+"' --include=*.java "${SCAN_ROOTS[@]}" 2>/dev/null \
    | tr -d '"' | sed 's/^kof\.//' | sort -u
  for b in $BARE_SURFACES; do
    grep -rq "\"$b\"\\.equals" --include=*.java "${SCAN_ROOTS[@]}" 2>/dev/null && echo "$b" || true
  done
}

if [ "${1:-}" = "--selftest" ]; then
  tmp="kof-compiler/src/main/java/R1SelfTestProbe.java"
  tldg="$(mktemp "${TMPDIR:-/tmp}/r1-ledger.XXXXXX")"
  trap 'rm -f "$tmp" "$tldg"' EXIT
  printf 'final class R1SelfTestProbe { static final String P = "kof.ml"; static final String N = "kof.quantum"; }\n' > "$tmp"
  out="$(set +e; bash "$0" 2>&1; echo "rc=$?")"
  echo "$out" | grep -q "heavy domain — forbidden" || { echo "SELFTEST FAIL: heavy-domain not caught"; exit 1; }
  echo "$out" | grep -q "undocumented namespace 'kof.quantum'" || { echo "SELFTEST FAIL: drift not caught"; exit 1; }
  echo "$out" | grep -q "rc=1" || { echo "SELFTEST FAIL: gate did not fail"; exit 1; }
  # R5 tier gate (D4-A): a namespace line without a tier must fail.
  printf 'math base-stdlib — no tier here\n' > "$tldg"
  o2="$(set +e; R1_LEDGER="$tldg" bash "$0" 2>&1; echo "rc=$?")"
  echo "$o2" | grep -q "no valid R5 tier" || { echo "SELFTEST FAIL: missing tier not caught"; exit 1; }
  # R5 tier gate: an unpinned promotion to `stable` must fail (never silent, never bulk).
  printf 'math base-stdlib stable — illegal promotion\n' > "$tldg"
  o3="$(set +e; R1_LEDGER="$tldg" bash "$0" 2>&1; echo "rc=$?")"
  echo "$o3" | grep -q "declared stable without the R5 DoD" || { echo "SELFTEST FAIL: illegal promotion not caught"; exit 1; }
  echo "SELFTEST OK: heavy-domain + drift + R5 tier (missing/promotion) caught, gate rc=1"
  exit 0
fi

[ -f "$LEDGER" ] || { echo "missing ledger $LEDGER" >&2; exit 1; }
allowed="$(grep -vE '^\s*(#|$)' "$LEDGER" | awk '{print $1}' | sort -u)"
layers_of() { grep -vE '^\s*(#|$)' "$LEDGER" | awk -v n="$1" '$1==n{print $2}'; }

fail=0
found="$(scan_ns)"

for ns in $found; do
  for d in $HARD_DENY; do
    [ "$ns" = "$d" ] && { echo "VIOLATION: 'kof.$ns' is a HARD-DENY heavy domain — forbidden in base stdlib/platform (R1/AGENTS invariant 1; official package only)"; fail=1; }
  done
  if ! grep -qx "$ns" <<< "$allowed"; then
    echo "VIOLATION: undocumented namespace 'kof.$ns' — not in $LEDGER. Run the §3.4 decision order and add a ledger line with its layer (never silently)."
    fail=1
  fi
done

for ns in $allowed; do
  layer="$(layers_of "$ns" | head -1)"
  case "$layer" in documented-only|excluded) continue ;; esac
  grep -qx "$ns" <<< "$found" || echo "note: ledger entry 'kof.$ns' no longer found in sources — prune or confirm" >&2
done

# R5 tier gate (D4-A): every namespace line declares a tier; `stable` is denied
# unless pinned in STABLE_ALLOWLIST (promotion registry), and a pin must match.
while read -r ns layer tier _rest; do
  case "$layer" in documented-only|excluded) continue ;; esac
  case "$tier" in
    experimental) ;;
    stable)
      grep -qw "$ns" <<< "$STABLE_ALLOWLIST" \
        || { echo "VIOLATION: '$ns' declared stable without the R5 DoD — promotion needs the backend-parity.md §Stability registry row + a pin in STABLE_ALLOWLIST (D4-A: born experimental, never in bulk, never by age)"; fail=1; } ;;
    *)
      echo "VIOLATION: '$ns' has no valid R5 tier ('${tier:-<missing>}') — every namespace line needs 'experimental' or 'stable' (D4-A; R5 machine gate)"; fail=1 ;;
  esac
done < <(grep -vE '^\s*(#|$)' "$LEDGER")

for ns in $STABLE_ALLOWLIST; do
  tier="$(grep -vE '^\s*(#|$)' "$LEDGER" | awk -v n="$ns" '$1==n{print $3; exit}')"
  [ "$tier" = stable ] \
    || { echo "VIOLATION: '$ns' is pinned stable in STABLE_ALLOWLIST but the ledger tier is '${tier:-<missing>}' (drift)"; fail=1; }
done

if [ "$fail" = 0 ]; then
  echo "R1 boundary OK: $(grep -cvE '^\s*(#|$)|^\S+\s+excluded' "$LEDGER" || true) namespaces registered"
  grep -vE '^\s*(#|$)' "$LEDGER" | awk '$2!="excluded"{c[$2]++} END{for(l in c) printf "  %-18s %s\n", l":", c[l]}' | sort
  grep -vE '^\s*(#|$)' "$LEDGER" | awk '$2!="excluded"{t[$3]++} END{for(k in t) printf "  tier %-13s %s\n", k":", t[k]}' | sort
fi
exit "$fail"
