#!/usr/bin/env bash
#
# kof-issues-agent-script-test.sh — #570: o script do passo "Auto-reply to new issues" do
# .github/workflows/kof-issues-agent.yml executa sem lancar excecao para comentario humano
# (github.event NAO existe dentro do actions/github-script). Sem rede, sem writes no GitHub.
#
# Uso: scripts/tests/kof-issues-agent-script-test.sh   (exit 0 = verde)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
if ! command -v node >/dev/null 2>&1; then
    echo "  SKIP — node ausente (o CI ubuntu tem node; skip honesto, nunca verde falso)"
    exit 0
fi
node scripts/tests/kof-issues-agent-script-test.js
