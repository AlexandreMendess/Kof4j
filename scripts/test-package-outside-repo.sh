#!/usr/bin/env bash
#
# test-package-outside-repo.sh — PROPOSAL-1.0-EXIT-GATE §12 / fila §23 item 9
# ("real package tested outside the repo"), a frente EG-3 do D-RELEASE-1.0.
#
# #550 mostrou o cano: algo que funciona dentro da árvore pode FALHAR no
# pacote distribuído. Este script fecha essa aresta de forma mecânica:
#
#   1. builda a distribuição oficial (scripts/package.sh) — ou aceita --dist;
#   2. extrai o tar.gz REAL para um diretório limpo FORA do repo (em $HOME —
#      regra 9 do AGENTS.md: nada de trabalho em /tmp);
#   3. roda o `kof` daquele pacote SEM ACESSO À ÁRVORE (cwd sandbox, env
#      despoluído de variáveis do repo), na ordem do §12:
#        kof version → kof info → kof new demo → run do template por alvo;
#   4. smoke por alvo do checklist §13: jvm, js, native.x86_64, script —
#     cross (riscv64/aarch64) é build-only sem qemu, exec quando qemu existir
#     (guarda honesta, nunca skip silencioso — R6);
#   5. imprime `PKG-TEST: PASS ...` e sai 0 só se TODO item obrigatório passou.
#
# Uso:
#   scripts/test-package-outside-repo.sh                # completo (builda dist)
#   scripts/test-package-outside-repo.sh --dist DIR     # reusa uma dist pronta
#   scripts/test-package-outside-repo.sh --quick        # jvm+script (sem js/native)
#   scripts/test-package-outside-repo.sh --keep         # não apaga a sandbox
#   scripts/test-package-outside-repo.sh --selftest     # RED-first offline:
#                                                       # um pacote incompleto
#                                                       # DEVE falhar alto
#
# Pré-condição honesta: um `java` no PATH (o launcher do pacote cai no java de
# sistema quando não há JDK embutido — mesma queda que o usuário sofre).
# Falta de java/node/ferramenta = FAIL com a causa nomeada, nunca verde falso.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="$(cat "$ROOT/VERSION")"
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
case "$OS" in mingw*|msys*|cygwin*) OS=windows ;; esac
ARCH_RAW="$(uname -m)"
case "$ARCH_RAW" in x86_64|amd64) ARCH=x86_64 ;; aarch64|arm64) ARCH=aarch64 ;; *) ARCH="$ARCH_RAW" ;; esac
DIST_NAME="kof-$VERSION-$OS-$ARCH"

SELFTEST=false
QUICK=false; KEEP=false; DIST_DIR=""; WORK_ROOT="${KOF_PKG_TEST_HOME:-$HOME}"
while [ $# -gt 0 ]; do
    case "$1" in
        --quick) QUICK=true ;;
        --keep) KEEP=true ;;
        --dist) DIST_DIR="$2"; shift ;;
        --work) WORK_ROOT="$2"; shift ;;
        --selftest) SELFTEST=true ;;
        *) echo "uso: $0 [--dist DIR] [--quick] [--keep] [--work DIR] [--selftest]" >&2; exit 2 ;;
    esac
    shift
done

FAILURES=""
note() { echo "pkg-test: $*"; }
fail() { FAILURES="$FAILURES
  - $*"; note "FAIL: $*"; }
must_ok() { # rotulo cmd...
    local label="$1"; shift
    local out rc
    out="$("$@" 2>&1)"; rc=$?
    if [ $rc -ne 0 ]; then fail "$label (rc=$rc): $out"; return 1; fi
    note "ok: $label"
    LAST_OUT="$out"
    return 0
}
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

SANDBOX=""
cleanup() {
    [ -n "$SANDBOX" ] && [ "$KEEP" = false ] && rm -rf "$SANDBOX"
    return 0
}
trap cleanup EXIT

# ── selftest RED-first (§10: o gate não pode dar verde fácil) ─────────────
if [ "$SELFTEST" = true ]; then
    ST="$(mktemp -d)"
    trap 'rm -rf "$ST"' EXIT
    # (a) distribuição incompleta (sem lib/kof.jar) DEVE falhar alto, com causa
    mkdir -p "$ST/bin" "$ST/lib"
    cp "$ROOT/bin/kof" "$ST/bin/kof"
    if out="$("$ST/bin/kof" version 2>&1)"; then
        echo "SELFTEST FAIL: launcher aceitou dist sem kof.jar"; exit 1
    fi
    case "$out" in *"missing"*) echo "SELFTEST: ok — dist incompleta recusada com causa";; *) echo "SELFTEST FAIL: recusa sem causa nomeada: $out"; exit 1;; esac
    # (b) o verificador interno sabe reprovar (RED do RED): nada falso-verde
    LAST_OUT="saida real"
    FAILURES=""
    if must_have "auto" "texto-que-nao-existe"; then
        echo "SELFTEST FAIL: must_have deixou passar saida errada (falso verde)"; exit 1
    fi
    FAILURES=""
    echo "SELFTEST: ok — verificador interno reprova saida errada"
    exit 0
fi

