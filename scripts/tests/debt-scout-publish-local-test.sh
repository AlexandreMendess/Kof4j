#!/usr/bin/env bash
# debt-scout-publish-local-test.sh — structural test do ensaio LOCAL da
# Wave 3: issue_body.py (dossie do V2 §63, entrada nao confiavel sanitizada)
# e publish_local.py (gates C3/allowlist/seguranca/ownership/dedup/
# actionability/completude/orcamento, saida so em arquivos). Nenhum dos dois
# tem caminho de escrita no GitHub — o selftest prova que nao importam
# subprocess/urllib/http/socket.
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
for mod in issue_body publish_local; do
    if ! OUT="$(python3 "scripts/debt-scout/$mod.py" --selftest 2>&1)"; then
        echo "FALHOU: $mod --selftest:"; echo "$OUT"; rc=1
    else
        echo "ok  — asserções do $mod.py verdes"
    fi
done

# ponta a ponta no repo real, num diretorio temporario (nunca .debt-scout/ do usuario)
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
python3 scripts/debt-scout/scan.py --out "$T/report.json" >/dev/null || { echo "FALHOU: scan.py"; exit 1; }
if ! python3 scripts/debt-scout/publish_local.py --report "$T/report.json" \
        --out-dir "$T/issues" --preview 2 | python3 -c "
import json, sys
r = json.load(sys.stdin)
assert r['issues_opened_on_github'] == 0
assert len(r['previews']) == 2, r['previews']
for e in r['opened']:
    assert e['debt_fingerprint'].startswith('sha256:')
print(len(r['opened']))
"; then
    echo "FALHOU: publish_local ponta a ponta no repo real"; rc=1
else
    echo "ok  — publish_local no repo real: 0 escritas no GitHub, previews gerados"
fi
exit $rc
