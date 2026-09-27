#!/usr/bin/env bash
# audit-stubs.sh — inventário REPRODUTÍVEL de incompletude NÃO documentada
# (frente de revisão, sessão 9094; v2 21/09 = método ESTRUTURAL, ordem da mantenedora).
# Não altera nada: só lê e imprime.
#
# Uso: scripts/audit-stubs.sh [raiz-do-repo] [--section N]
#      sem --section roda todos os cortes; --section N roda só o corte N (fatias).
#
# CORTES 1-6 = marcadores (grep). CORTES 7-10 = estrutura (v2): não dependem de
# `TODO`/marcador algum — pegam a incompletude que PRETENDE funcionar (o riscosilencioso
# R6): facade de retorno trivial, corpo de método vazio, `catch` que só absorve, e
# `throw UnsupportedOperationException` SEM código de gap.
#
# CUIDADO DE RUÍDO (medido 21/09): os comentários do Kof são em português, onde
# "todo" = "todos" (every) e "stub honesto" = recusa deliberada R6 (documentada no
# próprio código). Portanto um match aqui é CANDIDATO, nunca achado: a triagem é
# manual e compara com docs/bugs-and-gaps/* antes de catalogar.
# Ver docs/bugs-and-gaps/uncatalogued-stubs-audit.md (frente de revisão).
set -uo pipefail

ROOT=""; SECTION=""
for a in "$@"; do
    case "$a" in
        --section) SECTION="__next__" ;;
        *) if [ "$SECTION" = "__next__" ]; then SECTION="$a"; else ROOT="$a"; fi ;;
    esac
done
ROOT="${ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT"

mapfile -t ROOTS < <(ls -d */src/main 2>/dev/null || true)
if [ "${#ROOTS[@]}" -eq 0 ]; then
    echo "audit-stubs: nenhum */src/main em $ROOT" >&2
    exit 1
fi

hdr() { [ -z "$SECTION" ] || [ "$SECTION" = "$1" ] || return 0; printf '\n## %s\n' "$2"; }
run() { [ -z "$SECTION" ] || [ "$SECTION" = "$1" ]; }
n()   { grep -rnE "$1" "${ROOTS[@]}" --include=*.java 2>/dev/null || true; }

printf '# audit-stubs — %s (v2)\n' "$(date -Iseconds)"
printf 'raiz: %s\nroots: %s\n' "$ROOT" "${ROOTS[*]}"

hdr 1 "1. TODO/FIXME/XXX/HACK (case-sensitive, .java) — candidatos, triar à mão"
run 1 && n '\b(TODO|FIXME|XXX|HACK)\b'

hdr 2 "2. UnsupportedOperationException (.java) — distinguir R6 honesto de stub real"
run 2 && n 'UnsupportedOperationException'

hdr 3 "3. catch vazio (.java) — possível swallow silencioso (R6)"
run 3 && n 'catch \([^)]*\)[[:space:]]*\{[[:space:]]*\}'

hdr 4 "4. 'not implemented'/'unimplemented' (.java)"
run 4 && n 'not[ _]?implemented|unimplemented'

hdr 5 "5. testes desabilitados (@Disabled/@Ignore) — esconder trabalho pendente"
run 5 && { grep -rnE '@(Disabled|Ignore)\b' */src/test 2>/dev/null || true; }

