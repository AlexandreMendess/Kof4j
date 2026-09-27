[English](16-lambdas.md) | [Português](16-lambdas.pt_BR.md)

# 16 — Lambdas

> **Status: implemented (JVM / Native / JS) — 0.5.0-beta — examples verified in the compiler**
>
> Lambdas `(x: Int) -> expr` with captures work on the three targets;
> `map/filter/reduce` on `List<T>` use lambdas.

## What lambdas are

Lambdas are anonymous functions — blocks of code that can be passed as
arguments or stored in variables.

## Syntax

```kf
main() {
    var dobro = (x: Int) -> x * 2
    var resultado = dobro(5)
    println(resultado)     // 10

    var soma = (a: Int, b: Int) -> a + b
    println(soma(3, 4))    // 7

    var constante = () -> 99
    println(constante())   // 99
}
```

## With collections

```kf
var nomes = listOf("Ana", "Bob", "Carlos")

var maiusculos = nomes.map((nome: String) -> nome.toUpperCase())
// ["ANA", "BOB", "CARLOS"]

var longos = nomes.filter((nome: String) -> nome.length > 3)
// ["Carlos"]
```

## Capture (closures)

A lambda captures variables from the scope where it was created:

```kf
var fator = 2
var dobro = (x: Int) -> x * fator
println(dobro(5))    // 10
```

## Mutable capture (02/09 — JVM verified)

A captured variable that is **mutated** (inside or outside the lambda) is
**boxed** — the lambda sees the updated value:

```kf
main() {
    var offset = 10
    var f2 = (x: Int) -> x + offset
    println(f2(5))        // 15
    offset = 20           // mutation OUTSIDE the lambda
    println(f2(5))        // 25 — the lambda sees the new value

    var counter = 0
    var inc = () -> { counter = counter + 1 }   // lambda WRITES to the outer one
    inc()
    inc()
    println(counter)      // 2
}
```

> **History (02/09):** previously the mutation outside the lambda was not detected — the
> variable was captured **by value** and the read stayed outdated
> (it returned 15 instead of 25). Fixed in `CompilerDriver`
> (`collectMutatedCaptures`). **Native:** the direction "lambda writes to the
> outer variable" works; the direction "reads the outer variable after it is
> mutated outside the lambda" is still a known bug (it produces the wrong value) —
> use with caution on the native target.

## Practical rule

- A lambda that **only reads** a variable: capture by value, no surprises.
- A **mutated** variable + lambda: the compiler boxes it — it works on the JVM; on
  Native, prefer that the mutation happens **inside** the lambda.

## Method reference — planned

`::nome` is not supported yet. Use an explicit lambda:

```kf
for (var nome in listOf("Ana", "Bob")) {
    println(nome)
}
```

## Exercises

1. Write a lambda `(x: Int) -> x * x` and use it with `listOf(1,2,3,4).map`.
2. Capture a variable, call the lambda, change the variable and call it again —
   verify that the JVM reflects the change.
3. Use `filter` to extract only the even numbers from `listOf(1,2,3,4,5,6,7,8,9,10)` and
   `reduce` to sum them.

## Next step

[Functional Programming →](17-functional-programming.md)
