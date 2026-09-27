[English](06-functions.md) | [Português](06-functions.pt_BR.md)

# 06 — Functions

> **Status: implemented (JVM / Native / JS) — 0.5.0-beta — examples verified in the compiler**
>
> Top-level functions, methods, expression bodies, default parameters,
> recursion and functions as values (lambdas) work on the JVM, Native
> and KofJS targets.

## Top-level functions

There is no `fun`/`func` — the function is declared by name, with the return
type **before** the name or **after** the parameters:

```kf
Int soma(Int a, Int b) {
    return a + b
}

main() {
    println(soma(2, 3))   // 5
}
```

## The valid forms

```kf
main() { println("entry point") }             // the only one without an explicit type
String saudacao() { return "oi" }             // return before the name
despedida(): String { return "tchau" }        // return after the parameters
void fazIsso() { println("x") }               // explicit void
```

## Expression body

For single-expression functions:

```kf
Bool positivo(Int x) = x > 0

main() {
    println(positivo(5))    // true
    println(positivo(-1))   // false
}
```

## Parameters

```kf
void imprimir(String mensagem, Int vezes) {
    var i = 0
    while (i < vezes) {
        print(mensagem)
        i = i + 1
    }
}
```

## Default parameters

```kf
void greet(String name = "world") {
    println("hello " + name)
}

main() {
    greet("Mel")
    greet()          // uses the default — "hello world"
}
```

`Server(8080)` / `Server()` for classes follow the same semantics, resolved
at compile-time.

## Return

In `void` functions, `return` alone ends the flow:

```kf
void maybe(Bool condition) {
    if (condition) {
        return
    }
    println("not-returned")
}
```

`return;` is also accepted for compatibility.

## Recursion

```kf
Int fatorial(Int n) {
    if (n <= 1) { return 1 }
    return n * fatorial(n - 1)
}

main() {
    println(fatorial(5))   // 120
}
```

## Functions as values

Lambdas are first-class values — stored in a variable and passed
as an argument (this is how `map/filter/reduce` work, see ch. 12 and 16):

```kf
main() {
    var dobro = (x: Int) -> x * 2
    println(dobro(5))                                  // 10

    var nums = listOf(1, 2, 3)
    var dobrados = nums.map((x: Int) -> x * 2)         // [2, 4, 6]
    println(dobrados.get(0))                           // 2
}
```

> **Note:** a function type IS legal as a declared parameter in BOTH forms — type-first
> (`Int aplica((Int) -> Int f, Int x)`) and annotated (`Int aplica(f: (Int) -> Int, x: Int)`) —
> measured 42/42 on the 0.4.0 tip (parser fix §277/#389, `NestedFnTypeArityTest`). Pass an
> anonymous **lambda** at the call site: a bare named-function reference does not bind
> (`training/anti-patterns/fake-idioms.md`).

## Overloading (0.5.0-beta)

Two functions with the SAME name coexist when their signatures differ
(arity or parameter types):

```kf
main() {
    println(g(4))       // 4
    println(g(4, 5))    // 9
}
Int g(Int x) { return x }
Int g(Int x, Int y) { return x + y }
```

An exact duplicate (same name + same parameters) is an error (SEM047).
Changing ONLY the return type does not create an overload — the call
becomes ambiguous (SEM057). Class methods overload the same way
(`training/idioms/classes.md`, §131).

## Exercises

1. Write `Int maximo(Int a, Int b)` as an expression body and use it.
2. Write a recursive `Int fib(Int n)` and print `fib(10)`.
3. Create `listOf(1,2,3,4).map(...)` that returns the squares. Compare with a
   manual loop — which expresses the intention better?
4. Write a function with a default parameter that generates a personalized
   greeting.

## Next step

[Classes and Objects →](07-classes-and-objects.md)
