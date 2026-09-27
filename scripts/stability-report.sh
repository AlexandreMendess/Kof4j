#!/usr/bin/env bash
#
# stability-report.sh — condicao 4 do gate 0.5.0 (`D-RELEASE-0.5.0-GATE`):
# transforma o log de uma corrida REAL da suite num relatorio machine-readable,
# para o gate consumir `STABILITY: GREEN` a partir de MEDICAO, nunca de memoria.
#
# O que mede (honesto, nada alem): a ultima linha
#   TOTAL: tests=N failures=F errors=E skipped=S
# do log. `STABILITY: GREEN` exige F=0 e E=0. A matriz de conformidade
# (`ConformanceMatrixTest` / `ConformanceMatrixDocTest`) e parte da suite — um
# log 0F/0E ja a inclui; a paridade por alvo e a condicao 1 (EG-5,
# `scripts/target-matrix.sh`). Este script NAO re-executa a suite (caro): ele
# le o log de uma corrida ja feita na candidata.
#
# Uso:
#   scripts/stability-report.sh --suite-log FILE [--sha SHA]
#   KOF_SUITE_LOG=FILE scripts/stability-report.sh
#   scripts/stability-report.sh --selftest
# rc: 0 GREEN · 1 RED · 3 sem log/ambiente.
set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)"

LOG="${KOF_SUITE_LOG:-}"; SHA=""; SELFTEST=false
while [ $# -gt 0 ]; do
  case "$1" in
    --suite-log) LOG="$2"; shift ;;
    --sha) SHA="$2"; shift ;;
    --selftest) SELFTEST=true ;;
    *) echo "uso: $0 --suite-log FILE [--sha SHA] | --selftest" >&2; exit 2 ;;
  esac
  shift
done

want_sha() { [ -n "$SHA" ] && echo "$SHA" || git rev-parse HEAD 2>/dev/null || echo "unknown"; }
short() { echo "${1:0:12}"; }

# verdict_from_log LOG -> imprime STABILITY: ... ; rc 0 GREEN, 1 RED, 3 sem TOTAL
# GREEN exige: ultima linha TOTAL com 0F/0E E o log estampado pelo safe-suite.sh
# com SUITE-SHA == sha esperado (--sha ou HEAD) E SUITE-DIRTY=0. Um log de outro
# commit (ou de arvore suja) NAO certifica o tip — unknown, nunca verde falso.
verdict_from_log() {
  local log="$1" line t f e s lsha ldir want
  [ -f "$log" ] || { echo "STABILITY: unknown — log ausente: $log"; return 3; }
  line="$(grep -E 'TOTAL: tests=[0-9]+ failures=[0-9]+ errors=[0-9]+' "$log" | tail -1)"
  if [ -z "$line" ]; then
    echo "STABILITY: unknown — sem linha TOTAL no log: $log"; return 3
  fi
  t="$(sed -E 's/.*tests=([0-9]+).*/\1/' <<<"$line")"
  f="$(sed -E 's/.*failures=([0-9]+).*/\1/' <<<"$line")"
  e="$(sed -E 's/.*errors=([0-9]+).*/\1/' <<<"$line")"
  s="$(sed -E 's/.*skipped=([0-9]+).*/\1/' <<<"$line")"
  lsha="$(sed -n 's/^SUITE-SHA: //p' "$log" | tail -1)"
  ldir="$(sed -n 's/^SUITE-DIRTY: //p' "$log" | tail -1)"
  want="$(want_sha)"
  if [ "$f" -ne 0 ] || [ "$e" -ne 0 ]; then
    echo "STABILITY: RED sha=$(short "${lsha:-$want}") tests=$t failures=$f errors=$e skipped=$s"
    return 1
  fi
  if [ -z "$lsha" ]; then
    echo "STABILITY: unknown — log sem SUITE-SHA (corrida anterior a estampa); re-rode scripts/safe-suite.sh"; return 3
  fi
  case "$lsha" in
    "$want"*) ;;
    *) echo "STABILITY: unknown — log do sha $(short "$lsha"), esperado $(short "$want") — re-meça no tip"; return 3 ;;
  esac
  if [ "${ldir:-0}" != "0" ]; then
    echo "STABILITY: unknown — arvore suja (SUITE-DIRTY=$ldir) na corrida; o log nao representa o sha $(short "$lsha")"; return 3
  fi
  echo "STABILITY: GREEN sha=$(short "$lsha") tests=$t failures=0 errors=0 skipped=$s"
  return 0
}

if [ "$SELFTEST" = true ]; then
  ST="$(mktemp -d)"; trap 'rm -rf "$ST"' EXIT
  fail() { echo "SELFTEST FAIL: $*" >&2; exit 2; }
  HEAD_SHA="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
  { printf 'SUITE-SHA: %s\n' "$HEAD_SHA"; printf 'SUITE-DIRTY: 0\n'
    printf 'x\nTOTAL: tests=100 failures=0 errors=0 skipped=5\n'; } > "$ST/green"
  printf 'x\nTOTAL: tests=100 failures=1 errors=0 skipped=5\n' > "$ST/redf"
  printf 'x\nTOTAL: tests=100 failures=0 errors=2 skipped=5\n' > "$ST/rede"
  printf 'x\nTOTAL: tests=100 failures=0 errors=0 skipped=5\n' > "$ST/unbound"
  printf 'sem total aqui\n' > "$ST/none"
  { printf 'SUITE-SHA: 0000000000000000000000000000000000000000\n'
    printf 'x\nTOTAL: tests=100 failures=0 errors=0 skipped=5\n'; } > "$ST/othersha"
  { printf 'SUITE-SHA: %s\n' "$HEAD_SHA"; printf 'SUITE-DIRTY: 3\n'
    printf 'x\nTOTAL: tests=100 failures=0 errors=0 skipped=5\n'; } > "$ST/dirty"
  out="$(verdict_from_log "$ST/green")" || fail "log verde reprovado (falso vermelho): $out"
  grep -q "STABILITY: GREEN" <<<"$out" || fail "log verde nao deu GREEN"
  verdict_from_log "$ST/redf" >/dev/null && fail "failures=1 aceito (falso verde)"
  verdict_from_log "$ST/rede" >/dev/null && fail "errors=2 aceito (falso verde)"
  verdict_from_log "$ST/none" >/dev/null && fail "log sem TOTAL aceito (falso verde)"
  out="$(verdict_from_log "$ST/unbound")" && fail "log SEM SUITE-SHA aceito (falso verde): $out"
  grep -q "STABILITY: unknown" <<<"$out" || fail "log sem SUITE-SHA nao deu unknown: $out"
  out="$(verdict_from_log "$ST/othersha")" && fail "log de OUTRO sha aceito (falso verde): $out"
  grep -q "STABILITY: unknown" <<<"$out" || fail "log de outro sha nao deu unknown: $out"
  out="$(verdict_from_log "$ST/dirty")" && fail "log de arvore SUJA aceito (falso verde): $out"
  echo "SELFTEST: ok — GREEN so com 0F/0E + SUITE-SHA do tip + SUITE-DIRTY=0; outro sha / sujo / sem-stamp / sem-TOTAL reprovados"
  exit 0
fi

verdict_from_log "$LOG"
exit $?
