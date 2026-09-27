#!/usr/bin/env bash
#
# check-release-050-gate-test.sh — prova local (sem rede, sem gh) da logica do
# scripts/check_release_050_gate.sh, o GATE DE RELEASE 0.5.0
# (D-RELEASE-0.5.0-GATE, diretiva da mantenedora 20/09).
#
# RED-first do mecanismo (o gate nao pode dar verde facil):
#   (1) selftest embutido: fixture limpa = exit 0; suja = exit 1; inconclusiva
#       (sem paridade/estabilidade) = exit 2;
#   (2) rodada offline com overrides de fonte de dados classifica as 7 condicoes
#       e nunca crasha.
#
# Uso: scripts/tests/check-release-050-gate-test.sh   (exit 0 = tudo verde)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
GATE="scripts/check_release_050_gate.sh"
FAILED=0
pass() { echo "  ok  — $1"; }
fail() { echo "  FAIL— $1"; FAILED=1; }

bash "$GATE" --selftest >/dev/null 2>&1; rc=$?
[ "$rc" = 0 ] && pass "selftest embutido (limpa=0, suja=1, inconclusiva=2)" || fail "selftest embutido rc=$rc"

T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
printf '100\tdocumentation,post-1.0\n' > "$T/issues"
printf 'EG-1\tDONE\nEG-5\tOPEN\n' > "$T/eg"
printf 'PARITY: 100%%\n' > "$T/parity"
printf 'STABILITY: GREEN\n' > "$T/stab"
printf '0\n' > "$T/pending"
printf 'DECISIONS.md\nroadmap.md\n' > "$T/loose"
: > "$T/spec"
printf 'EN open/partial (0):\nPT open/partial (0):\n' > "$T/kb"

out="$(R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_TSV="$T/eg" R050_PARITY_FILE="$T/parity" \
  R050_STABILITY_FILE="$T/stab" R050_PENDING_FILE="$T/pending" R050_LOOSE_MD_FILE="$T/loose" \
  R050_SPEC_GAPS_FILE="$T/spec" R050_KNOWN_BUGS_CMD="cat $T/kb" R050_OPEN_BLOCKS=0 \
  bash "$GATE" 2>&1)"; rc=$?
printf '%s' "$out" | grep -q '0.5.0 release gate' || fail "sem cabecalho do gate"
case "$rc" in
  1) pass "EG-5 aberto -> RED (exit 1)";;
  *) fail "esperado exit 1 com EG-5 aberto, veio $rc";;
esac
printf '%s' "$out" | grep -q 'edges .*RED' || fail "condicao edges nao ficou RED"
printf '%s' "$out" | grep -q 'parity .*GREEN' || fail "paridade medida nao ficou GREEN"
printf '%s' "$out" | grep -q 'bugs_gaps .*GREEN' || fail "bugs_gaps limpo nao ficou GREEN"

# ── cenario RED-first: gh autenticado mas a QUERY de issues FALHA nao pode
#    virar "0 open bug issues" GREEN (falso-verde de API transitoria). ────────
FAKE="$T/bin"; mkdir -p "$FAKE"
cat > "$FAKE/gh" <<'GHEOF'
#!/usr/bin/env bash
case "$*" in
  *"issue list"*) exit 1 ;;
  *) exit 0 ;;
esac
GHEOF
chmod +x "$FAKE/gh"
out="$(PATH="$FAKE:$PATH" R050_EG_TSV="$T/eg" R050_PARITY_FILE="$T/parity" \
  R050_STABILITY_FILE="$T/stab" R050_PENDING_FILE="$T/pending" R050_LOOSE_MD_FILE="$T/loose" \
  R050_SPEC_GAPS_FILE="$T/spec" R050_KNOWN_BUGS_CMD="cat $T/kb" R050_OPEN_BLOCKS=0 \
  bash "$GATE" 2>&1)"
printf '%s' "$out" | grep -q 'bug_issues .*GREEN' && fail "gh issue list falhou mas bug_issues ficou GREEN (falso-verde)"
printf '%s' "$out" | grep -q 'bug_issues .*UNKNOWN' && pass "query gh falha -> bug_issues UNKNOWN (nao GREEN)" || fail "query gh falha nao virou UNKNOWN"

