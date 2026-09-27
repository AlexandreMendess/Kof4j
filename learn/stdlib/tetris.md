[English](tetris.md) | [Português](tetris.pt_BR.md)

# kof.tetris — the platform's own game engine face

> **Status: JVM ✅ · parity ledger tracks the other targets.**

| Function | Form |
|----------|------|
| `run` | `run() -> void` |

```kf
kof.tetris.run()   // the reference game — proof the platform renders a game loop
```

- `tetris.run()` is the platform's acceptance toy: a complete game (input,
  physics, rendering) running through Kof's own engine (`D-GRAPHICS-GAMING`:
  the graphics/media engine is Kof's own, full cross-target parity as its
  acceptance criterion — FFI bindings stay limited to window/GPU/audio
  devices).
- Not a "library you call" in real apps — it is the reference showing the
  loop idiom the engine exposes.

**See also:** [35 — kof.ui](../35-kof-ui.md) — widgets are intent; the engine
renders.
