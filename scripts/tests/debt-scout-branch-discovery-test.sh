#!/usr/bin/env bash
# debt-scout-branch-discovery-test.sh — structural test da descoberta de
# branch/ref do Debt Scout (Wave 1). Prova que o parser do AGENTS.md e a
# checagem de existencia de ref funcionam. NAO assume "sem drift" — essa
# e' uma propriedade do estado ATUAL da branch (beta-0.5.0 nao tem drift
# agora, `main` TEM: `AGENTS.md` da main declara `beta-0.4.0`, que nao
# existe mais como ref — achado real rodando esta ferramenta contra main,
# 23/09). O invariante testado e': exists=True <=> 0 candidatos,
# exists=False <=> exatamente 1 candidato C1 schema-valido.
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(python3 scripts/debt-scout/branch_discovery.py --selftest 2>&1)"; then
    echo "FALHOU: selftest do branch_discovery:"; echo "$OUT"; rc=1
else
    echo "ok  — asserções do branch_discovery verdes (inclui checagem AO VIVO"
    echo "      do repo real, seja qual for o estado de drift agora)"
fi

JSON="$(python3 scripts/debt-scout/branch_discovery.py)"
if ! python3 -c "
import json, sys
sys.path.insert(0, 'scripts/debt-scout')
import schema
d = json.loads(sys.argv[1])
assert d['default_branch'], 'default_branch vazio'
assert d['declared_active_branch'], 'AGENTS.md nao declarou branch ativa'
exists = d['declared_active_branch_exists']
candidates = d['candidates']
if exists is True:
    assert candidates == [], candidates
elif exists is False:
    assert len(candidates) == 1, candidates
    assert candidates[0]['confidence'] == 'C1', candidates[0]
    errors = schema.validate_candidate(candidates[0])
    assert errors == [], errors
else:
    raise AssertionError(f'declared_active_branch_exists inesperado: {exists!r}')
" "$JSON" 2>&1; then
    echo "FALHOU: CLI sem --selftest produziu estado inconsistente"
    echo "$JSON"
    rc=1
else
    EXISTS="$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['declared_active_branch_exists'])" "$JSON")"
    N="$(python3 -c "import json,sys; print(len(json.loads(sys.argv[1])['candidates']))" "$JSON")"
    echo "ok  — CLI sem --selftest: declared_active_branch_exists=$EXISTS, $N candidato(s), estado consistente"
fi

exit $rc
