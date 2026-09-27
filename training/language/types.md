[English](types.md) | [Português](types.pt_BR.md)

# Kof Types

**Version:** 0.5.0-beta (Sep 2026)

## Primitive Types

| Type | Size | Description |
|------|------|-------------|
| `bool` | 4 bytes | Boolean |
| `byte` | 1 byte | Signed byte |
| `short` | 2 bytes | Signed short |
| `int` | 4 bytes | Signed integer |
| `long` | 8 bytes | Signed long |
| `float` | 4 bytes | IEEE 754 float |
| `double` | 8 bytes | IEEE 754 double |
| `char` | 4 bytes | UTF-32 codepoint |
| `string` | reference | KofString |
| `void` | — | No return |

Nullable: suffix `?` → `String?`, `Int?`, `Point?` (NullableType — since 0.2.6-beta). `if (x != null)` narrows to non-null via `isAssignable`.

## Reference Types

### Classes
```kof
class User(String name, Int age) { }
var u = User("Mel", 30)
```

### Records
```kof
record Point(Int x, Int y)
var p = Point(10, 20)
switch (p) {
    case Point(var x, var y): println(x)
}
```

### Generics + Box<T>

```kof
class Box<T>(T value) {
    get(): T { return value }
}
var b: Box<Int> = Box(42)   // primitive T OK — substituteTypeVariable fix 25/08
var l: List<Box<Int>> = listOf(Box(1), Box(2))
var dobrados = listOf(1,2,3).map((x: Int) -> x * 2)
```

Erasure with boxing via the call-site's `parameterTypes`.

### Arrays
```kof
var arr = new Int[10]
var strings = new String[5]
var bigs = new Long[10]    // ✅ real Long[] (JVM long[]; Native/JS same)
```

### Primitive casts (`as`) — since 0.2.6-beta (01/09)

```kof
var c = 104 as Char          // ✅ real I2C — Char from the codepoint
println(String.valueOf(c))   // "h"
var big: Long = 3000000000
var i = big as Int           // ✅ real L2I — narrowing Long→Int
// widening Int→Long is implicit; narrowing Long→Int requires `as Int`
```

- `x as Char`: codepoint from the Int (check: `String.valueOf(x as Char)`).
- `big as Int`: truncates the Long to Int (like Java).
- Never use a mental `KofCheckCast` for primitives — the compiler emits
  real numeric conversions (I2C/L2I), not an object checkcast.

### Long arithmetic (fixed-point / precision)

```kof
var acc: Long = 0
var i = 0
while (i < 2048) {
    acc = acc + bigs[i] * bigs[i]   // ✅ Long×Long→Long (no Int overflow)
    i = i + 1
}
var rms = acc / 2048                // Long division ok
```

- Literal suffixed by assignment (`var acc: Long = 0`) — no `L` suffix.
- Int×Long promotes to Long; Int×Int stays Int (may overflow).
- Fixed-point pattern: states in MICRO (1e-6) and weights in NANO (1e-9),
  Long accumulator, division at the end (`acc / 1_000_000_000` style).

### Interfaces
```kof
interface Speaker {
    speak(): String
}
```

### Enums

```kof
enum Color { Red, Green, Blue }
```

- Runtime representation: the constant name itself (String-backed).
- `==` compares by content; constant name printed directly.
- `Color.values() -> List<String>`; `Color.valueOf("Red") -> Color?`;
  `c.name() -> String`.
- Unknown constant → compile error SEM030.
- Exhaustive switch required (all constants or default) → SEM031.
- Mapped to `java/lang/String` in JVM descriptors on all targets.

### Nullable

```kof
String? s = mapOf("k", "x").get("k")   // null via API (no `= null` — SEM048 since 10/09)
Int? n = 5
if (s != null) {
    println(s.length)   // OK — narrowing
}
String t = s            // error SEM021: String? not assignable to String without a check
```

`NullableType(inner)` in `Type.java`; `TypeChecker.isAssignable` handles `Nullable → non-null`.

**Nullable FIELDS follow the same contract on ALL 4 targets** (D-NULL-INTENT, `slot ⇔
load/store`; §295/§278 family): a field declared `Int?`/`Long?`/`Double?`/`Char?`/`Troolean`
reads **`null` before any write** (JS fixed 20/09, §365 `dd418419`), and writing a primitive
boxes it (`b.n = 42` works — §361 `e293c4a5`). A field store passes the SAME assignability
gate as a local (`x.n = "s"` in `Int n` → SEM012, §368 `5cd078c1`) — and the char idiom is
the single-quote literal everywhere: `c = 'x'` runs on the 4 targets, `c = "x"` (String
into `Char`/`Char?`) is SEM012, field or local. Sentinel `= null` literals stay rejected (SEM048) — `null`
reaches a `T?` field via the API or the never-written read, exactly like locals.


### Troolean (three-state — 0.5.0-beta, D-TROOL)

`Bool` has **exactly two values**. What needs `true / false / unknown` is
`Troolean` (DECISIONS.md §D-TROOL):

```kof
Troolean t                 // declaration without instantiation = unknown
Troolean nb() { return null }   // unknown reaches the variable via API
Bool? b = nb()             // ERROR SEM095: 'Bool' has two values; use 'Troolean'
println(t)                 // "null"
if (t == true) { }         // the comparisons separate the three states
if (t) { }                 // sugar for `t == true` — unknown falls in the false branch
var r = nb() && fb()       // false — F dominates AND (Kleene)
var u = nb() && tb()       // null — unknown persists
```

`!`/`&&`/`||` over `Troolean` follow the **Kleene** tables (F dominates AND,
T dominates OR, `NOT U = U`); the result of a logical with a `Troolean` side
is `Troolean` (to consume as `Bool` narrow with `== true`/`!= null`).
`println` shows `true`/`false`/`null`. Proof: `TrooleanLawE2ETest` (JVM+Script+JS;
the tables are measured on Native-x86-64 in the landing probes).

## Type Inference

```kof
var x = 10          // Int
var s = "Hello"     // String
var p = Point(1, 2) // Point
var b = Box(5)      // Box<Int> inferred
val y = 10          // KofScript (.ks): top-level var/val → KofScriptGlobals
```

## Explicit Types

```kof
Int x = 10
String s = "Hello"
Point p = Point(1, 2)
String? maybe = mapOf("k", "x").get("k")   // null via API (no `= null` — SEM048 since 10/09)
Box<Int> boxed = Box<Int>(5)
```

## Type Compatibility

- Widening: `Int` → `Long` → `Float` → `Double`
- Nullable: `String` assignable to `String?`, not vice-versa without a `!= null` check
- String + anything → String (concatenation)
- Comparison operators → Bool
- Logical operators → Bool
- Erasure: `List<Int>` and `List<String>` same runtime, boxing via call-site
