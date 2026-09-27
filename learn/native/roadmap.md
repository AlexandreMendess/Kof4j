[English](roadmap.md) | [Português](roadmap.pt_BR.md)

# KofNative Roadmap

> **0.5.0-beta — Sep 2026 — free-list GC done, Target separation done, MySQL via kof_db WIP**

## Principles

1. **Don't break Kof4J** — every change must be validated against the JVM backend
2. **Incremental** — each milestone is small, testable, rollbackable
3. **Correct first** — don't optimize before it works
4. **Documented** — each milestone has documentation and tests

## Current status

The native backend is already functional. Here is what has already been implemented:

### ✅ Completed

- **Milestone 0 — Baseline**: All JVM tests passing
- **Milestone 1 — Target Abstraction**: `Target` enum, `Backend` interface, CLI with `--target`
- **Milestone 2 — Native Backend Skeleton**: NativeBackend structure
- **Milestone 3 — Native Hello World**: x86-64 ELF that prints "Hello World"
- **Milestone 4 — Primitive Values**: Int, Long, Float, Double, Bool, Char
- **Milestone 5 — Functions**: Function declaration and call
- **Milestone 6 — Strings**: String literals and basic operations
- **Milestone 7 — Control Flow**: If/else, while, for ✅
- **Milestone 8 — Arrays**: Native arrays ✅
- **Milestone 9 — Value Types / Structs**: Records as native structs ✅
- **Milestone 10 — Objects**: Classes with inheritance and dispatch ✅
- **Milestone 11 — Exceptions**: Native try/catch (unwinding) ✅
- **Milestone 12 — Generics**: Erasure (like JVM; `Box<T>` with primitive `T`) ✅
- **Milestone 13 — Target separation**: `NATIVE_RISCV64/AARCH64` + `parseTarget native.risc/arm` ✅
- **Milestone 14 — Free-list GC**: `kof_free_head` `mmap` reuse ✅
- **Milestone 15 — kof_db**: SQLite via `.so` ✅
- **Milestone 16 — `spawn`/`await` via pthread** (31/08 — CONC001 closed): trampoline + implicit join + thread-safe allocator (futex) ✅
- **Milestone 17 — Real FP (XMM)** (31/08 — FLT001/JSN001 closed): `vcvtsi2sd`/`mulsd`, dtoa via snprintf ✅
- **Milestone 18 — Full JSON**: objects/records + arrays (JSN002/JSN003/JSN001 closed) ✅

### 🔄 In development

- **Native MySQL/MariaDB**: wire protocol over sockets (SHA-1 scramble auth done; full handshake, query and prepared statements still missing)
- **GC mark-sweep**: pending (memory returned today only on the `munmap` fallback)
- **riscv64/aarch64**: codegen still x86_64 (placeholders via qemu)

## Detailed milestones

### Milestone 0 — Baseline ✅

**Objective:** Ensure everything works before modifying.

**Actions:**
- [x] Run all existing tests
- [x] Compile all examples
- [x] Validate records (x()=3, y()=7)
- [x] Record the current state as baseline
- [x] Create branch `feature/native`

**Success criterion:** zero regressions, clean build.

---

### Milestone 1 — Target Abstraction ✅

**Objective:** Introduce the minimal abstraction to distinguish JVM/Native.

**Changes:**
- [x] Create `Target { JVM, NATIVE }` enum
- [x] Create `Backend` interface
- [x] Parameterize `CompilerDriver.compile()` with target
- [x] Default remains JVM

**New files:**
- `Target.java`
- `Backend.java`

**Modified files:**
- `CompilerDriver.java` — parameterize compile(), extract interface
- `Main.java` (CLI) — add `--target` flag

**Tests:**
- [x] All JVM tests pass (regression)
- [x] `--target jvm` generates the same output as before
- [x] `--target native` generates an executable

**Success criterion:** zero regressions, functional target flag.

---

### Milestone 2 — Native Backend Skeleton ✅

**Objective:** Create the NativeBackend structure without generating code.

**Changes:**
- [x] Create `NativeBackend implements Backend`
- [x] Implement empty `emit()`
- [x] NativeBackend.emit() returns "not yet implemented" error

**New files:**
- `NativeBackend.java`

**Tests:**
- [x] JVM keeps working
- [x] NativeBackend accepts IR and returns a clear error
- [x] CLI `--target native` shows an appropriate message

