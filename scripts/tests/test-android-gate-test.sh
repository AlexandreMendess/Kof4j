#!/usr/bin/env bash
#
# test-android-gate-test.sh — prova local (sem rede, sem SDK) da logica do
# scripts/test-android-gate.sh, o GATE PROPRIO do alvo Android (EXIT GATE 1.0,
# EG-10, decidido em `D-1.0-EDGES`). Usa um SDK e um `kof` FAKE: nenhum APK
# real e montado aqui.
#
# RED-first do mecanismo (o gate nao pode dar verde facil):
#   (1) sem ANDROID_HOME            => exit 3 NOMEANDO o que falta (nunca skip mudo);
#   (2) SDK sem plataforma          => exit 3 NOMEANDO platforms;
#   (3) pipeline honesto            => PASS (apk com manifest + classes.dex);
#   (4) pipeline rc=0 sem apk       => FAIL (rc=0 mentiroso nao passa);
#   (5) pipeline rc!=0              => FAIL;
#   (6) apk sem classes.dex         => FAIL (artefato invalido nao passa).
#
# Uso: scripts/tests/test-android-gate-test.sh   (exit 0 = todos os cenarios passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
GATE="scripts/test-android-gate.sh"
FAILED=0
pass() { echo "  ok  — $1"; }
fail() { echo "  FAIL— $1"; FAILED=1; }
expect() { # desc esperado real
  [ "$2" = "$3" ] && pass "$1 (rc=$3)" || fail "$1: esperado rc=$2, veio rc=$3"
}

# ── cenario 1: selftest RED-first embutido ────────────────────────────────
out="$(bash "$GATE" --selftest 2>&1)"; rc=$?
expect "selftest embutido" 0 "$rc"
printf '%s' "$out" | grep -q "reprova vazio/sem-dex/inexistente" && pass "verificador interno reprova" || fail "selftest nao provou a reprovacao"

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

# ── SDK FAKE: build-tools completa + (opcional) plataforma ────────────────
make_sdk() { # $1 = dir; $2 = with-platform (yes|no); $3 = build-tools versao (default 35.0.0)
  local sdk="$1" with="$2" ver="${3:-35.0.0}"
  local bt="$sdk/build-tools/$ver"
  mkdir -p "$bt"
  for t in aapt2 d8 zipalign apksigner; do
    printf '#!/bin/sh\nexit 0\n' > "$bt/$t"; chmod +x "$bt/$t"
  done
  [ "$with" = yes ] && { mkdir -p "$sdk/platforms/android-34"; : > "$sdk/platforms/android-34/android.jar"; }
  return 0
}
SDK_OK="$TMP/sdk-ok"; make_sdk "$SDK_OK" yes
SDK_NOPLAT="$TMP/sdk-noplat"; make_sdk "$SDK_NOPLAT" no
SDK_OLD="$TMP/sdk-old"; make_sdk "$SDK_OLD" yes 34.0.0

# ── kof FAKE: honra --output e materializa (ou nao) o APK ─────────────────
make_fake_kof() { # $1 = modo (ok|noapk|fail|invalid)
  local mode="$1" d="$TMP/dist-$1"
  mkdir -p "$d/bin"
  cat > "$d/bin/kof" <<EOF
#!/usr/bin/env bash
mode="$mode"
out="gen"; prev=""
for a in "\$@"; do [ "\$prev" = "--output" ] && out="\$a"; prev="\$a"; done
[ "\$mode" = "fail" ] && { echo "pipeline boom" >&2; exit 1; }
mkdir -p "\$out/target"
[ "\$mode" = "noapk" ] && exit 0
apk="\$(pwd)/\$out/target/kof-app.apk"
work="\$(mktemp -d)"
printf '<manifest/>' > "\$work/AndroidManifest.xml"
[ "\$mode" != "invalid" ] && printf 'dex' > "\$work/classes.dex"
( cd "\$work" && jar cf "\$apk" \$( [ "\$mode" = "invalid" ] && echo AndroidManifest.xml || echo AndroidManifest.xml classes.dex ) )
exit 0
EOF
  chmod +x "$d/bin/kof"
  echo "$d"
}

run_gate() { # sdk dist -> stdout; rc em $?
  KOF_ANDROID_GATE_HOME="$TMP" ANDROID_HOME="$1" bash "$GATE" --dist "$2" --work "$TMP" 2>&1
}

# ── cenario 2: sem ANDROID_HOME => exit 3 nomeando ────────────────────────
D_OK="$(make_fake_kof ok)"
out="$(env -u ANDROID_HOME KOF_ANDROID_GATE_HOME="$TMP" bash "$GATE" --dist "$D_OK" --work "$TMP" 2>&1)"; rc=$?
expect "sem ANDROID_HOME recusa" 3 "$rc"
printf '%s' "$out" | grep -q "ANDROID_HOME" && pass "causa nomeada (ANDROID_HOME)" || fail "nao nomeou ANDROID_HOME"

# ── cenario 3: SDK sem plataforma => exit 3 nomeando ──────────────────────
out="$(run_gate "$SDK_NOPLAT" "$D_OK")"; rc=$?
expect "SDK sem plataforma recusa" 3 "$rc"
printf '%s' "$out" | grep -q "platforms" && pass "causa nomeada (platforms)" || fail "nao nomeou platforms"

# ── cenario 3b: build-tools < 35 => exit 3 nomeando (nao certifica) ───────
out="$(run_gate "$SDK_OLD" "$D_OK")"; rc=$?
expect "build-tools antiga recusa" 3 "$rc"
printf '%s' "$out" | grep -q "35" && pass "causa nomeada (build-tools >= 35)" || fail "nao nomeou build-tools 35"

# ── cenario 4: pipeline honesto => PASS ───────────────────────────────────
out="$(run_gate "$SDK_OK" "$D_OK")"; rc=$?
expect "pipeline honesto" 0 "$rc"
printf '%s' "$out" | grep -q "ANDROID-GATE: PASS" && pass "veredito PASS emitido" || fail "sem veredito PASS"

# ── cenario 5: rc=0 sem apk => FAIL (mentira nao passa) ───────────────────
out="$(run_gate "$SDK_OK" "$(make_fake_kof noapk)")"; rc=$?
expect "rc=0 sem apk reprovado" 1 "$rc"
printf '%s' "$out" | grep -q "nao produziu" && pass "ausencia de APK apontada" || fail "rc=0 sem apk passou (falso-verde)"

# ── cenario 6: pipeline rc!=0 => FAIL ─────────────────────────────────────
out="$(run_gate "$SDK_OK" "$(make_fake_kof fail)")"; rc=$?
expect "pipeline rc!=0 reprovado" 1 "$rc"

# ── cenario 7: apk sem classes.dex => FAIL ────────────────────────────────
out="$(run_gate "$SDK_OK" "$(make_fake_kof invalid)")"; rc=$?
expect "apk invalido reprovado" 1 "$rc"
printf '%s' "$out" | grep -q "classes.dex" && pass "artefato invalido apontado" || fail "apk sem dex passou (falso-verde)"

if [ "$FAILED" = 1 ]; then
  echo "== RESULTADO: FALHOU =="
  exit 1
fi
echo "== RESULTADO: todos os cenarios OK =="
