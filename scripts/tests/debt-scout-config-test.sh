#!/usr/bin/env bash
# debt-scout-config-test.sh — structural test do carregador de config do
# Debt Scout (Wave 1). Red-first: o --selftest precisa MORDER as fixtures
# plantadas (mode!=shadow, model.enabled=true, budgets invertidos, ...) e o
# .debt-scout.yml real do repo precisa continuar validando.
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(python3 scripts/debt-scout/config.py --selftest 2>&1)"; then
    echo "FALHOU: selftest (fixture plantada nao capturada ou config real quebrada):"
    echo "$OUT"
    rc=1
else
    echo "ok  — fixtures plantadas capturadas + .debt-scout.yml real valida"
fi

# O CLI sem --selftest precisa imprimir o config real resolvido como JSON.
if ! JSON="$(python3 scripts/debt-scout/config.py 2>&1)"; then
    echo "FALHOU: carregar .debt-scout.yml real:"; echo "$JSON"; rc=1
elif ! python3 -c "import json,sys; d=json.loads(sys.argv[1]); assert d['mode']=='shadow'; assert d['model']['enabled'] is False" "$JSON" 2>/dev/null; then
    echo "FALHOU: JSON resolvido nao tem mode=shadow/model.enabled=false:"; echo "$JSON"; rc=1
else
    echo "ok  — CLI sem --selftest imprime JSON resolvido com mode=shadow"
fi

exit $rc
