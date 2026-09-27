[English](29-compiler-internals.md) | [Português](29-compiler-internals.pt_BR.md)

# 29 — Compiler Internals

> **Kof 0.5.0-beta — targets jvm/native/native.risc/native.arm/js/kofc — `intention->Kof->frontend->IR->backend->runtime`**

## Architecture

```
.kf source
  ↓ Lexer          (Lexer.java)
  ↓ Token stream
  ↓ Parser         (Parser.java)
  ↓ AST            (AstNodes.java)
  ↓ Type system    (Type.java)
  ↓ IR             (IRNodes.java)
  ↓ Optimizer      (Optimizer.java, always active)
  ↓ Backend        (JvmBackend.java or NativeBackend.java)
  ↓ Output         (.class or ELF)
```

Each stage has a clear responsibility.

The **IR optimizer** (`Optimizer.java`) runs on every build: constant
folding, branch simplification (constant conditions → direct jumps), dead
stack effects, unreachable code elimination (with try/catch regions
preserved), jump-to-next elimination and arithmetic identities. Debug
positions of the surviving ops are preserved. `kof inspect` exposes the
statistics (ops before/after).

## Lexer (Lexer.java)

**Responsibility**: convert text into tokens.

**Input**: string with the source code
**Output**: list of `Token`

The lexer is hand-written (not generated). Each character is analyzed sequentially.

Example tokens:
```
"record" → RECORD
"Point"  → IDENTIFIER
"("      → LPAREN
"Int"    → INT (type)
"x"      → IDENTIFIER
")"      → RPAREN
```

**File**: `kof-compiler/src/main/java/dev/kof/compiler/Lexer.java`

## Parser (Parser.java)

**Responsibility**: convert tokens into an AST (Abstract Syntax Tree).

**Input**: list of `Token`
**Output**: `CompilationUnitNode`

The parser is a recursive descent parser. Each grammar rule is a Java method.

Example:
```kf
record Point(Int x, Int y)
```

The parser recognizes:
- `record` → start of RecordDeclarationNode
- `Point` → name
- `(` → start of the components
- `Int x` → RecordComponentNode
- `,` → separator
- `Int y` → RecordComponentNode
- `)` → end of the components

**File**: `kof-compiler/src/main/java/dev/kof/compiler/Parser.java`

## AST (AstNodes.java)

**Responsibility**: represent the syntactic structure of the code.

The AST is a tree of nodes. Each node represents a language construct.

```
CompilationUnitNode
  └── RecordDeclarationNode
        ├── name: "Point"
        ├── components:
        │     ├── RecordComponentNode(type="Int", name="x")
        │     └── RecordComponentNode(type="Int", name="y")
        └── members: []
```

**File**: `kof-compiler/src/main/java/dev/kof/compiler/AstNodes.java`

## IR (IRNodes.java)

**Responsibility**: intermediate representation suitable for code generation.

The IR is a lower-level representation than the AST. Each IR operation maps directly to one or a few low-level instructions.

```
IRClass(name="Point", superName="java/lang/Record")
  ├── IRField(name="x", descriptor="I")
  ├── IRField(name="y", descriptor="I")
  ├── IRMethod(name="<init>", descriptor="(II)V")
  │     └── IRBasicBlock:
  │           ├── LoadLocal("LPoint;", 0)
  │           ├── InvokeSpecial("java/lang/Record", "<init>", "()V")
  │           ├── LoadLocal("LPoint;", 0)
  │           ├── LoadLocal("I", 1)
  │           ├── PutField("Point", "x", "I")
  │           └── ReturnVoid
  └── IRMethod(name="x", descriptor="()I")
        └── IRBasicBlock:
              ├── LoadLocal("LPoint;", 0)
              ├── GetField("Point", "x", "I")
              └── Return("I")
```

**File**: `kof-compiler/src/main/java/dev/kof/compiler/IRNodes.java`

## JVM Backend (JvmBackend.java)

**Responsibility**: convert IR into JVM bytecode using ASM.

The backend uses the ASM library to generate valid `.class` files.

For each `IRMethod`, it:
1. Creates a `MethodVisitor`
2. Visits each IR operation
3. Emits the corresponding bytecode instruction

**File**: `kof-compiler/src/main/java/dev/kof/compiler/JvmBackend.java`

## Native Backend (NativeBackend.java)

**Responsibility**: convert IR into native x86-64 code.

The backend generates x86-64 assembly, which is assembled and linked to create an ELF executable.

