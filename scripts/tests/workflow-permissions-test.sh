#!/usr/bin/env bash
# workflow-permissions-test.sh — structural test do gate de least privilege dos
# workflows. Red-first como os vizinhos da suíte: o gate precisa MORDER fixtures
# plantadas (--selftest) e continuar VERDE contra o estado real do repo.
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(python3 scripts/check_workflow_permissions.py --selftest 2>&1)"; then
    echo "FALHOU: selftest (fixture plantada nao capturada):"; echo "$OUT"; rc=1
else
    echo "ok  — fixtures plantadas capturadas"
fi
if ! OUT2="$(python3 scripts/check_workflow_permissions.py 2>&1)"; then
    echo "FALHOU: workflows reais fora do least privilege:"; echo "$OUT2"; rc=1
else
    echo "ok  — workflows reais em least privilege (isencoes declaradas: $(echo "$OUT2" | grep -c '^isento:'))"
fi
exit $rc
