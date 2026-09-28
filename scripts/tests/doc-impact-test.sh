#!/usr/bin/env bash
#
# doc-impact-test.sh — prova local de scripts/check_doc_impact.sh (#648).
# Fixtures SEM git: tokens + consumidores + mudancas num diretorio temporario.
# RED-first do mecanismo: o detector DEVE pegar o consumidor vivo esquecido e
# so fechar verde quando o mesmo diff toca o consumidor (ou waiver).
set -uo pipefail
HERE="$(cd "$(dirname "$(readline -f "$0" 2>/dev/null || echo "$0")")" && pwd)"
REPO="$(cd "$(dirname "$HERE")/.." && pwd)"
GATE="$REPO/scripts/check_doc_impact.sh"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
fail() { echo "FAIL: $*"; exit 1; }
[ -x "$GATE" ] || [ -f "$GATE" ] || fail "gate ausente: $GATE"

mk_corpus() { # $1=dir
  mkdir -p "$1/docs/bugs-and-gaps" "$1/docs/development" "$1/learn" "$1/docs/tooling" "$1/docs/stdlib"
  printf '# ledger\n\n## §999 — face X — Status: ✅ FIXED 28/09\n\n## §998 — face Y\n' > "$1/docs/bugs-and-gaps/known-bugs.md"
  printf '| parity cell | the divergence is §999 |\n' > "$1/docs/backend-parity.md"
  printf '## D-FAKE-999 — decision\n\nbody\n' > "$1/docs/development/DECISIONS.md"
  printf 'see the plan at `docs/development/plan-moved.md` here\n' > "$1/learn/40-x.md"
  printf 'current text\n' > "$1/docs/tooling/PLAN-MOVED.md"
}

# ---- caso 1: § status mudou, consumidor vivo NAO atualizado -> FAIL -------
C="$T/c1"; mk_corpus "$C"
printf 'status\t§999\n' > "$C/tok"; printf 'docs/bugs-and-gaps/known-bugs.md\n' > "$C/ch"
if out="$(bash "$GATE" --corpus "$C" --tokens-file "$C/tok" --changed-file "$C/ch" --waivers /dev/null 2>&1)"; then
  fail "caso1: deveria FAIL (backend-parity.md cita §999 e nao foi tocado): $out"
fi
echo "$out" | grep -qE '§999 ->.*docs/backend-parity.md -> FAIL' || fail "caso1 saida: $out"

# ---- caso 2: consumidor atualizado no mesmo diff -> PASS -------------------
printf 'docs/bugs-and-gaps/known-bugs.md\ndocs/backend-parity.md\n' > "$C/ch"
bash "$GATE" --corpus "$C" --tokens-file "$C/tok" --changed-file "$C/ch" --waivers /dev/null >/dev/null 2>&1 \
  || fail "caso2: deveria PASS com consumidor no diff"

# ---- caso 3: waiver historico (prefixo) -> PASS ----------------------------
printf '*\tdocs/\n' > "$C/wv"; printf 'docs/bugs-and-gaps/known-bugs.md\n' > "$C/ch"
bash "$GATE" --corpus "$C" --tokens-file "$C/tok" --changed-file "$C/ch" --waivers "$C/wv" >/dev/null 2>&1 \
  || fail "caso3: waiver '* docs/' deveria deixar passar"

# ---- caso 4: D-* novo com consumidor esquecido -> FAIL ---------------------
printf 'decision\tD-FAKE-999\n' > "$C/tok2"; printf 'docs/development/DECISIONS.md\n' > "$C/ch2"
printf 'aligned with D-FAKE-999 (must update when it changes)\n' >> "$C/docs/development/roadmap.md"
if bash "$GATE" --corpus "$C" --tokens-file "$C/tok2" --changed-file "$C/ch2" --waivers /dev/null >/dev/null 2>&1; then
  fail "caso4: deveria FAIL (roadmap cita D-FAKE-999 sem toque)"
fi

# ---- caso 5: documento movido; consumidor do caminho antigo -> FAIL; e o
#      waiver por token+consumidor especifico nao vale para outro consumidor --
printf 'moved\tdocs/development/plan-moved.md\n' > "$C/tok3"
printf 'docs/tooling/PLAN-MOVED.md\n' > "$C/ch3"
if out="$(bash "$GATE" --corpus "$C" --tokens-file "$C/tok3" --changed-file "$C/ch3" --waivers /dev/null 2>&1)"; then
  fail "caso5: deveria FAIL (learn/40-x.md aponta o caminho antigo): $out"
fi
printf 'docs/development/plan-moved.md\tlearn/40-x.md\n' > "$C/wv3"
bash "$GATE" --corpus "$C" --tokens-file "$C/tok3" --changed-file "$C/ch3" --waivers "$C/wv3" >/dev/null 2>&1 \
  || fail "caso5b: waiver especifico token->consumidor deveria PASS"

# ---- caso 6: sem tokens -> PASS trivial ------------------------------------
: > "$C/tok4"; printf 'README.md\n' > "$C/ch4"
bash "$GATE" --corpus "$C" --tokens-file "$C/tok4" --changed-file "$C/ch4" --waivers /dev/null >/dev/null 2>&1 \
  || fail "caso6: sem tokens deveria PASS"

# ---- caso 7: modo git real no repositorio (diff do proprio tip) ------------
bash "$GATE" >/dev/null 2>&1 || fail "caso7: gate no diff real do repositorio quebrou/FALHOU"

# ---- caso 8: hook registrado no agent-verify (contrato #648) --------------
grep -q "doc_impact" "$REPO/scripts/agent-verify.sh" || fail "caso8: agent-verify sem hook doc_impact"

echo "doc-impact-test: VERDE (8 casos)"
