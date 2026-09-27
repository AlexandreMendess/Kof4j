#!/usr/bin/env bash
#
# stability-report-test.sh — prova local (sem maven, sem rede) da logica do
# scripts/stability-report.sh, o medidor da condicao 4 do gate 0.5.0
# (`D-RELEASE-0.5.0-GATE`). Le logs FAKE: nenhuma suite e executada aqui.
#
# RED-first: GREEN so com failures=0 E errors=0 E o log estampado pelo
# safe-suite.sh (SUITE-SHA do tip + SUITE-DIRTY=0); failure/error reprova; log
# de outro sha, de arvore suja, sem SUITE-SHA ou sem TOTAL nao certifica (exit 3).
#
# Uso: scripts/tests/stability-report-test.sh   (exit 0 = todos os cenarios passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
REPORT="scripts/stability-report.sh"
FAILED=0
pass() { echo "  ok  — $1"; }
fail() { echo "  FAIL— $1"; FAILED=1; }
expect() { [ "$2" = "$3" ] && pass "$1 (rc=$3)" || fail "$1: esperado rc=$2, veio rc=$3"; }

# ── cenario 1: selftest RED-first embutido ────────────────────────────────
out="$(bash "$REPORT" --selftest 2>&1)"; rc=$?
expect "selftest embutido" 0 "$rc"
printf '%s' "$out" | grep -q "0F/0E" && pass "verificador interno reprova" || fail "selftest nao provou a reprovacao"

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
printf 'SUITE-SHA: deadbee\nSUITE-DIRTY: 0\nbuilding...\nTOTAL: tests=3233 failures=0 errors=0 skipped=13\n' > "$TMP/green.log"
printf 'building...\nTOTAL: tests=3233 failures=1 errors=0 skipped=13\n' > "$TMP/fail.log"
printf 'building...\nTOTAL: tests=3233 failures=0 errors=2 skipped=13\n' > "$TMP/err.log"
printf 'sem total\n' > "$TMP/none.log"
printf 'building...\nTOTAL: tests=3233 failures=0 errors=0 skipped=13\n' > "$TMP/unbound.log"
printf 'SUITE-SHA: 0000000\nSUITE-DIRTY: 0\nbuilding...\nTOTAL: tests=3233 failures=0 errors=0 skipped=13\n' > "$TMP/othersha.log"
printf 'SUITE-SHA: deadbee\nSUITE-DIRTY: 2\nbuilding...\nTOTAL: tests=3233 failures=0 errors=0 skipped=13\n' > "$TMP/dirty.log"

# ── cenario 2: log 0F/0E => GREEN ─────────────────────────────────────────
out="$(bash "$REPORT" --suite-log "$TMP/green.log" --sha deadbee 2>&1)"; rc=$?
expect "log 0F/0E certifica" 0 "$rc"
grep -q "STABILITY: GREEN" <<<"$out" && pass "GREEN emitido" || fail "sem GREEN"
grep -q "sha=deadbee" <<<"$out" && pass "veredito amarrado ao SHA" || fail "SHA nao amarrado"

# ── cenario 3: 1 failure => RED ───────────────────────────────────────────
out="$(bash "$REPORT" --suite-log "$TMP/fail.log" 2>&1)"; rc=$?
expect "failure reprova" 1 "$rc"
grep -q "STABILITY: RED" <<<"$out" && pass "RED emitido (failure)" || fail "failure passou (falso-verde)"

# ── cenario 4: 1 error => RED ─────────────────────────────────────────────
out="$(bash "$REPORT" --suite-log "$TMP/err.log" 2>&1)"; rc=$?
expect "error reprova" 1 "$rc"
grep -q "STABILITY: RED" <<<"$out" && pass "RED emitido (error)" || fail "error passou (falso-verde)"

# ── cenario 5: log sem TOTAL => nao certifica (exit 3) ────────────────────
out="$(bash "$REPORT" --suite-log "$TMP/none.log" 2>&1)"; rc=$?
expect "log sem TOTAL nao certifica" 3 "$rc"

# ── cenario 6: log ausente => nao certifica (exit 3) ──────────────────────
out="$(bash "$REPORT" --suite-log "$TMP/naoexiste.log" 2>&1)"; rc=$?
expect "log ausente nao certifica" 3 "$rc"

# ── cenario 7: log de OUTRO sha => nao certifica (exit 3) ─────────────────
out="$(bash "$REPORT" --suite-log "$TMP/othersha.log" --sha deadbee 2>&1)"; rc=$?
expect "log de outro sha nao certifica" 3 "$rc"
grep -q "STABILITY: unknown" <<<"$out" && pass "unknown emitido (outro sha)" || fail "outro sha nao deu unknown"

# ── cenario 8: arvore SUJA na corrida => nao certifica (exit 3) ───────────
out="$(bash "$REPORT" --suite-log "$TMP/dirty.log" --sha deadbee 2>&1)"; rc=$?
expect "log de arvore suja nao certifica" 3 "$rc"

# ── cenario 9: log SEM SUITE-SHA (corrida antiga) => nao certifica (exit 3) ─
out="$(bash "$REPORT" --suite-log "$TMP/unbound.log" --sha deadbee 2>&1)"; rc=$?
expect "log sem SUITE-SHA nao certifica" 3 "$rc"

if [ "$FAILED" = 1 ]; then
  echo "== RESULTADO: FALHOU =="
  exit 1
fi
echo "== RESULTADO: todos os cenarios OK =="
