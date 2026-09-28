#!/usr/bin/env bash
#
# test-suite-audit-test.sh — prova local (sem maven, sem suite) do
# `scripts/test-suite-audit.sh` (Fase 2 do `test-architecture-plan`,
# `D-TEST-ARCHITECTURE-GO`). Le uma arvore FAKE de fontes de teste.
#
# RED-first: sem fonte = rc 3; com fonte, sleeps/oversized/duplicados precisam
# bater exatamente com a fixture.
#
# Uso: scripts/tests/test-suite-audit-test.sh   (exit 0 = todos os cenarios passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
AUDIT="scripts/test-suite-audit.sh"
FAILED=0
pass() { echo "  ok  — $1"; }
fail() { echo "  FAIL— $1"; FAILED=1; }
expect() { [ "$2" = "$3" ] && pass "$1 (rc=$3)" || fail "$1: esperado rc=$2, veio rc=$3"; }

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/mod/src/test/java/dev/x" "$TMP/mod/src/test/java/dev/y"
cat > "$TMP/mod/src/test/java/dev/x/ATest.java" <<'JAVA'
package dev.x;
class ATest {
    void shared() { Thread.sleep(1); }
    void aOnly() { }
}
JAVA
cat > "$TMP/mod/src/test/java/dev/y/BTest.java" <<'JAVA'
package dev.y;
class BTest {
    void shared() { }
    void bOnly() { }
}
JAVA
# oversized: >= 500 lines (500 pad lines + 1 method)
BIG="$TMP/mod/src/test/java/dev/y/BigTest.java"
{ echo "package dev.y;"; echo "class BigTest {"; echo "    void big() { }"; for i in $(seq 1 500); do echo "    // pad $i"; done; echo "}"; } > "$BIG"

# ── cenario 1: sem fonte de teste => rc 3 ────────────────────────────────
mkdir -p "$TMP/empty"
out="$(bash "$AUDIT" --root "$TMP/empty" 2>&1)"; rc=$?
expect "arvore sem fontes nao certifica" 3 "$rc"
grep -q "TEST-AUDIT: unknown" <<<"$out" && pass "unknown emitido (sem fonte)" || fail "nao emitiu unknown"

# ── cenario 2: sleeps medidos ─────────────────────────────────────────────
out="$(bash "$AUDIT" --root "$TMP" 2>&1)"; rc=$?
expect "arvore com fontes mede" 0 "$rc"
grep -qE "sleeps=1 oversized\(>=500\)=1 duplicate-across-classes=1" <<<"$out" \
  && pass "totais (1 sleep / 1 oversized / 1 duplicado)" || fail "totais errados: $(grep TEST-AUDIT <<<"$out")"

# ── cenario 3: o site de sleep certo ──────────────────────────────────────
grep -q "ATest.java:3:    void shared() { Thread.sleep(1); }" <<<"$out" \
  && pass "sleep localizado (ATest.java:3)" || fail "sleep nao localizado"

# ── cenario 4: oversized lista o arquivo grande ───────────────────────────
grep -q "BigTest.java" <<<"$out" && pass "oversized lista BigTest" || fail "oversized sem BigTest"

# ── cenario 5: duplicado entre classes (shared em ATest+BTest) ─────────────
grep -qE "shared" <<<"$out" && pass "duplicado 'shared' presente" || fail "duplicado 'shared' ausente"
grep -qE "aOnly|bOnly" <<<"$out" && fail "metodo nao-duplicado vazou" || pass "nao-duplicados fora do relatorio"

# ── cenario 6: saida markdown ─────────────────────────────────────────────
bash "$AUDIT" --root "$TMP" --md "$TMP/out.md" --quiet >/dev/null 2>&1
grep -q "sleeps=1 oversized(>=500)=1 duplicate-across-classes=1" "$TMP/out.md" \
  && pass "markdown com resumo medido" || fail "markdown sem resumo"
grep -q '`shared`' "$TMP/out.md" && pass "markdown com o duplicado" || fail "markdown sem duplicado"

if [ "$FAILED" = 1 ]; then
  echo "== RESULTADO: FALHOU =="
  exit 1
fi
echo "== RESULTADO: todos os cenarios OK =="
