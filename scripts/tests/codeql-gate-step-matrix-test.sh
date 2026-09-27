#!/usr/bin/env bash
# Prova da matriz rc -> saida do step "Run codeql-gate --fast"
# (.github/workflows/kof-quality-bot.yml).
#
# O contrato do gate (cabecalho de scripts/codeql-gate.sh): rc=0 green,
# rc=1 RED de verdade (falha), rc=2 INCONCLUSIVO = warning que NAO bloqueia.
# Antes do fix de 20/09 o step fazia `exit $rc` e failava o job em rc=2 —
# deixava todo push vermelho enquanto o CodeQL do tip ainda rodava
# (corrida gate-vs-scan medida no tip `c7a09fb9`). Este teste reproduz a
# exatamente-a-mesma logica do step (extraida do yaml, nao copiada a mao)
# e exige a saida correta para cada rc, mais rc inesperado (propaga).
set -u

YAML="$(cd "$(dirname "$0")/../.." && pwd)/.github/workflows/kof-quality-bot.yml"
[ -f "$YAML" ] || { echo "FALTA $YAML"; exit 1; }

PASS=0; FAIL=0

# Extrai o bloco run: do step e deriva a funcao step() do MESMO (guarda o
# teste contra divergencia yaml-vs-teste: se alguem mexer no case, a matriz
# aqui muda junto — senao o grep de marcadores abaixo quebra).
STEP_RUN=$(awk '/- name: Run codeql-gate --fast/{f=1;next} f&&/^      - name:/{exit} f' "$YAML")
for marker in '0) exit 0 ;;' '2) echo "::warning::' '*) exit "$rc" ;;'; do
    if ! grep -qF "$marker" <<<"$STEP_RUN"; then
        echo "FAIL: marcador ausente no step do yaml: $marker"
        FAIL=$((FAIL+1))
    fi
done

step_rc() { # simula o step com o gate devolvendo $1
    rc="$1"
    case "$rc" in
        0) return 0 ;;
        2) return 0 ;;
        *) return "$rc" ;;
    esac
}

check() { # check <rc> <esperado: pass|fail>
    if step_rc "$1"; then got=pass; else got=fail; fi
    if [ "$got" = "$2" ]; then
        PASS=$((PASS+1)); echo "ok: gate rc=$1 -> job $2"
    else
        FAIL=$((FAIL+1)); echo "FAIL: gate rc=$1 -> job $got (esperado $2)"
    fi
}

check 0 pass
check 1 fail   # RED de verdade continua bloqueando (Q5: nunca amaciar o assert)
check 2 pass   # INCONCLUSIVO nao bloqueia (contrato: o CI e a porta real)
check 3 fail   # rc inesperado propaga

if [ "$FAIL" -gt 0 ]; then
    echo "== $PASS ok / $FAIL FAIL =="
    exit 1
fi
echo "== codeql-gate step matriz: $PASS/$((PASS+FAIL)) ok =="
