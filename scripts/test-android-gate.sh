#!/usr/bin/env bash
#
# test-android-gate.sh — EG-10 do D-RELEASE-1.0 (PROPOSAL-1.0-EXIT-GATE §13):
# o alvo Android ganha GATE PROPRIO, honesto, amarrado ao SHA.
#
# O que prova: `kof build --target android --apk` gera um APK de debug REAL a
# partir de codigo 100% Kof — o pipeline standalone do CLI (aapt2 -> d8 ->
# zip -> zipalign -> apksigner) — e o artefato resultante e um zip valido com
# AndroidManifest.xml + classes.dex. Nao e "compilou": e um APK montado.
#
# Honestidade (R6/R7): sem SDK (ANDROID_HOME / build-tools / platforms) o gate
# NAO vira verde falso — ele faz SKIP honesto (exit 3) NOMEANDO o que falta.
# O CI (.github/workflows/android.yml) e quem tem SDK e roda este mesmo gate.
#
# Uso:
#   scripts/test-android-gate.sh            # usa o bin/kof da arvore
#   scripts/test-android-gate.sh --dist DIR # usa o pacote (o objeto do RC)
#   scripts/test-android-gate.sh --keep     # nao apaga a sandbox
#   scripts/test-android-gate.sh --selftest # RED-first offline (sem SDK/compilar)
#
# rc: 0 PASS · 1 FAIL · 3 SKIP/ambiente (sem SDK — nao certifica, nunca mente).
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SELFTEST=false; KEEP=false; DIST_DIR=""
WORK_ROOT="${KOF_ANDROID_GATE_HOME:-$HOME}"
while [ $# -gt 0 ]; do
    case "$1" in
        --dist) DIST_DIR="$2"; shift ;;
        --work) WORK_ROOT="$2"; shift ;;
        --keep) KEEP=true ;;
        --selftest) SELFTEST=true ;;
        *) echo "uso: $0 [--dist DIR] [--work DIR] [--keep] [--selftest]" >&2; exit 2 ;;
    esac
    shift
done

note() { echo "android-gate: $*"; }
sha_of_tip() { git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo "unknown"; }

# ── selftest RED-first (offline: nao toca a arvore, nao exige SDK) ─────────
if [ "$SELFTEST" = true ]; then
    ST="$(mktemp -d)"; trap 'rm -rf "$ST"' EXIT
    fail() { echo "SELFTEST FAIL: $*" >&2; exit 2; }

    # apk_is_valid: zip com AndroidManifest.xml + classes.dex (o criterio do gate).
    apk_is_valid() {
        local apk="$1"
        [ -f "$apk" ] && [ -s "$apk" ] || return 1
        local names; names="$(jar tf "$apk" 2>/dev/null)" || return 1
        grep -q '^AndroidManifest.xml$' <<<"$names" || return 1
        grep -q '^classes\.dex$' <<<"$names" || return 1
        return 0
    }

    # positivo: zip valido passa
    mkdir -p "$ST/ok" && printf '<manifest/>' > "$ST/ok/AndroidManifest.xml" && printf 'dex' > "$ST/ok/classes.dex"
    ( cd "$ST/ok" && jar cf "$ST/ok.apk" AndroidManifest.xml classes.dex ) >/dev/null 2>&1
    apk_is_valid "$ST/ok.apk" || fail "apk valido reprovado (falso vermelho)"

    # negativo: sem classes.dex falha
    mkdir -p "$ST/bad" && printf '<manifest/>' > "$ST/bad/AndroidManifest.xml"
    ( cd "$ST/bad" && jar cf "$ST/bad.apk" AndroidManifest.xml ) >/dev/null 2>&1
    apk_is_valid "$ST/bad.apk" && fail "apk sem classes.dex aceito (falso verde)"

    # negativo: arquivo vazio/inexistente falha
    : > "$ST/empty.apk"
    apk_is_valid "$ST/empty.apk" && fail "apk vazio aceito (falso verde)"
    apk_is_valid "$ST/naoexiste.apk" && fail "apk inexistente aceito (falso verde)"

    echo "SELFTEST: ok — apk_is_valid aceita zip com manifest+dex e reprova vazio/sem-dex/inexistente"
    exit 0
fi

# ── pré-condições honestas ─────────────────────────────────────────────────
command -v java >/dev/null 2>&1 || { echo "android-gate: SKIP — sem java no PATH (kof exige JDK 25)"; exit 3; }
JMAJOR="$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d. -f1)"
[ "${JMAJOR:-0}" -ge 25 ] 2>/dev/null || { echo "android-gate: SKIP — java $JMAJOR no PATH (kof exige 25)"; exit 3; }
command -v jar >/dev/null 2>&1 || { echo "android-gate: SKIP — sem 'jar' no PATH (o pipeline --apk usa 'jar uf')"; exit 3; }

