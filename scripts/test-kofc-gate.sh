#!/usr/bin/env bash
#
# test-kofc-gate.sh — EXIT GATE 1.0, EG-9 (decidido em `D-1.0-EDGES`, 20/09):
# o KofC e parte da Stable 1.0 Surface e carrega GATE PROPRIO.
#
# O que este gate prova, amarrado ao SHA do candidato:
#   1. o toolchain nativo existe (as + ld ou gcc) — ausencia = FAIL com causa
#      nomeada, nunca skip silencioso (R6);
#   2. o compilador KofC builda (mvn -o -pl kof-c-compiler -am compile) ou usa
#      classes ja construidas (--classes);
#   3. o SUBCONJUNTO suportado compila E EXECUTA com a saida correta (o binario
#      ELF real e rodado, nao so gerado) — corpus de 5 casos medido do
#      KofCCompilerTest;
#   4. entrada malformada e REJEITADA com diagnostico e SEM binario (a classe
#      de bug do #485: AST lixo nao pode virar binario silencioso — Q7/R6).
#
# Uso:
#   scripts/test-kofc-gate.sh                 # build + corpus (completo)
#   scripts/test-kofc-gate.sh --classes DIR   # reusa classes construidas
#   scripts/test-kofc-gate.sh --work DIR      # sandbox (default: $HOME)
#   scripts/test-kofc-gate.sh --keep          # nao apaga a sandbox
#   scripts/test-kofc-gate.sh --selftest      # RED-first offline: o verificador
#                                             # interno DEVE reprovar saida errada
#
# Pre-condicao honesta: `as` e (`ld` ou `gcc`) no PATH e um JDK >= 25 (class
# file 69). Faltando qualquer um, o gate FALHA nomeando o que falta — nunca
# finge verde (mesma doutrina do test-package-outside-repo.sh).
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLASSES=""
SELFTEST=false; KEEP=false; WORK_ROOT="${KOFC_GATE_HOME:-$HOME}"
SHA=""
while [ $# -gt 0 ]; do
    case "$1" in
        --classes) CLASSES="$2"; shift ;;
        --work) WORK_ROOT="$2"; shift ;;
        --sha) SHA="$2"; shift ;;
        --keep) KEEP=true ;;
        --selftest) SELFTEST=true ;;
        *) echo "uso: $0 [--classes DIR] [--work DIR] [--sha SHA] [--keep] [--selftest]" >&2; exit 2 ;;
    esac
    shift
done

FAILURES=""
note() { echo "kofc-gate: $*"; }
fail() { FAILURES="$FAILURES
  - $*"; note "FAIL: $*"; }
must_have() { # rotulo saida esperada...
    local label="$1"; shift
    for needle in "$@"; do
        case "$LAST_OUT" in
            *"$needle"*) : ;;
            *) fail "$label: saida nao contem '$needle' — foi: $(printf '%s' "$LAST_OUT" | head -c 200)"; return 1 ;;
        esac
    done
    return 0
}

# ── selftest RED-first (o gate nao pode dar verde facil) ───────────────────
if [ "$SELFTEST" = true ]; then
    LAST_OUT="saida real"
    FAILURES=""
    if must_have "auto" "texto-que-nao-existe"; then
        echo "SELFTEST FAIL: must_have deixou passar saida errada (falso verde)"; exit 1
    fi
    FAILURES=""
    LAST_OUT="Expected ')'"
    must_have "diag" "Expected" || { echo "SELFTEST FAIL: nao reconhece o diagnostico esperado"; exit 1; }
    FAILURES=""
    echo "SELFTEST: ok — verificador interno reprova saida errada e aceita a certa"
    exit 0
fi

# ── pre-condicoes honestas ─────────────────────────────────────────────────
[ -d "$HOME/tools/apache-maven-3.9.9/bin" ] && PATH="$HOME/tools/apache-maven-3.9.9/bin:$PATH"
[ -d "$HOME/tools/jdk-25/bin" ] && PATH="$HOME/tools/jdk-25/bin:$PATH"
export PATH
command -v java >/dev/null 2>&1 || { echo "kofc-gate: SEM java no PATH (o KofC exige JDK >= 25)" >&2; exit 3; }
JMAJOR="$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d. -f1)"
[ "${JMAJOR:-0}" -ge 25 ] 2>/dev/null || { echo "kofc-gate: java $JMAJOR < 25 — ambiente sem JDK, nao bug do KofC" >&2; exit 3; }
command -v as >/dev/null 2>&1 || { echo "kofc-gate: SEM 'as' no PATH (o KofC emite ELF via GAS) — ambiente, nao bug" >&2; exit 3; }
if ! command -v ld >/dev/null 2>&1 && ! command -v gcc >/dev/null 2>&1; then
    echo "kofc-gate: SEM 'ld' nem 'gcc' no PATH (o KofC linka o ELF) — ambiente, nao bug" >&2; exit 3
