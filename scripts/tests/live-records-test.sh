#!/usr/bin/env bash
# live-records-test.sh — structural test do gate de contagem viva nos READMEs da lane.
# Red-first: o gate precisa capturar uma contagem errada plantada (--selftest) e
# continuar VERDE contra o estado real do repo.
set -u
cd "$(git rev-parse --show-toplevel)"
rc=0
if ! OUT="$(bash scripts/check_live_records.sh --selftest 2>&1)"; then
    echo "FALHOU: selftest:"; echo "$OUT"; rc=1
else
    echo "ok  — contagem errada plantada capturada"
fi
if ! OUT2="$(bash scripts/check_live_records.sh 2>&1)"; then
    echo "FALHOU: estado real do repo:"; echo "$OUT2"; rc=1
else
    echo "ok  — estado real do repo consistente"
fi
exit $rc
