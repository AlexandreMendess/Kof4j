#!/usr/bin/env python3
"""Pós-processa o bloco gerado pelo all-contributors-cli: injeta o selo
kof-badge.png pequenininho (alt="kof original contributor") ao lado dos
emojis de contribuição de cada contribuinte. Rodar depois de
`npx all-contributors-cli generate` (o generate sobrescreve o bloco)."""
import re, sys

BADGE = '<img src="kof-badge.png" width="16" alt="kof original contributor" title="kof original contributor"/> '

for path in sys.argv[1:]:
    t = open(path, encoding="utf-8").read()
    # só dentro do bloco ALL-CONTRIBUTORS-LIST
    m = re.search(r"(<!-- ALL-CONTRIBUTORS-LIST:START.*?-->.*?<!-- ALL-CONTRIBUTORS-LIST:END -->)", t, re.S)
    if not m:
        sys.exit(f"bloco não encontrado em {path}")
    block = m.group(1)
    patched = block.replace("<br /><a href=", "<br />" + BADGE + "<a href=")
    open(path, "w", encoding="utf-8").write(t.replace(block, patched))
    print(f"{path}: selo injetado")
