[English](tetris.md) | [Português](tetris.pt_BR.md)

# kof.tetris — a face de engine de jogos da própria plataforma

> **Status: JVM ✅ · o ledger de paridade rastreia os outros alvos.**

| Função | Forma |
|--------|-------|
| `run` | `run() -> void` |

```kf
kof.tetris.run()   // o jogo de referência — prova de que a plataforma roda um game loop
```

- `tetris.run()` é o brinquedo de aceitação da plataforma: um jogo completo
  (input, física, renderização) rodando na engine própria do Kof
  (`D-GRAPHICS-GAMING`: a engine gráfica/mídia é do Kof, com paridade total
  cross-target como critério de aceitação — bindings FFI ficam limitados a
  janela/GPU/áudio).
- Não é "biblioteca para chamar" em apps reais — é a referência que mostra o
  idioma de loop que a engine expõe.

**Veja também:** [35 — kof.ui](../35-kof-ui.pt_BR.md) — widgets são
intenção; a engine renderiza.
