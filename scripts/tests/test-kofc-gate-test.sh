#!/usr/bin/env bash
#
# test-kofc-gate-test.sh — prova local (sem rede, sem toolchain) da logica do
# scripts/test-kofc-gate.sh, o GATE PROPRIO do KofC (EXIT GATE 1.0, EG-9,
# decidido em `D-1.0-EDGES`). Usa um compilador FAKE via KOFC_GATE_CC: nenhum
# ELF real e gerado aqui.
#
# RED-first do mecanismo (o gate nao pode dar verde facil):
#   (1) compilador honesto => PASS;
#   (2) binario que imprime valor ERRADO => FAIL (a comparacao de stdout morde);
#   (3) compilador que ACEITA entrada malformada => FAIL (a classe de bug do
#       #485: AST lixo nao pode virar binario silencioso — Q7/R6);
#   (4) toolchain ausente => exit 3 nomeando o que falta (nunca skip mudo).
#
# Uso: scripts/tests/test-kofc-gate-test.sh   (exit 0 = todos os cenarios passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
GATE="scripts/test-kofc-gate.sh"
FAILED=0
pass() { echo "  ok  — $1"; }
fail() { echo "  FAIL— $1"; FAILED=1; }
expect() { # desc esperado real
  [ "$2" = "$3" ] && pass "$1 (rc=$3)" || fail "$1: esperado rc=$2, veio rc=$3"
}

# ── cenario 1: selftest RED-first embutido ────────────────────────────────
out="$(bash "$GATE" --selftest 2>&1)"; rc=$?
expect "selftest embutido" 0 "$rc"
printf '%s' "$out" | grep -q "reprova saida errada" && pass "verificador interno reprova" || fail "selftest nao provou a reprovacao"

# ── fake compiler: classes dir valido + KOFC_GATE_CC ──────────────────────
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/classes/dev/kof/c"
: > "$TMP/classes/dev/kof/c/KofCCompiler.class"

# caso -> stdout esperado (espelha o corpus do gate)
expected_of() { case "$1" in hello) echo 42;; while_) echo 5;; if_) echo 1;; deref) echo 42;; ops) echo 13;; esac; }

make_fake_cc() { # $1 = modo (ok|wrong|acceptbad)
  local mode="$1" f="$TMP/cc-$1"
  cat > "$f" <<EOF
#!/usr/bin/env bash
mode="$mode"
src="\$1"; outdir="\$2"
base="\$(basename "\$src" .c)"
if [ "\$base" = "bad" ]; then
  if [ "\$mode" = "acceptbad" ]; then
    mkdir -p "\$outdir"; printf '#!/usr/bin/env bash\necho 1\n' > "\$outdir/\$base"; chmod +x "\$outdir/\$base"
    exit 0
  fi
  echo "Expected ')' at line 2" >&2
  exit 1
fi
mkdir -p "\$outdir"
case "\$base" in
  hello) v=42;; while_) v=5;; if_) v=1;; deref) v=42;; ops) v=13;; *) v=0;;
esac
[ "\$mode" = "wrong" ] && v=999
printf '#!/usr/bin/env bash\necho %s\n' "\$v" > "\$outdir/\$base"
chmod +x "\$outdir/\$base"
exit 0
EOF
  chmod +x "$f"
  echo "$f"
}

run_gate() { # modo -> stdout; rc em $?
  local cc; cc="$(make_fake_cc "$1")"
  KOFC_GATE_CC="$cc" bash "$GATE" --classes "$TMP/classes" --work "$TMP" 2>&1
}

# ── cenario 2: compilador honesto => PASS ─────────────────────────────────
out="$(run_gate ok)"; rc=$?
expect "compilador honesto" 0 "$rc"
printf '%s' "$out" | grep -q "KOFC-GATE: PASS" && pass "veredito PASS emitido" || fail "sem veredito PASS"

# ── cenario 3: binario com saida ERRADA => FAIL (nao falso-verde) ─────────
out="$(run_gate wrong)"; rc=$?
expect "saida errada reprovada" 1 "$rc"
printf '%s' "$out" | grep -q "!= esperado" && pass "divergencia de stdout apontada" || fail "saida errada passou (falso-verde)"

# ── cenario 4: malformado ACEITO => FAIL (bug #485 nao volta) ─────────────
out="$(run_gate acceptbad)"; rc=$?
expect "malformado aceito reprovado" 1 "$rc"
printf '%s' "$out" | grep -q "AST lixo virou binario" && pass "classe #485 detectada" || fail "malformado aceito passou (R6/Q7 violado)"

# ── cenario 5: toolchain ausente => exit 3 nomeando o que falta ───────────
BIN="$TMP/path-no-as"
mkdir -p "$BIN"
for t in bash java head cut tr mktemp git rm dirname; do
  p="$(command -v "$t" 2>/dev/null)" && ln -sf "$p" "$BIN/$t"
done
out="$(env PATH="$BIN" HOME="$TMP/no-home" bash "$GATE" --classes "$TMP/classes" 2>&1)"; rc=$?
expect "sem 'as' recusa nomeando" 3 "$rc"
printf '%s' "$out" | grep -q "SEM 'as'" && pass "causa nomeada" || fail "nao nomeou a ferramenta ausente"

if [ "$FAILED" = 1 ]; then
  echo "== RESULTADO: FALHOU =="
  exit 1
fi
echo "== RESULTADO: todos os cenarios OK =="
