[English](random.md) | [Português](random.pt_BR.md)

# kof.random — sorteio com entropia do OS

> **Status: estável — 4/4 targets** · forms measured from the real dispatchers
> (`StdCatalog`); the namespace chapter set is the 1:1 contract.

| Function | Form |
|----------|------|

| Função | Forma |
|--------|-------|
| `double` | `double() -> Double` |
| `boolean` | `boolean() -> Bool` |
| `int` | `int(Int n) -> Int` |
| `hex` | `hex(Int n) -> String` |
| `randomBytesHex` | `randomBytesHex(Int n) -> String` |
| `randomInt` | `randomInt(Int n) -> Int` |
| `randomBoolean` | `randomBoolean() -> Bool` |
| `randomString` | `randomString(Int n, String s) -> String` |
```
```kf
random.int(10)         // 0..9
random.boolean()       // true|false
random.double()        // [0,1)
random.hex(16)         // OS entropy
random.randomString(8)
```
```

> Paridade: 4/4 (B27/B28).

**Veja também:** [39 — Standard Library universal](../39-stdlib.pt_BR.md) — a história completa e a tabela honesta de paridade.
