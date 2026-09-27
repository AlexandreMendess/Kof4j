#!/usr/bin/env bash
#
# target-matrix-test.sh — prova local (sem rede, sem compilar) do gate EG-5 do
# EXIT GATE 1.0 (D-RELEASE-1.0; §13/§14 da PROPOSAL, fila §23 item 10).
# Cobre a RED-first do mecanismo com um `kof` FAKE e ferramentas fake:
#   (a) o --selftest do gate reprova saida divergente e aceita igualdade;
#   (b) preflight sem JDK sai 3 alto (ausencia de ambiente nunca vira verde);
#   (c) matriz inteira com saidas iguais → PASS (rc=0);
#   (d) um alvo core divergente → FAIL (rc=1) nomeando o alvo (nenhum falso verde).
# A matriz REAL (6 alvos + qemu) roda no release-prep, nao aqui (~2min).
#
# Uso: scripts/tests/target-matrix-test.sh   (exit 0 = verde)
set -u
cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
FB="$TMP/bin"; mkdir -p "$FB" "$TMP/dist/bin" "$TMP/work"

# ── ferramentas fake ───────────────────────────────────────────────────────
cat > "$FB/java" <<'FAKE'
#!/usr/bin/env bash
echo 'openjdk version "25.0.4" 2025-01-01' >&2
FAKE
for t in node as ld riscv64-linux-gnu-as aarch64-linux-gnu-as; do
    printf '#!/usr/bin/env bash\nexit 0\n' > "$FB/$t"
done
for a in riscv64 aarch64; do
    printf '#!/usr/bin/env bash\ncat "$1"\n' > "$FB/qemu-$a"
done
cat > "$TMP/dist/bin/kof" <<'FAKE'
#!/usr/bin/env bash
sub="$1"; target=""; i=1
while [ $i -le $# ]; do
    case "${!i}" in --target) i=$((i+1)); target="${!i}" ;; esac
    i=$((i+1))
done
case "$sub" in
    run)
        echo "matrix:start"; echo "sum=10"; echo "Hello, Kof!"; echo "matrix:end"
        [ "${FAKE_DIVERGE:-}" = "$target" ] && echo "WRONG"
        ;;
    build)
        mkdir -p build/classes/Default
        { echo "matrix:start"; echo "sum=10"; echo "Hello, Kof!"; echo "matrix:end"; } > build/classes/Default/Main
        ;;
esac
exit 0
FAKE
chmod +x "$FB"/* "$TMP/dist/bin/kof"

# ── (a) selftest do comparador ─────────────────────────────────────────────
out="$(bash scripts/target-matrix.sh --selftest 2>&1)"; rc=$?
if [ $rc -ne 0 ] || ! printf '%s' "$out" | grep -q 'SELFTEST: ok'; then
    echo "FAIL: --selftest nao provou o comparador (rc=$rc): $out" >&2; exit 1
fi

# ── (b) preflight sem java → rc=3, com causa nomeada ───────────────────────
out="$(env -i PATH=/nonexistent HOME=/nonexistent "$(command -v bash)" scripts/target-matrix.sh 2>&1)"; rc=$?
if [ $rc -ne 3 ]; then echo "FAIL: preflight sem java deveria sair 3, saiu $rc: $out" >&2; exit 1; fi
case "$out" in *"SEM java"*) : ;; *) echo "FAIL: preflight sem causa nomeada: $out" >&2; exit 1 ;; esac

# ── (c) matriz fake coerente → PASS ────────────────────────────────────────
out="$(PATH="$FB:/usr/bin:/bin" bash scripts/target-matrix.sh --dist "$TMP/dist" --work "$TMP/work" 2>&1)"; rc=$?
if [ $rc -ne 0 ] || ! printf '%s' "$out" | grep -q 'TARGET-MATRIX: PASS'; then
    echo "FAIL: matriz coerente deveria PASSAR (rc=$rc): $out" >&2; exit 1
fi

# ── (d) um alvo core divergente → FAIL nomeando o alvo ─────────────────────
out="$(PATH="$FB:/usr/bin:/bin" FAKE_DIVERGE=script bash scripts/target-matrix.sh --dist "$TMP/dist" --work "$TMP/work" 2>&1)"; rc=$?
if [ $rc -ne 1 ]; then echo "FAIL: divergencia deveria sair 1, saiu $rc: $out" >&2; exit 1; fi
case "$out" in *"script"*) : ;; *) echo "FAIL: FAIL nao nomeou o alvo divergente: $out" >&2; exit 1 ;; esac

echo "target-matrix-test: ok — comparador RED-first, preflight alto sem JDK, PASS coerente, FAIL nomeia o alvo"
