[English](22-jvm.md) | [Português](22-jvm.pt_BR.md)

# 22 — JVM

> **Kof 0.5.0-beta — `intention->Kof->frontend->IR->backend->runtime`**

## What the compiler generates

The JVM backend (`JvmBackend`, via ASM) generates **V21 bytecode** (Tooling API
Level 21):

- **real exception table** — `try/catch/finally` with handlers in the `.class`
  (not hand-thrown exceptions);
- **virtual threads** — `spawn` uses virtual threads; the program waits for the
  tasks (implicit join);
- `SourceFile` + `LineNumberTable` (and `LocalVariableTable` when there is
  debug metadata) — `kof debug` consumes this (DAP MVP, JVM target).

## How Kof runs (JVM is one of the backends — see Target separation `native.risc/arm` in ch. 31)

```
You write:     record Point(Int x, Int y)
                      ↓
Kof compiler:  lexer → parser → AST → IR → bytecode
                      ↓
JVM receives:  Point.class
                      ↓
Class Loader:  loads Point.class into memory
                      ↓
Bytecode Verifier: verifies that the bytecode is safe
                      ↓
JIT Compiler:  converts bytecode to native machine code
                      ↓
Execution:     runs like any Java program
```

## Bytecode

Bytecode is the intermediate representation of the program. It is what the compiler generates and the JVM executes.

Each bytecode instruction is very simple:

```
aload_0      → loads the "this" reference
iload_1      → loads the integer from parameter 1
putfield     → stores a value in a field
invokevirtual → calls a method
```

One line of Kof can generate several bytecode instructions.

## Class Loading

When the JVM finds `Point.class`:

1. **Loading**: reads the `.class` file and creates an internal representation
2. **Linking**: verifies integrity, allocates memory for constants
3. **Initialization**: runs static initializers (if they exist)

## Bytecode Verification

Before executing, the JVM verifies:
- the types are correct
- the instructions are valid
- the stack does not overflow
- the jumps point to valid positions

If verification fails, the program does not run.

## JIT (Just-In-Time) Compiler

The JVM does not execute bytecode directly. It compiles to native machine code at runtime.

- Methods that run little: execute as bytecode
- Methods that run a lot (hot): compiled to native
- The JIT optimizes based on real profiling

This means Kof code can be as fast as C++ code after warmup.

## Garbage Collection

Kof does not need manual memory management. The JVM automatically collects objects that are no longer referenced.

## Memory Model

Kof respects the Java Memory Model:
- `volatile` guarantees visibility between threads
- `synchronized` guarantees atomicity
- Happens-before relationship is preserved

## Next step

[Testing →](23-testing.md)
