#!/usr/bin/env bash
# check_workflow_pins.sh — gate de pin de Actions por SHA (D-ARTIFACT-TRUST §5,
# hardening P2). Toda referência `uses:` de terceiros em .github/workflows/
# precisa apontar para um commit imutável, nunca para uma tag/branch movel:
#   uses: owner/repo[/path]@<sha-de-40-hex>   # vN   <- comentario com a versao
#   uses: docker://imagem@sha256:<64-hex>     # vN
# O comentario `# vN` e obrigatorio: e ele que o Dependabot (github-actions)
# usa para propor o bump do SHA, e o que deixa o pin legivel para humanos.
# Referencias locais (`./...`) sao do proprio repo e ficam fora.
#
# Isencoes: scripts/workflow-pins-exempt.txt, uma linha por arquivo
#   "<arquivo.yml> <motivo>". Isencao e divida declarada, nunca silenciosa:
# o gate imprime cada arquivo isento, e uma isencao para arquivo que ja esta
# 100% pinado (ou que nao existe) e DRIFT e falha.
#
# Exit: 0 limpo · 1 violacao ou drift. --selftest prova que o gate morde.
set -euo pipefail
cd "$(dirname "$0")/.."

WF_DIR="${PINS_WORKFLOWS_DIR:-.github/workflows}"
EXEMPT="${PINS_EXEMPT_FILE:-scripts/workflow-pins-exempt.txt}"

# Emite "<arquivo>:<linha>: <referencia>" para cada `uses:` fora de comentario
# que nao esta pinado por SHA/digest com comentario de versao.
violations_in() {
  awk '
    /^[[:space:]]*#/ { next }
    {
      line = $0
      if (line !~ /(^|[[:space:]-])uses:[[:space:]]*/) next
      sub(/^.*uses:[[:space:]]*/, "", line)
      comment = ""
      if (match(line, /[[:space:]]+#/)) {
        comment = substr(line, RSTART)
        line = substr(line, 1, RSTART - 1)
      }
      gsub(/["\047[:space:]]/, "", line)
      if (line ~ /^\.\//) next
      ok = 0
      if (line ~ /^docker:\/\/.+@sha256:[0-9a-f]{64}$/) ok = 1
      else if (line ~ /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.\/-]+@[0-9a-f]{40}$/) ok = 1
      if (ok && comment !~ /#[[:space:]]*v?[0-9]/) {
        printf "%s:%d: %s (sem comentario de versao `# vN`)\n", FILENAME, FNR, line
      } else if (!ok) {
        printf "%s:%d: %s\n", FILENAME, FNR, line
      }
    }
  ' "$1"
}

is_exempt() { [ -f "$EXEMPT" ] && grep -qE "^$1([[:space:]]|\$)" "$EXEMPT"; }

run_gate() {
  local bad=0 exempt_seen=0 f name out
  for f in "$WF_DIR"/*.yml "$WF_DIR"/*.yaml; do
    [ -f "$f" ] || continue
    name="$(basename "$f")"
    out="$(violations_in "$f")"
    if is_exempt "$name"; then
      exempt_seen=$((exempt_seen + 1))
      if [ -z "$out" ]; then
        echo "DRIFT: $name esta em $EXEMPT mas ja esta 100% pinado — remova a isencao" >&2
        bad=1
      else
        echo "isento: $name ($(printf '%s\n' "$out" | wc -l | tr -d ' ') ref(s) nao pinadas — divida declarada em $EXEMPT)"
      fi
    elif [ -n "$out" ]; then
      printf '%s\n' "$out" >&2
      bad=1
    fi
  done
  if [ -f "$EXEMPT" ]; then
    local ex
    while IFS= read -r ex; do
      case "$ex" in ''|'#'*) continue;; esac
      name="${ex%%[[:space:]]*}"
      if [ ! -f "$WF_DIR/$name" ]; then
        echo "DRIFT: $EXEMPT isenta $name, que nao existe em $WF_DIR" >&2
        bad=1
      fi
    done < "$EXEMPT"
  fi
  if [ "$bad" -ne 0 ]; then
    echo "check_workflow_pins: FALHOU — pine por SHA completo (# vN ao lado) ou declare a isencao com motivo" >&2
    return 1
  fi
  echo "check_workflow_pins: OK ($exempt_seen arquivo(s) isento(s))"
}

if [ "${1:-}" = "--selftest" ]; then
  tmp="$(mktemp -d "${TMPDIR:-/tmp}/pins.XXXXXX")"
  trap 'rm -rf "$tmp"' EXIT
  SHA=0123456789abcdef0123456789abcdef01234567
  DIG=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
  WF_DIR="$tmp/wf"; EXEMPT="$tmp/exempt.txt"
  mkdir -p "$WF_DIR"
  : > "$EXEMPT"
  expect() { # <esperado 0|1> <descricao>
    local want="$1" desc="$2" rc=0
    run_gate >/dev/null 2>&1 || rc=1
    if [ "$rc" != "$want" ]; then echo "SELFTEST FALHOU: $desc (esperava rc=$want, veio rc=$rc)" >&2; exit 1; fi
    echo "  ok — $desc"
  }
  w() { printf '%s\n' "$2" > "$WF_DIR/$1"; }

  w a.yml "      - uses: actions/checkout@$SHA # v7";                         expect 0 "SHA + comentario de versao passa"
  w a.yml "        uses: github/codeql-action/init@$SHA # v4";               expect 0 "sub-caminho (owner/repo/path) pinado passa"
  w a.yml "        uses: docker://zricethezav/gitleaks@sha256:$DIG # v8.28.0"; expect 0 "docker@sha256 passa"
  w a.yml "        uses: ./.github/actions/local";                           expect 0 "acao local fica fora"
  w a.yml "        # uses: actions/checkout@v4";                             expect 0 "comentario nao conta"
  w a.yml "        uses: actions/checkout@v4";                               expect 1 "tag movel e capturada"
  w a.yml "        uses: actions/checkout@main";                             expect 1 "branch e capturada"
  w a.yml "        uses: actions/checkout@0123456";                          expect 1 "SHA abreviado e capturado"
  w a.yml "        uses: actions/checkout@${SHA}0";                          expect 1 "SHA de 41 hex e capturado"
  w a.yml "        uses: actions/checkout@$SHA";                             expect 1 "SHA sem comentario de versao e capturado"
  w a.yml "        uses: docker://zricethezav/gitleaks:v8.28.0";             expect 1 "docker por tag e capturado"
  w a.yml "        uses: docker://zricethezav/gitleaks@sha256:abc # v1";     expect 1 "digest curto e capturado"
  w a.yml "        uses: actions/checkout@v4 # 0123456789abcdef0123456789abcdef01234567"; expect 1 "SHA so no comentario nao vale"
  w a.yml "      - uses: 'actions/checkout@v4'";                             expect 1 "tag entre aspas e capturada"

  # isencao: divida declarada passa; drift (ja pinado / arquivo ausente) falha
  w a.yml "        uses: actions/checkout@v4"
  printf 'a.yml motivo\n' > "$EXEMPT";                            expect 0 "isencao declarada passa"
  w a.yml "        uses: actions/checkout@$SHA # v7";                        expect 1 "isencao de arquivo ja pinado e drift"
  w a.yml "        uses: actions/checkout@v4"
  printf 'sumiu.yml motivo\n' > "$EXEMPT";                        expect 1 "isencao de arquivo inexistente e drift"
  echo "check_workflow_pins --selftest: OK"
  exit 0
fi

run_gate
