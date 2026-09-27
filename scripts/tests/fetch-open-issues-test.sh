#!/usr/bin/env bash
# fetch-open-issues-test.sh — test do fetcher de issues abertas (offline).
# Cobre: (a) PRs sao excluidos (gh issue list nao lista PRs); (b) o formato
# `numero<TAB>labels` que o gate consome; (c) falha ALTA quando nao consegue
# medir — o gate deve seguir UNKNOWN, nunca "0 bugs" falso (R6/Q5).
set -u
cd "$(git rev-parse --show-toplevel)"
rc=0
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT

cat > "$T/issues.json" << 'JSON'
[
 {"number": 1, "labels": [{"name": "bug"}], "title": "x"},
 {"number": 2, "labels": [{"name": "enhancement"}], "title": "y"},
 {"number": 3, "labels": [{"name": "bug"}], "title": "z", "pull_request": {"url": "u"}}
]
JSON
OUT="$(KOF_ISSUES_JSON="$T/issues.json" bash scripts/fetch-open-issues.sh 2>&1)"; orc=$?
if [ "$orc" -ne 0 ]; then
    echo "FALHOU: modo offline retornou rc=$orc"; echo "$OUT"; rc=1
elif printf '%s\n' "$OUT" | grep -q $'^1\tbug$' && \
     printf '%s\n' "$OUT" | grep -q $'^2\tenhancement$'; then
    echo "ok  — formato numero<TAB>labels"
else
    echo "FALHOU: formato inesperado:"; echo "$OUT"; rc=1
fi
if printf '%s\n' "$OUT" | grep -q $'^3\t'; then
    echo "FALHOU: PR (#3) nao foi excluido"; rc=1
else
    echo "ok  — PRs excluidos da contagem de issues"
fi
OUT2="$(KOF_ISSUES_JSON="$T/nao-existe.json" bash scripts/fetch-open-issues.sh 2>&1)"; rc2=$?
if [ "$rc2" -ne 0 ] && ! printf '%s\n' "$OUT2" | grep -qE '^[0-9]+	'; then
    echo "ok  — falha alta sem linhas (gate seguiria UNKNOWN)"
else
    echo "FALHOU: nao falhou alto em fonte ilegivel (rc=$rc2)"; echo "$OUT2"; rc=1
fi
exit $rc
