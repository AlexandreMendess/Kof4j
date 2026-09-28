#!/usr/bin/env bash
# promotion-gate-test.sh — prova o Promotion Gate (D-QUALITY-PIPELINE-2609).
# Hermético (sem rede/gh): só o módulo puro scripts/pipeline/promotion_gate.py.
# Contrato: 100% dos checks obrigatórios verdes (≥80% foi dropado); a matriz de
# bypass é bloqueada mesmo com tudo verde; a janela de observação de 7 dias e a
# ausência de issues relacionadas condicionam prerelease->stable; a criação de
# tag é idempotente (versão já tagueada = BLOCKED).
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
G="scripts/pipeline/promotion_gate.py"
FAILED=0
pass() { echo "  ok  — $1"; }
fail() { echo "  FAIL— $1"; FAILED=1; }

T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
cat > "$T/all-pass.tsv" <<'EOF'
Build + Tests	PASS
Native cross	PASS
kof.io multiplatform	PASS
Structural quality gates	PASS
CodeQL Gate	PASS
bots	PASS
EOF
cat > "$T/one-red.json" <<'EOF'
[{"name":"Build + Tests","status":"FAIL"},{"name":"Native cross","status":"PASS"},
 {"name":"kof.io multiplatform","status":"PASS"},{"name":"Structural quality gates","status":"PASS"},
 {"name":"CodeQL Gate","status":"PASS"},{"name":"bots","status":"PASS"}]
EOF

echo "== selftest embutido =="
python3 "$G" --selftest >/dev/null 2>&1 && pass "selftest OK" || fail "selftest embutido falhou"

echo "== caminho feliz / bloqueio (rc 0 vs 1) =="
python3 "$G" --from-stage lab --to-stage testing --commit c1 --version 0.6.0 \
  --timestamp 2026-09-28T00:00:00Z --checks "$T/all-pass.tsv" >/dev/null 2>&1 \
  && pass "lab->testing tudo verde = PASSED (rc 0)" || fail "lab->testing deveria passar"
python3 "$G" --from-stage lab --to-stage testing --checks "$T/one-red.json" >/dev/null 2>&1 \
  && fail "um check vermelho deveria bloquear" || pass "um check vermelho = BLOCKED (rc 1)"
python3 "$G" --from-stage lab --to-stage testing >/dev/null 2>&1 \
  && fail "checks ausentes deveriam bloquear (fail closed)" || pass "checks ausentes = BLOCKED (fail closed)"

echo "== matriz de bypass bloqueada mesmo com tudo verde =="
for pair in "lab prerelease" "lab stable" "testing stable" "prerelease release/1.0.0" "stable prerelease"; do
  set -- $pair
  python3 "$G" --from-stage "$1" --to-stage "$2" --checks "$T/all-pass.tsv" \
    --promoted-at 2026-09-01T00:00:00Z --timestamp 2026-09-28T00:00:00Z >/dev/null 2>&1 \
    && fail "bypass $1->$2 deveria ser BLOCKED" || pass "bypass $1->$2 = BLOCKED"
done

echo "== janela de observação (7 dias) =="
python3 "$G" --from-stage prerelease --to-stage stable --checks "$T/all-pass.tsv" \
  --promoted-at 2026-09-20T00:00:00Z --timestamp 2026-09-28T00:00:00Z >/dev/null 2>&1 \
  && pass "prerelease->stable após 8 dias = PASSED" || fail "8 dias deveria passar"
python3 "$G" --from-stage prerelease --to-stage stable --checks "$T/all-pass.tsv" \
  --promoted-at 2026-09-25T00:00:00Z --timestamp 2026-09-28T00:00:00Z >/dev/null 2>&1 \
  && fail "3 dias deveria bloquear" || pass "prerelease->stable com 3 dias = BLOCKED"
python3 "$G" --from-stage prerelease --to-stage stable --checks "$T/all-pass.tsv" --related-issues 1 \
  --promoted-at 2026-09-20T00:00:00Z --timestamp 2026-09-28T00:00:00Z >/dev/null 2>&1 \
  && fail "issue relacionada deveria bloquear" || pass "issue relacionada na janela = BLOCKED"

echo "== tag idempotente =="
python3 "$G" --from-stage release/1.0.0 --to-stage kof-1.0.0-linux-x86_64 --checks "$T/all-pass.tsv" \
  --version 1.0.0 --already-tagged >/dev/null 2>&1 \
  && fail "tag duplicada deveria bloquear" || pass "versão já tagueada = BLOCKED (idempotente)"

echo "== relatório carrega os campos de auditoria =="
out="$(python3 "$G" --from-stage testing --to-stage prerelease --commit deadbeef --version 0.6.0 \
  --timestamp 2026-09-28T00:00:00Z --checks "$T/all-pass.tsv" 2>/dev/null)"
printf '%s' "$out" | grep -q 'from: testing (TESTING)' && printf '%s' "$out" | grep -q 'status: PASS' \
  && pass "render traz from/to/status" || fail "render faltando campos"

[ "$FAILED" = 0 ] && echo "== RESULTADO: todos os cenários OK ==" || { echo "== RESULTADO: FALHOU =="; exit 1; }
