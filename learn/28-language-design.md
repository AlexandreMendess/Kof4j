[English](28-language-design.md) | [Português](28-language-design.pt_BR.md)

# 28 — Language Design

> **Kof 0.5.0-beta — Sep 2026 — `intention->Kof->frontend->IR->backend->runtime`**

## Philosophy

Kof exists because Java is one of the most powerful platforms in the world, but it demands an absurd amount of code to express simple ideas.

Kof's central question is:

> "Are we eliminating real complexity or just hiding complexity?"

If we are just hiding complexity, the feature needs to be reconsidered.

## The intention paradigm

> It is not a formal paradigm — it is object orientation taken to the extreme.

The chain: **intention → Kof → compiler → backend**. The programmer writes
*what* they want; the compiler and the runtime decide *how*, per target and by
convention. The mechanism never rises into the user's code:

| Intention | You write | The mechanism stays with |
|-----------|-----------|--------------------------|
| parallelism | `spawn tarefa()` | virtual threads (JVM) / pthread (Native, 31/08) |
| HTTP | `app.get("/users/:id") { ... }` | own server, no container |
| HTTP client | `http.get(url)` | `kof.http` JVM+JS (HTTP002 Native) |
| UI | `Window(...)`, `Button("+1", () -> ...)` | KofJS + native webview |
| JSON | `json.decode<User>(body)` | engine + binding per type |
| color | `Palette.red` | 32-bit Int, channels via bitwise |
| nullable | `String?` | compile-time check |
| pattern | `case String s` / `Point(x,y)` | instanceof+checkcast / field loads per backend |
| script | `var x = 5` at the top | `KofScriptGlobals` (repl --watch) |

The intention compiles on all targets; the target that cannot realize it
reports at compile-time with a gap code (`HTTP002`, `WEB002`) — never
silently. Details in `docs/philosophy.md`.

## The multiplatform vision

Kof is not just a language for the JVM. It is a language that can compile to different targets:

```text
                         KOF
                          │
                    Kof Compiler (frontend)
                          │
                       Kof IR
                          │
          ┌───────────────┼────────────────┬───────────┐
          │               │                │           │
       Kof4J          KofNative         KofJS      KofScript/KofC
          │          ┌────┼────┐          │           │
          ▼          ▼    ▼    ▼          ▼           ▼
        JVM       x86-64 riscv arm    ES Module   Globals/C-ELF
       .class      ELF   ELF  ELF      .mjs        repl/kof c
```

**The language does not change. The target changes.**

This is a fundamental design decision. The same Kof source can generate (0.5.0-beta):
- JVM bytecode for applications that need the Java ecosystem
- Native executables x86-64 / riscv64 (`native.risc`) / aarch64 (`native.arm`) for CLI tools and systems (Target separation)
- ES Modules for the browser/webview via KofJS (see [chapter 37](37-kofjs.md))
- Direct execution via KofScript (`var`/`val`→`KofScriptGlobals`) and C via KofC (`kof c` native-only)

## Design decisions

### Less ceremony, not less information

```java
// Java: 40 lines
public final class User {
    private final String name;
    public User(String name) { this.name = name; }
    public String name() { return name; }
    // equals, hashCode, toString...
}

// Kof: 1 line
record User(String name)
```

The second form generates exactly the same thing as the first. We did not remove information — we removed repetition.

### The JVM is the runtime

Kof does not invent:
- garbage collector
- scheduler
- memory model
- thread system

The JVM already does this. Kof uses what already exists.

For the native backend, Kof uses:
- direct x86-64 assembly
- Linux syscall conventions
- minimal runtime in C

### Native runtime (0.2.0)

Native uses a **free-list GC with mark-sweep** (`kof_free_head` reuse + `kof_gc_mark`
conservative stack+bss + `kof_gc_sweep`, 03/09 — `KofGcE2ETest` 3/3; auto-collect on
exhaustion still PENDING — needs safe-points, §260), `spawn` via **pthread** (31/08 — `CONC001` closed), **real XMM** floating point (`vcvtsi2sd`/`mulsd`, `FLT001` closed) and full JSON (objects/records/arrays — `JSN001/002/003` closed). `kof_db` brings **native SQLite** and MySQL in progress (wire protocol, SHA-1 auth scramble). None of this leaks into Kof code — it is `intention->Kof->frontend->IR->backend->runtime`.

### Compile-time > runtime

If something can be resolved at compile-time, it must be.

```kf
var user = User("Mel")
```

The compiler knows that `user` is a `User`. This does not need reflection at runtime.

### Java interoperability is sacred

Kof code:
- calls Java code
- is called by Java code
- uses Java libraries
- works with Java frameworks

This is not negotiable.

### One frontend, multiple backends

The compiler has a single frontend that generates an intermediate representation (IR). From that IR, different backends can transform the same program:

```text
Kof Source
    │
    ▼
Lexer → Parser → AST → IR
    │
    ├──────────► JVM Backend → .class
    ├──────────► Native Backend → ELF
    └──────────► Script Backend → Runtime
```

This lets the language grow without fragmenting.

## Syntax

### Records

We chose `record` because it is the simplest construct for immutable data:

```kf
record Point(Int x, Int y)
```

### Modifiers

Modifiers are explicit when important:

```kf
public class User(String name) { ... }
private String password
static Int count
```

### Functions

Functions are declared without a keyword — the name comes first. The return
type can be prefixed (`String nome()`) or suffixed (`nome(): String`):

```kf
main() = print("Hello")

dobro(Int x) = x * 2

somar(Int a, Int b): Int {
    return a + b
}
```

## Type System

Kof is strongly and statically typed.

```kf
var nome = "Mel"     // type: String (inferred)
String nome = "Mel"  // type: String (explicit)
```

Both are statically typed. Inference does not change that.

## What we do not do

- We do not create macros
- We do not create metaclasses
- We do not create compile-time macros
- We do not create our own VM (we use the JVM)
- We do not create our own runtime (we use the operating system)

Each feature must prove that it is worth the complexity.

## Multiplatform philosophy

Kof's multiplatform philosophy is based on three principles:

1. **One compiler, multiple targets** — the same source code can generate code for different platforms
2. **The language does not change** — there are no "dialects" for different targets
3. **The backend is a compiler decision** — the developer chooses the target, not the language

This lets Kof be used for:
- Corporate applications on the JVM
- Native CLI tools
- Interactive scripts
- Web applications via KofJS

## Next step

[Compiler Internals →](29-compiler-internals.md)
