#!/usr/bin/env bash
#
# agent-verify-wiring-test.sh — prova de que os hooks dos gates CHANGELOG/ledger
# no agent-verify DISPARAM de verdade. O suite-test dos gates prova o detector;
# aqui prova o fio em DOIS niveis:
#   (1) regex  — o `touches` de cada hook casa exatamente as familias de arquivos;
#   (2) funcional — extrai o esqueleto real dos blocos do dispatcher e o executa
#       com uma lista `changed` sintetica, exigindo a chamada certa. Um regex
#       perfeito dentro de um bloco aninhado errado = gate morto em silencio, e
#       so o nivel 1 NAO veria — foi exatamente o bug que a lane plantou ao
#       inserir `live_records` e que motivou este segundo nivel.
#
# Uso: scripts/tests/agent-verify-wiring-test.sh   (exit 0 = fios ligados)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
AV=scripts/agent-verify.sh
fail=0
hook_pat() { # $1 = rotulo do run_gate -> regex do touches imediatamente anterior
    local p
    p="$(grep -B1 "run_gate $1 " "$AV" | grep -oE "touches '[^']+'" | sed "s/touches '//; s/'$//")"
    [ -n "$p" ] || { echo "MISSING: hook '$1' sumiu do agent-verify.sh"; return 1; }
    printf '%s\n' "$p"
}
hit() { printf '%s\n' "$1" | grep -qE "$2"; }
for_each_pat() { # $1=pat $2=tipo(yes|no) $3..=caminhos
    local pat="$1" want="$2"; shift 2
    local c got
    for c in "$@"; do
        if hit "$c" "$pat"; then got=yes; else got=no; fi
        if [ "$got" != "$want" ]; then
            echo "FIO QUEBRADO: '$c' -> $got (esperado $want) em [$pat]"; fail=1
        fi
    done
}
skeleton_calls() { # $1 = caminho changed -> nomes dos gates que o dispatcher real chamaria
    local sk; sk="$(sed -n '/^if touches/,/^DIST=/p' "$AV" | grep -E '^(if touches|fi|    run_gate)')"
    ( changed="$1"
      touches() { printf '%s\n' "$changed" | grep -qE "$1"; }
      run_gate() { echo "$1"; }
      eval "$sk" )
}
want_called() { # $1=caminho $2=gate  (captura antes: grep -q + pipefail + SIGPIPE
    local calls; calls="$(skeleton_calls "$1")"   # dariam falso-negativo — ver DOING)
    printf '%s\n' "$calls" | grep -qx "$2" || {
        echo "FIO MORTO: '$1' deveria chamar '$2' e nao chamou (bloco aninhado/morto?)"; fail=1; }
}
want_not() { # $1=caminho $2=gate
    local calls; calls="$(skeleton_calls "$1")"
    if printf '%s\n' "$calls" | grep -qx "$2"; then
        echo "FIO LARGO: '$1' nao deveria chamar '$2'"; fail=1
    fi
}
CHG="$(hook_pat changelog_ledger)" || exit 1
ANC="$(hook_pat ledger_anchors)" || exit 1
LRC="$(hook_pat live_records)" || exit 1
for_each_pat "$CHG" yes CHANGELOG.md CHANGELOG.pt_BR.md \
    docs/bugs-and-gaps/known-bugs.md docs/bugs-and-gaps/known-bugs.pt_BR.md \
    scripts/changelog-ledger-waivers.txt
for_each_pat "$CHG" no docs/development/README.md kof-runtime/src/main/java/dev/kof/runtime/KofJsRunner.java
for_each_pat "$ANC" yes docs/bugs-and-gaps/known-bugs.md docs/bugs-and-gaps/known-bugs.pt_BR.md
for_each_pat "$ANC" no CHANGELOG.md scripts/changelog-ledger-waivers.txt
for_each_pat "$LRC" yes docs/development/README.md docs/development/README.pt_BR.md \
    docs/development/DECISIONS.md docs/development/DECISIONS.pt_BR.md
for_each_pat "$LRC" no CHANGELOG.md docs/bugs-and-gaps/known-bugs.md
want_called docs/development/README.md live_records
want_called docs/development/DECISIONS.md live_records
want_not   docs/development/README.md ledger_anchors
want_called docs/bugs-and-gaps/known-bugs.md ledger_anchors
want_not   docs/bugs-and-gaps/known-bugs.md live_records
want_called docs/bugs-and-gaps/known-bugs.md changelog_ledger
want_called CHANGELOG.md changelog_ledger
want_called docs/qualquer.md docs_lang
# mutacao do proprio teste: trocar o regex real por um impossivel deve derrubar
if printf 'CHANGELOG.md\n' | grep -qE "^CHANGELOG\\.NUNCA$"; then echo "MUTACAO INVALIDA"; exit 1; fi
[ "$fail" -eq 0 ] && echo "ok  — fios changelog_ledger/ledger_anchors/live_records batendo (regex + funcional)"
exit $fail
