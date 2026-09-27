#!/usr/bin/env bash
# verify-release-identity.sh — mecaniza o gate RATIFICADO do EXIT GATE 1.0:
#   "o digest do pacote TESTADO == o digest do pacote PUBLICADO"   (PROPOSAL §32.6 [RATIFICADO],
#   D-1.0-EDGES Q7 / §35), mais a integridade do proprio SHA256SUMS da release.
#
# NAO decide raiz de confianca, formato de proveniencia nem politica de verificacao (isso e a
# decisao aberta da #571): so confere fatos de digest que o contrato ja exige. Somente leitura.
#
# Uso:
#   scripts/verify-release-identity.sh --release <tag> [--repo owner/repo] [--tested <sha256>] [--require-tested]
#   scripts/verify-release-identity.sh --dir <diretorio-com-assets-e-SHA256SUMS>  [--tested <sha256>] [--require-tested]
#
# Verificacoes (cada linha: PASS | FAIL | WARN | NOT_RUN):
#   - SHA256SUMS existe;
#   - cada linha do SHA256SUMS aponta para um asset real e o digest confere
#     (modo --release: contra o digest que o GitHub registra no servidor; modo --dir: recalculado);
#   - o ARQUIVO (*.tar.gz / *.zip) esta coberto (FAIL se nao); outros assets sem checksum = WARN;
#   - --tested: o digest testado == o digest de algum arquivo publicado. Sem --tested a checagem e
#     NOT_RUN — nunca um verde falso; --require-tested transforma o NOT_RUN em falha.
# Exit: 0 ok · 1 alguma falha (ou NOT_RUN com --require-tested) · 2 uso · 3 API/gh indisponivel.
#
# Seams de teste (sem rede): VRI_ASSETS_FILE (linhas "nome<TAB>sha256|-") e VRI_SUMS_FILE.
set -u
REPO="KofLang/Kof4j"; TAG=""; DIR=""; TESTED=""; REQ=0
while [ $# -gt 0 ]; do
  case "$1" in
    --release) TAG="${2:-}"; shift 2 ;;
    --repo) REPO="${2:-}"; shift 2 ;;
    --dir) DIR="${2:-}"; shift 2 ;;
    --tested) TESTED="$(printf '%s' "${2:-}" | tr 'A-F' 'a-f')"; shift 2 ;;
    --require-tested) REQ=1; shift ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) echo "uso: $0 (--release <tag> | --dir <dir>) [--repo o/r] [--tested <sha256>] [--require-tested]" >&2; exit 2 ;;
  esac
done
{ [ -n "$TAG" ] || [ -n "$DIR" ]; } || { echo "uso: informe --release <tag> ou --dir <dir>" >&2; exit 2; }

TAB="$(printf '\t')"
W="$(mktemp -d)"; trap 'rm -rf "$W"' EXIT
ASSETS="$W/assets.tsv"   # nome<TAB>sha256 (ou "-" = servidor nao tem digest)
SUMS="$W/sums.tsv"       # sha256<TAB>nome
: > "$ASSETS"; : > "$SUMS"
HAVE_SUMS=0

hash_of() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
parse_sums() { # stdin: SHA256SUMS (coreutils; aceita CRLF e o marcador `*` de binario)
  tr -d '\r' | sed -nE "s/^([0-9a-fA-F]{64})[[:space:]]+\*?(.+)\$/\1${TAB}\2/p" | awk -F"$TAB" -v OFS="$TAB" '{print tolower($1), $2}'
}

