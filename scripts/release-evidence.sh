#!/usr/bin/env bash
# release-evidence.sh — manifesto de EVIDENCIA POR ALVO do EXIT GATE 1.0 (PROPOSAL §32.7;
# D-1.0-EDGES Q7 / §35: "evidence manifest per target" e "digest do testado == digest do publicado").
#
# Um TSV auditavel (uma linha por execucao de gate): timestamp, alvo, SHA candidata, resultado,
# digest do PACOTE TESTADO, prova (job/comando) e verificador. `check` so aprova se os 8 alvos da
# superficie Stable tem evidencia GREEN na MESMA SHA candidata; RED/SKIP/NOT_RUN nunca viram verde,
# evidencia de SHA antiga nao vale, e o digest do pacote testado e obrigatorio (exceto Script, que
# nao tem artefato). O digest impresso por `digest` encadeia com
# `verify-release-identity.sh --tested <digest>` (o gate ratificado testado == publicado).
#
# Nao decide raiz de confianca/proveniencia (a #571) e nao fixa politica de flake: um RED seguido de
# GREEN na mesma SHA passa, mas AVISA (rerun ate ficar verde nunca e silencioso).
#
# Uso:
#   scripts/release-evidence.sh add <manifesto.tsv> --target <t> --sha <sha> --result GREEN|RED|SKIP|NOT_RUN \
#                                                    --digest <sha256|-> --proof <job-ou-comando> --verifier <quem>
#   scripts/release-evidence.sh check <manifesto.tsv> --sha <sha-candidata> [--targets "t1 t2 ..."]
#   scripts/release-evidence.sh digest <manifesto.tsv> <alvo>
# Exit: 0 ok · 1 check reprovado · 2 uso/entrada invalida.
set -u
DEFAULT_TARGETS="jvm x86-64 riscv64 aarch64 js script kofc android"
TAB="$(printf '\t')"
die() { echo "release-evidence: $*" >&2; exit 2; }
lc() { printf '%s' "$1" | tr 'A-Z' 'a-z'; }

