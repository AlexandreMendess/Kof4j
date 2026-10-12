#!/usr/bin/env bash
# check_golden_count.sh — a contagem da suíte de equivalência (`tests/golden/`) tem
# UMA fonte de verdade: a ÁRVORE. Cada doc vivo que cita "N cases / M checks" (EN)
# ou "N casos / M checagens" (PT) tem que bater com o número de diretórios de caso
# em `tests/golden/` (checks = casos × 4 alvos: jvm/native/js/script).
#
# Motivo (#769, lane docs/lab 11/10): a contagem derivou 4× em sequência
# (62→64→65→66 casos) e cada deriva foi uma correção manual de doc-truth. Este gate
# tranca a fonte mecânica. Snapshots datados (CHANGELOG, audits, history e o próprio
# `test-architecture-plan`, que registra o histórico POR fatia) ficam FORA — são
# registros históricos, não claims correntes.
#
# Uso: scripts/check_golden_count.sh   (rc=0 ok / rc=1 deriva)
set -u
cd "$(dirname "$0")/.."

GOLDEN=tests/golden
[ -d "$GOLDEN" ] || { echo "GOLDEN-COUNT: FAIL — $GOLDEN ausente"; exit 1; }
N=$(find "$GOLDEN" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')
CHECKS=$((N * 4))

# Docs vivos que carregam a contagem corrente (EN + PT).
DOCS=(
  docs/stdlib/stdlib.md
  docs/stdlib/stdlib.pt_BR.md
  docs/development/README.md
  docs/development/README.pt_BR.md
)

ok=1
for f in "${DOCS[@]}"; do
  [ -f "$f" ] || continue
  # EN: "N cases / M checks"; PT: "N casos / M checagens".
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    got_n="${line%% *}"
    rest="${line#* }"
    got_m="${rest##* }"
    if [ "$got_n" != "$N" ] || [ "$got_m" != "$CHECKS" ]; then
      echo "GOLDEN-COUNT: FAIL — $f cita '$got_n ... $got_m' mas a árvore é $N casos / $CHECKS checks"
      ok=0
    fi
  done < <(grep -oE '[0-9]+ (cases / [0-9]+ checks|casos / [0-9]+ checagens)' "$f" | sed 's/ cases \/ / /; s/ casos \/ / /; s/ checks$//; s/ checagens$//')
done

if [ "$ok" = 1 ]; then
  echo "GOLDEN-COUNT: OK — docs vivos citam $N casos / $CHECKS checks (= árvore × 4 alvos)"
fi
exit $((1 - ok))
