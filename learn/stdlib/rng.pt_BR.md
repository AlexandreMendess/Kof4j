[English](rng.md) | [Português](rng.pt_BR.md)

# kof.rng — PRNG determinístico com semente

> **Status: estável — 4/4 targets** · forms measured from the real dispatchers
> (`StdCatalog`); the namespace chapter set is the 1:1 contract.

| Function | Form |
|----------|------|

| Função | Forma |
|--------|-------|
| `seed` | `seed(Int n) -> void` |
| `int` | `int(Int n) -> Int` |
| `boolean` | `boolean() -> Bool` |
| `double` | `double() -> Double` |
| `string` | `string(Int n, String s) -> String` |
```
```kf
rng.seed(42)           // deterministic PRNG for tests
rng.int(10)            // same sequence on every target
rng.string(5)
```
```

> Paridade: 4/4 (X8 slices 1–2).

**Veja também:** [39 — Standard Library universal](../39-stdlib.pt_BR.md) — a história completa e a tabela honesta de paridade.