cmd="${1:-}"; shift || true
case "$cmd" in
  add)
    M="${1:-}"; [ -n "$M" ] || die "uso: add <manifesto> --target ... (ver --help)"; shift
    T=""; S=""; R=""; D=""; P=""; V=""
    while [ $# -gt 0 ]; do
      case "$1" in
        --target) T="$(lc "${2:-}")"; shift 2 ;;
        --sha) S="$(lc "${2:-}")"; shift 2 ;;
        --result) R="${2:-}"; shift 2 ;;
        --digest) D="$(lc "${2:-}")"; shift 2 ;;
        --proof) P="${2:-}"; shift 2 ;;
        --verifier) V="${2:-}"; shift 2 ;;
        *) die "flag desconhecida: $1" ;;
      esac
    done
    printf '%s' "$T" | grep -qE '^[a-z0-9._-]+$' || die "--target invalido: '$T'"
    printf '%s' "$S" | grep -qE '^[0-9a-f]{7,40}$' || die "--sha invalida (7 a 40 hex): '$S'"
    case "$R" in GREEN|RED|SKIP|NOT_RUN) ;; *) die "--result deve ser GREEN|RED|SKIP|NOT_RUN: '$R'" ;; esac
    { [ "$D" = "-" ] || printf '%s' "$D" | grep -qE '^[0-9a-f]{64}$'; } || die "--digest deve ser sha256 (64 hex) ou '-': '$D'"
    [ -n "$P" ] || die "--proof e obrigatorio (job/comando que produziu o resultado)"
    [ -n "$V" ] || die "--verifier e obrigatorio"
    case "$T$S$R$D$P$V" in *"$TAB"*|*$'\n'*) die "campos nao podem conter TAB/quebra de linha" ;; esac
    [ -s "$M" ] || printf '# timestamp\ttarget\tsha\tresult\tdigest\tproof\tverifier\n' > "$M"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$T" "$S" "$R" "$D" "$P" "$V" >> "$M"
    echo "recorded: $T $R @ ${S:0:12}"
    ;;
  digest)
    M="${1:-}"; T="$(lc "${2:-}")"; [ -f "$M" ] && [ -n "$T" ] || die "uso: digest <manifesto> <alvo>"
    d="$(awk -F"$TAB" -v t="$T" '/^#/{next} $2==t{d=$5} END{print d}' "$M")"
    [ -n "$d" ] && [ "$d" != "-" ] || { echo "release-evidence: sem digest para o alvo '$T'" >&2; exit 1; }
    printf '%s\n' "$d"
    ;;
  check)
    M="${1:-}"; [ -n "$M" ] || die "uso: check <manifesto> --sha <sha>"; shift
    C=""; TARGETS="$DEFAULT_TARGETS"
    while [ $# -gt 0 ]; do
      case "$1" in
        --sha) C="$(lc "${2:-}")"; shift 2 ;;
        --targets) TARGETS="$(lc "${2:-}")"; shift 2 ;;
        *) die "flag desconhecida: $1" ;;
      esac
    done
    printf '%s' "$C" | grep -qE '^[0-9a-f]{7,40}$' || die "--sha (candidata) invalida: '$C'"
    echo "== release-evidence check (candidata ${C:0:12}; alvos: $TARGETS)"
    FAIL=0
    fail() { echo "FAIL     $1"; FAIL=$((FAIL+1)); }
    if [ ! -f "$M" ]; then fail "manifest not found: $M"; echo "-- 1 fail"; exit 1; fi
    for t in $TARGETS; do
      any="$(awk -F"$TAB" -v t="$t" '/^#/{next} $2==t{n++} END{print n+0}' "$M")"
      if [ "$any" -eq 0 ]; then fail "$t: no evidence in the manifest"; continue; fi
      # linhas do alvo cuja SHA casa com a candidata (prefixo nos dois sentidos, para SHA curta)
      rows="$(awk -F"$TAB" -v t="$t" -v c="$C" '/^#/{next} $2==t && (index(c,$3)==1 || index($3,c)==1){print}' "$M")"
      if [ -z "$rows" ]; then
        seen="$(awk -F"$TAB" -v t="$t" '/^#/{next} $2==t{printf "%s ", substr($3,1,12)}' "$M")"
        fail "$t: stale evidence — recorded only for other commit(s): $seen"; continue
      fi
      last="$(printf '%s\n' "$rows" | tail -n 1)"
      res="$(printf '%s' "$last" | awk -F"$TAB" '{print $4}')"; dg="$(printf '%s' "$last" | awk -F"$TAB" '{print $5}')"
      if [ "$res" != "GREEN" ]; then fail "$t: last result $res is not GREEN"; continue; fi
      if [ "$t" != "script" ] && [ "$dg" = "-" ]; then fail "$t: tested-package digest missing (needed for tested == published)"; continue; fi
      earlier="$(printf '%s\n' "$rows" | awk -F"$TAB" '$4!="GREEN"{r=r (r==""?"":"/") $4} END{print r}')"
      echo "PASS     $t GREEN @ ${C:0:12}${dg:+ (digest ${dg:0:12}…)}"
      [ -n "$earlier" ] && echo "WARN     $t: earlier $earlier run(s) on this same commit before the GREEN one (rerun) — flake policy applies"
    done
    if [ "$FAIL" -gt 0 ]; then echo "-- $FAIL fail — RED: the per-target evidence manifest does not support this candidate"; exit 1; fi
    echo "-- all targets GREEN on the same candidate"
    ;;
  ""|-h|--help) sed -n '2,25p' "$0"; [ -z "$cmd" ] && exit 2 || exit 0 ;;
  *) die "subcomando desconhecido: $cmd (add|check|digest)" ;;
esac
