#!/usr/bin/env bash
# check_ledger_anchors.sh — paridade de âncoras EN<->PT do ledger known-bugs.
#
# Cada seção do ledger carrega um link de troca de língua ("**PT:** [§N ...]
# (known-bugs.pt_BR.md#slug)" e o simétrico PT->EN). O href precisa ser o
# slug GitHub EXATO do heading de destino (minúsculas, diacríticos dobrados,
# pontuação removida, espaço->hífen, `_` preservado). 10/13 links medidos
# 21/09 eram versões ABREVIADAS à mão — o leitor cai em 404 e o docs-lang
# (que pareia arquivos, não hrefs) não vê nada. Este gate confere por
# igualdade de string, dos dois lados, e --selftest planta slug truncado.
# O regex casa `#NNN-` (HÍFEN ÚNICO OU DUPLO) de propósito: um href malformado
# como `#435-gate` (faltando o 2º hífen do slug real `#435--gate`) escapava de
# um regex `[0-9]+--` e passava verde para sempre — o mesmo ponto cego das
# âncoras abreviadas, agora fechado (21/09, §435: 14 links estavam assim).
# 22/09 (§443): o regex ainda exigia `[0-9]+` na abertura, então um href com `-`
# inicial (`#-443--…`, slug hasheado com o `## ` junto) nem era INSPECIONADO.
# Agora todo href `known-bugs(.pt_BR).md#…` é lido e um slug que não abre com
# `NNN-` é malformado (RED) — foi assim que 4 links quebrados de §433/§434
# apareceram (passavam verdes há um dia).
#
# Uso: scripts/check_ledger_anchors.sh            # rc!=0 com âncora quebrada
#      scripts/check_ledger_anchors.sh --selftest
set -u
cd "$(dirname "$0")/.."

python3 - "${1:-}" << 'PYEOF'
import re, sys, os

def gh_slug(h):
    s = h.lower()
    for a, b in [('à','a'),('á','a'),('â','a'),('ã','a'),('ä','a'),('é','e'),('è','e'),
                 ('ê','e'),('ë','e'),('í','i'),('ì','i'),('î','i'),('ï','i'),('ó','o'),
                 ('ò','o'),('ô','o'),('õ','o'),('ö','o'),('ú','u'),('ù','u'),('û','u'),
                 ('ü','u'),('ç','c'),('ñ','n')]:
        s = s.replace(a, b)
    s = re.sub(r"[^a-z0-9 _-]", "", s)          # pontuação fora; _ fica
    return s.replace(" ", "-")

def heads(t):
    return {int(m.group(1)): m.group(2)
            for m in re.finditer(r"^## §(\d+) (.*)$", t, re.M)}

def check(en_txt, pt_txt, label):
    bad = []
    for src, dst, name in [(en_txt, pt_txt, "EN->PT"), (pt_txt, en_txt, "PT->EN")]:
        hd = heads(dst)
        for m in re.finditer(r"\]\(known-bugs(?:\.pt_BR)?\.md#([^\)]+)\)", src):
            slug = m.group(1)
            mm = re.match(r"(\d+)-", slug)
            if not mm:
                # href que NÃO abre com `NNN-` (ex.: `-443--…`, um slug hasheado
                # com o `## ` junto) escapava do regex antigo `[0-9]+-` e passava
                # verde para sempre — o mesmo ponto cego das âncoras abreviadas
                # (§435), reaberto por um `-` inicial (22/09, §443). Nunca mais.
                bad.append(f"{name}: href malformado (não abre com NNN-): {slug[:100]}")
                continue
            sec = int(mm.group(1))
            if sec not in hd:
                bad.append(f"{name} §{sec}: heading de destino ausente"); continue
            want = gh_slug(f"§{sec} " + hd[sec])
            if slug != want:
                bad.append(f"{name} §{sec}:\n   tem : {slug[:100]}\n   quer: {want[:100]}")
    print(f"{label}: {len(bad)} âncora(s) quebrada(s)")
    for b in bad: print("  - " + b)
    return bad

if "--selftest" in sys.argv:
    en = "## §1 — heading cheio com palavra final\n<!-- pt-switch --> **PT:** [x](known-bugs.pt_BR.md#1--heading-cheio)\n"
    pt = "## §1 — heading cheio com palavra final\n<!-- en-switch --> **EN:** [x](known-bugs.md#1--heading-cheio-com-palavra-final)\n"
    # (a) EN->PT truncado deve falhar; PT->EN correto deve passar
    bad_a = check(en, pt, "selftest-a")
    ok_a = len(bad_a) == 1 and "§1" in bad_a[0] and "EN->PT" in bad_a[0]
    # (b) hífen-único malformado (#2-outro) deve ser capturado; lado correto limpo
    en2 = "## §2 — outro heading\n<!-- pt-switch --> **PT:** [x](known-bugs.pt_BR.md#2-outro-heading)\n"
    pt2 = "## §2 — outro heading\n<!-- en-switch --> **EN:** [x](known-bugs.md#2--outro-heading)\n"
    bad_b = check(en2, pt2, "selftest-b")
    ok_b = len(bad_b) == 1 and "§2" in bad_b[0] and "EN->PT" in bad_b[0]
    # (c) href com `-` inicial (slug hasheado com o `## ` junto) deve ser capturado
    en3 = "## §3 — terceiro heading\n<!-- pt-switch --> **PT:** [x](known-bugs.pt_BR.md#-3--terceiro-heading)\n"
    pt3 = "## §3 — terceiro heading\n<!-- en-switch --> **EN:** [x](known-bugs.md#3--terceiro-heading)\n"
    bad_c = check(en3, pt3, "selftest-c")
    ok_c = len(bad_c) == 1 and "malformado" in bad_c[0] and "EN->PT" in bad_c[0]
    if ok_a and ok_b and ok_c:
        print("SELFTEST OK: truncamento, hífen-único e href com `-` inicial capturados, lado correto limpo")
        sys.exit(0)
    print("SELFTEST FALHOU:", bad_a, bad_b, bad_c); sys.exit(1)

for a, b in [("docs/bugs-and-gaps/known-bugs.md", "docs/bugs-and-gaps/known-bugs.pt_BR.md")]:
    if not (os.path.isfile(a) and os.path.isfile(b)):
        print("UNKNOWN: ledger ausente"); sys.exit(2)
    bad = check(open(a).read(), open(b).read(), "real")
sys.exit(1 if bad else 0)
PYEOF
