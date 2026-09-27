#!/usr/bin/env bash
# debt-scout-schema-test.sh — structural test da taxonomia de 3 eixos e do
# schema v2 do Candidate (Wave 1). O selftest da taxonomia cruza os blocos
# ```text de TAXONOMY.md contra os sets em taxonomy.py: doc e codigo NUNCA
# podem divergir em silencio (aplica o proprio DOC_CODE_DRIFT do Debt Scout
# nele mesmo).
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(python3 scripts/debt-scout/taxonomy.py --selftest 2>&1)"; then
    echo "FALHOU: taxonomy --selftest (TAXONOMY.md e taxonomy.py divergiram):"
    echo "$OUT"
    rc=1
else
    echo "ok  — TAXONOMY.md e taxonomy.py concordam nos 3 eixos + escala de lock-in"
fi

if ! OUT2="$(python3 scripts/debt-scout/schema.py --selftest 2>&1)"; then
    echo "FALHOU: schema --selftest:"; echo "$OUT2"; rc=1
else
    echo "ok  — asserções do schema v2 do Candidate verdes"
fi

exit $rc
