#!/usr/bin/env bash
# debt-scout-scan-test.sh — structural test do orquestrador scan.py
# (última unidade de código da Wave 1). O selftest cobre um diretório
# não-git isolado (degrada sem crash), um config inválido (recusa
# escanear) e uma varredura AO VIVO do repo real. Este teste tambem
# exercita o CLI ponta-a-ponta (--phase state, --out) sem deixar sujeira.
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(python3 scripts/debt-scout/scan.py --selftest 2>&1)"; then
    echo "FALHOU: scan --selftest:"; echo "$OUT"; rc=1
else
    echo "ok  — asserções do scan.py verdes (temp dir isolado + config invalido"
    echo "      recusado + fixture plantada + varredura ao vivo do repo real)"
fi

STATE_JSON="$(python3 scripts/debt-scout/scan.py --phase state)"
# NAO assume "sem drift" (essa e' uma propriedade do estado atual da
# branch, nao um invariante do codigo — `main` tem drift real agora,
# `beta-0.5.0` nao). O invariante e' que a resolucao acontece sem crash.
if ! echo "$STATE_JSON" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['default_branch']
assert d['declared_active_branch_exists'] is not None
" 2>/dev/null; then
    echo "FALHOU: --phase state nao resolveu o estado real do repo"
    rc=1
else
    EXISTS="$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['declared_active_branch_exists'])" "$STATE_JSON")"
    echo "ok  — --phase state: resolvido (declared_active_branch_exists=$EXISTS)"
fi

OUT_FILE=".debt-scout/out/candidates-test.json"
rm -rf .debt-scout
python3 scripts/debt-scout/scan.py --out "$OUT_FILE" >/dev/null
if [ ! -f "$OUT_FILE" ]; then
    echo "FALHOU: --out nao escreveu $OUT_FILE"
    rc=1
else
    if ! python3 -c "
import json
with open('$OUT_FILE', encoding='utf-8') as f:
    d = json.load(f)
assert d['summary']['issues_opened'] == 0
assert all(c['publication']['eligible'] is False for c in d['candidates'])
" 2>/dev/null; then
        echo "FALHOU: $OUT_FILE tem forma inesperada"
        rc=1
    else
        echo "ok  — --out escreve um relatorio valido ($(python3 -c "
import json
print(json.load(open('$OUT_FILE', encoding='utf-8'))['summary']['total_candidates'])
") candidatos, issues_opened=0)"
    fi
fi
rm -rf .debt-scout

exit $rc
