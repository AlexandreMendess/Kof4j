#!/usr/bin/env bash
#
# verify-release-identity-test.sh — prova offline (sem rede, sem gh) de scripts/verify-release-identity.sh,
# o verificador do gate RATIFICADO "digest do pacote testado == digest do pacote publicado" (§32.6/§35).
# Usa fixtures em diretorio temporario (--dir) e os seams VRI_ASSETS_FILE/VRI_SUMS_FILE (modo online).
#
# Uso: scripts/tests/verify-release-identity-test.sh   (exit 0 = tudo verde)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
V="scripts/verify-release-identity.sh"
FAILED=0
expect() { # descricao, rc esperado, rc real
  if [ "$2" != "$3" ]; then echo "!!! $1: esperado rc=$2, veio rc=$3"; FAILED=1; else echo "ok: $1 (rc=$3)"; fi
}
has() { # descricao, texto, saida
  printf '%s\n' "$3" | grep -q -- "$2" || { echo "!!! $1: nao achou '$2' na saida"; FAILED=1; return 1; }
}
sha() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }

T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
mk() { # dir: arquivo + jar avulso + SHA256SUMS cobrindo so o arquivo
  rm -rf "$1"; mkdir -p "$1"
  printf 'archive-bytes-v1' > "$1/kof-1.0-linux.tar.gz"
  printf 'jar-bytes' > "$1/kof-cli-1.0.jar"
  printf '%s  kof-1.0-linux.tar.gz\n' "$(sha "$1/kof-1.0-linux.tar.gz")" > "$1/SHA256SUMS"
}

# 1) bom: arquivo coberto e igual; jar avulso sem checksum = WARN (nao FAIL)
mk "$T/a"; out="$(bash "$V" --dir "$T/a" 2>&1)"; rc=$?
expect "dir bom (jar descoberto e so WARN)" 0 "$rc"
has "1" "PASS.*kof-1.0-linux.tar.gz" "$out"; has "1" "WARN.*kof-cli-1.0.jar" "$out"

# 2) arquivo adulterado depois do checksum -> FAIL
mk "$T/b"; printf 'X' >> "$T/b/kof-1.0-linux.tar.gz"
out="$(bash "$V" --dir "$T/b" 2>&1)"; rc=$?
expect "arquivo adulterado -> rc 1" 1 "$rc"; has "2" "FAIL.*mismatch" "$out"

# 3) o ARQUIVO (tar.gz/zip) sem linha no SHA256SUMS -> FAIL
mk "$T/c"; : > "$T/c/SHA256SUMS"; printf '%s  other.txt\n' "$(sha "$T/c/kof-cli-1.0.jar")" > "$T/c/SHA256SUMS"; cp "$T/c/kof-cli-1.0.jar" "$T/c/other.txt"
out="$(bash "$V" --dir "$T/c" 2>&1)"; rc=$?
expect "arquivo sem checksum -> rc 1" 1 "$rc"; has "3" "FAIL.*archive not covered" "$out"

# 4) SHA256SUMS lista um arquivo que nao existe -> FAIL
mk "$T/d"; printf '%s  ghost.tar.gz\n' "$(printf 'z' | sha256sum 2>/dev/null | cut -d' ' -f1)" >> "$T/d/SHA256SUMS"
out="$(bash "$V" --dir "$T/d" 2>&1)"; rc=$?
expect "linha do SHA256SUMS sem asset -> rc 1" 1 "$rc"; has "4" "FAIL.*missing" "$out"

# 5) sem SHA256SUMS -> FAIL
mk "$T/e"; rm "$T/e/SHA256SUMS"
out="$(bash "$V" --dir "$T/e" 2>&1)"; rc=$?
expect "sem SHA256SUMS -> rc 1" 1 "$rc"; has "5" "FAIL.*SHA256SUMS" "$out"

# 6) marcador binario do coreutils (`hex *arquivo`, como no windows) e parseado
mk "$T/f"; printf '%s *kof-1.0-linux.tar.gz\n' "$(sha "$T/f/kof-1.0-linux.tar.gz")" > "$T/f/SHA256SUMS"
out="$(bash "$V" --dir "$T/f" 2>&1)"; rc=$?
expect "marcador '*' do SHA256SUMS" 0 "$rc"

# 7) --tested: igual -> PASS; diferente -> FAIL; ausente -> NOT_RUN (nunca PASS); --require-tested -> rc 1
mk "$T/g"; H="$(sha "$T/g/kof-1.0-linux.tar.gz")"
out="$(bash "$V" --dir "$T/g" --tested "$H" 2>&1)"; rc=$?
expect "--tested igual ao publicado" 0 "$rc"; has "7a" "PASS.*tested == published" "$out"
out="$(bash "$V" --dir "$T/g" --tested "$(printf '0%.0s' $(seq 64))" 2>&1)"; rc=$?
expect "--tested DIFERENTE do publicado -> rc 1" 1 "$rc"; has "7b" "FAIL.*tested != published" "$out"
out="$(bash "$V" --dir "$T/g" 2>&1)"; rc=$?
expect "sem --tested: NOT_RUN, nao e falso verde" 0 "$rc"; has "7c" "NOT_RUN.*tested" "$out"
out="$(bash "$V" --dir "$T/g" --require-tested 2>&1)"; rc=$?
expect "--require-tested sem digest -> rc 1" 1 "$rc"

# 8) modo online por fixture: digest do servidor diverge do SHA256SUMS -> FAIL; sem digest no servidor -> WARN
printf 'kof.tar.gz\t%s\nkof-cli.jar\t-\n' "$(printf '1%.0s' $(seq 64))" > "$T/assets.tsv"
printf '%s  kof.tar.gz\n' "$(printf '2%.0s' $(seq 64))" > "$T/sums.txt"
out="$(VRI_ASSETS_FILE="$T/assets.tsv" VRI_SUMS_FILE="$T/sums.txt" bash "$V" --release fixture-tag 2>&1)"; rc=$?
expect "online: digest do servidor != SHA256SUMS -> rc 1" 1 "$rc"; has "8a" "FAIL.*mismatch" "$out"; has "8b" "WARN.*kof-cli.jar" "$out"
printf '%s  kof.tar.gz\n' "$(printf '1%.0s' $(seq 64))" > "$T/sums.txt"
out="$(VRI_ASSETS_FILE="$T/assets.tsv" VRI_SUMS_FILE="$T/sums.txt" bash "$V" --release fixture-tag 2>&1)"; rc=$?
expect "online: digest do servidor == SHA256SUMS" 0 "$rc"

[ "$FAILED" -eq 0 ] && echo "== verify-release-identity: VERDE" || echo "== verify-release-identity: VERMELHA"
exit "$FAILED"