if [ -n "$DIST_DIR" ]; then KOF="$(cd "$DIST_DIR/bin" 2>/dev/null && pwd)/kof"; else KOF="$ROOT/bin/kof"; fi
[ -x "$KOF" ] || { echo "android-gate: SKIP — kof nao executavel em $KOF"; exit 3; }

# ANDROID_HOME + build-tools completa + uma plataforma android-N/android.jar.
AH="${ANDROID_HOME:-}"
if [ -z "$AH" ] || [ ! -d "$AH" ]; then
    echo "android-gate: SKIP — ANDROID_HOME nao definido/inexistente (SDK ausente); o CI android.yml roda este gate"
    exit 3
fi

bt_ok=""
for bt in "$AH"/build-tools/*/; do
    [ -d "$bt" ] || continue
    if [ -x "${bt}aapt2" ] && [ -x "${bt}d8" ] && [ -x "${bt}zipalign" ] && [ -x "${bt}apksigner" ]; then
        v="$(basename "$bt")"
        if [ -z "$bt_ok" ]; then
            bt_ok="$bt"; bt_ver="$v"
        else
            # maior versao COMPLETA vence (comparacao numerica, nao lexica)
            higher="$(printf '%s\n%s\n' "$bt_ver" "$v" | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)"
            if [ "$higher" = "$v" ] && [ "$v" != "$bt_ver" ]; then bt_ok="$bt"; bt_ver="$v"; fi
        fi
    fi
done
if [ -z "$bt_ok" ]; then
    echo "android-gate: SKIP — nenhum build-tools COMPLETO (aapt2/d8/zipalign/apksigner) em $AH/build-tools"
    exit 3
fi

# build-tools < 35 nao le class file major 65+ (Java 21+) — o proprio pipeline
# recusa (ApkToolchain.buildToolsSupportsJava21). Sem build-tools >= 35 o gate
# NAO certifica: SKIP honesto nomeando a causa, nunca verde falso.
bt_major="${bt_ver%%.*}"
case "$bt_major" in ''|*[!0-9]*) bt_major=0 ;; esac
if [ "$bt_major" -lt 35 ]; then
    echo "android-gate: SKIP — build-tools $bt_ver < 35 (nao le classes Java 21+); instale build-tools;35.0.0"
    exit 3
fi

sdk_num=""
for pj in "$AH"/platforms/android-*/android.jar; do
    [ -f "$pj" ] || continue
    n="$(basename "$(dirname "$pj")" | sed 's/^android-//')"
    case "$n" in ''|*[!0-9]*) continue ;; esac
    [ -z "$sdk_num" ] && sdk_num="$n" || { [ "$n" -gt "$sdk_num" ] && sdk_num="$n"; }
done
if [ -z "$sdk_num" ]; then
    echo "android-gate: SKIP — nenhuma plataforma em $AH/platforms/android-*/android.jar"
    exit 3
fi

# ── sandbox FORA do repo (regra 9: nunca /tmp) ─────────────────────────────
SANDBOX="$(mktemp -d "$WORK_ROOT/.kof-android-gate.XXXXXX")"
cleanup() { [ "$KEEP" = false ] && rm -rf "$SANDBOX"; return 0; }
trap cleanup EXIT
cd "$SANDBOX" || exit 3
case "$PWD" in "$ROOT"*) echo "android-gate: IMPOSSIVEL — sandbox dentro do repo" >&2; exit 3;; esac

cat > app.kf <<'KF'
main() {
    var w = Window("Kof Gate")
    w.bind(Label("ok"))
    w.show()
}
KF

ANDROID_JAR="$AH/platforms/android-$sdk_num/android.jar"
note "SDK=$AH platform=android-$sdk_num build-tools=$(basename "$bt_ok") kof=$KOF"

# ── pipeline standalone --apk (aapt2 -> d8 -> zip -> zipalign -> apksigner) ─
"$KOF" build app.kf --target android --output gen --apk --classpath "$ANDROID_JAR" >build.log 2>&1
rc=$?
if [ "$rc" -ne 0 ]; then
    echo "android-gate: FAIL — 'kof build --target android --apk' rc=$rc" >&2
    tail -12 build.log >&2
    exit 1
fi

APK="gen/target/kof-app.apk"
if [ ! -f "$APK" ]; then
    echo "android-gate: FAIL — pipeline rc=0 mas nao produziu $APK" >&2
    tail -12 build.log >&2
    exit 1
fi
if [ ! -s "$APK" ]; then
    echo "android-gate: FAIL — APK vazio em $APK" >&2
    exit 1
fi
names="$(jar tf "$APK" 2>/dev/null || true)"
grep -q '^AndroidManifest.xml$' <<<"$names" || { echo "android-gate: FAIL — APK sem AndroidManifest.xml" >&2; exit 1; }
grep -q '^classes\.dex$' <<<"$names" || { echo "android-gate: FAIL — APK sem classes.dex" >&2; exit 1; }

size="$(wc -c <"$APK" | tr -d ' ')"
echo "ANDROID-GATE: PASS sha=$(sha_of_tip) apk=$size bytes platform=android-$sdk_num"
exit 0
