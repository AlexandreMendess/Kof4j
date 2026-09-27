#!/usr/bin/env bash
# check-reproducible-build.sh — o jar do kof-cli e BYTE-reprodutivel?
#
# Um gate de identidade de artefato so pode dizer "recompilar da o mesmo digest" se o build for
# reprodutivel (EXIT GATE §32.6 / D-1.0-EDGES Q7). Este script mede: clona o commit em DOIS
# caminhos limpos e DIFERENTES (filesystem nativo — o `mvn clean` falha em mount de Windows),
# roda `mvn clean package -DskipTests` em cada e compara o sha256 do jar.
#
# Nunca da veredito falso: build que falha = exit 2 (invalido), nao "diferente" nem "igual".
# Pesado (2 builds, ~6 min) — nao entra na suite rapida; rodar no RC / quando mexer no empacotamento.
#
# Uso: scripts/check-reproducible-build.sh [commit]      (default: HEAD do repositorio atual)
# Exit: 0 byte-identicos · 1 diferentes · 2 build/clone falhou (medicao invalida) · 3 mvn ausente.
set -u
COMMIT="${1:-HEAD}"
SRC="$(git rev-parse --show-toplevel)"
SHA="$(git -C "$SRC" rev-parse "$COMMIT")" || { echo "commit invalido: $COMMIT" >&2; exit 2; }
command -v mvn >/dev/null 2>&1 || { echo "mvn ausente" >&2; exit 3; }
BASE="${KOF_REPRO_DIR:-$HOME/.kof-repro}"; rm -rf "$BASE"; mkdir -p "$BASE" || exit 2
trap 'rm -rf "$BASE"' EXIT
hash_of() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }

echo "== check-reproducible-build @ ${SHA:0:12}"
build() { # rotulo, dir  -> imprime o sha256 do jar em $BASE/<rotulo>.sha
  git clone -q "$SRC" "$BASE/$2" && git -C "$BASE/$2" checkout -q "$SHA" || { echo "clone $2 falhou" >&2; exit 2; }
  ( cd "$BASE/$2" && mvn -q -pl kof-cli -am clean package -DskipTests > "$BASE/$1.log" 2>&1 ) \
    || { echo "BUILD $1 FALHOU — medicao invalida (ver $BASE/$1.log)" >&2; tail -3 "$BASE/$1.log" >&2; exit 2; }
  jar="$(ls "$BASE/$2"/kof-cli/target/kof-cli-*.jar 2>/dev/null | grep -v -e original -e sources -e javadoc | head -1)"
  [ -n "$jar" ] && [ -f "$jar" ] || { echo "jar nao encontrado apos o build $1" >&2; exit 2; }
  hash_of "$jar" > "$BASE/$1.sha"; echo "build $1 ($2): sha256 $(cut -c1-16 "$BASE/$1.sha")…  $(basename "$jar")"
}
build A pathA; build B pathB-with-another-name
if [ "$(cat "$BASE/A.sha")" = "$(cat "$BASE/B.sha")" ]; then
  echo "RESULT: BYTE-IDENTICAL across two clean builds at different paths — the jar is reproducible"; exit 0
fi
echo "RESULT: DIFFERENT digests — the build is NOT byte-reproducible (jar metadata/timestamps; try project.build.outputTimestamp)"; exit 1
