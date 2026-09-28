#!/usr/bin/env bash
#
# check_doc_impact.sh — #648: uma mudança NORMATIVA (§NNN que troca de estado,
# decisão D-*, documento movido) não pode fechar com consumidor documental vivo
# desatualizado. Para cada token do diff, lista os arquivos `.md` que o citam e
# exige que CADA consumidor tenha sido tocado pelo MESMO diff (ou waiver).
#
# Uso real (manifesto do agent-verify / CI do candidato):
#   scripts/check_doc_impact.sh [--base SHA] [--head SHA]
# Saída fixture (testes):
#   scripts/check_doc_impact.sh --corpus DIR --changed-file F --tokens-file F
# Tokens (file: `kind<TAB>token`):
#   status  §NNN     — linha de estado de §NNN mudou em known-bugs
#   decision D-NAME  — cabeçalho `## D-NAME` mudou em DECISIONS
#   moved   <path>   — documento renomeado/movido (caminho ANTIGO)
# Waivers (DOC_IMPACT_WAIVERS, default scripts/doc-impact-waivers.txt):
#   `<token|*><TAB><prefixo-do-consumidor>`
# Saída: `token -> consumidores -> PASS|FAIL` por token; exit 1 se qualquer FAIL.
# História imutável (CHANGELOG/DOING/history) é waiveada por padrão — registro
# não é consumidor vivo (regra "State, not history").
set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

BASE=""; HEADR="HEAD"; CHANGED=""; TOKENS=""; CORPUS="."
WAIVERS="${DOC_IMPACT_WAIVERS:-scripts/doc-impact-waivers.txt}"
while [ $# -gt 0 ]; do case "$1" in
  --base) BASE="${2:-}"; shift 2;;
  --head) HEADR="${2:-}"; shift 2;;
  --changed-file) CHANGED="${2:-}"; shift 2;;
  --tokens-file) TOKENS="${2:-}"; shift 2;;
  --corpus) CORPUS="${2:-}"; shift 2;;
  --waivers) WAIVERS="${2:-}"; shift 2;;
  -h|--help) sed -n '2,20p' "$0"; exit 0;;
  *) echo "check_doc_impact: argumento desconhecido: $1" >&2; exit 2;;
esac; done

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
CH="$TMP/changed"; : > "$CH"

if [ -z "$TOKENS" ]; then
  # modo real: deriva do diff git
  [ -n "$BASE" ] || BASE="$(git merge-base HEAD origin/lab 2>/dev/null || git rev-parse HEAD~1 2>/dev/null || echo '')"
  if [ -z "$BASE" ]; then echo "PASS (sem base para diff — nada a medir)"; exit 0; fi
  git diff --name-only "$BASE".."$HEADR" > "$CH" 2>/dev/null
  git diff -U0 "$BASE".."$HEADR" -- docs/bugs-and-gaps/known-bugs.md docs/bugs-and-gaps/known-bugs.pt_BR.md \
    | grep -E '^[+-]' | grep -vE '^(\+\+\+|---)' \
    | grep -E '§[0-9]+' | grep -iE 'FIXED|OPEN|IN PROGRESS|BLOCKED|LANDED|landed|🟡|✅|🔧|⏸|next|próxima' \
    | grep -oE '§[0-9]+' | sort -u | awk '{printf "status\t%s\n", $0}' > "$TMP/tokens"
  git diff -U0 "$BASE".."$HEADR" -- docs/development/DECISIONS.md docs/development/DECISIONS.pt_BR.md \
    | grep -E '^[+-]## D-' | grep -oE 'D-[A-Z0-9._-]+' | sort -u | sed 's/^/decision\t/' >> "$TMP/tokens"
  git diff --name-status -M "$BASE".."$HEADR" | grep -E '^R[0-9]*\b' | while IFS=$'\t' read -r st old new; do
    printf 'moved\t%s\n' "$old"
  done >> "$TMP/tokens"
  TOKENS="$TMP/tokens"
else
  : > "$CH"  # modo fixture: --changed-file já popula abaixo
fi
[ -n "$CHANGED" ] && [ -f "$CHANGED" ] && cat "$CHANGED" >> "$CH"
[ -n "$TOKENS" ] && [ -f "$TOKENS" ] || TOKENS=/dev/null
sort -u "$CH" -o "$CH"

is_changed() { grep -qxF "$1" "$CH"; }
waived() { # token consumidor
  local tok="$1" con="$2" pre
  [ -f "$WAIVERS" ] || return 1
  while read -r pre; do
    [ -n "$pre" ] || continue
    case "$con" in "$pre"*) return 0;; esac
  done < <(awk -F'\t' -v t="$tok" '$1==t || $1=="*" {print $2}' "$WAIVERS" | grep -v '^#')
  return 1
}

consumers_for() { # kind token
  local kind="$1" tok="$2"
  local pat
  case "$kind" in
    moved) pat="$tok" ;;
    *)     pat="$tok" ;;
  esac
  grep -rlF --include='*.md' -e "$pat" "$CORPUS" 2>/dev/null \
    | grep -v '^\.git/' | grep -v '/target/' \
    | sed "s|^\./||; s|^$CORPUS/||" || true
  if [ "$kind" = "moved" ]; then
    # consumidor também é quem cita só o basename (ex.: `plan-foo.md` cruft)
    grep -rlF --include='*.md' -e "$(basename "$tok")" "$CORPUS" 2>/dev/null \
      | grep -v '^\.git/' | grep -v '/target/' \
      | sed "s|^\./||; s|^$CORPUS/||" || true
  fi
}

RC=0; N=0
while IFS=$'\t' read -r kind tok; do
  [ -n "$tok" ] || continue
  N=$((N+1))
  # origem do token não é consumidor de si mesma
  # A fonte do token nao e consumidora de si mesma; learn/ e training/
  # sao consumidores (foram exatamente eles que apodreceram no #648).
  SRC_SKIP='^docs/bugs-and-gaps/known-bugs\.(md|pt_BR\.md)|^docs/development/DECISIONS\.(md|pt_BR\.md)'
  [ "$kind" = "decision" ] && SRC_SKIP='^docs/development/DECISIONS\.(md|pt_BR\.md)'
  [ "$kind" = "moved" ] && SRC_SKIP='^$'
  STALE=""
  while read -r f; do
    [ -n "$f" ] || continue
    printf '%s' "$f" | grep -qE "$SRC_SKIP" && continue
    is_changed "$f" && continue
    waived "$tok" "$f" && continue
    STALE="$STALE $f"
  done < <(consumers_for "$kind" "$tok" | sort -u)
  if [ -n "$STALE" ]; then
    printf '%s ->%s -> FAIL (consumidor vivo nao atualizado no mesmo diff)\n' "$tok" "$STALE"
    RC=1
  else
    printf '%s -> (nenhum consumidor pendente) -> PASS\n' "$tok"
  fi
done < "$TOKENS"
[ "$N" -eq 0 ] && echo "PASS (no normative tokens in diff)"
exit "$RC"