**Success criterion:** validated architecture, zero regressions.

---

### Milestone 3 — Native Hello World ✅

**Objective:** Generate an x86-64 ELF that prints "Hello World".

**Requirements:**
- [x] Generate x86-64 assembly
- [x] Assemble with `as`
- [x] Link with `ld`
- [x] Generate a valid ELF

**Input:**
```kof
main() = print("Hello World")
```

**Output:**
```
$ ./hello
Hello World
```

**Modified files:**
- `NativeBackend.java` — implement emission via assembly

**Tests:**
- [x] JVM keeps working
- [x] Native generates a valid ELF
- [x] Executable runs and prints "Hello World"

**Success criterion:** native hello world without JVM.

---

### Milestone 4 — Primitive Values ✅

**Objective:** Support native primitive values.

**Features:**
- [x] Int (32-bit)
- [x] Long (64-bit)
- [x] Float (32-bit)
- [x] Double (64-bit)
- [x] Bool (1-bit, extended to i32)
- [x] Char (16-bit)

**Example:**
```kof
main() {
    var x = 42
    var y = 3.14
    var z = true
}
```

**Type mapping:**
| Kof | x86-64 |
|-----|--------|
| Int | %edi, %esi, etc. |
| Long | %rdi, %rsi, etc. |
| Float | %xmm0, %xmm1, etc. |
| Double | %xmm0, %xmm1, etc. |
| Bool | zero-extended to i32 |

**Tests:**
- [x] Parser: types recognized
- [x] IR: operations with correct types
- [x] Native: values passed correctly
- [x] JVM: zero regression

**Success criterion:** primitives work on both backends.

---

### Milestone 5 — Functions ✅

**Objective:** Support function declaration and call.

**Features:**
- [x] Functions with return
- [x] Parameters
- [x] Function call
- [x] System V AMD64 call convention

**Example:**
```kof
add(Int a, Int b): Int {
    return a + b
}

main() {
    var result = add(3, 4)
    print(result)
}
```

**Calling convention mapping:**
| Parameter | Register |
|----------|----------|
| 1st Int/Long | %rdi |
| 2nd Int/Long | %rsi |
| 3rd Int/Long | %rdx |
| 4th Int/Long | %rcx |
| 5th Int/Long | %r8 |
| 6th Int/Long | %r9 |
| Float/Double | %xmm0-%xmm7 |
| Return | %rax (Int/Long), %xmm0 (Float/Double) |

**Tests:**
- [x] Functions with 0, 1, 2, 3+ parameters
- [x] Return of all types
- [x] Nested calls
- [x] JVM regression

**Success criterion:** native functions work.

---

### Milestone 6 — Strings ✅

**Objective:** Support native strings.

**Design decision:** native strings are different from java.lang.String.

**Representation:**
```
struct String {
    i64 length;
    i8* data;      // UTF-8
}
```

**Minimal runtime:**
- `kof_string_create(const char* data, i64 length)` — allocates a string
- `kof_string_print(String* s)` — prints
- `kof_string_concat(String* a, String* b)` — concatenates

**Example:**
```kof
main() {
    var name = "World"
    print("Hello, " + name)
}
```

**Tests:**
- [x] String literal → String object
- [x] Concatenation
- [x] Print
- [x] JVM regression

**Success criterion:** strings work natively.

---

### Milestone 7 — Control Flow ✅

**Objective:** Support if/else, while, for.

**Features:**
- [x] If/else with branching
- [x] While loop
- [x] For loop
- [x] Comparisons (including long/float/double)

**Example:**
```kof
main() {
    var i = 0
    while (i < 10) {
        print(i)
        i = i + 1
    }
}
```

**Success criterion:** functional native control flow.

---

### Milestone 8 — Arrays ✅

**Objective:** Support native arrays.

**Representation:**
```
struct Array {
    i64 length;
    i8* data;      // raw data
}
```

**Tests:**
- [x] Array creation
- [x] Access by index
- [x] Array length
- [x] JVM regression

---

### Milestone 9 — Value Types / Structs ✅

**Objective:** Records as native structs.

**Representation:**
```kof
record Point(Int x, Int y)
```

Generates:
```
struct Point {
    i32 x;
    i32 y;
}
```

**Allocation:**
- Stack allocation for known sizes
- Heap allocation via runtime