# ── cenario RED-first: comando do ledger de bugs FALHANDO (saida ilegivel) nao
#    pode virar "0 known-bugs" GREEN. ───────────────────────────────────────
out="$(R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_TSV="$T/eg" R050_PARITY_FILE="$T/parity" \
  R050_STABILITY_FILE="$T/stab" R050_PENDING_FILE="$T/pending" R050_LOOSE_MD_FILE="$T/loose" \
  R050_SPEC_GAPS_FILE="$T/spec" R050_KNOWN_BUGS_CMD="false" R050_OPEN_BLOCKS=0 \
  bash "$GATE" 2>&1)"
printf '%s' "$out" | grep -q 'bugs_gaps .*GREEN' && fail "ledger de bugs falhou mas bugs_gaps ficou GREEN (falso-verde)"
printf '%s' "$out" | grep -q 'bugs_gaps .*UNKNOWN' && pass "ledger falho -> bugs_gaps UNKNOWN (nao GREEN)" || fail "ledger falho nao virou UNKNOWN"

# ── cenario RED-first: tabela EG VAZIA/ilegivel nao pode virar "sem aresta" GREEN ─
: > "$T/egempty"
out="$(R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_TSV="$T/egempty" R050_PARITY_FILE="$T/parity" \
  R050_STABILITY_FILE="$T/stab" R050_PENDING_FILE="$T/pending" R050_LOOSE_MD_FILE="$T/loose" \
  R050_SPEC_GAPS_FILE="$T/spec" R050_KNOWN_BUGS_CMD="cat $T/kb" R050_OPEN_BLOCKS=0 \
  bash "$GATE" 2>&1)"
printf '%s' "$out" | grep -q 'edges .*GREEN' && fail "tabela EG vazia mas edges ficou GREEN (falso-verde)"
printf '%s' "$out" | grep -q 'edges .*UNKNOWN' && pass "EG vazia -> edges UNKNOWN (nao GREEN)" || fail "EG vazia nao virou UNKNOWN"

# ── cenario RED-first: fonte de decisao ILEGIVEL nao pode virar decisions GREEN ─
out="$(R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_TSV="$T/eg" R050_PARITY_FILE="$T/parity" \
  R050_STABILITY_FILE="$T/stab" R050_PENDING_FILE="$T/nao-existe" R050_LOOSE_MD_FILE="$T/loose" \
  R050_SPEC_GAPS_FILE="$T/spec" R050_KNOWN_BUGS_CMD="cat $T/kb" R050_OPEN_BLOCKS=0 \
  bash "$GATE" 2>&1)"
printf '%s' "$out" | grep -q 'decisions .*GREEN' && fail "fonte de decisao ilegivel mas decisions ficou GREEN (falso-verde)"
printf '%s' "$out" | grep -q 'decisions .*UNKNOWN' && pass "decisao ilegivel -> decisions UNKNOWN (nao GREEN)" || fail "decisao ilegivel nao virou UNKNOWN"

# ── cenario RED-first: lista de docs soltos ILEGIVEL nao pode virar loose_docs GREEN ─
out="$(R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_TSV="$T/eg" R050_PARITY_FILE="$T/parity" \
  R050_STABILITY_FILE="$T/stab" R050_PENDING_FILE="$T/pending" R050_LOOSE_MD_FILE="$T/nao-existe" \
  R050_SPEC_GAPS_FILE="$T/spec" R050_KNOWN_BUGS_CMD="cat $T/kb" R050_OPEN_BLOCKS=0 \
  bash "$GATE" 2>&1)"
printf '%s' "$out" | grep -q 'loose_docs .*GREEN' && fail "lista de docs ilegivel mas loose_docs ficou GREEN (falso-verde)"
printf '%s' "$out" | grep -q 'loose_docs .*UNKNOWN' && pass "lista ilegivel -> loose_docs UNKNOWN (nao GREEN)" || fail "lista ilegivel nao virou UNKNOWN"

if [ "$FAILED" = 1 ]; then
  echo "== check-release-050-gate: VERMELHA =="
  exit 1
fi
echo "== check-release-050-gate: VERDE =="