fi

# ── classes do compilador ──────────────────────────────────────────────────
if [ -z "$CLASSES" ]; then
    CLASSES="$ROOT/kof-c-compiler/target/classes"
    if [ ! -d "$CLASSES" ]; then
        note "buildando kof-c-compiler (mvn -o -pl kof-c-compiler -am compile)..."
        ( cd "$ROOT" && mvn -o -pl kof-c-compiler -am compile -q ) \
            || { echo "kofc-gate: build do kof-c-compiler FALHOU (Q2 — saida acima)" >&2; exit 1; }
    fi
fi
[ -d "$CLASSES" ] || { echo "kofc-gate: classes ausentes em $CLASSES" >&2; exit 1; }
[ -f "$CLASSES/dev/kof/c/KofCCompiler.class" ] || { echo "kofc-gate: KofCCompiler.class ausente em $CLASSES" >&2; exit 1; }
# KOFC_GATE_CC e gancho do --test offline (fakes); nunca usado em push real.
CC="${KOFC_GATE_CC:-java -cp $CLASSES dev.kof.c.KofCCompiler}"

[ -n "$SHA" ] || SHA="$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"

# ── sandbox fora da arvore (regra 9: nada de trabalho em /tmp) ─────────────
SANDBOX="$(mktemp -d "$WORK_ROOT/.kofc-gate.XXXXXX")"
cleanup() { [ "$KEEP" = false ] && rm -rf "$SANDBOX"; return 0; }
trap cleanup EXIT
cd "$SANDBOX"

compile_run() { # nome fonte esperado  -> $LAST_OUT = stdout do binario
    local name="$1" src="$2" expected="$3"
    printf '%s\n' "$src" > "$name.c"
    local out
    out="$($CC "$name.c" "$name-out" 2>&1)"; local rc=$?
    if [ $rc -ne 0 ]; then fail "caso $name: compilacao falhou (rc=$rc): $out"; return 1; fi
    local bin="$name-out/$name"
    [ -x "$bin" ] || { fail "caso $name: binario nao executavel em $bin"; return 1; }
    LAST_OUT="$("$bin" 2>&1)"; local rrc=$?
    if [ $rrc -ne 0 ]; then fail "caso $name: binario saiu rc=$rrc"; return 1; fi
    LAST_OUT="$(printf '%s' "$LAST_OUT" | tr -d '\r')"
    if [ "$LAST_OUT" != "$expected" ]; then
        fail "caso $name: stdout '$LAST_OUT' != esperado '$expected'"; return 1
    fi
    note "ok: $name = $expected"
    return 0
}

note "corpus suportado (compila E executa; SHA $SHA)"
compile_run hello   'int x;
void main() { x = 42; print_arg = x; print(); }' 42
compile_run while_  'int x;
int y;
void main() { x = 0; y = 5; while(x < y) { x = x + 1; } print_arg = x; print(); }' 5
compile_run if_     'int x;
int y;
void main() { x = 10; y = 0; if(x > 5) { y = 1; } print_arg = y; print(); }' 1
compile_run deref   'int x;
int p;
void main() { x = 99; p = &x; *(int*)p = 42; print_arg = x; print(); }' 42
compile_run ops     'int a;
int b;
int c;
void main() { a = 10; b = 3; c = a + b; print_arg = c; print(); }' 13

note "rejeicao de entrada malformada (R6/Q7: nada de binario de AST lixo)"
printf 'int x;\nvoid main) { x = 1;\n' > bad.c
LAST_OUT="$($CC bad.c bad-out 2>&1)"; rc=$?
if [ $rc -eq 0 ]; then
    fail "malformado: compilou (rc=0) — AST lixo virou binario (regressao do #485)"
else
    must_have "malformado" "Expected" || true
    if [ -e bad-out/bad ]; then fail "malformado: binario emitido apesar do erro"; else note "ok: malformado rejeitado com diagnostico"; fi
fi

# ── veredito ───────────────────────────────────────────────────────────────
if [ -n "$FAILURES" ]; then
    echo "KOFC-GATE: FAIL (sha=$SHA) — itens fora do contrato:" >&2
    echo "$FAILURES" >&2
    exit 1
fi
echo "KOFC-GATE: PASS sha=$SHA cases=5 + reject=1"
exit 0
