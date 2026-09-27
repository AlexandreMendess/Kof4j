#!/usr/bin/env bash
# changelog-ledger-test.sh — structural test do gate CHANGELOG×ledger.
# Red-first como os vizinhos da suíte: o gate precisa CAPTURAR um flip falso
# plantado (--selftest) e continuar VERDE contra o estado real do repo.
set -u
cd "$(git rev-parse --show-toplevel)"

rc=0
if ! OUT="$(bash scripts/check_changelog_ledger.sh --selftest 2>&1)"; then
    echo "FALHOU: selftest (fixture plantada nao capturada):"; echo "$OUT"; rc=1
else
    echo "ok  — flip falso plantado capturado"
fi
if ! OUT2="$(bash scripts/check_changelog_ledger.sh 2>&1)"; then
    echo "FALHOU: estado real do repo com drift:"; echo "$OUT2"; rc=1
else
    echo "ok  — estado real do repo consistente"
fi
exit $rc
