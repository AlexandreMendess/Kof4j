#!/usr/bin/env bash
# workflow-pins-test.sh — structural test do gate de pin de Actions por SHA.
# Red-first como os vizinhos da suíte: o gate precisa MORDER fixtures plantadas
# (--selftest) e continuar VERDE contra o estado real de .github/workflows.
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(bash scripts/check_workflow_pins.sh --selftest 2>&1)"; then
    echo "FALHOU: selftest (fixture plantada nao capturada):"; echo "$OUT"; rc=1
else
    echo "ok  — fixtures plantadas capturadas"
fi
if ! OUT2="$(bash scripts/check_workflow_pins.sh 2>&1)"; then
    echo "FALHOU: workflows reais com referencia nao pinada:"; echo "$OUT2"; rc=1
else
    echo "ok  — workflows reais pinados (isencoes declaradas: $(echo "$OUT2" | grep -c '^isento:'))"
fi
exit $rc