if [ -n "$DIR" ]; then
  [ -d "$DIR" ] || { echo "diretorio inexistente: $DIR" >&2; exit 2; }
  for f in "$DIR"/*; do
    [ -f "$f" ] || continue
    n="$(basename "$f")"
    if [ "$n" = "SHA256SUMS" ]; then HAVE_SUMS=1; parse_sums < "$f" > "$SUMS"; else printf '%s\t%s\n' "$n" "$(hash_of "$f")" >> "$ASSETS"; fi
  done
else
  if [ -n "${VRI_ASSETS_FILE:-}" ]; then
    cp "$VRI_ASSETS_FILE" "$ASSETS"
  else
    command -v gh >/dev/null 2>&1 || { echo "gh ausente (modo --release precisa da API)" >&2; exit 3; }
    gh api "repos/$REPO/releases/tags/$TAG" --jq '.assets[]|[.name,((.digest // "-")|sub("^sha256:";""))]|@tsv' > "$ASSETS" 2>"$W/err" \
      || { echo "API indisponivel: $(head -c 160 "$W/err")" >&2; exit 3; }
  fi
  if [ -n "${VRI_SUMS_FILE:-}" ]; then HAVE_SUMS=1; parse_sums < "$VRI_SUMS_FILE" > "$SUMS"
  elif gh release download "$TAG" -R "$REPO" -p SHA256SUMS -O - > "$W/sums.raw" 2>/dev/null; then HAVE_SUMS=1; parse_sums < "$W/sums.raw" > "$SUMS"; fi
  # o proprio SHA256SUMS aparece como asset; nao entra na checagem de cobertura
  grep -v "^SHA256SUMS${TAB}" "$ASSETS" > "$W/a2" 2>/dev/null; mv "$W/a2" "$ASSETS"
fi

PASS=0; FAIL=0; WARN=0
say() { printf '%-8s %s\n' "$1" "$2"; case "$1" in PASS) PASS=$((PASS+1));; FAIL) FAIL=$((FAIL+1));; WARN) WARN=$((WARN+1));; esac; }
short() { printf '%s' "$1" | cut -c1-12; }
is_archive() { case "$1" in *.tar.gz|*.zip) return 0;; *) return 1;; esac; }

echo "== verify-release-identity (${TAG:+release $TAG @ $REPO}${DIR:+dir $DIR})"
if [ "$HAVE_SUMS" -ne 1 ] || [ ! -s "$SUMS" ]; then
  say FAIL "SHA256SUMS missing or empty — integrity of the published assets is unverifiable"
fi

while IFS="$TAB" read -r h n; do
  [ -n "$n" ] || continue
  d="$(awk -F"$TAB" -v n="$n" '$1==n{print $2; exit}' "$ASSETS")"
  if [ -z "$d" ]; then say FAIL "missing: $n is listed in SHA256SUMS but is not a release asset"
  elif [ "$d" = "-" ]; then say WARN "no server-side digest for $n — cannot be cross-checked (SHA256SUMS says $(short "$h")…)"
  elif [ "$d" = "$h" ]; then say PASS "covered $n (sha256 $(short "$h")…)"
  else say FAIL "mismatch $n: SHA256SUMS $(short "$h")… != asset $(short "$d")…"
  fi
done < "$SUMS"

while IFS="$TAB" read -r n d; do
  [ -n "$n" ] || continue
  if ! awk -F"$TAB" -v n="$n" '$2==n{f=1} END{exit f?0:1}' "$SUMS"; then
    if is_archive "$n"; then say FAIL "archive not covered by SHA256SUMS: $n"
    else say WARN "not covered by any checksum: $n"; fi
  fi
done < "$ASSETS"

TESTED_STATE="NOT_RUN"
if [ -n "$TESTED" ]; then
  match=""
  while IFS="$TAB" read -r n d; do
    is_archive "$n" && [ "$d" = "$TESTED" ] && match="$n"
  done < "$ASSETS"
  if [ -n "$match" ]; then say PASS "tested == published ($match, sha256 $(short "$TESTED")…)"; TESTED_STATE="PASS"
  else say FAIL "tested != published: tested $(short "$TESTED")… matches no published archive (the tested bytes are NOT the published bytes)"; TESTED_STATE="FAIL"; fi
else
  say NOT_RUN "tested == published — no --tested digest given (the ratified equality is UNVERIFIED)"
fi

echo "-- $PASS pass, $FAIL fail, $WARN warn; tested==published: $TESTED_STATE"
[ "$FAIL" -gt 0 ] && exit 1
[ "$REQ" -eq 1 ] && [ "$TESTED_STATE" = "NOT_RUN" ] && { echo "RED — --require-tested: the tested digest was not provided"; exit 1; }
exit 0