Pipeline:
1. IR → x86-64 Assembly
2. Assembly → Object (via `as`)
3. Object → Executable (via `ld`)

**File**: `kof-compiler/src/main/java/dev/kof/compiler/NativeBackend.java`

## Backend Interface (Backend.java)

**Responsibility**: abstract different code generation backends.

```java
interface Backend {
    void emit(Object irModule, Path outputDir) throws IOException;
}
```

This lets the compiler support multiple targets without coupling.

**File**: `kof-compiler/src/main/java/dev/kof/compiler/Backend.java`

## Target Enum (Target.java)

**Responsibility**: identify the compilation target (Target separation 0.2.0: `NATIVE` vs `NATIVE_RISCV64`/`NATIVE_AARCH64`, `JS`, `ANDROID`, `KofC` separate).

```java
enum Target {
    JVM,
    NATIVE,           // x86-64
    NATIVE_RISCV64,   // riscv64 (--target=native.risc)
    NATIVE_AARCH64,   // aarch64 (--target=native.arm)
    JS,
    ANDROID,
    // kofc = KofCCompiler (C subset → native-only x86-64 ELF, separate)
}
```

**File**: `kof-compiler/src/main/java/dev/kof/compiler/Target.java`

## CompilerDriver (CompilerDriver.java)

**Responsibility**: orchestrate the whole pipeline.

```
compile(sourceFile, outputDir, target):
  1. Read file
  2. Lexer → tokens
  3. Parser → AST
  4. Lowering → IR
  5. Select backend based on the target
  6. Backend → output
  7. Write file
```

**File**: `kof-compiler/src/main/java/dev/kof/compiler/CompilerDriver.java`

## Diagnostics (Diagnostic.java, DiagnosticCollector.java)

**Responsibility**: collect and report errors.

Errors point to the original position in the `.kf` file:

```
error: type mismatch
  --> src/main/kf/User.kf:12:5
   |
12 |     name = 42
   |     ^^^^ expected String, found Int
```

**Files**: `Diagnostic.java`, `DiagnosticCollector.java`

## Current state of the compiler

| Component | Status |
|-----------|--------|
| Lexer | ✅ Complete (55+ keywords, `String?`; NO `let`/`const` — JS sugar removed from KofScript 06/09) |
| Parser | ✅ Functional (records, classes, interfaces, functions, `case String s`, `Point(x,y)`, `String?`) |
| AST | ✅ Complete for supported constructs |
| Type system | ✅ `String?` nullable, `List<T>` inference, imports `a.b.C` fixed |
| Symbol table | ⚠️ Defined but not fully used |
| IR | ✅ Defined, functional lowering (`intention->Kof->frontend->IR->backend->runtime`) |
| Optimizer | ✅ Passes always active (constant folding, dead code, branch simplification) |
| JVM Backend | ✅ Functional (via ASM, bytecode V21, exception table, virtual threads; ) |
| Native Backend | ✅ Functional (x86-64 free-list GC + spawn/pthread + FP XMM; riscv/arm placeholders via qemu) |
| Diagnostics | ✅ Functional |
| KofScript (`KofScriptGlobals`) | ✅ top-level `var`/`val` → `KofScriptGlobals`, repl, --watch (no `let`/`const`) |
| KofC (`KofCCompiler`) | ✅ C subset → native-only ELF (`kof c`) |
| CLI | ✅ Functional (26 commands: build, run, serve, check, test, script, repl, c, fmt, config gen, bench, profile, inspect, decompile, translate, compare, migrate, debug, info, lsp, install, deps, editor, new, init, version) |

## Multiplatform architecture

The compiler architecture was designed to support multiple backends:

```text
                    Kof Source (.kf / .ks / .c)
                          │
                          ▼
                        Lexer (String?; no let/const — KofScript is pure Kof)
                          │
                          ▼
                        Parser (case String s, Point(x,y))
                          │
                          ▼
                         AST (+ KofScriptGlobals, KofC AST)
                          │
                          ▼
                     Kof IR (shared) — intention->Kof->frontend->IR->backend->runtime
                      /       |       \      \
                     /        |        \      \
                    ▼         ▼         ▼       ▼
             JVM Backend  Native(x86/riscv/arm)  JS   KofC
                    │           │               │     │
                    ▼           ▼               ▼     ▼
                .class       ELF .o          .mjs   ELF
                    │           │               │     │
                    ▼           ▼               ▼     ▼
                javac/jar     ld → executable  GraalJS  ld
```

The shared IR lets the same code be compiled to different targets without modifications.

## Next step

[Contributing →](30-contributing.md)
