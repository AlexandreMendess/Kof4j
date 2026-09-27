#!/usr/bin/env bash
#
# release-evidence-test.sh — prova offline de scripts/release-evidence.sh, o manifesto de evidencia POR ALVO
# do EXIT GATE 1.0 (§32.7 / D-1.0-EDGES Q7 — ratificado). Sem rede. Regras testadas: os 8 alvos da
# superficie Stable; mesma SHA candidata; so GREEN vale (RED/SKIP/NOT_RUN nunca viram verde);
# evidencia de SHA antiga e recusada; digest do pacote testado obrigatorio (exceto Script).
#
# Uso: scripts/tests/release-evidence-test.sh   (exit 0 = tudo verde)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
E="scripts/release-evidence.sh"
FAILED=0
expect() { if [ "$2" != "$3" ]; then echo "!!! $1: esperado rc=$2, veio rc=$3"; FAILED=1; else echo "ok: $1 (rc=$3)"; fi; }
has() { printf '%s\n' "$3" | grep -q -- "$2" || { echo "!!! $1: nao achou '$2' na saida"; FAILED=1; }; }
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
SHA="1234567890abcdef1234567890abcdef12345678"; OLD="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
D() { printf '%s' "$1" | sha256sum 2>/dev/null | cut -d' ' -f1; }
DIG="$(printf 'b%.0s' $(seq 64))"
TARGETS="jvm x86-64 riscv64 aarch64 js script kofc android"
fill() { # manifesto, sha, resultado — 8 alvos
  rm -f "$1"
  for t in $TARGETS; do
    dg="$DIG"; [ "$t" = "script" ] && dg="-"
    bash "$E" add "$1" --target "$t" --sha "$2" --result "$3" --digest "$dg" --proof "job:https://ci.example/run/1#$t" --verifier "ci-bot" >/dev/null || return 1
  done
}

# 1) os 8 alvos GREEN na mesma SHA -> ok
fill "$T/m1.tsv" "$SHA" GREEN; out="$(bash "$E" check "$T/m1.tsv" --sha "$SHA" 2>&1)"; rc=$?
expect "8 alvos GREEN na SHA candidata" 0 "$rc"

# 2) alvo ausente -> FAIL nomeando o alvo
grep -v "$(printf '\tkofc\t')" "$T/m1.tsv" > "$T/m2.tsv"
out="$(bash "$E" check "$T/m2.tsv" --sha "$SHA" 2>&1)"; rc=$?
expect "alvo ausente (kofc) -> rc 1" 1 "$rc"; has 2 "FAIL.*kofc.*no evidence" "$out"

# 3) evidencia de SHA ANTIGA nao vale para a candidata
fill "$T/m3.tsv" "$OLD" GREEN; out="$(bash "$E" check "$T/m3.tsv" --sha "$SHA" 2>&1)"; rc=$?
expect "evidencia de outra SHA -> rc 1" 1 "$rc"; has 3 "FAIL.*stale" "$out"

# 4) RED, SKIP e NOT_RUN nunca sao verde
for r in RED SKIP NOT_RUN; do
  fill "$T/m4.tsv" "$SHA" GREEN
  bash "$E" add "$T/m4.tsv" --target riscv64 --sha "$SHA" --result "$r" --digest "$DIG" --proof "job:x" --verifier ci-bot >/dev/null
  out="$(bash "$E" check "$T/m4.tsv" --sha "$SHA" 2>&1)"; rc=$?
  expect "ultimo resultado $r em riscv64 -> rc 1" 1 "$rc"; has "4-$r" "FAIL.*riscv64.*not GREEN" "$out"
done

# 5) RED seguido de GREEN na mesma SHA passa, mas AVISA (rerun ate ficar verde nao e silencioso)
fill "$T/m5.tsv" "$SHA" GREEN
bash "$E" add "$T/m5.tsv" --target js --sha "$SHA" --result RED --digest "$DIG" --proof "job:1" --verifier ci-bot >/dev/null
bash "$E" add "$T/m5.tsv" --target js --sha "$SHA" --result GREEN --digest "$DIG" --proof "job:2" --verifier ci-bot >/dev/null
out="$(bash "$E" check "$T/m5.tsv" --sha "$SHA" 2>&1)"; rc=$?
expect "RED depois GREEN na mesma SHA passa" 0 "$rc"; has 5 "WARN.*js.*RED" "$out"

# 6) digest do pacote testado e obrigatorio (exceto Script, que nao tem artefato)
fill "$T/m6.tsv" "$SHA" GREEN
bash "$E" add "$T/m6.tsv" --target jvm --sha "$SHA" --result GREEN --digest "-" --proof "job:3" --verifier ci-bot >/dev/null
out="$(bash "$E" check "$T/m6.tsv" --sha "$SHA" 2>&1)"; rc=$?
expect "jvm sem digest -> rc 1" 1 "$rc"; has 6 "FAIL.*jvm.*digest" "$out"

# 7) add recusa entradas invalidas (nada e gravado)
: > "$T/m7.tsv"
bash "$E" add "$T/m7.tsv" --target jvm --sha "nao-e-sha" --result GREEN --digest "$DIG" --proof p --verifier v >/dev/null 2>&1; expect "add: SHA invalida" 2 "$?"
bash "$E" add "$T/m7.tsv" --target jvm --sha "$SHA" --result VERDE --digest "$DIG" --proof p --verifier v >/dev/null 2>&1; expect "add: resultado invalido" 2 "$?"
bash "$E" add "$T/m7.tsv" --target jvm --sha "$SHA" --result GREEN --digest "curto" --proof p --verifier v >/dev/null 2>&1; expect "add: digest invalido" 2 "$?"
bash "$E" add "$T/m7.tsv" --target jvm --sha "$SHA" --result GREEN --digest "$DIG" --proof "a	b" --verifier v >/dev/null 2>&1; expect "add: TAB no campo" 2 "$?"
bash "$E" add "$T/m7.tsv" --target jvm --sha "$SHA" --result GREEN --digest "$DIG" --proof "" --verifier v >/dev/null 2>&1; expect "add: proof vazio" 2 "$?"
[ ! -s "$T/m7.tsv" ] && echo "ok: nada gravado nas entradas invalidas" || { echo "!!! entrada invalida foi gravada"; FAILED=1; }

# 8) `digest` devolve o digest do ULTIMO registro do alvo (para encadear com verify-release-identity --tested)
fill "$T/m8.tsv" "$SHA" GREEN
out="$(bash "$E" digest "$T/m8.tsv" jvm 2>&1)"; rc=$?
expect "digest jvm" 0 "$rc"; [ "$out" = "$DIG" ] && echo "ok: digest impresso" || { echo "!!! digest errado: $out"; FAILED=1; }

# 9) manifesto inexistente -> FAIL
out="$(bash "$E" check "$T/nao-existe.tsv" --sha "$SHA" 2>&1)"; rc=$?
expect "manifesto inexistente -> rc 1" 1 "$rc"

[ "$FAILED" -eq 0 ] && echo "== release-evidence: VERDE" || echo "== release-evidence: VERMELHA"
exit "$FAILED"
