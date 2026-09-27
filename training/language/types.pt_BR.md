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

Nullable: suffix `?` → `String?`, `Int?`, `Point?` (NullableType — desde 0.2.6-beta). `if (x != null)` narrows para non-null via `isAssignable`.

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
var b: Box<Int> = Box(42)   // T primitivo OK — substituteTypeVariable fix 25/08
var l: List<Box<Int>> = listOf(Box(1), Box(2))
var dobrados = listOf(1,2,3).map((x: Int) -> x * 2)
```

Erasure com boxing via `parameterTypes` do call-site.

### Arrays
```kof
var arr = new Int[10]
var strings = new String[5]
var bigs = new Long[10]    // ✅ Long[] real (JVM long[]; Native/JS idem)
```

### Casts primitivos (`as`) — desde 0.2.6-beta (01/09)

```kof
var c = 104 as Char          // ✅ I2C real — Char do codepoint
println(String.valueOf(c))   // "h"
var big: Long = 3000000000
var i = big as Int           // ✅ L2I real — narrowing Long→Int
// widening Int→Long é implícito; narrowing Long→Int exige `as Int`
```

- `x as Char`: codepoint do Int (verificação: `String.valueOf(x as Char)`).
- `big as Int`: trunca o Long para Int (como Java).
- Nunca use `KofCheckCast` mental para primitivos — o compilador emite
  conversões numéricas reais (I2C/L2I), não checkcast de objeto.

### Aritmética Long (fixed-point / precisão)

```kof
var acc: Long = 0
var i = 0
while (i < 2048) {
    acc = acc + bigs[i] * bigs[i]   // ✅ Long×Long→Long (sem overflow de Int)
    i = i + 1
}
var rms = acc / 2048                // divisão Long ok
```

- Literal sufixado por atribuição (`var acc: Long = 0`) — sem sufixo L.
- Int×Long promove para Long; Int×Int permanece Int (pode overflow).
- Padrão fixed-point: estados em MICRO (1e-6) e pesos em NANO (1e-9),
  acumulador Long, divisão no fim (`acc / 1_000_000_000` estilo).

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
String? s = mapOf("k", "x").get("k")   // null via API (sem `= null` — SEM048 desde 10/09)
Int? n = 5
if (s != null) {
    println(s.length)   // OK — narrowing
}
String t = s            // erro SEM021: String? não atribuível a String sem check
```

`NullableType(inner)` em `Type.java`; `TypeChecker.isAssignable` trata `Nullable → non-null`.

**CAMPOS nullable seguem o MESMO contrato nos 4 alvos** (D-NULL-INTENT, `slot ⇔
load/store`; familia §295/§278): um campo declarado `Int?`/`Long?`/`Double?`/`Char?`/`Troolean`
le **`null` antes de qualquer escrita** (JS corrigido 20/09, §365 `dd418419`), e escrever um
primitivo o boxa (`b.n = 42` funciona — §361 `e293c4a5`; a face de escrita `Char?` e um gap
aberto conhecido, §368 — nao anunciar). Literais sentinel `= null` continuam rejeitados
(SEM048) — `null` chega a um campo `T?` via API ou pela leitura nunca-escrita, exatamente
como nos locais.


### Troolean (tres estados — 0.5.0-beta, D-TROOL)

`Bool` tem **exatamente dois valores**. O que precisa de `true / false / desconhecido`
e `Troolean` (DECISIONS.md §D-TROOL):

```kof
Troolean t                 // declaracao sem instancia = unknown
Troolean nb() { return null }   // unknown chega pela API
Bool? b = nb()             // ERRO SEM095: 'Bool' tem dois valores; use 'Troolean'
println(t)                 // "null"
if (t == true) { }         // as comparacoes separam os tres estados
if (t) { }                 // acucar de `t == true` — unknown cai no ramo falso
var r = nb() && fb()       // false — F domina o AND (Kleene)
var u = nb() && tb()       // null — o unknown persiste
```

`!`/`&&`/`||` sobre `Troolean` seguem as tabelas de **Kleene** (F domina AND,
T domina OR, `NOT U = U`); o resultado de logico com lado `Troolean` e
`Troolean` (para consumir como `Bool`, faca narrowing com `== true`/`!= null`).
`println` mostra `true`/`false`/`null`. Prova: `TrooleanLawE2ETest` (JVM+Script+JS;
as tabelas sao medidas no Native-x86-64 nos probes do landing).

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
String? maybe = mapOf("k", "x").get("k")   // null via API (sem `= null` — SEM048 desde 10/09)
Box<Int> boxed = Box<Int>(5)
```

## Type Compatibility

- Widening: `Int` → `Long` → `Float` → `Double`
- Nullable: `String` assignable to `String?`, not vice-versa without `!= null` check
- String + anything → String (concatenation)
- Comparison operators → Bool
- Logical operators → Bool
- Erasure: `List<Int>` e `List<String>` mesmo runtime, boxing via call-site
