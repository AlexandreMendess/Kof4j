#!/usr/bin/env bash
# check_changelog_ledger.sh — paridade CHANGELOG ↔ ledger known-bugs.
#
# Toda entrada de CHANGELOG que AFIRMA um flip ✅/FIXED de `§NNN` precisa
# achar o §NNN FECHADO no último status do ledger da MESMA língua. O
# classificador de status é o do check_known_bugs_status.sh (última linha de
# status vence) — este gate consome a lista "open/partial" que ele imprime.
#
# Por que existe: 21/09 — um rebase de base velha (tick `d9384a5b`) reverteu
# silenciosamente o §388 de ✅ FIXED para 🟡 PARTIAL nas duas línguas enquanto
# o CHANGELOG continuava alegando o flip. Zero conflito, zero aviso: só o
# ledger mentindo. Este gate transforma esse cenário em FAIL vermelho na hora
# em que QUALQUER lado for tocado de novo (e no manifesto de toda lane docs).
#
# Uso:
#   scripts/check_changelog_ledger.sh              # rc!=0 se houver drift
#   scripts/check_changelog_ledger.sh --selftest   # fixture plantada deve pegar
set -u
cd "$(dirname "$0")/.."

LEDGER_CMD="${LEDGER_CMD:-bash scripts/check_known_bugs_status.sh}"
WAIVERS="${WAIVERS:-scripts/changelog-ledger-waivers.txt}"
open_ids() { # $1 = "EN|PT"
    $LEDGER_CMD 2>/dev/null | grep "^$1 open" | sed -E 's/^[^)]*\): //' \
        | tr ' ' '\n' | grep -E '^[0-9]+$' | sort -u
}
waived() { # $1=id $2=lang — citação histórica listada no waiver file
    [ -f "$WAIVERS" ] && grep -vE '^\s*#' "$WAIVERS" | awk '{print $1" "$2}' | grep -qx "$1 $2"
}

# Afirmações de flip: token FIXED/✅/FECHAD/CORRIGIDO a ≤80 chars do §NNN,
# na MESMA linha. Negativas ("ainda não FIXED", "ficaria ✅") só pegam se a
# linha inteira alegar o fato — histórico citado no CHANGELOG é fato, e se o
# ledger discorda disso É o drift que queremos ver.
CLAIM_RE='§([0-9]+)[^§]{0,80}(✅|fixed|fechad|corrigid|closed|encerrad)'

check_pair() { # $1=changelog $2=id lingua $3=live-ids $4=waivers-file
    python3 - "$1" "$2" "$3" "$4" << 'PYEOF'
import re, sys
cl, lang, opens, wfile = sys.argv[1], sys.argv[2], set(sys.argv[3].split()), sys.argv[4]
drift = 0
try:
    waived = {tuple(l.split()[:2]) for l in open(wfile, encoding="utf-8")
              if l.strip() and not l.strip().startswith("#")}
except OSError:
    waived = set()
sec = re.compile("§([0-9]+)")
tok = re.compile(r"(?i)(✅|fixed|fechad|corrigid|closed|encerrad)")
for line in open(cl, encoding="utf-8"):
    if "retract" in line.lower() or "retrat" in line.lower():
        continue
    secs = [(m.start(), int(m.group(1))) for m in sec.finditer(line)]
    for m in tok.finditer(line):
        prev = [s for s in secs if s[0] < m.start()]
        if not prev:
            continue
        sp, sid = max(prev, key=lambda x: x[0])
        if m.start() - sp > 80:
            continue
        if str(sid) in opens and (str(sid), lang) not in waived:
            print(f"DRIFT [{lang}]: {cl} afirma \u00a7{sid} fechado mas o ledger est\u00e1 vivo:")
            print("    " + line.strip()[:110])
            drift = 1
# higiene de waiver: id JA FECHADO no ledger nao pode seguir isentado (esconderia
# drift futuro). Se o changelog ainda cita o id, a linha da waiver e obsoleta.
claimed = {s for l2 in open(cl, encoding="utf-8") for s in re.findall(r"§([0-9]+)", l2)}
for wid, wlang in sorted(waived):
    if wlang == lang and wid not in opens and wid in claimed:
        print(f"STALE [{lang}]: waiver §{wid} nao se justifica mais (fechado no ledger"
              ") — remover a linha de changelog-ledger-waivers.txt")
        drift = 1
sys.exit(drift)
PYEOF
}

