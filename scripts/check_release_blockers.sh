#!/usr/bin/env bash
# check_release_blockers.sh — machine gate of the KOF 1.0 EXIT GATE for the
# "zero OPEN release-blockers" item (normative text:
# docs/PROPOSAL-1.0-EXIT-GATE.md §11; decision D-RELEASE-1.0).
#
# Why: "the absence of a label called release-blocker" proves nothing (§11).
# Every OPEN issue must be EXPLICITLY in exactly one of five categories:
#
#   1.0-blocks       BLOCKS 1.0        — must be closed before the first RC
#   1.0-outside      OUTSIDE 1.0 SURFACE — real issue, subject outside the 1.0 surface
#   post-1.0         POST-1.0          — accepted after 1.0, never a 1.0 blocker
#   not-a-bug        NOT A BUG / CLOSE — not a bug; close it
#   tracking/contract TRACKING         — release-process/umbrella issue (5th
#                     category, maintainer 20/09): valid during stabilization,
#                     must still close before the RC
#
# The gate is RED when any OPEN issue has ZERO or MORE THAN ONE of these labels
# (ambiguous/absent classification cannot be ignored). `--rc-gate` additionally
# fails while any `1.0-blocks` is still OPEN — that is the §8 checklist item.
#
# Ledger: scripts/release-blockers.tsv is the auditable record (issue, category,
# reason, owner); when present, the gate checks that the ledger covers exactly
# the OPEN issues and agrees with the labels.
#
# Usage:
#   scripts/check_release_blockers.sh                 # classify gate (labels + ledger)
#   scripts/check_release_blockers.sh --rc-gate       # + zero OPEN 1.0-blocks (RC cut)
#   scripts/check_release_blockers.sh --selftest      # planted fixture must classify right
#   scripts/check_release_blockers.sh --fixture FILE  # offline: TSV "number<TAB>labels"
#
# Exit codes: 0 healthy; 1 unclassified/conflicting (or ledger drift);
#             2 selftest failure; 3 API unavailable; 4 RC gate: 1.0-blocks OPEN.
#
# RED-first (D-RELEASE-1.0 §23.7): the classification logic was exercised by
# --selftest on planted fixtures BEFORE any label existed on the repo.

set -u
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
GH_REPO="${RELEASE_BLOCKERS_REPO:-KofLang/Kof4j}"
LEDGER="$REPO_DIR/scripts/release-blockers.tsv"

CATEGORIES="1.0-blocks 1.0-outside post-1.0 not-a-bug tracking/contract"

# classify <labels-csv> -> prints the single category, or "UNCLASSIFIED" /
# "CONFLICT:<a>+<b>". Pure function: no network, no state — this is what
# --selftest exercises.
classify() {
  local labels hit="" n=0 c
  labels=" $(printf '%s' "$1" | tr ',' ' ' | tr -s '[:space:]' ' ') "
  for c in $CATEGORIES; do
    case "$labels" in
      *" $c "*) n=$((n+1)); hit="${hit:+$hit+}$c" ;;
    esac
  done
  if [ "$n" -eq 0 ]; then echo "UNCLASSIFIED"; else
    if [ "$n" -eq 1 ]; then echo "$hit"; else echo "CONFLICT:$hit"; fi
  fi
}

# read_issues <fixture|-> -> "number<TAB>labels" lines for OPEN issues only.
# Exit status is the API's: 0 with EMPTY output means "zero OPEN issues"
# (a valid, successful answer), NOT "API down". RELEASE_BLOCKERS_GH_CMD is the
# test hook that stands in for the `gh api` call (same contract: rc + stdout).
read_issues() {
  local fixture="$1"
  if [ -n "$fixture" ]; then
    cat "$fixture"
    return 0
  fi
  if [ -n "${RELEASE_BLOCKERS_GH_CMD:-}" ]; then
    eval "$RELEASE_BLOCKERS_GH_CMD"
    return $?
  fi
  # NOTE: /issues returns PRs too — filter them out (pull_request field).
  gh api "/repos/$GH_REPO/issues?state=open&per_page=100" --paginate \
    --jq '.[] | select(.pull_request == null) | [(.number|tostring), ([.labels[].name]|join(","))] | @tsv' 2>/dev/null
}