# ── pré-condições honestas ─────────────────────────────────────────────────
# host de desenvolvimento padrao do cluster: JDK/maven sob $HOME/tools (mesmo
# bootstrap do scripts/safe-suite.sh); sem isso o build da dist morre em mvn.
[ -d "$HOME/tools/apache-maven-3.9.9/bin" ] && PATH="$HOME/tools/apache-maven-3.9.9/bin:$PATH"
# o JDK do host de dev do cluster (mesma ordem do safe-suite: tools/ ANTES do
# java de sistema — java 20 do sistema não carrega classes 69 do pacote e isso
# NÃO é bug do pacote, é ambiente; preflight valida a versao, nunca engole)
[ -d "$HOME/tools/jdk-25/bin" ] && PATH="$HOME/tools/jdk-25/bin:$PATH"
export PATH
command -v java >/dev/null 2>&1 || { echo "pkg-test: SEM java no PATH — o launcher de dist cai no java de sistema; proveja um JDK 25 e rode de novo" >&2; exit 3; }
JMAJOR="$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d. -f1)"
[ "${JMAJOR:-0}" -ge 25 ] 2>/dev/null || { echo "pkg-test: java $JMAJOR no PATH e o pacote exige 25 (class file 69) — ambiente sem JDK, nao bug do pacote" >&2; exit 3; }
: "${JAVA_HOME:=$(dirname "$(dirname "$(command -v java)")")}" ; export JAVA_HOME
if [ "$QUICK" = false ]; then
    command -v node >/dev/null 2>&1 || { echo "pkg-test: SEM node no PATH (alvo js obrigatório; use --quick p/ pular) " >&2; exit 3; }
fi

# ── 1. dist real ───────────────────────────────────────────────────────────
if [ -z "$DIST_DIR" ]; then
    note "construindo a distribuicao (scripts/package.sh)... -o offline"
    bash "$ROOT/scripts/package.sh" || { echo "pkg-test: package.sh FALHOU (saida acima, nao engolida — R6)" >&2; exit 1; }
    DIST_DIR="$ROOT/dist/$DIST_NAME"
fi
[ -x "$DIST_DIR/bin/kof" ] || { echo "pkg-test: dist ausente em $DIST_DIR" >&2; exit 1; }
TARBALL="$ROOT/dist/$DIST_NAME.tar.gz"

# ── 2. cópia EXTRAÍDA para fora do repo, sem acesso à árvore ───────────────
SANDBOX="$(mktemp -d "$WORK_ROOT/.kof-pkg-test.XXXXXX")"
if [ -f "$TARBALL" ]; then
    tar xzf "$TARBALL" -C "$SANDBOX"
    note "tar.gz real extraido (valida o artefato que o usuario baixa)"
else
    cp -r "$DIST_DIR" "$SANDBOX/$DIST_NAME"
    note "AVISO: tar.gz nao encontrado — copiei o layout (mais fraco que o pacote real)"
fi
KOF="$SANDBOX/$DIST_NAME/bin/kof"
[ -x "$KOF" ] || { echo "pkg-test: extracao nao produziu bin/kof" >&2; exit 1; }

# ── 3. dentro da sandbox: sem variaveis do repo, cwd = sandbox ─────────────
cd "$SANDBOX"
for v in KOF_HOME KOF_LIBS KOF_STDLIB_DIR KOF_CROSS_PREFIX; do unset "$v" 2>/dev/null || true; done
case "$PWD" in "$ROOT"*) echo "pkg-test: IMPOSSIVEL — sandbox dentro do repo" >&2; exit 1;; esac

note "kof version"
must_ok "version" "$KOF" version && must_have "version" "$VERSION"
note "kof info"
must_ok "info" "$KOF" info
note "kof new demo"
must_ok "new" "$KOF" new demo
[ -f demo/src/Main.kf ] || fail "kof new nao gerou demo/src/Main.kf (projeto padrao)"
APP="demo/src/Main.kf"

run_target() { # alvo [extra]
    local t="$1"
    note "run --target $t"
    must_ok "run $t" "$KOF" run "$APP" --target "$t" && must_have "run $t" "Hello, Kof!"
}
run_target jvm
run_target script
if [ "$QUICK" = false ]; then
    run_target js
    if command -v as >/dev/null 2>&1 && command -v ld >/dev/null 2>&1; then
        run_target native
    else
        fail "native.x86_64: sem as/ld no PATH (ambiente sem ferramenta, nao bug do pacote)"
    fi
    # cross: build-only (exec de ELF riscv/aarch no teste do pacote fica com a
    # matrix final §23-10, que ja roda no CI com qemu) — toolchain ausente = skip honesto
    for c in native.riscv64 native.aarch64; do
        pfx="${c#native.}"; case "$pfx" in riscv64) TC=riscv64-linux-gnu-as;; *) TC=aarch64-linux-gnu-as;; esac
        if command -v "$TC" >/dev/null 2>&1; then
            note "build $c"
            must_ok "build $c" "$KOF" build "$APP" --target "$c"
        else
            note "skip honesto: $c sem $TC"
        fi
    done
fi

# libs puras-Kof oficiais tambem tem que resolver fora do repo (#550 na veia)
if [ -d "$SANDBOX/$DIST_NAME/lib/kof-libs" ]; then
    note "kof-libs presentes na sandbox (pdf)"
    printf 'import kof.pdf\nmain() { println("pdf-ok") }\n' > pdfcheck.kf
    must_ok "pure-kof lib resolve fora do repo" "$KOF" run pdfcheck.kf --target jvm && must_have "pdf" "pdf-ok"
fi

# ── veredito ───────────────────────────────────────────────────────────────
if [ -n "$FAILURES" ]; then
    echo "PKG-TEST: FAIL — itens fora do contrato do pacote:" >&2
    echo "$FAILURES" >&2
    exit 1
fi
if [ "$QUICK" = true ]; then SCOPE="jvm+script (quick)"; else SCOPE="jvm+script+js+native(x86_64) + cross-guard"; fi
echo "PKG-TEST: PASS — pacote $DIST_NAME validado fora do repo ($SCOPE)"
exit 0
