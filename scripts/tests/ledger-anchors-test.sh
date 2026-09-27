#!/usr/bin/env bash
# ledger-anchors-test.sh — structural test do gate de âncoras do ledger.
# Red-first: slug truncado plantado deve ser capturado; o repo real, verde.
set -u
cd "$(git rev-parse --show-toplevel)"
rc=0
if ! OUT="$(bash scripts/check_ledger_anchors.sh --selftest 2>&1)"; then
    echo "FALHOU: selftest nao capturou o truncamento plantado:"; echo "$OUT"; rc=1
else
    echo "ok  — slug truncado plantado capturado"
fi
if ! OUT2="$(bash scripts/check_ledger_anchors.sh 2>&1)"; then
    echo "FALHOU: repo real com âncora quebrada:"; echo "$OUT2"; rc=1
else
    echo "ok  — repo real com todas as âncoras exatas"
fi
exit $rc