selftest() {
  local fixture tmp rc=0
  tmp="$(mktemp)"; fixture="$tmp"
  # planted fixture: one of each valid category + two violations
  cat > "$fixture" <<'FIX'
101	bug,1.0-blocks
102	enhancement,1.0-outside
103	documentation,post-1.0
104	question,not-a-bug
105	bug
106	bug,1.0-blocks,post-1.0
107	1.0-blocks
108	documentation,tracking/contract
FIX
  local out
  out="$(run_gate "$fixture" "" 2>&1)"; rc=$?
  local fail=0
  # expect: 2 violations (105 unclassified, 106 conflict); 2 1.0-blocks (101,107)
  printf '%s\n' "$out" | grep -q "UNCLASSIFIED  *#105" || { echo "selftest: did not flag #105"; fail=1; }
  printf '%s\n' "$out" | grep -q "CONFLICT.*#106"       || { echo "selftest: did not flag #106"; fail=1; }
  printf '%s\n' "$out" | grep -q "#101"                 || { echo "selftest: lost #101"; fail=1; }
  printf '%s\n' "$out" | grep -q "tracking/contract *#108" || { echo "selftest: did not classify #108"; fail=1; }
  [ "$rc" -eq 1 ] || { echo "selftest: expected exit 1, got $rc"; fail=1; }
  # rc-gate variant must fail because 1.0-blocks are open
  run_gate "$fixture" "--rc-gate" >/dev/null 2>&1; local rc2=$?
  [ "$rc2" -eq 4 ] || { echo "selftest: --rc-gate expected exit 4, got $rc2"; fail=1; }
  # empty-but-SUCCESSFUL API (zero OPEN issues) must read as "0 open
  # 1.0-blocks", never NAO-AVALIADO — the bug that pinned `edges` to UNKNOWN
  # whenever the repo had no open issue (rc 0 + empty != API down).
  local out3 rc3
  out3="$(LEDGER=/dev/null RELEASE_BLOCKERS_GH_CMD=':' run_gate "" "" 2>&1)"; rc3=$?
  printf '%s\n' "$out3" | grep -q -- "-- 0 open 1.0-blocks" \
    || { echo "selftest: empty API not counted as 0 open 1.0-blocks"; fail=1; }
  printf '%s\n' "$out3" | grep -q "NAO-AVALIADO" \
    && { echo "selftest: empty API wrongly reported NAO-AVALIADO"; fail=1; }
  [ "$rc3" -eq 0 ] || { echo "selftest: empty API expected rc 0, got $rc3"; fail=1; }
  # a FAILING API (rc != 0) must stay NAO-AVALIADO (rc 3), never 0 blocks
  local out4 rc4
  out4="$(LEDGER=/dev/null RELEASE_BLOCKERS_GH_CMD='exit 3' run_gate "" "" 2>&1)"; rc4=$?
  printf '%s\n' "$out4" | grep -q "NAO-AVALIADO" \
    || { echo "selftest: failing API not NAO-AVALIADO"; fail=1; }
  [ "$rc4" -eq 3 ] || { echo "selftest: failing API expected rc 3, got $rc4"; fail=1; }
  rm -f "$tmp"
  if [ "$fail" -eq 0 ]; then echo "selftest: OK (classification + rc-gate fixtures)"; return 0; fi
  return 2
}

# run_gate <fixture|-> <extra-flag> -> report; rc per the contract
run_gate() {
  local fixture="$1" extra="$2" rows rc=0 violations=0 blocks=0 read_rc=0
  rows="$(read_issues "$fixture")"; read_rc=$?
  if [ "$read_rc" -ne 0 ] && [ -z "$fixture" ]; then
    echo "  NAO-AVALIADO — API indisponivel (rate limit?); o CI continua sendo a porta real"
    return 3
  fi
  local n labels cat
  while IFS=$'\t' read -r n labels; do
    [ -n "$n" ] || continue
    cat="$(classify "$labels")"
    case "$cat" in
      UNCLASSIFIED) echo "  UNCLASSIFIED  #$n  (no release-blocker label)"; violations=$((violations+1)) ;;
      CONFLICT:*)   echo "  CONFLICT      #$n  (${cat#CONFLICT:})"; violations=$((violations+1)) ;;
      1.0-blocks)   blocks=$((blocks+1)); echo "  1.0-blocks    #$n" ;;
      *)            echo "  $cat    #$n" ;;
    esac
  done <<< "$rows"

  if [ -z "$fixture" ] && [ -f "$LEDGER" ]; then
    local llabels lcat2
    while IFS=$'\t' read -r ln lcat _rest; do
      case "$ln" in ''|'#'*|'issue'*) continue ;; esac
      llabels="$(printf '%s\n' "$rows" | awk -F'\t' -v n="$ln" '$1==n{print $2}')"
      if [ -z "$llabels" ]; then
        echo "  LEDGER-DRIFT  #$ln  (in ledger but not OPEN)"; violations=$((violations+1)); continue
      fi
      lcat2="$(classify "$llabels")"
      if [ "$lcat2" != "$lcat" ]; then
        echo "  LEDGER-DRIFT  #$ln  (ledger=$lcat labels=$lcat2)"; violations=$((violations+1))
      fi
    done < "$LEDGER"
    # reverse coverage: every OPEN issue must be recorded in the ledger
    local rn rlabels
    while IFS=$'\t' read -r rn rlabels; do
      [ -n "$rn" ] || continue
      awk -F'\t' -v n="$rn" '$1==n{found=1} END{exit found?0:1}' "$LEDGER" \
        || { echo "  LEDGER-MISSING  #$rn  (OPEN but absent from the ledger)"; violations=$((violations+1)); }
    done <<< "$rows"
  fi

  echo "  -- $blocks open 1.0-blocks; $violations classification violation(s)"
  [ "$violations" -gt 0 ] && rc=1
  if [ "$extra" = "--rc-gate" ] && [ "$blocks" -gt 0 ]; then
    echo "  RED — RC gate: $blocks OPEN 1.0-blocks (must be 0 before the first RC)"
    rc=4
  fi
  return "$rc"
}

case "${1:-}" in
  --selftest) selftest; exit $? ;;
  --fixture)
    [ -n "${2:-}" ] || { echo "uso: $0 --fixture FILE" >&2; exit 64; }
    run_gate "$2" "${3:-}"; exit $? ;;
  --rc-gate)  echo "== release-blockers gate (rc-gate) =="; run_gate "" "--rc-gate"; exit $? ;;
  ""|--help|-h)
    echo "== release-blockers gate =="
    run_gate "" ""; exit $? ;;
  *) echo "uso: $0 [--rc-gate|--selftest|--fixture FILE]" >&2; exit 64 ;;
esac
