#!/usr/bin/env bash
#
# check-release-blockers-test.sh — prova local (sem rede) da logica do
# scripts/check_release_blockers.sh, o gate mecanico do item §11 do EXIT GATE
# 1.0 (D-RELEASE-1.0). Usa --fixture (TSV canned) e o --selftest embutido:
# nenhuma chamada ao GitHub.
#
# Cobre a RED-first do mecanismo: issue sem label (UNCLASSIFIED) e issue com
# dois labels de categoria (CONFLICT) tem de reprovar; fixture limpa passa;
# --rc-gate reprova enquanto houver 1.0-blocks aberto; a 5a categoria
# (tracking/contract) classifica sem virar violacao.
#
# Uso: scripts/tests/check-release-blockers-test.sh   (exit 0 = tudo verde)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
GATE="scripts/check_release_blockers.sh"
FAILED=0

expect() { # desc, expected_rc, actual_rc
  if [ "$2" != "$3" ]; then echo "!!! $1: esperado rc=$2, veio rc=$3"; FAILED=1
  else echo "ok: $1 (rc=$3)"; fi
}

bash "$GATE" --selftest >/dev/null 2>&1; expect "selftest embutido" 0 $?

tmp="$(mktemp)"
cat > "$tmp" <<'FIX'
201	bug,1.0-blocks
202	documentation,post-1.0
FIX
out="$(bash "$GATE" --fixture "$tmp" 2>&1)"; rc=$?
expect "fixture limpa (2 classificadas, 1 bloco)" 0 "$rc"
printf '%s\n' "$out" | grep -q "1 open 1.0-blocks" || { echo "!!! contagem de 1.0-blocks errada"; FAILED=1; }

out="$(bash "$GATE" --fixture "$tmp" --rc-gate 2>&1)"; rc=$?
expect "rc-gate com 1 bloco -> rc 4" 4 "$rc"

cat > "$tmp" <<'FIX'
301	bug
FIX
out="$(bash "$GATE" --fixture "$tmp" 2>&1)"; rc=$?
expect "fixture nao-classificada -> rc 1" 1 "$rc"
printf '%s\n' "$out" | grep -q "UNCLASSIFIED  *#301" || { echo "!!! nao sinalizou #301"; FAILED=1; }

cat > "$tmp" <<'FIX'
401	1.0-blocks,post-1.0
FIX
out="$(bash "$GATE" --fixture "$tmp" 2>&1)"; rc=$?
expect "fixture conflito -> rc 1" 1 "$rc"
printf '%s\n' "$out" | grep -q "CONFLICT.*#401" || { echo "!!! nao sinalizou #401"; FAILED=1; }

cat > "$tmp" <<'FIX'
501	documentation,tracking/contract
FIX
out="$(bash "$GATE" --fixture "$tmp" 2>&1)"; rc=$?
expect "fixture tracking/contract -> rc 0" 0 "$rc"
printf '%s\n' "$out" | grep -q "tracking/contract *#501" || { echo "!!! nao classificou #501"; FAILED=1; }

cat > "$tmp" <<'FIX'
601	tracking/contract,not-a-bug
FIX
out="$(bash "$GATE" --fixture "$tmp" 2>&1)"; rc=$?
expect "fixture conflito tracking -> rc 1" 1 "$rc"
printf '%s\n' "$out" | grep -q "CONFLICT.*#601" || { echo "!!! nao sinalizou #601"; FAILED=1; }

rm -f "$tmp"
[ "$FAILED" -eq 0 ] && echo "== check-release-blockers: VERDE" || echo "== check-release-blockers: VERMELHA"
exit "$FAILED"
