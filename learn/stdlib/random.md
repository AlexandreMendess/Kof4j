[English](random.md) | [Português](random.pt_BR.md)

# kof.random — draw with OS entropy

> **Status: stable — 4/4 targets** · forms measured from the real dispatchers
> (`StdCatalog`); the namespace chapter set is the 1:1 contract.

| Function | Form |
|----------|------|
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

> Parity: 4/4 (B27/B28).

**See also:** [39 — Universal Standard Library](../39-stdlib.md) — the full story and the honest parity table.
