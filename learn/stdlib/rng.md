[English](rng.md) | [Português](rng.pt_BR.md)

# kof.rng — seedable deterministic PRNG

> **Status: stable — 4/4 targets** · forms measured from the real dispatchers
> (`StdCatalog`); the namespace chapter set is the 1:1 contract.

| Function | Form |
|----------|------|
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

> Parity: 4/4 (X8 slices 1–2).

**See also:** [39 — Universal Standard Library](../39-stdlib.md) — the full story and the honest parity table.
