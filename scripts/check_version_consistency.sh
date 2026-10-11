#!/usr/bin/env bash
# check_version_consistency.sh — a versão tem UMA fonte de verdade e o
# compilador tem que Reportar exatamente ela (#814, ordem da mantenedora 11/10:
# "a tag tem que corresponder à versão informada pelo compilador em si").
#
# Verifica (falha fechada, rc=1 em qualquer divergência):
#   1. VERSION (raiz) == pom.xml <revision> == CADA componente de
#      kof-compiler/src/main/resources/dev/kof/version.properties
#      (kof.version, kof.compiler.version, kof.runtime.version, kof.stdlib.version).
#   2. Face de RUNTIME: se houver um kof-cli jar empacotado em target, `kof
#      version` tem que imprimir "kof <VERSION>" — o que o compilador INFORMA
#      tem que ser a versão da árvore. Jar ausente = pulo NOMEADO (a face
#      estática acima já certifica a fonte; a release re-verifica no empacote).
#
# Uso: scripts/check_version_consistency.sh   (rc=0 ok / rc=1 divergência)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FAIL=0

say() { echo "check_version_consistency: $*"; }
die() { say "RED: $*"; FAIL=1; }

[ -f "$ROOT/VERSION" ] || { die "VERSION ausente na raiz"; say "rc=1"; exit 1; }
VERSION="$(cat "$ROOT/VERSION")"
[ -n "$VERSION" ] || { die "VERSION vazio"; say "rc=1"; exit 1; }

POM_REV="$(sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' "$ROOT/pom.xml" | head -1)"
[ "$POM_REV" = "$VERSION" ] || die "pom.xml <revision>='$POM_REV' != VERSION='$VERSION'"

PROPS="$ROOT/kof-compiler/src/main/resources/dev/kof/version.properties"
[ -f "$PROPS" ] || { die "version.properties ausente"; say "rc=1"; exit 1; }
for KEY in kof.version kof.compiler.version kof.runtime.version kof.stdlib.version; do
    VAL="$(sed -n "s:^$KEY=::p" "$PROPS" | head -1 | tr -d '[:space:]')"
    [ "$VAL" = "$VERSION" ] || die "version.properties $KEY='$VAL' != VERSION='$VERSION'"
done

# face de runtime: o que o compilador INFORMA (KofVersion lê o
# version.properties EMPACOTADO — é exatamente o que vai no jar)
JAR="$(ls "$ROOT"/kof-cli/target/kof-cli-*.jar 2>/dev/null | head -1 || true)"
if [ -n "$JAR" ]; then
    JAVA_BIN="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
    REPORTED="$("${JAVA_BIN:-java}/bin/java" -jar "$JAR" version 2>/dev/null | head -1)"
    [ "$REPORTED" = "kof $VERSION" ] || die "o compilador empacotado informa '$REPORTED' != 'kof $VERSION' (recompile antes de empacotar)"
else
    say "PULO nomeado: sem jar empacotado em kof-cli/target — face de runtime fica para o empacote (a face estática acima certifica as fontes)"
fi

if [ "$FAIL" -ne 0 ]; then
    say "rc=1 — dessincronização de versão (#814); rode scripts/bump-version.sh <versão> para sincronizar TUDO"
    exit 1
fi
say "OK — VERSION/pom/version.properties$([ -n "$JAR" ] && echo "/compilador empacotado" || echo "") = $VERSION"
exit 0
