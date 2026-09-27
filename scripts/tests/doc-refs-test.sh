#!/usr/bin/env bash
# doc-refs-test.sh — structural test do gate de referencias nos docs da lane.
# Red-first: o gate precisa capturar path quebrado e SHA orfao plantados
# (--selftest) e continuar VERDE contra o estado real do repo.
set -u
cd "$(git rev-parse --show-toplevel)"
rc=0
if ! OUT="$(bash scripts/check_doc_refs.sh --selftest 2>&1)"; then
    echo "FALHOU: selftest:"; echo "$OUT"; rc=1
else
    echo "ok  — path quebrado + SHA orfao plantados capturados"
fi
if ! OUT2="$(bash scripts/check_doc_refs.sh 2>&1)"; then
    echo "FALHOU: estado real do repo:"; echo "$OUT2"; rc=1
else
    echo "ok  — referencias do repo integras"
fi
exit $rc
