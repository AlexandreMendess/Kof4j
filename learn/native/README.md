[English](README.md) | [Português](README.pt_BR.md)

# Multiplatform — One Language, Multiple Worlds

> **Kof 0.5.0-beta — Sep 2026 — targets jvm/native/native.risc/native.arm/js/kofc — `intention->Kof->frontend->IR->backend->runtime`**

## The vision

Kof is not just a language for the JVM. It is a language that can compile to different targets while keeping the same syntax and semantics.

```text
                         KOF
                          │
                    Kof Compiler (frontend → IR)
                          │
                       Kof IR  — intention->Kof->frontend->IR->backend->runtime
                          │
          ┌───────────────┼────────────────┬───────────┐
          │               │                │           │
       Kof4J          KofNative         KofJS      KofScript/KofC
          │          ┌────┼────┐          │           │
          ▼          ▼    ▼    ▼          ▼           ▼
        JVM       x86-64 riscv arm    ES Module   Globals/C-ELF
       .class      ELF   ELF  ELF      .mjs        repl/kof c
```

## The backends

### Kof4J (JVM)

The JVM backend generates `.class` bytecode that runs on any JVM.

```kf
record Point(Int x, Int y)
```

```bash
kof build point.kf --target=jvm
# Generates: Point.class
```

**Advantages:**
- Full compatibility with the Java ecosystem
- Access to millions of libraries
- JIT compilation
- Sophisticated garbage collection
- Portability (any JVM)

### KofNative (Native: x86-64 / riscv64 / aarch64)

The native backend generates x86-64 ELF (`native`), riscv64 (`native.risc`) and aarch64 (`native.arm`). x86-64 is stable: free-list GC (`kof_free_head`, `mmap` reuse; mark-sweep pending), `spawn`/`await` via pthread (31/08 — CONC001 closed), real XMM floating point (`FLT001` closed), full JSON (objects/records/arrays — JSN001/002/003 closed), native SQLite and MySQL in progress (wire protocol, SHA-1 scramble auth). riscv/arm are placeholders (codegen still x86_64, cross via `as`/`ld` + qemu).

```kf
main() = print("Hello, World!")
```

```bash
kof build main.kf --target=native
# Generates: main (ELF executable)
./main
# Output: Hello, World!
```

**Advantages:**
- No need for an installed JVM
- Standalone executable
- Native performance
- Simple distribution (just the binary)
- Ideal for CLI tools and systems

### KofScript (0.5.0-beta)

`kof script` / `kof repl` — top-level `var`/`val` become persistent `KofScriptGlobals` (there is no `let`/`const` — JS sugar removed 06/09), `--watch` re-executes; jvm/native/js targets.

```bash
kof run script.kf
# Runs directly without compiling
```

**Advantages:**
- No build step
- Immediate execution
- Ideal for automation and experiments

### KofJS (JS) + KofC (kofc)

KofJS generates ES Modules via GraalJS (`kof.http` JVM+JS, HTTP002 Native). KofC (`kof c <file.c>`) compiles a C subset (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`) → native-only x86-64 ELF.

```bash
kof build app.kf --target=js   # ES Module
kof script app.ks --watch      # KofScript
kof c app.c --run               # KofC native-only
```

**Advantages:**
- Same language for backend and frontend
- No need to learn JavaScript
- Access to the DOM and browser APIs

## How it works

### Compilation pipeline

```text
Kof Source (.kf)
    │
    ▼
  Lexer → tokens
    │
    ▼
  Parser → AST
    │
    ▼
  IR (shared, intention->Kof->frontend->IR->backend->runtime)
    │
    ├──────────► JVM Backend → .class
    │
    ├──────────► Native Backend → ELF (x86-64 free-list / riscv / arm)
    │
    ├──────────► JS Backend → .mjs
    │
    ├──────────► KofScript → Globals+IR→backend
    │
    └──────────► KofC → native-only ELF
```

### Shared IR

The intermediate representation (IR) is shared across all backends. This allows:

1. **Same language** — there are no dialects for different targets
2. **Same semantics** — the meaning of the code does not change
3. **Shared optimizations** — improvements to the IR benefit all backends
4. **Easy addition of new backends** — just implement the IR → target translation

### Multiplatform example

```kf
record Point(Int x, Int y)

main() {
    var p = Point(3, 7)
    println(p)
}
```

**JVM:**
```bash
kof build main.kf --target=jvm
java -cp . main
# Output: Point[x=3, y=7]
```

**Native:**
```bash
kof build main.kf --target=native
./main
# Output: Point[x=3, y=7]
```

**Same code. Same output. Different targets.**

## When to use each backend

| Backend | Use when | Examples |
|---------|----------|----------|
| **JVM** | You need the Java ecosystem, libraries, frameworks | Spring APIs, microservices, enterprise applications |
| **Native** | You need a standalone executable, no JVM | CLI tools, containers, systems, utilities |
| **Script** | You need fast execution, no build | Automation, scripts, prototyping |
| **JS** | You need a web frontend | Web interfaces, SPAs, PWAs |

## Current status

| Backend | Status | Description |
|---------|--------|-------------|
| **JVM** | ✅ Functional | Generates `.class` via ASM (V21 bytecode, exception table, virtual threads) |
| **Native** | ✅ Functional | Generates x86-64 ELF via assembly (free-list GC, spawn/pthread, FP XMM, SQLite); riscv/arm placeholders |
| **Script** | ✅ KofScript (var/val→Globals, repl, --watch) | Interactive runtime |
| **KofJS** | ✅ alpha | ES Modules via embedded GraalJS; `kof.http` through Java HttpClient interop |
| **KofC** | ✅ native-only | C subset → x86-64 ELF |

## Compiler architecture

```text
                    Kof Source (.kf)
                          │
                          ▼
                        Lexer
                          │
                          ▼
                        Parser
                          │
                          ▼
                         AST
                          │
                          ▼
                     Semantic Analysis
                          │
                          ▼
                     Kof IR (shared)
                      /       \
                     /         \
                    ▼           ▼
             JVM Backend    Native Backend
                    │           │
                    ▼           ▼
                .class       ELF .o
                    │           │
                    ▼           ▼
                javac/jar     ld → executable
```

## Documentation

- [KofNative Architecture](architecture.md) — details of the multiplatform architecture
- [Backend Options](backend-options.md) — analysis of the native backend options
- [Roadmap](roadmap.md) — development plan for the native backend

## Next step

[KofNative Architecture →](architecture.md)
