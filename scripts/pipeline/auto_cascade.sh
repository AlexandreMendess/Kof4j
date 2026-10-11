#!/usr/bin/env bash
# auto_cascade.sh — per-commit promotion automation (quality-pipeline,
# automation-enforcement; maintainer live order 11/10: "era pra cada commit na
# lab avaliar se iria ter promoção automatica e merge com as proximas").
#
# Reproduces as code the cascade the maintainer demonstrated by hand twice on
# 11/10 (PRs #799-#801 and #802-#804): evaluate the lab tip, then
# lab → testing → stable → prerelease, then the pre-release publish.
#
# Subcommands:
#   evaluate   rc 0 = the cascade may fire; rc 1 = honest skip (reasons printed)
#   plan       print the hops the cascade would take + the publish flag
#   cascade    execute (needs GH_TOKEN; AUTO_CASCADE_DRY_RUN=1 prints actions
#              without executing — the local proof of the decision paths)
#
# Env (cascade): SUITE_VERDICT=success|failure (the 14.3 measurement, computed
# by the workflow via stability-report.sh on the safe-suite log),
# SUITE_SHA (the measured tip; defaults to the lab tip).
#
# NEVER touches `main`: the main-gate ruleset keeps it human-only (AGENTS.md —
# AI does not merge into main). The date-less final release stays the
# maintainer's merge + dispatch.
set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)"

REPO="${GITHUB_REPOSITORY:-KofLang/Kof4j}"
DRY="${AUTO_CASCADE_DRY_RUN:-}"
HOPS=(lab:testing testing:stable stable:prerelease)

say() { echo "auto-cascade: $*"; }
die() { echo "auto-cascade: REFUSED — $*" >&2; exit 1; }

origin_tip() { git rev-parse --verify "origin/$1" 2>/dev/null; }

trees_equal() {
    [ "$(git rev-parse "$1^{tree}")" = "$(git rev-parse "$2^{tree}")" ]
}

# The hops whose two ends do not already carry the same tree.
needed_hops() {
    local hop from to f t
    for hop in "${HOPS[@]}"; do
        from="${hop%%:*}"; to="${hop##*:}"
        f="$(origin_tip "$from")"; t="$(origin_tip "$to")"
        [ -n "$f" ] || { echo "ERR origin/$from missing (fetch first)"; return 2; }
        [ -n "$t" ] || { echo "ERR origin/$to missing (fetch first)"; return 2; }
        trees_equal "$f" "$t" || printf '%s\n' "$hop"
    done
}

# Publish (the pre-release artifacts) only when VERSION has no tags yet —
# a version already published (final or dated) waits for the next bump.
version_fresh() {
    local v
    v="$(cat VERSION)"
    [ -z "$(git ls-remote --tags origin "kof-${v}*" 2>/dev/null)" ]
}

# The CI "Structural quality gates" measurement (ci.yml), verbatim steps, plus
# the D-LAB-STABILITY blocking entries (rc-gate, open bugs) and divergence.
cmd_evaluate() {
    local rc=0 bugs version rev
    git fetch origin lab testing stable prerelease --quiet

    version="$(cat VERSION)"
    rev="$(grep -oP '(?<=<revision>)[^<]+' pom.xml)"
    # The ci.yml semantics on a clean checkout: bump must be a no-op. Here the
    # restore goes back to the PRE-BUMP BYTES (snapshot), never to HEAD — a
    # restore-to-HEAD would silently discard uncommitted version-file work on
    # an agent's tree (measured incident). NO `git stash` either: with clean
    # VERSION/pom a stash pop consumes an UNRELATED entry from the host's
    # stash stack (second measured incident).
    local vsrc=(VERSION pom.xml kof-compiler/src/main/resources/dev/kof/version.properties)
    local snap="$PWD/.auto-cascade-vsrc.$$"
    mkdir -p "$snap"
    local f dirty_pre=0
    for f in "${vsrc[@]}"; do
        cp -p "$f" "$snap/$(basename "$f")" 2>/dev/null
        git diff --quiet -- "$f" || dirty_pre=1
    done
    if [ "$version" != "$rev" ]; then
        say "RED version-sources ($version != $rev)"; rc=1
    elif ! scripts/bump-version.sh >/dev/null 2>&1; then
        say "RED version-sources (bump-version.sh failed)"; rc=1
    elif ! git diff --exit-code -- "${vsrc[@]}" >/dev/null; then
        say "RED version-sources (bump-version.sh leaves a diff)"; rc=1
    fi
    for f in "${vsrc[@]}"; do
        [ -f "$snap/$(basename "$f")" ] && cp -p "$snap/$(basename "$f")" "$f"
    done
    rm -rf "$snap"
    [ "$dirty_pre" = "0" ] || say "note: version files carried uncommitted local state (restored byte-for-byte)"
    scripts/check_500.sh >/dev/null 2>&1 || { say "RED check_500"; rc=1; }
    scripts/check_stdlib_boundary.sh --selftest >/dev/null 2>&1 && scripts/check_stdlib_boundary.sh >/dev/null 2>&1 \
        || { say "RED stdlib_boundary"; rc=1; }
    python3 scripts/gen_signatures.py --selftest >/dev/null 2>&1 && python3 scripts/gen_signatures.py check >/dev/null 2>&1 \
        || { say "RED gen_signatures"; rc=1; }
    bash scripts/tests/run-agent-tests.sh >/dev/null 2>&1 || { say "RED agent-tests"; rc=1; }
    scripts/check_release_blockers.sh --rc-gate >/dev/null 2>&1 \
        || { say "RED rc-gate:"; scripts/check_release_blockers.sh --rc-gate >&2 || true; rc=1; }
    bugs="$(gh issue list --repo "$REPO" --state open --label bug --limit 1000 --json number --jq 'length' 2>/dev/null || echo '?')"
    [ "$bugs" = "0" ] || { say "RED open bug issue(s): $bugs"; rc=1; }
    local hops
    hops="$(needed_hops)" || die "$(needed_hops 2>&1)"
    if [ -z "$hops" ]; then say "SKIP no divergence — the stages already carry the lab tree"; rc=1; fi

    if [ "$rc" = 0 ]; then say "GREEN — the cascade may fire"; else say "evaluate rc=1 — honest skip (the cut slips; D-LAB-STABILITY)"; fi
    return "$rc"
}

