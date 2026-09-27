[English](architecture.md) | [Português](architecture.pt_BR.md)

# KofNative Architecture

> **Kof 0.5.0-beta — Target separation + free-list GC + kof_db MySQL**

## Overview

KofNative is the extension of the Kof compiler to generate native Linux x86-64 binaries. It does not replace the JVM backend — it works in parallel.

```
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
                    Kof IR (shared) — intention->Kof->frontend->IR->backend->runtime
                     /       |       \
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

## Components of the current compiler

### Backend-agnostic (reusable without change)

| Component | File | Status |
|------------|---------|--------|
| Token | `Token.java` | ✅ Reusable |
| TokenType | `TokenType.java` | ✅ Reusable |
| Lexer | `Lexer.java` | ✅ Reusable |
| AST | `AstNodes.java` | ✅ Reusable |
| Parser | `Parser.java` | ✅ Reusable |
| SourcePosition | `SourcePosition.java` | ✅ Reusable |
| Diagnostic | `Diagnostic.java` | ✅ Reusable |
| DiagnosticCollector | `DiagnosticCollector.java` | ✅ Reusable |
| CompilationResult | `CompilationResult.java` | ✅ Reusable |
| Type | `Type.java` | ✅ Reusable |
| SymbolTable | `SymbolTable.java` | ✅ Reusable |

### Backend-coupled (specific to each target)

| Component | File | Target | Status |
|------------|---------|--------|--------|
| Backend | `Backend.java` | Both | ✅ Common interface |
| Target | `Target.java` | All | ✅ Enum `JVM/NATIVE/NATIVE_RISCV64/NATIVE_AARCH64/JS/ANDROID` + `isNative()`/`nativeArch()` + `parseTarget native.risc/arm` |
| CompilerDriver | `CompilerDriver.java` | Both | ✅ Parameterized orchestrator |
| JvmBackend | `JvmBackend.java` | JVM | ✅ Functional via ASM |
| NativeBackend | `NativeBackend.java` | Native | ✅ Functional via assembly |

## The compilation pipeline

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
  IR (shared)
    │
    ├──────────► JVM Backend → .class
    │
    └──────────► Native Backend → ELF
```

## Where NativeBackend plugs in

```
CompilerDriver.compileSources(sources, outputDir, target, moduleRoot)
  │
  ├─── target == JVM ──→ lowerToIR() → JvmBackend.emit()
  │
  └─── target == NATIVE → lowerToIR() → NativeBackend.emit()
```

### Changes implemented in CompilerDriver

1. **`target` parameter** in the compile entry point (`compileSources`)
2. **`Backend` interface** for decoupling
3. **Target-based selection** — `new JvmBackend()` or `new NativeBackend()`
4. **Separate helpers** — `toDescriptor()`, `toInternalName()`, `computeAccess()` stay in the backends

### Current structure

```java
// Backend.java (interface)
interface Backend {
    void emit(Object irModule, Path outputDir) throws IOException;
}

// CompilerDriver.java (modified)
public CompilationResult compile(Path sourceFile, Path outputDir, Target target) {
    // lexer, parser, AST — unchanged
    // ...
    
    Backend backend = switch (target) {
        case JVM -> new JvmBackend();
        case NATIVE -> new NativeBackend();
    };
    
    var ir = lowerToIR(unit, diagnostics);
    backend.emit(ir, outputDir);
}
```

## The current native backend

The native backend generates x86-64 assembly, which is assembled and linked:

```text
Kof IR
    │
    ▼
NativeBackend.emit()
    │
    ▼
x86-64 Assembly (.s)
    │
    ▼
as → Object (.o)
    │
    ▼
ld → Executable (ELF)
```

### Example of generated assembly

```kf
main() = print("Hello")
```

```asm
.section .data
hello: .asciz "Hello"

.section .text
.globl _start
_start:
    mov $1, %rax
    mov $1, %rdi
    lea hello(%rip), %rsi
    mov $5, %rdx
    syscall
    
    mov $60, %rax
    xor %rdi, %rdi
    syscall
```

### Calling convention

The native backend uses the System V AMD64 ABI:

| Parameter | Register |
|----------|-------------|
| 1st Int/Long | %rdi |
| 2nd Int/Long | %rsi |
| 3rd Int/Long | %rdx |
| 4th Int/Long | %rcx |
| 5th Int/Long | %r8 |
| 6th Int/Long | %r9 |
| Float/Double | %xmm0-%xmm7 |
| Return | %rax (Int/Long), %xmm0 (Float/Double) |

## Risks

| Risk | Impact | Mitigation |
|-------|---------|-----------|
| Complex hand-written assembly | High | Implement incrementally |
| Incorrect calling convention | High | Exhaustive tests |
| Native strings different from Java | Medium | Minimal runtime in C |
| Native GC needs to be implemented | High | Start with an arena allocator |
| Linux x86-64 ABI must be correct | High | Use direct Linux syscalls |

## Feature checklist

- [x] Functional lexer
- [x] Functional parser
- [x] IR defined
- [x] Functional native backend
- [x] CLI with --target=native
- [x] Records generate structs (free-list GC in 0.2.0)
- [x] main functions work
- [x] Strings work
- [x] println works
- [x] Control flow
- [x] Classes with inheritance (virtual dispatch; `super.metodo()` = SUP001)
- [x] Exceptions (try/catch/finally + unwinding)
- [x] Generics (erasure; `Box<T>` with primitive/Boxed `T`)
- [x] `spawn`/`await` via pthread (31/08 — CONC001 closed)
- [x] Real XMM floating point (`vcvtsi2sd`/`mulsd`, dtoa via snprintf — FLT001/JSN001 closed)
- [x] JSON objects/records + complete arrays (JSN002/JSN003/JSN001 closed)
- [x] Native SQLite (direct link to the `.so`)
- [ ] Native MySQL/MariaDB (wire protocol: SHA-1 scramble auth done; WIP)
- [ ] GC mark-sweep (today free-list `kof_free_head`)
- [ ] riscv64/aarch64 (codegen still x86_64 — placeholder via qemu)

## Next step

[Backend Options →](backend-options.md)
