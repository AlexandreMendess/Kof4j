#!/usr/bin/env bash
#
# check_doc_refs.sh — integridade das REFERENCIAS nos docs:
#   1) todo `docs/**/*.md` citado (em qualquer doc do repositorio) existe — pega
#      movimentos/renames que deixaram a referencia para tras (native-multiarch.md,
#      language/types.md na lane; workflow-plan, ecosystem-coverage, PHASE_F runtime
#      e CONFORMANCE_MATRIX no corpus, 21/09);
#   2) todo SHA hex entre crases (8..40) citado nos docs da lane (docs/development)
#      existe no git — pega prova orfa (o repair de git de 21/09 reescreveu historico
#      e deixou 11 SHAs citados sem objeto correspondente).
# Referencias legitimas que nao resolvem (nota historica de rename, outro repo,
# destino de movimento futuro, doc prospectivo) ficam explicitas em
# scripts/doc-refs-waivers.txt, datadas — a mesma classe dos changelog-ledger-waivers.
#
# AUSENCIA e FALHA: um path/sha novo que nao resolve e rc!=0 ate ser corrigido ou
# justificado no waiver (regra anti-neutering da lane).
#
# Uso: scripts/check_doc_refs.sh            # rc!=0 se houver referencia orfa
#      scripts/check_doc_refs.sh --selftest # casos bons e ruins plantados
# Env (teste): DOCREF_PATH_DIR, DOCREF_SHA_DIR, DOCREF_WAIVERS
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
PDIR="${DOCREF_PATH_DIR:-docs}"
SDIR="${DOCREF_SHA_DIR:-docs/development}"
WAV="${DOCREF_WAIVERS:-scripts/doc-refs-waivers.txt}"

if [ "${1:-}" = "--selftest" ]; then
    T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
    mkdir -p "$T/d"
    HEAD_SHA="$(git rev-parse HEAD)"
    printf 'ref %s\nsha `%s`\n' 'docs/development/README.md' "$HEAD_SHA" > "$T/d/a.md"
    if ! DOCREF_PATH_DIR="$T/d" DOCREF_SHA_DIR="$T/d" DOCREF_WAIVERS="$T/w" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: referencias validas (path existente + SHA do HEAD) deviam passar"; exit 1; fi
    printf 'ref %s\n' 'docs/development/nao-existe-xyz.md' > "$T/d/a.md"
    if DOCREF_PATH_DIR="$T/d" DOCREF_SHA_DIR="$T/d" DOCREF_WAIVERS="$T/w" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: path inexistente passou"; exit 1; fi
    printf 'sha `deadbeefdeadbeef`\n' > "$T/d/a.md"
    if DOCREF_PATH_DIR="$T/d" DOCREF_SHA_DIR="$T/d" DOCREF_WAIVERS="$T/w" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: SHA inexistente passou"; exit 1; fi
    printf 'path\tdocs/development/nao-existe-xyz.md\tfixture\nsha\tdeadbeefdeadbeef\tfixture\n' > "$T/w"
    printf 'ref %s\nsha `deadbeefdeadbeef`\n' 'docs/development/nao-existe-xyz.md' > "$T/d/a.md"
    if ! DOCREF_PATH_DIR="$T/d" DOCREF_SHA_DIR="$T/d" DOCREF_WAIVERS="$T/w" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: path+sha no waiver deviam passar"; exit 1; fi
    echo "SELFTEST OK: path valido/inexistente + SHA valido/inexistente + waiver"
    exit 0
fi

[ -d "$PDIR" ] || { echo "FALHA: diretorio de docs (paths) inexistente: $PDIR"; exit 1; }
[ -d "$SDIR" ] || { echo "FALHA: diretorio de docs (SHAs) inexistente: $SDIR"; exit 1; }

python3 - "$PDIR" "$SDIR" "$WAV" << 'PYEOF'
import re, sys, os, glob, subprocess
pdir, sdir, wav = sys.argv[1], sys.argv[2], sys.argv[3]
wpath, wsha = set(), set()
try:
    for line in open(wav, encoding="utf-8"):
        line = line.split("#", 1)[0].rstrip("\n")
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        kind, tok = parts[0].strip(), parts[1].strip()
        if kind == "path":
            wpath.add(tok)
        elif kind == "sha":
            wsha.add(tok)
except OSError:
    pass  # sem arquivo de waiver != passe livre: so nada esta dispensado

bad = 0
cp = cs = 0
for f in sorted(glob.glob(os.path.join(pdir, "**", "*.md"), recursive=True)):
    text = open(f, encoding="utf-8").read()
    text = re.sub(r"https?://\S+", "", text)  # URLs nao sao paths do repo
    base = os.path.basename(f)
    for p in sorted(set(re.findall(r"docs/[A-Za-z0-9/_.-]+\.md", text))):
        cp += 1
        if not os.path.exists(p) and p not in wpath:
            print(f"REF QUEBRADA ({base}): {p} nao existe no repo"); bad = 1
for f in sorted(glob.glob(os.path.join(sdir, "*.md"))):
    base = os.path.basename(f)
    text = re.sub(r"https?://\S+", "", open(f, encoding="utf-8").read())
    for s in sorted(set(re.findall(r"`([0-9a-f]{8,40})`", text))):
        cs += 1
        if s in wsha:
            continue
        if subprocess.run(["git", "cat-file", "-e", s], capture_output=True).returncode != 0:
            print(f"SHA FANTASMA ({base}): {s} nao existe no repo"); bad = 1
if not bad:
    print(f"OK: {cp} refs de path (docs/) + {cs} SHAs (development) integros "
          f"({len(wpath)} paths + {len(wsha)} shas no waiver)")
sys.exit(1 if bad else 0)
PYEOF
