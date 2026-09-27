[English](04-variables-and-types.md) | [Português](04-variables-and-types.pt_BR.md)

# 04 — Variables and Types

## What you will learn

In this chapter you will understand how to declare variables, how the type system works, and how type inference operates.

## Variable declaration (0.5.0-beta)

In Kof there are two keywords for variables (`var`/`val`); there is **no**
`let`/`const` (JS sugar removed from KofScript 06/09 — see below):

### `var` — mutable variable

```kf
var nome = "Mel"
nome = "Outro"  // works
```

### `val` — constant value

```kf
val PI = 3.14
// PI = 2.0  // ERROR: cannot reassign
```

### `let` / `const` — DO NOT exist (sugar removed 06/09)

KofScript is **pure Kof executed directly** — it is NOT JavaScript. There is
**no `let`/`const` alias**: `let x = 5` gives `SEM011`/`PARSE011` (*measured on
the tip jar, 16/09*). Use `var`/`val`; the wrapper's only job is the script
model — top-level `var`/`val` become static fields of `KofScriptGlobals` and
loose statements become `main()`:

```kf
var nome = "Mel"   // top-level .ks → KofScriptGlobals.nome (static field)
val pi = 3.14      // same thing; there is no let/const
println(nome)      // a loose statement is wrapped into main()
```

## Explicit typing

You can specify the type explicitly:

```kf
Int idade = 26
String nome = "Mel"
Bool ativo = true
```

## Type inference

When you use `var` or `val`, the compiler infers the type:

```kf
var idade = 26        // compiler knows it is Int
var nome = "Mel"      // compiler knows it is String
var pi = 3.14         // compiler knows it is Double
var ativo = true      // compiler knows it is Bool
```

This **is not** dynamic typing. The compiler knows the type at compile-time. It is just a more concise way of writing.

```kf
// These two lines are equivalent:
var nome = "Mel"
String nome = "Mel"
```

## Reference types

### Records

```kf
record Point(Int x, Int y)
```

### Troolean (three-valued)

`Bool` has exactly two values; `Troolean` carries `true`/`false`/`unknown`
(D-TROOL, 0.5.0-beta). `Bool?` written by the user is SEM095 — the pointer
names `Troolean`. `if (t)` is sugar for `if (t == true)`; `&&`/`||`/`!` follow
the Kleene tables (unknown persists). Details: `training/language/types.md`
§Troolean.

### Classes

```kf
class User(String name)
```

### Arrays

There is no array literal (`{1, 2, 3}` / `[1, 2, 3]` do not compile). Use
`new Tipo[n]` and fill by index:

```kf
var numeros = new Int[3]
numeros[0] = 1
numeros[1] = 2
numeros[2] = 3
println(numeros.length)    // 3
```

For a dynamic sequence, use `listOf(1, 2, 3)` (see ch. 12).

### Enums

```kf
enum Color { Red, Green, Blue }
```

An enum declares a closed set of constants. The runtime value is the
name itself — comparison is by content (`==` works as expected) and
`println(Color.Red)` prints `Red`.

Built-in API:

| Call | Returns | Description |
|---------|---------|-----------|
| `Color.values()` | `List<String>` | all constants, in declaration order |
| `Color.valueOf("Red")` | `Color?` | constant by name; `null` if invalid |
| `c.name()` | `String` | the name of the constant |

A nonexistent constant is a compilation error:

```kf
Color.Nope   // SEM030: enum 'Color' has no constant 'Nope'
```

**Exhaustive switch**: a switch over an enum must cover **all** the
constants or have a `default` — otherwise it becomes error `SEM031` listing the
missing cases:

```kf
String nome(Color c) {
    var r = ""
    switch (c) {
        case Color.Red:   { r = "vermelho" }
        case Green:       { r = "verde" }      // unqualified also works
        case Color.Blue:  { r = "azul" }
    }
    return r
}   // without the three cases and without default → SEM031
```

## Conversions

### Widening (automatic)

The compiler automatically converts smaller types to larger ones:

```kf
Int i = 42
Long l = i     // Int → Long (automatic)
Double d = i   // Int → Double (automatic)
```

### Narrowing (casting)

Conversion from larger to smaller needs an explicit cast:

```kf
Double d = 3.14
Int i = d as Int   // Double → Int (needs 'as')
```

## Compatibility with Java types

Kof uses the same types as the JVM:

| Kof | Java | JVM |
|-----|------|-----|
| `Bool` | `boolean` | `Z` |
| `Byte` | `byte` | `B` |
| `Short` | `short` | `S` |
| `Int` | `int` | `I` |
| `Long` | `long` | `J` |
| `Float` | `float` | `F` |
| `Double` | `double` | `D` |
| `Char` | `char` | `C` |
| `String` | `String` | `Ljava/lang/String;` |

## KofScript: top-level `var`/`val` + String? (0.2.0; sugar let/const REMOVED 06/09)

```kf
var x = 5            // top-level .ks → static field of KofScriptGlobals
String? s = mapOf("k", "abc").get("k")   // basic nullable — null via API (= null is SEM048)
if (s != null) { println(s.length()) }
```

## Current status (0.5.0-beta)

✅ `var` and `val` work
✅ top-level `var`/`val` in KofScript → `KofScriptGlobals` (there is NO `let`/`const` — JS sugar removed 06/09)
✅ Basic nullable `String?`
✅ Type inference works
✅ Records work
✅ Classes with fields work
✅ Type checking (semantic analysis, `SEM` errors at compile-time)
✅ Automatic conversions (widening: `Int→Long`, `Int→Double`)

## Exercise 1

Declare variables of all primitive types (Int, Double, Bool, Char...) and print their values with println.

## Exercise 2

Create a record `Produto` with fields `nome String`, `preco Double` and `quantidade Int`. Create an instance and access its values.

## Next step

[Control Flow →](05-control-flow.md)
