[English](02-first-program.md) | [Português](02-first-program.pt_BR.md)

# 02 — First Program

> **Kof 0.5.0-beta — Sep 2026 — targets jvm/native/native.risc/native.arm/js/kofc**

## The most basic construct

In Kof, the simplest construct for which the compiler generates valid bytecode is the **record**:

```kf
record Ponto(Int x, Int y)
```

This creates a JVM class with:
- two private final fields (`x`, `y`)
- a public constructor that accepts `Int` and `Int`
- two public methods `x()` and `y()` that return the values
- a `toString()` method

In Native it becomes a struct with equivalent fields and methods; in JS, an ES class — the `intention->Kof->frontend->IR->backend->runtime` chain preserves the semantics.

## Understanding each part

```
record      → keyword: defines a record
Ponto       → class name
(           → start of the components
Int x       → first component: type Int, name x
,           → separator
Int y       → second component: type Int, name y
)           → end of the components
```

## Creating instances

In Java, to create an instance you write `new User("Mel", ...)`. In Kof,
construction is `Classe(args)` **without the `new`** (the idiomatic form); `new`
is still accepted for backward compatibility, with the same semantics:

```kf
var p = Ponto(3, 7)      // idiomatic form (recommended)
var old = new Ponto(3, 7) // explicit form (backward compatible)
```

The compiler generates the constructor and the accessors automatically.

## Accessing values

The accessor methods are generated automatically:

```java
Ponto p = new Ponto(3, 7);
p.x()  // returns 3
p.y()  // returns 7
```

## A complete program

Now Kof supports `main()`. You can create a complete program:

File `main.kf`:

```kf
main() = print("Olá, mundo!")
```

Compiling and running:

```bash
kof run main.kf                 # jvm (default)
kof run main.kf --target=native # ELF x86-64
kof run main.kf --target=js     # ES Module via embedded GraalJS
```

Result:

```
Olá, mundo!
```

## A program with records

File `ponto.kf`:

```kf
record Ponto(Int x, Int y)

main() {
    var p = Ponto(3, 7)
    print(p)
}
```

Running:

```bash
kof run ponto.kf
```

Result:

```
Ponto[x=3, y=7]
```

### Pattern matching with destructuring (0.2.0)

Records already destructure in `switch`:

```kf
record Ponto(Int x, Int y)

String descreve(Object o) {
    switch (o) {
        case Ponto(x, y): { return "ponto " + x + "," + y }
        case String s: { return "texto " + s }
        default: { return "outro" }
    }
}

main() {
    println(descreve(Ponto(3, 7)))  // point 3,7
    println(descreve("kof"))        // text kof
}
```

```bash
kof run ponto.kf --target=jvm     # JVM instanceof+checkcast
kof run ponto.kf --target=native  # Native rbx→rcx fix
kof run ponto.kf --target=js      # JS typeof
```

### KofScript: top-level `var`/`val` become global

KofScript is pure Kof — there is **no `let`/`const`** (JS sugar removed 06/09;
`let x = 5` → `PARSE011`/`SEM011`). In `kof script` / `kof repl`, `var`/`val`
at file level become static fields of the generated `KofScriptGlobals`, and
loose statements become `main()`:

File `demo.ks`:

```kf
var nome = "Mel"
val pi = 3.14

println(nome + " " + pi)   // a loose statement is wrapped into main()
```

```bash
kof script demo.ks                # direct execution
kof script --repl                 # incremental REPL (type 'exit' to quit)
kof script demo.ks --watch        # re-executes on save
```

### KofC: native-only C subset

`kof c` does not compile Kof — it compiles a subset of C to an x86-64 ELF:

```c
// hello.c
int x = 42;
void printInt(int v);

int main() {
    if (x > 0) {
        printInt(x);
    }
    while (x > 0) { x = x - 1; }
    return 0;
}
```

```bash
kof c hello.c --run               # compiles via GAS+LD and executes
kof c hello.c --output ./bin      # only compiles (native-only, no --target jvm/js)
```

## Variables and inference

Kof supports type inference:

```kf
var nome = "Mel"
var idade = 26
var pi = 3.14
var apelido: String? = null   // basic String? (0.2.0): nullable with compile-time check
```

The compiler understands the types automatically.

## Exercise 1

1. Create a file `coordenada.kf`
2. Define a record with two fields: `lat Double` and `lon Double`
3. Compile with the CLI
4. Check with `javap -v Coordenada.class`

## Exercise 2

1. Create a record `Pessoa` with fields `nome String` and `idade Int`
2. Create a main function that creates a person and prints their data
3. Run it with `kof run`

## Exercise 3 — destructuring + KofScript

1. Create `ponto.kf` with `record Ponto(Int x, Int y)` and a `switch` with `case Ponto(x, y):`
2. Run it with `kof run --target=jvm` and `--target=js`
3. Create `demo.ks` with top-level `var n = 10` and use `n` inside `main()` via `kof script demo.ks`

## Next step

[Language Basics →](03-language-basics.md)
