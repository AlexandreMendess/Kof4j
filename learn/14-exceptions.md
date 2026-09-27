[English](14-exceptions.md) | [Português](14-exceptions.pt_BR.md)

# 14 — Exceptions

> **Status: implemented (JVM / Native / JS) — 0.5.0-beta — examples verified in the compiler**
>
> `throw`/`try`/`catch`/`finally` with real unwinding on JVM, Native and KofJS.
> Kof throws **Strings** (`throw "mensagem"` / `catch (String e)`), not
> exception class instances.

## throw

Kof throws a value (the message goes straight to the `catch`):

```kf
throw "valor inválido"
```

> **Important (verified 02/09):** the exception is a **String**.
> `throw 42` / `catch (Int e)` generate invalid bytecode on the JVM — do not use.
> For absence as a value, use `String?` (ch. 13).

## try/catch/finally

```kf
main() {
    try {
        var conexao = abrirConexao()
    } catch (String e) {
        println("erro: " + e)
    } finally {
        println("cleanup")    // always runs
    }
}
```

## Throwing contextualized values

The "identity" of the failure comes from the message itself:

```kf
User findUser(Int id) {
    if (id < 0) {
        throw "user not found: " + id
    }
    return User("u" + id)
}

class User(String name) { }

main() {
    try {
        var u = findUser(-1)
        println(u.name)
    } catch (String e) {
        println("caught: " + e)    // caught: user not found: -1
    } finally {
        println("cleanup")         // cleanup
    }
}
```

## Absence vs error

```kf
// Absence (data may not exist) → String?
String? find(Int id) {
    if (id == 1) { return "mel" }
    return null
}

// Real error (absence is a defect) → throw
String findOrThrow(Int id) {
    if (id == 1) { return "mel" }
    throw "not found: " + id
}
```

## Limitations (02/09, verified)

- Exceptions are **Strings** only — no exception object.
- On Native, the first `catch` of a `try` captures (no dispatch by type
  among multiple catches).
- No stack trace on Native.

## Exercises

1. Write `Double divide(Int a, Int b)` that throws `"division by zero"` when
   `b == 0`; handle it with `try/catch` in `main`.
2. Convert a function that returns `""` as "not found" to `String?`
   (ch. 13) and then to `throw` — explain when to use each one.
3. Verify that `finally` runs on the normal path, on the caught one and on the
   propagated one.

## Next step

[Pattern Matching →](15-pattern-matching.md)
