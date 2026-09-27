#!/usr/bin/env bash
# setup-cross-toolchain-test.sh — test offline do helper de toolchain cross.
# Cria um prefixo FALSO ja "preparado" e verifica que o script (a) o detecta sem
# rede/root e (b) imprime os 3 exports que tornam a matriz de paridade verde.
set -u
cd "$(git rev-parse --show-toplevel)"
rc=0
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
P="$T/prefix"
mkdir -p "$P/usr/bin" "$P/usr/lib/x86_64-linux-gnu"
for a in aarch64 riscv64; do
    mkdir -p "$P/usr/$a-linux-gnu/lib"
    printf '#!/bin/sh\necho "GNU assembler (fake)"\n' > "$P/usr/bin/$a-linux-gnu-as"
    printf '#!/bin/sh\nexit 0\n' > "$P/usr/bin/qemu-$a"
    chmod +x "$P/usr/bin/$a-linux-gnu-as" "$P/usr/bin/qemu-$a"
    : > "$P/usr/$a-linux-gnu/lib/libc.so"
done

OUT="$(KOF_CROSS_PREFIX="$P" bash scripts/setup-cross-toolchain.sh 2>/dev/null)"; orc=$?
if [ "$orc" -ne 0 ]; then
    echo "FALHOU: script rc=$orc num prefixo ja preparado"; echo "$OUT"; rc=1
else
    echo "ok  — prefixo preparado detectado sem rede"
fi
for want in "PATH=\"$P/usr/bin:" "KOF_CROSS_SYSROOT=\"$P\""; do
    if printf '%s\n' "$OUT" | grep -qF "$want"; then
        echo "ok  — export presente: ${want%%=*}"
    else
        echo "FALHOU: export ausente ($want)"; echo "$OUT"; rc=1
    fi
done
if printf '%s\n' "$OUT" | grep -qF "LD_LIBRARY_PATH=\"$P/usr/lib/x86_64-linux-gnu:"; then
    echo "ok  — export presente: LD_LIBRARY_PATH"
else
    echo "FALHOU: LD_LIBRARY_PATH ausente"; echo "$OUT"; rc=1
fi
EXP="$(KOF_CROSS_PREFIX="$P" bash scripts/setup-cross-toolchain.sh --export 2>/dev/null)"
if [ "$(printf '%s\n' "$EXP" | grep -c '^export ')" -eq 3 ]; then
    echo "ok  — --export emite exatamente 3 exports"
else
    echo "FALHOU: --export nao emitiu 3 exports:"; echo "$EXP"; rc=1
fi
exit $rc