hdr 6 "6. assumeTrue (SKIP honesto de ambiente — só sinaliza volume)"
run 6 && { grep -rc "assumeTrue\|Assumptions\." "${ROOTS[@]}"/*/src/test 2>/dev/null || true; }

# ── cortes estruturais (v2), via python ──────────────────────────────────────
python3 - "$ROOT" "${SECTION:-}" << 'PYEOF'
import glob, os, re, sys
root = sys.argv[1]
section = sys.argv[2]
files = sorted(glob.glob(os.path.join(root, "*/src/main/**/*.java"), recursive=True))
CONTROL = {"if", "for", "while", "switch", "catch", "synchronized", "try"}

def blank_comments(s):
    ch = list(s); i = 0; n = len(s)
    while i < n:
        if s[i] == '/' and i + 1 < n and s[i + 1] == '/':
            j = s.find('\n', i); j = n if j < 0 else j
            for k in range(i, j):
                if ch[k] != '\n': ch[k] = ' '
            i = j
        elif s[i] == '/' and i + 1 < n and s[i + 1] == '*':
            j = s.find('*/', i + 2); j = n - 2 if j < 0 else j
            for k in range(i, j + 2):
                if ch[k] != '\n': ch[k] = ' '
            i = j + 2
        else:
            i += 1
    return ''.join(ch)

TRIVIAL = re.compile(r'^return\s*(null|false|0L?|0\.0[fFdD]?|"")\s*;$')
facades, empties = [], []
for f in files:
    src = open(f, encoding="utf-8", errors="replace").read()
    c = blank_comments(src)
    i = 0
    while True:
        p = c.find(')', i)
        if p < 0: break
        q = p + 1
        while q < len(c) and c[q] in ' \t\r\n': q += 1
        if q >= len(c) or c[q] != '{':
            i = p + 1; continue
        depth = 1; j = q + 1
        while j < len(c) and depth > 0:
            if c[j] == '{': depth += 1
            elif c[j] == '}': depth -= 1
            j += 1
        body = ' '.join(c[q + 1:j - 1].split())
        k = p; d2 = 0
        while k >= 0:
            if c[k] == ')': d2 += 1
            elif c[k] == '(':
                d2 -= 1
                if d2 == 0: break
            k -= 1
        m = k - 1
        while m >= 0 and c[m] in ' \t\r\n': m -= 1
        e = m
        while m >= 0 and (c[m].isalnum() or c[m] in '_$'): m -= 1
        name = c[m + 1:e + 1]
        sigstart = k
        while sigstart > 0 and c[sigstart - 1] not in ';{}': sigstart -= 1
        sig = ' '.join(c[sigstart:q].split())
        line = c.count('\n', 0, q) + 1
        if name not in CONTROL and not re.search(r'\b(abstract|native)\b', sig):
            loc = f"{f}:{line}"
            if body == '':
                empties.append((loc, sig))
            elif TRIVIAL.match(body):
                facades.append((loc, sig, body))
        i = j

swallow = []
for f in files:
    src = open(f, encoding="utf-8", errors="replace").read()
    for m in re.finditer(r'catch\s*\([^)]*\)\s*\{', src):
        open_brace = m.end() - 1
        depth = 1; j = open_brace + 1
        while j < len(src) and depth > 0:
            if src[j] == '{': depth += 1
            elif src[j] == '}': depth -= 1
            j += 1
        body = src[open_brace + 1:j - 1].strip()
        if body and re.fullmatch(r'(?:\s|//[^\n]*|/\*.*?\*/)*', body, re.S):
            line = src.count('\n', 0, m.start()) + 1
            swallow.append(f"{f}:{line}")

hardfail = []
for f in files:
    src = open(f, encoding="utf-8", errors="replace").read()
    for m in re.finditer(r'UnsupportedOperationException\s*\(\s*"([^"]*)"', src):
        if not re.search(r'R6|DB\d+|§|gapCode|GAP|M\d\d\d\d', m.group(1)):
            line = src.count('\n', 0, m.start()) + 1
            hardfail.append(f"{f}:{line}  \"{m.group(1)[:60]}\"")

# ── passada 5: fachadas de resposta vazia + testes false-green (v2.1) ────────
testfiles = sorted(glob.glob(os.path.join(root, "*/src/test/**/*.java"), recursive=True))
dapfacade = []
for f in files:
    src = open(f, encoding="utf-8", errors="replace").read()
    for m in re.finditer(r'default\s*->\s*respond\([^;]*Map\.of\(\)', src):
        line = src.count('\n', 0, m.start()) + 1
        dapfacade.append(f"{f}:{line}  default -> respond(..., Map.of())")
    b = os.path.basename(f)
    if "Lsp" in b or "Dap" in b or "Debug" in b:
        for m in re.finditer(r'default\s*->\s*\{\s*\}', src):
            line = src.count('\n', 0, m.start()) + 1
            dapfacade.append(f"{f}:{line}  default -> {{ }} (sem resposta)")

weakgreen = []
for f in testfiles:
    src = open(f, encoding="utf-8", errors="replace").read()
    for m in re.finditer(r'assert(?:True|False)\(\s*(?:true|false)\s*\)', src):
        line = src.count('\n', 0, m.start()) + 1
        weakgreen.append(f"{f}:{line}  {m.group(0)[:40]}")
    for m in re.finditer(r'assumeTrue\([^;\n]*\bsuccess\s*\(\)', src):
        line = src.count('\n', 0, m.start()) + 1
        weakgreen.append(f"{f}:{line}  assumeTrue(...success()) — SKIP que pode mascarar regressao")

debugtests = [f for f in testfiles if re.search(r'DebugTest', os.path.basename(f))]

def show(sec, title, rows, fmt):
    if section and section != sec: return
    print(f"\n## {title}")
    for r in rows:
        print("   " + fmt(r))

show("7", "7. Facade: método concreto cujo corpo é só um return trivial (v2, estrutural)",
     facades, lambda r: f"{r[0]}  {r[1]}  ->  {r[2]}")
show("9", "9. catch com corpo SÓ comentário (absorve em silêncio) (v2)",
     swallow, lambda r: r)
show("10", "10. throw UnsupportedOperationException SEM código de gap (v2) — hard-fail não documentado",
     hardfail, lambda r: r)
show("11", "11. Fachada de resposta vazia DAP/LSP: default -> { } / respond(..., Map.of()) (v2.1)",
     dapfacade, lambda r: r)
show("12", "12. Testes false-green: assertTrue(true)/assertFalse(false) ou assumeTrue(...success()) (v2.1)",
     weakgreen, lambda r: r)
show("13", "13. Classes *DebugTest* (harness só-print, sem assert) (v2.1)",
     debugtests, lambda r: r)

if not section or section == "resumo":
    print("\n## resumo")
    print(f"facades triviais:               {len(facades)}")
    print(f"corpos vazios (design, info):   {len(empties)}")
    print(f"catch so-comentario:            {len(swallow)}")
    print(f"hard-fail sem codigo:           {len(hardfail)}")
    print(f"fachada resposta vazia:         {len(dapfacade)}")
    print(f"testes false-green:             {len(weakgreen)}")
    print(f"debug tests so-print:           {len(debugtests)}")
PYEOF

if [ -z "$SECTION" ]; then
    printf '\n## resumo (marcadores)\n'
    printf 'candidatos TODO/FIXME:          %s\n' "$(n '\b(TODO|FIXME|XXX|HACK)\b' | wc -l)"
    printf 'UnsupportedOperationException:  %s\n' "$(n 'UnsupportedOperationException' | wc -l)"
    printf 'catch vazio:                    %s\n' "$(n 'catch \([^)]*\)[[:space:]]*\{[[:space:]]*\}' | wc -l)"
fi
printf '\nLembrete: catálogo confirmado vai para docs/bugs-and-gaps/known-bugs.md (§NNN, EN+PT);\neste script só levanta candidatos.\n'
