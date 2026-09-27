[English](backend-options.md) | [Português](backend-options.pt_BR.md)

# Native Backend Options

> **0.5.0-beta — Native free-list GC, Target separation (native.risc/arm), kof_db SQLite+MySQL**

## Current status

The native backend is already implemented and functional (0.5.0-beta). It generates x86-64 / riscv64 / aarch64 assembly directly (Target separation), with free-list GC on x86-64 and `kof_db` MySQL WIP, without using LLVM or other external libraries.

## Implemented approach: Direct assembly

### What it is

Generating x86-64 code and ELF files by hand in Java, with no external dependencies.

### How it works

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

### Advantages

- **Zero dependencies** — no need for LLVM, JavaCPP or any library
- **Total control** — we know exactly what is being generated
- **Simplicity** — x86-64 assembly is relatively simple for basic operations
- **Portability** — works on any Linux x86-64

### Disadvantages

- **No optimizations** — we have no register allocation, instruction scheduling, etc.
- **Hand-written code** — every instruction must be implemented by hand
- **Maintenance** — adding new features requires manual work

### What we get for free

- Nothing from a library

### What we need to implement

- ~50+ x86-64 instructions (already implemented)
- System V AMD64 calling convention (already implemented)
- String/data sections (already implemented)
- Linux syscalls (already implemented)

## Evaluated options (history)

### 1. LLVM via JavaCPP (rejected)

**What it is:** Java wrapper for the LLVM C API using JavaCPP.

**Why rejected:**
- ~100MB dependency
- Integration complexity
- The project wanted zero dependencies

### 2. Cranelift (rejected)

**What it is:** Code generation framework in Rust.

**Why rejected:**
- There is no mature Java API
- Requires a Java → Rust binding

### 3. Compile to C (rejected)

**What it is:** Generate C code, use GCC/Clang as the backend.

**Why rejected:**
- It would be a transpiler, not a native compiler
- It does not allow fine control of the generated code

### 4. System Backend (chosen)

**What it is:** Generate x86-64 code and ELF files by hand in Java.

**Why chosen:**
- Zero dependencies
- Total control
- Incremental implementation possible
- Works for basic operations

## Comparison matrix (history)

| Criterion | LLVM/JavaCPP | Cranelift | System ELF | C transpiler |
|----------|:---:|:---:|:---:|:---:|
| Java integration | 6 | 2 | 5 | 9 |
| Performance | 10 | 7 | 5 | 10 |
| Optimizations | 10 | 7 | 2 | 10 |
| Dependencies | 6 | 8 | 10 | 5 |
| Maintenance | 7 | 3 | 3 | 8 |
| **Weighted** | **7.6** | **4.4** | **5.0** | — |

## Final decision

**System Backend (direct assembly)** is the right choice for the first native backend.

**Main reason:** we want to prove that Kof can generate native code. Direct assembly minimizes dependencies and maximizes control.

**When to reconsider:**
- If we need complex optimizations → evaluate LLVM
- If we need support for multiple architectures → evaluate LLVM
- If the hand-written code becomes unmanageable → evaluate LLVM

## What we need to implement

### Already implemented

1. **Basic instructions** — mov, add, sub, mul, div, cmp (+ FP XMM: `vcvtsi2sd`, `mulsd` — FLT001 closed)
2. **Function calls** — System V AMD64 calling convention
3. **Strings** — string literals and operations
4. **Records** — structs with fields
5. **Functions** — declaration and call
6. **Linux syscalls** — write, exit, open, read, close...
7. **Control flow, classes (inheritance/dispatch), exceptions (unwinding), generics (erasure)**
8. **`spawn`/`await` via pthread** (31/08 — CONC001 closed), thread-safe allocator (futex)
9. **Full JSON** (objects/records/arrays — JSN001/002/003 closed) + **native SQLite**

### In development

1. **Native MySQL/MariaDB** — wire protocol over sockets (SHA-1 scramble auth done)
2. **GC mark-sweep** — today free-list `kof_free_head`
3. **riscv64/aarch64** — codegen still x86_64 (placeholders via qemu)

## Implementation example

### Assembly generation for Hello World

```kf
main() = print("Hello, World!")
```

```asm
.section .data
hello: .asciz "Hello, World!\n"

.section .text
.globl _start
_start:
    # write(1, hello, 14)
    mov $1, %rax
    mov $1, %rdi
    lea hello(%rip), %rsi
    mov $14, %rdx
    syscall
    
    # exit(0)
    mov $60, %rax
    xor %rdi, %rdi
    syscall
```

### Assembly generation for records

```kf
record Point(Int x, Int y)
```

```asm
.section .text
.globl Point_init
Point_init:
    # Point(x, y)
    mov %rdi, 8(%rax)
    mov %rsi, 16(%rax)
    ret

.globl Point_x
Point_x:
    mov 8(%rax), %rax
    ret

.globl Point_y
Point_y:
    mov 16(%rax), %rax
    ret
```

## Dependencies

### Native runtime (minimal)

The native backend requires a minimal runtime in C:

- `kof_alloc.c` — arena allocator
- `kof_string.c` — string operations
- `kof_io.c` — print, read
- `kof_runtime.c` — initialization

Compiled as a static `.a`, linked by `ld`.

## Next step

[Roadmap →](roadmap.md)
