#!/usr/bin/env bash
# check_queue_enum.sh — a ENUMERACAO da fila de bugs abertos (§2 do
# docs/development/README.md+PT) tem que nomear EXATAMENTE o conjunto da
# autoridade (`scripts/check_known_bugs_status.sh`), nao so a contagem.
#
# Motivo (#769, lane docs/lab 11/10): `check_live_records` valida o NUMERO da
# fila ("**N items in the open queue**"), mas a linha `Authority =` tambem
# enumera os itens em prosa — e essa lista derivou 4x em sequencia (itens
# fechados listados como abertos, itens novos omitidos) e foi revertida por
# merges de outras lanes 3x. Nenhum gate checava a LISTA. Este gate tranca.
#
# Regiao: o trecho entre o marcador de contagem e o ancora historico `§641 (`
# (o inicio da narrativa "fixed this lane"). Se o ancora mudar, o gate falha
# NOMEADO (atualize junto com a prosa, como o regex de contagem do
# check_live_records).
#
# Uso: scripts/check_queue_enum.sh   (rc=0 ok / rc=1 deriva)
set -u
cd "$(dirname "$0")/.."

AUTH="$(bash scripts/check_known_bugs_status.sh | sed -nE 's/^EN open[^(]*\([0-9]+\): (.*)/\1/p' | head -1)"
[ -n "$AUTH" ] || { echo "QUEUE-ENUM: FAIL — nao extrai a autoridade (check_known_bugs_status mudou?)"; exit 1; }
AUTH_SET="$(echo "$AUTH" | tr ' ' '\n' | sed 's/^§//' | grep -E '[0-9]' | sort -u | tr '\n' ' ')"

ok=1
for f in docs/development/README.md docs/development/README.pt_BR.md; do
  [ -f "$f" ] || continue
  line="$(grep -m1 -E '^(Authority|Autoridade) = ' "$f")"
  [ -n "$line" ] || { echo "QUEUE-ENUM: FAIL — sem linha Authority/Autoridade em $f"; ok=0; continue; }
  # recorta a regiao de enumeracao: apos o marcador de contagem ("11/10: "),
  # antes do ancora historico "; §641 (" (inicio da narrativa "fixed this lane")
  case "$line" in
    *"11/10: "*"; §641 ("*) region="${line#*11/10: }"; region="${region%%; §641 (*}" ;;
    *) echo "QUEUE-ENUM: FAIL — $f sem o marcador '11/10: ' ou o ancora '; §641 (' (atualize o gate junto com a prosa)"; ok=0; continue ;;
  esac
  docs_set="$(echo "$region" | grep -oE '§[0-9]+' | sed 's/^§//' | grep -E '[0-9]' | sort -u | tr '\n' ' ')"
  if [ "$docs_set" != "$AUTH_SET" ]; then
    echo "QUEUE-ENUM: FAIL — $f enumera [$docs_set] mas a autoridade e [$AUTH_SET]"
    ok=0
  fi
done

if [ "$ok" = 1 ]; then
  echo "QUEUE-ENUM: OK — README §2 enumera exatamente a autoridade ($AUTH_SET)"
fi
exit $((1 - ok))
