#!/usr/bin/env bash
# debt-scout-fingerprint-test.sh — structural test do fingerprint de
# finding/dívida do Debt Scout (Wave 1). --selftest prova idempotência,
# sensibilidade a cada campo e a ausência estrutural de linha/commit/
# timestamp como parâmetro (contrato §6).
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(python3 scripts/debt-scout/fingerprint.py --selftest 2>&1)"; then
    echo "FALHOU: selftest do fingerprint:"; echo "$OUT"; rc=1
else
    echo "ok  — asserções do fingerprint (finding + debt) verdes"
fi

A="$(python3 scripts/debt-scout/fingerprint.py finding RULE sym claim)"
B="$(python3 scripts/debt-scout/fingerprint.py finding RULE sym claim)"
if [ "$A" != "$B" ]; then
    echo "FALHOU: CLI 'finding' nao e deterministico entre duas chamadas"; rc=1
else
    echo "ok  — CLI 'finding' e deterministico entre chamadas de processo separadas"
fi

exit $rc
