[English](17-functional-programming.md) | [Português](17-functional-programming.pt_BR.md)

# 17 — Functional Programming

> **Status: implemented — `map/filter/reduce` on `List<T>` (0.5.0-beta) — JVM/Native/JS — examples verified in the compiler**
>
> Kof is not a functional language, but `List<T>` offers
> idiomatic `map/filter/reduce` — the transformation is an expression, not a
> manual loop.

## map — transform each element

```kf
var nums = listOf(1, 2, 3, 4, 5)
var dobrados = nums.map((x: Int) -> x * 2)
println(dobrados.get(0))   // 2
println(dobrados.size)     // 5
```

## filter — select elements

```kf
var pares = nums.filter((x: Int) -> x % 2 == 0)
println(pares.size)        // 2 — [2, 4]
```

## reduce — accumulate

```kf
var soma = nums.reduce((acc: Int, x: Int) -> acc + x, 0)
println(soma)              // 15
```

## Combining

```kf
record User(String nome, Int idade)

main() {
    var usuarios = listOf(
        User("Mel", 26),
        User("Ana", 34),
        User("Bob", 17)
    )

    var adultos = usuarios.filter((u: User) -> u.idade() >= 18)
        .map((u: User) -> u.nome().toUpperCase())
    println(adultos.size)          // 2
    println(adultos.get(0))        // MEL
}
```

## Why not a manual loop

```kf
// ❌ Manual loop — the "what" (mapping) is hidden in the "how" (iterating)
var nomes = listOf()
for (var u in usuarios) { nomes.add(u.nome()) }

// ✅ map — expresses the intention
var nomes2 = usuarios.map((u: User) -> u.nome())
```

## Immutability and `val`

`val` prevents reassignment of the variable, but `List` remains mutable through
methods (`add`, `set`):

```kf
val lista = listOf(1, 2, 3)
lista.add(4)              // works — the list is mutable
// lista = listOf(9)      // error — val cannot be reassigned
```

For truly immutable data, use `record` + `json.encode`/`json.decode`
(see ch. 12 and `docs/stdlib/stdlib.md`).

## Pure functions

A pure function has no side effects — same input, same output:

```kf
Int dobro(Int x) = x * 2
```

Prefer pure functions in `map/filter/reduce` (without mutating external state).

## Exercises

1. Given `listOf(1,2,3,4,5,6)`, compute the sum of the squares of the even numbers with a
   chain `filter(...).map(...).reduce(...)`.
2. Mentally order the output of `usuarios.filter((u) -> u.idade() < 30)
   .map((u) -> u.nome())` — confirm with `kof run`.
3. Rewrite a `for` that builds a list of names using `map` (exercise from
   ch. 12).

## Next step

[Concurrency →](18-concurrency.md)