if [ "${1:-}" = "--selftest" ]; then
    T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
    mkdir -p "$T/scripts" "$T/docs/bugs-and-gaps"
    sed 's/^set -u/set -u/' "$0" > "$T/scripts/check_changelog_ledger.sh"
    cat > "$T/docs/bugs-and-gaps/known-bugs.md" << 'EOF'
# ledger fixture
## §999 — bug plantado — 🟡 OPEN (21/09)
## §998 — bug consertado — ✅ FIXED 21/09
EOF
    cp "$T/docs/bugs-and-gaps/known-bugs.md" "$T/docs/bugs-and-gaps/known-bugs.pt_BR.md"
    cat > "$T/scripts/check_known_bugs_status.sh" << 'EOF'
#!/usr/bin/env bash
echo "EN open/partial (1): 999"
echo "PT open/partial (1): 999"
echo "OK: statuses consistent EN×PT, no unknowns"
EOF
    cat > "$T/CHANGELOG.md" << 'EOF'
  - **§999 ✅ FIXED 21/09** — flip que o ledger NEGIGA (deve dar DRIFT)
  - **§998 ✅ FIXED 21/09** — caso honesto (deve passar)
EOF
    cp "$T/CHANGELOG.md" "$T/CHANGELOG.pt_BR.md"
    OUT="$(bash "$T/scripts/check_changelog_ledger.sh" 2>&1)"; RC=$?
    if [ $RC -ne 0 ] && printf '%s' "$OUT" | grep -q "DRIFT.*§999" && ! printf '%s' "$OUT" | grep -q "DRIFT.*§998"; then
        echo "SELFTEST OK: §999 pego, §998 limpo, rc=$RC"
        exit 0
    fi
    echo "SELFTEST FALHOU (rc=$RC):"; printf '%s\n' "$OUT"; exit 1
fi

check_reverse() { # $1=ledger $2=id lingua $3=live-ids $4=changelog $5=waivers
    python3 - "$1" "$2" "$3" "$4" "$5" << 'PYEOF2'
import re, sys
led, lang, opens, cl, wfile = sys.argv[1], sys.argv[2], set(sys.argv[3].split()), sys.argv[4], sys.argv[5]
FLOOR = 400  # pratica uniforme a partir daqui (medido 21/09: 25 ids anteriores sem entrada,
             # ZERO apos 400). Piso = regra de epoca, nao reescreve historia nem da perdao
             # individual; antes de 400 o CHANGELOG e historico e o ledger venceu.
try:
    rwaived = {t.split()[0][1:] for t in (l.split() for l in open(wfile, encoding="utf-8"))
               if t and t[0].startswith("R") and len(t) > 1 and t[1] == lang}
except OSError:
    rwaived = set()
ids = {m.group(1) for m in re.finditer(r"(?m)^#{2,4} §([0-9]+)", open(led, encoding="utf-8").read())}
text = open(cl, encoding="utf-8").read()
miss = [i for i in sorted(int(x) for x in ids - opens)
        if i >= FLOOR and f"§{i}" not in text and str(i) not in rwaived]
for i in miss:
    print(f"MISSING [{lang}]: ledger fecha §{i} (piso {FLOOR}) e o CHANGELOG nao tem entrada")
sys.exit(1 if miss else 0)
PYEOF2
}
rc=0
for pair in "CHANGELOG.md:EN:docs/bugs-and-gaps/known-bugs.md" \
            "CHANGELOG.pt_BR.md:PT:docs/bugs-and-gaps/known-bugs.pt_BR.md"; do
    IFS=: read -r cl lang led <<< "$pair"
    [ -f "$led" ] || continue
    check_pair "$cl" "$lang" "$(open_ids "$lang")" "$WAIVERS" || rc=1
    check_reverse "$led" "$lang" "$(open_ids "$lang")" "$cl" "$WAIVERS" || rc=1
done
if [ $rc -eq 0 ]; then
    n_en="$(grep -ciE "§[0-9]+[^§]{0,80}(✅|fixed|fechad|corrigid|closed|encerrad)" CHANGELOG.md 2>/dev/null || true)"
    echo "OK: CHANGELOG×ledger consistent (${n_en:-0} afirmações EN conferidas)"
fi
exit $rc
