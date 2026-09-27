#!/usr/bin/env bash
# debt-scout-satd-test.sh — structural test do detector SATD (Wave 1,
# primeiro detector real). O selftest inclui uma varredura AO VIVO do
# repo inteiro: todo candidato emitido precisa validar contra o schema
# v2 e ser sempre C0 (nunca mais alto — um marcador sozinho e sinal, nao
# divida confirmada).
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(python3 scripts/debt-scout/detectors/satd.py --selftest 2>&1)"; then
    echo "FALHOU: satd --selftest:"; echo "$OUT"; rc=1
else
    echo "ok  — asserções do satd verdes (inclui varredura ao vivo do repo real,"
    echo "      todo candidato C0 + schema-valido + sem loop de auto-deteccao)"
fi

# pipe direto (stdin) — evita a traducao de path /tmp entre Git Bash e o
# python nativo do Windows neste sandbox.
if ! N="$(python3 scripts/debt-scout/detectors/satd.py | python3 -c "
import json, sys
c = json.load(sys.stdin)
assert isinstance(c, list)
assert all(x['confidence'] == 'C0' for x in c)
assert all(x['publication']['eligible'] is False for x in c)
assert all('.java' not in x['locations'][0]['path'] for x in c)
print(len(c))
")"; then
    echo "FALHOU: CLI sem --selftest produziu candidato fora do esperado"
    rc=1
else
    echo "ok  — CLI sem --selftest: $N candidatos C0, nenhum .java, nenhum eligible"
fi

exit $rc
