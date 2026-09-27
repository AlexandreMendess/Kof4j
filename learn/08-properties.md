[English](08-properties.md) | [Português](08-properties.pt_BR.md)

# 08 — Fields and Data Access ("Properties")

> **Status: implemented — direct field access, no getters/setters (0.5.0-beta, examples verified in the compiler)**
>
> Kof does **not** have JavaBeans: there is no conventional getter/setter nor
> framework reflection. A field is accessed directly: `u.name` (read) and
> `u.name = "Mel"` (write). This is a philosophy decision, not a gap — see
> `docs/philosophy.md` and `training/anti-patterns/java-like-code.md`.

## The Java problem

In Java, exposing a field requires ceremony:

```java
private String name;

public String getName() { return name; }
public void setName(String name) { this.name = name; }
```

This exists because of JavaBeans, serialization and reflection frameworks.
**Kof has none of these conventions** — so the ceremony disappears.

## Two models of "data with parameters"

Kof has **two** models, and it is essential to distinguish them:

| Declaration | Runtime | Fields | Access | Mutable? |
|-----------|---------|--------|--------|----------|
| `record Point(Int x, Int y)` | record | private `final` | `p.x()` (accessor) | no |
| `class User(String name, Int age)` | **record** (same) | private `final` | `u.name` (→ accessor) | no |
| `class Conta { String titular; constructor(...) }` | class | public | `c.titular` | yes |

> `class X(...)` is an **alias of `record X(...)`** — it compiles to a
> `java.lang.Record` (immutable). For **mutable state** with parameters, use
> explicit fields + `constructor(...)`.

## 1. Immutable data → record (and `class X(...)`)

```kf
record Point(Int x, Int y)
// class Point(Int x, Int y) — identical

main() {
    var p = Point(10, 20)
    println(p.x())        // 10 — record accessor
    println(p)            // Point[x=10, y=20] (JVM)
    // p.x = 99           // COMPILATION ERROR SEM038: records are immutable
}
```

`u.name` (without parentheses) in a record **also reads** — the compiler lowers
it to the accessor. But **writing** (`u.name = ...`) is invalid (final field).

## 2. Mutable state → class with `constructor(...)`

```kf
class Conta {
    String titular
    Double saldo

    public constructor(String titular, Double saldo) {
        this.titular = titular
        this.saldo = saldo
    }

    depositar(Double valor) {
        saldo = saldo + valor
    }
}

main() {
    var c = Conta("Mel", 100.0)
    println(c.titular)          // "Mel" — public field, direct read
    c.saldo = 200.0             // direct write
    c.depositar(50.0)
    println(c.saldo)            // 250.0
}
```

Here the fields are **public** and **mutable** — direct read and write,
without getters/setters.

## Fields without a constructor

A class without an explicit constructor has a default no-argument constructor:

```kf
class Usuario {
    String nome
    Int idade
}

main() {
    var u = Usuario()
    u.nome = "Mel"
    println(u.nome)
}
```

## Access rules

- Fields are public by default (`private`/`protected` exist for when
  you really need to encapsulate).
- Do not write `getName()`/`setName()` by reflex — it is ceremony without semantics.
- Methods inside the class access the fields directly (`saldo = saldo + valor`).

## Anti-pattern (what NOT to do)

```kf
// ❌ Translated Java — getters/setters with no reason to exist
class User {
    private String name
    public getName(): String { return name }
    public setName(String name) { this.name = name }
}

// ✅ Kof — the field is the data
class User {
    String name
}
```

## Exercises

1. Create `class Conta(String titular, Double saldo)` and try
   `c.saldo = 300.0`. What happens? Explain why (compare with the
   `record`).
2. Write the same `Conta` as a **mutable class** (fields + `constructor`) and
   implement `depositar`/`sacar`. Validate with `kof run`.
3. Convert the data model of a simple app (e.g.: `User`, `Produto`)
   to `record` when immutable and class when mutable — decide case by case.

## Next step

[Interfaces →](09-interfaces.md)