**Tests:**
- [x] Struct creation
- [x] Field access
- [x] Pass by value
- [x] JVM regression

---

### Milestone 10 — Objects ✅

**Objective:** Classes with inheritance and dispatch.

**Features:**
- [x] Object layout + virtual dispatch
- [x] Field access
- [x] Constructors
- [ ] `super.metodo()` against classpath classes (SUP001)

**Example:**
```kof
class Animal(String nome) {
    falar(): String {
        return nome
    }
}

class Cachorro(String raca) extends Animal {
    override falar(): String {
        return nome + " late"
    }
}
```

**Dispatch:** vtable for virtual dispatch.

**Success criterion:** functional native polymorphism.

---

### Milestone 11 — Exceptions ✅

**Objective:** Support native try/catch.

**Mechanism:** hand-written implementation (no LLVM) — custom unwinding,
`try/catch/finally` with cleanup.

**Tests:**
- [x] Throw/catch
- [x] Finally
- [x] Stack unwinding
- [x] JVM regression

---

### Milestone 12 — Generics ✅

**Objective:** Support native generics.

**Strategy:** erasure (identical to the JVM), with primitive/Boxed `T`
substituted at compile-time (`Box<Int>` → `Int`).

```kof
class Box<T>(T value) {
    get(): T = value
}
```

**Tests:**
- [x] Basic generic types
- [x] Multiple instantiations
- [x] `Box<T>` with primitive `T` (`Box<Int>` + native `println`)
- [x] JVM regression

---

## Dependencies

### Native runtime (minimal)

`kof-runtime` module with:
- `kof_alloc.c` — arena allocator
- `kof_string.c` — string operations
- `kof_io.c` — print, read
- `kof_runtime.c` — initialization

Compiled as a static `.a`, linked by `ld`.

## Test plan

```
tests/
├── jvm/
│   ├── records/        ← existing tests
│   ├── classes/
│   └── regression/     ← ALL must pass
├── native/
│   ├── hello/
│   ├── primitives/
│   ├── functions/
│   ├── strings/
│   ├── control-flow/
│   ├── arrays/
│   ├── structs/
│   ├── objects/
│   ├── exceptions/
│   └── generics/
└── regression/
    ├── jvm-and-native/ ← tests that validate both
    └── jvm-only/       ← JVM-specific tests
```

**Rule:** every change to the type system or AST runs JVM and Native tests.

---

## Risks and mitigations

| Risk | Milestone | Mitigation |
|-------|-----------|-----------|
| Complex hand-written assembly | 7+ | Implement incrementally |
| Incorrect calling convention | 5 | Exhaustive tests with many parameters |
| Native strings different from Java | 6 | Document clearly, don't mix |
| GC needs to be implemented | 9+ | Start with arena, evolve to tracing GC |
| Complex native exceptions | 11 | Hand-written implementation, don't use LLVM |
| JVM regression | All | Mandatory regression tests |

---

## Estimated timeline

| Milestone | Status | Effort |
|-----------|--------|---------|
| 0 — Baseline | ✅ Completed | 0.5 day |
| 1 — Target Abstraction | ✅ Completed | 1 day |
| 2 — Backend Skeleton | ✅ Completed | 1 day |
| 3 — Hello World | ✅ Completed | 3-5 days |
| 4 — Primitives | ✅ Completed | 2-3 days |
| 5 — Functions | ✅ Completed | 3-5 days |
| 6 — Strings | ✅ Completed | 3-5 days |
| 7 — Control Flow | ✅ Completed | 3-5 days |
| 8 — Arrays | ✅ Completed | 2-3 days |
| 9 — Value Types | ✅ Completed | 5-7 days |
| 10 — Objects | ✅ Completed | 7-10 days |
| 11 — Exceptions | ✅ Completed | 5-7 days |
| 12 — Generics | ✅ Completed | 5-7 days |
| 13 — Target separation | ✅ Completed | — |
| 14 — Free-list GC | ✅ Completed | — |
| 15 — kof_db SQLite | ✅ Completed | — |
| 16 — spawn/await pthread | ✅ Completed (31/08) | — |
| 17 — Real FP (XMM) | ✅ Completed (31/08) | — |
| 18 — Full JSON | ✅ Completed (31/08) | — |

**In development:** full native MySQL, GC mark-sweep, riscv64/aarch64 (codegen).

---

## Next step

[KofNative Architecture →](architecture.md)