cmd_plan() {
    local hops
    hops="$(needed_hops)" || die "cannot plan: $(needed_hops 2>&1)"
    if [ -n "$hops" ]; then
        echo "hops:"
        printf '%s\n' "$hops" | sed 's/^/  promote: /'
    else
        echo "hops: (none — no divergence)"
    fi
    if version_fresh; then
        echo "  publish: yes (kof-$(cat VERSION)* tags absent)"
    else
        echo "  publish: no (kof-$(cat VERSION)* tags exist — skipped until the next bump)"
    fi
}

do_() {
    if [ -n "$DRY" ]; then echo "DRY: $*"; else "$@"; fi
}

post_check() { # name head_sha title summary
    do_ gh api "repos/$REPO/check-runs" \
        -f name="$1" -f head_sha="$2" -f status=completed -f conclusion=success \
        -f "output[title]=$3" -f "output[summary]=$4"
}

cmd_cascade() {
    [ -n "${SUITE_VERDICT:-}" ] || die "SUITE_VERDICT required (the 14.3 suite measurement)"
    [ "$SUITE_VERDICT" = "success" ] || die "suite verdict is failure — the cut slips (D-LAB-STABILITY)"
    git fetch origin lab testing stable prerelease --quiet
    local measured now_tip
    measured="${SUITE_SHA:-$(origin_tip lab)}"
    now_tip="$(origin_tip lab)"
    if [ "$now_tip" != "$measured" ]; then
        # The suite measured $measured but lab moved to $now_tip while it ran
        # (measured on live run 38131510159, 11/10: the 40-min suite finished
        # AFTER upstream landed). Promoting an unmeasured tip is refused; the
        # workflow re-dispatches to measure the new tip (exit 42 = STALE_TIP).
        say "STALE_TIP — lab moved while measuring: measured $measured, current $now_tip"
        exit 42
    fi
    local hops
    hops="$(needed_hops)" || die "cannot resolve stages"
    if [ -z "$hops" ]; then say "no divergence — nothing to do"; exit 0; fi

    local hop from to fsha pr note try
    for hop in $hops; do
        from="${hop%%:*}"; to="${hop##*:}"
        fsha="$(origin_tip "$from")"
        if [ "$fsha" = "$measured" ]; then note="measured tip"
        elif trees_equal "$fsha" "$measured"; then note="tree-identical to measured tip $measured"
        else die "hop head $from ($fsha) is not tree-identical to the measured tip — refusing (fail-closed)"
        fi
        local url
        pr="$(gh pr list --repo "$REPO" --head "$from" --base "$to" --state open \
              --json number --jq '.[0].number' 2>/dev/null || true)"
        if [ -z "$pr" ]; then
            url="$(do_ gh pr create --repo "$REPO" --head "$from" --base "$to" \
                --title "promote: $from → $to (auto-cascade)" \
                --body "Per-commit auto-cascade (automation-enforcement; maintainer live order 11/10). Suite verdict **$SUITE_VERDICT** measured on \`$measured\` (14.3 measurement — stability-report.sh over the safe-suite log).")"
            if [ -n "$DRY" ]; then pr="0"; else
                pr="$(sed -nE 's|.*/pull/([0-9]+).*|\1|p' <<<"${url:-}")"
            fi
        else
            say "adopting open promotion PR #$pr ($from → $to)"
        fi
        case "$pr" in
            ''|*[!0-9]*) die "no promotion PR number for $from → $to (create said: ${url:-<empty>}) — refusing to merge blind (fail-closed)" ;;
        esac
        # The ruleset-required status checks, posted with the MEASURED verdict
        # and its provenance (direct on the measured tip; tree-identity on the
        # downstream merge commits — never an unmeasured claim).
        post_check "Build + Tests" "$fsha" "auto-cascade: $SUITE_VERDICT" \
            "safe-suite verdict $SUITE_VERDICT certified on $measured; $note."
        post_check "Structural quality gates" "$fsha" "auto-cascade: $SUITE_VERDICT" \
            "structural sweep rc=0 certified on $measured; $note."
        local merged=0
        for try in 1 2 3; do
            if [ -n "$DRY" ]; then echo "DRY: gh pr merge $pr --repo $REPO --merge"; merged=1; break; fi
            if gh pr merge "$pr" --repo "$REPO" --merge 2>/dev/null; then merged=1; break; fi
            sleep 5
        done
        [ "$merged" = "1" ] || die "merge of PR #$pr refused 3× — leaving it open (fail-closed, auditable)"
        git fetch origin "$to" --quiet
        say "promoted $from → $to (PR #$pr)"
    done

    if version_fresh; then
        do_ gh workflow run release.yml --repo "$REPO" --ref prerelease
        say "release.yml dispatched on prerelease (VERSION $(cat VERSION) is fresh)"
    else
        say "publish skipped — kof-$(cat VERSION)* tags already exist (the next bump publishes)"
    fi
}

case "${1:-}" in
    evaluate) cmd_evaluate ;;
    plan) cmd_plan ;;
    cascade) cmd_cascade ;;
    *) echo "uso: auto_cascade.sh {evaluate|plan|cascade}" >&2; exit 2 ;;
esac
