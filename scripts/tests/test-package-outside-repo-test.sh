#!/usr/bin/env bash
#
# test-package-outside-repo-test.sh — prova local (sem rede) do gate EG-3 do
# EXIT GATE 1.0 (D-RELEASE-1.0; §12 da PROPOSAL: "real package tested outside
# the repo", fila §23 item 9). Cobre a RED-first do mecanismo:
#   (a) o --selftest do gate recusa uma dist incompleta COM a causa nomeada;
#   (b) o verificador interno reprova saida errada (nenhum falso verde);
#   (c) preflight sem JDK sai 3 alto (a ausencia de ambiente NUNCA vira verde).
# O PASS completo do gate (extrair o tar.gz real + smoke por alvo) roda no
# release-prep, nao aqui (usa mvn/node; ~2min).
#
# Uso: scripts/tests/test-package-outside-repo-test.sh   (exit 0 = verde)
set -u
cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1

out="$(bash scripts/test-package-outside-repo.sh --selftest 2>&1)"; rc=$?
n="$(printf '%s' "$out" | grep -c 'SELFTEST: ok')"
if [ $rc -ne 0 ] || [ "$n" -lt 2 ]; then
    echo "FAIL: selftest do gate nao provou (a)+(b) (rc=$rc): $out" >&2
    exit 1
fi

out="$(env -i PATH=/nonexistent HOME=/nonexistent "$(command -v bash)" scripts/test-package-outside-repo.sh --quick 2>&1)"; rc=$?
if [ $rc -ne 3 ]; then
    echo "FAIL: preflight sem java deveria sair 3, saiu $rc: $out" >&2
    exit 1
fi
case "$out" in *"SEM java"*) : ;; *) echo "FAIL: preflight sem causa nomeada: $out" >&2; exit 1 ;; esac

echo "test-package-outside-repo-test: ok — dist incompleta recusada com causa, verificador interno reprova, preflight alto sem JDK"
