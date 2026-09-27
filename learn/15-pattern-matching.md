[English](15-pattern-matching.md) | [Português](15-pattern-matching.pt_BR.md)

# 15 — Pattern Matching

> **Status: implemented (JVM / Native / JS) — 0.5.0-beta**
>
> `switch case String s` (type pattern) and record destructuring `case Point(x, y):` work on the three targets. Parser + Semantic + CompilerDriver with `Native rbx→rcx` fix and `JS typeof`.
>
> **Attention:** the pattern with a variable (`case String s`) exists **only inside `switch`**. The form `if (obj instanceof String s)` is **not supported** — `instanceof` is a simple boolean operator and does not *bind* a variable; to capture, use `switch` with `case` or `as` (cast).

## `instanceof` (type check)

`instanceof` checks the type (boolean) — it does **not** declare a variable:

```kf
main() {
    Object obj = "kof"
    if (obj instanceof String) {
        var s = obj as String
        println("é uma string: " + s)
    }
}
```

Runnable — `kof run --target=jvm|native|js`:

```kf
main() {
    Object o = "kof"
    if (o instanceof String) {
        var s = o as String
        assert(s == "kof")
        println(s)
    }
}
```

To capture the typed variable in a single step, use the `switch` pattern
(next section).

## switch with patterns — type pattern

```kf
record Circulo(Double raio)
record Retangulo(Double largura, Double altura)

String descreve(Object forma) {
    switch (forma) {
        case Circulo c: { return "círculo com raio " + c.raio() }
        case Retangulo r: { return "retângulo " + r.largura() + "x" + r.altura() }
        default: { return "forma desconhecida" }
    }
}

main() {
    println(descreve(Circulo(2.0)))
    println(descreve(Retangulo(3.0, 4.0)))
}
```

> **Two forms:**
> - **Statement** — `case Tipo var:` with a statement body (side effects).
> - **Expression (SYN001, 03/09)** — `case Tipo var ->` producing a **value**:
>   `var desc = switch (forma) { case Circulo c -> "raio " + c.raio(); default -> "?" }`.
>   Each case is a single expression; `default` is mandatory (or enum
>   exhaustiveness, otherwise `SEM032`); no `break`, no block scope. It works on the 3
>   targets (JVM/Native/JS) + riscv64/aarch64.

## Record destructuring — `Point(x, y)`

0.2.0 supports direct destructuring of the record in the `case`:

```kf
record Ponto(Int x, Int y)
record Pessoa(String nome, Int idade)

main() {
    Object o = Ponto(3, 7)
    switch (o) {
        case Ponto(x, y): {
            println("ponto " + x + "," + y)   // 3,7
        }
        case Pessoa(nome, idade): {
            println(nome + " " + idade)
        }
        case String s: {
            println("texto " + s)
        }
        default: {
            println("outro")
        }
    }

    // destructuring with explicit var also works:
    switch (Ponto(1, 2)) {
        case Ponto(var a, var b): { println(a + b) }  // 3
        default: {}
    }
}
```

Compile and run on the three targets — the chain `intention->Kof->frontend->IR->backend->runtime` keeps the semantics: the frontend normalizes `Ponto(x, y)` to `PatternExpr`, the IR emits `instanceof`+`checkcast`+`getfield` (JVM) / direct loads (Native) / `typeof`+field access (JS).

## Patterns in sealed hierarchies (0.5.0-beta — X5.2)

A `switch` **expression** over a `sealed` subject must cover every direct
subtype (all declared in the same file, see ch. 10) or provide `default`; a
missing case is `SEM081`. No `permits` clause is needed:

```kof
sealed class Shape
class Circle extends Shape { ... }
class Square extends Shape { ... }

String describe(Shape sh) {
    return switch (sh) {
        case Circle c -> "circle"
        case Square q -> "square"
    }
}
```

The exhaustiveness check is part of the typer (`SEM081` missing case; `SEM080`
subtype outside the sealed type's compilation unit).

## Patterns with guards (planned)

```kf
switch (nota) {
    case Int n when n >= 9: { println("excelente") }
    case Int n when n >= 7: { println("bom") }
    case Int n when n >= 5: { println("regular") }
    default: { println("reprovado") }
}
```

> `when` is still a future desugar — today use `if` inside the `case`.

## Next step

[Lambdas →](16-lambdas.md)
