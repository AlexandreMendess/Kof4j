[English](math.md) | [Português](math.pt_BR.md)

# kof.math — aritmética que todo programa repete

> **Status: estável em JVM/Script, Native x86-64, JS; cross/corner faces rastreada no ledger de paridade** · forms measured from the real dispatchers
> (`StdCatalog`); the namespace chapter set is the 1:1 contract.

| Function | Form |
|----------|------|

| Função | Forma |
|--------|-------|
| `abs` | `abs(Int n) -> Int` |
| `sign` | `sign(Int n) -> Int` |
| `clamp` | `clamp(Int v, Int lo, Int hi) -> Int` |
| `min` | `min(Int a, Int b) -> Int` |
| `max` | `max(Int a, Int b) -> Int` |
| `isEven` | `isEven(Int n) -> Bool` |
| `isOdd` | `isOdd(Int n) -> Bool` |
| `isPositive` | `isPositive(Int n) -> Bool` |
| `isNegative` | `isNegative(Int n) -> Bool` |
| `isZero` | `isZero(Int n) -> Bool` |
| `sqrt` | `sqrt(Double x) -> Double` |
| `lerp` | `lerp(Double a, Double b, Double t) -> Double` |
| `percentage` | `percentage(Double part, Double whole) -> Double` |
| `isInteger` | `isInteger(Double x) -> Bool` |
| `isDecimal` | `isDecimal(Double x) -> Bool` |
| `roundTo` | `roundTo(Double v, Int decimals) -> Double` |
| `pow` | `pow(Double base, Double exp) -> Double` |
| `parseInt` | `parseInt(String s) -> Int` |
| `parseLong` | `parseLong(String s) -> Long` |
| `parseDouble` | `parseDouble(String s) -> Double` |
| `parseIntOrDefault` | `parseIntOrDefault(String s, Int d) -> Int` |
| `parseLongOrDefault` | `parseLongOrDefault(String s, Long d) -> Long` |
| `parseDoubleOrDefault` | `parseDoubleOrDefault(String s, Double d) -> Double` |
```
```kf
math.abs(-5)          // 5
math.clamp(99, 0, 10) // 10   — clamps to [min,max]
math.sqrt(16.0)       // 4.0  — Double FIRST; -1.0 => NaN
math.lerp(0.0, 10.0, 0.5)  // 5.0
math.roundTo(3.14159, 2)   // 3.14 — half-away-from-zero
math.pow(2.0, 10.0)        // 1024.0 — libm on native x86
math.parseIntOrDefault("x", 0) // 0 — never throws
```
```

> Paridade: `MATH001` on the riscv64/aarch64 static cross (`pow` only) — ledger row 10.

**Veja também:** [39 — Standard Library universal](../39-stdlib.pt_BR.md) — a história completa e a tabela honesta de paridade.
