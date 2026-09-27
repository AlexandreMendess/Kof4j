[English](RUNTIME_ABI.md) | [Português](RUNTIME_ABI.pt_BR.md)

# RUNTIME_ABI.md — Kof Runtime Contract

**Date:** August 21, 2026
**Status:** Definition — Phase F

---

## 1. Overview

The Kof Runtime ABI defines the semantic contract between the Kof compiler and the runtime implementations (JVM and Native).

```
Kof Language
      ↓
  Kof IR
      ↓
Kof Runtime ABI
   ↙       ↘
JVM         Native
```

The ABI is NOT a bytecode or assembly specification. It is a behavioral contract that both implementations must satisfy.

---

## 2. Principles

1. **Platform independence** — the ABI does not assume endianness, word size, or calling convention
2. **Minimalism** — define only what is needed for the current language subset
3. **Evolvability** — new features can be added without breaking existing implementations
4. **Dual implementation** — each ABI feature must have a JVM and a Native implementation
5. **No JVM dependency** — the ABI does NOT reference java.lang.Object, java.lang.String, etc.

---

## 3. ABI Features

### 3.1 Allocation

| Operation | Description |
|----------|-----------|
| `kof_alloc(size)` | Allocates `size` bytes on the heap, returns a pointer |
| `kof_free(ptr)` | Frees memory allocated by kof_alloc |

**Contract:**
- `kof_alloc` returns a pointer aligned to 16 bytes
- `kof_alloc` returns NULL if memory is insufficient (treated as a runtime error)
- `kof_free` on a NULL pointer is a no-op
- `kof_free` on an already-freed pointer is undefined behavior (future: GC resolves it)

**JVM:** Delegates to `new` bytecode / JVM allocator
**Native:** Implementation via `malloc`/`free` or arena allocator

### 3.2 Object Model

Every Kof object has:

| Field | Size | Description |
|-------|---------|-----------|
| type_id | 4 bytes | Type identifier (index into the type table) |
| flags | 4 bytes | Object flags (mark bits, etc.) |
| fields... | variable | Field data in declaration order |

**Contract:**
- The type_id is determined at compile-time by the compiler
- The field layout is determined at compile-time by ClassLayout
- The object header is NOT accessible from Kof code
- Field access is via an offset computed at compile-time

**JVM:** The object header is managed by the JVM (klass pointer + mark word)
**Native:** The object header is part of the native kof-runtime

### 3.3 Field Layout

Each class has a field layout computed at compile-time:

```
FieldLayout:
  - className: String
  - fields: List<FieldInfo>
    - name: String
    - type: Type
    - offset: int (bytes from the start of the object)
    - size: int (size in bytes)
```

**Contract:**
- Fields are ordered in declaration order
- Each field has an offset and size determined by ClassLayout
- The compiler uses the ClassLayout to generate field access code
- NativeBackend consumes the ClassLayout (it does not compute offsets inline)

### 3.4 Strings

A Kof string is represented as (KofString):

```
KofString:
  - type_id: 4 bytes (= 1)
  - flags: 4 bytes (= 0)
  - length: int (4 bytes, UTF-8 byte length)
  - padding: 4 bytes
  - bytes: UTF-8 data (length bytes)
  - null terminator: 1 byte
```

**Contract:**
- Strings are immutable
- Encoding is UTF-8
- Length is byte length (not codepoint count)
- String literals are created via kof_string_from_literal
- `println` and `print` accept KofString
- `+` on strings produces concatenation (future: via kof_string_concat)
- `==` on strings produces equality (future: via kof_string_equals)

**Runtime Functions (Native):**

| Function | Signature | Description |
|--------|-----------|-----------|
| `kof_string_from_literal` | (data_ptr, byte_length) → str_ptr | Creates a KofString from a static literal |
| `kof_string_length` | (str_ptr) → int | Returns byte length |
| `kof_string_concat` | (str1, str2) → str3 | Concatenates two strings |
| `kof_string_equals` | (str1, str2) → bool | Compares content byte by byte |
| `kof_print_string` | (str_ptr) | Prints using the stored length |
| `kof_println_string` | (str_ptr) | Prints + newline |
| `kof_memcpy` | (dest, src, n) | Copies n bytes |

**JVM:** Delegates to java.lang.String
**Native:** Implementation via kof-runtime (NativeRuntime.java)

### 3.5 Arrays

A Kof array is represented as:

```
ArrayObject:
  - header (type_id, flags)
  - length: int (4 bytes)
  - element_size: int (4 bytes)
  - elements: bytes (length * element_size)
```

**Contract:**
- `array.length` returns the number of elements
- `array[i]` accesses the element at offset `header_size + i * element_size`
- Out-of-bounds access raises a runtime error
- Arrays of primitive types store values directly
- Arrays of reference types store pointers

**JVM:** Delegates to the JVM's native arrays
**Native:** Implementation via kof-runtime

### 3.6 Method Dispatch

| Type | Description | Mechanism |
|------|-----------|-----------|
| FUNCTION | Top-level function | Direct call (link-time) |
| STATIC | Static method | Direct call (link-time) |
| INSTANCE | Instance method | Direct call (future: virtual) |
| CONSTRUCTOR | Constructor | Direct call |

**Contract:**
- FUNCTION and STATIC are resolved at compile-time
- INSTANCE uses virtual dispatch via vtable (method in `method_table_ptr`)
- INTERFACE uses the same vtable (interface dispatch, Phase F.5)

**JVM:** The JVM handles it directly via vtable
**Native:** Direct call via `call ClassName_methodName`

### 3.7 Constructors

**Contract:**
1. Object allocation (`kof_alloc`)
2. Header initialization (type_id, flags)
3. Constructor call (`<init>`)
4. The constructor receives `this` as the first argument
5. The constructor can call `super.<init>()`

**JVM:** `NEW` + `DUP` + `INVOKESPECIAL <init>`
**Native:** `kof_alloc` + init header + `call ClassName_<init>`

### 3.8 Runtime Errors

| Error | Description | Behavior |
|------|-----------|---------------|
| `kof_null_error()` | NULL pointer access | Terminates with a message |
| `kof_bounds_error(i, len)` | Index out of bounds | Terminates with a message |
| `kof_panic(message)` | Generic error | Terminates with a message |
| `kof_alloc_error()` | Allocation failure | Terminates with a message |

**Contract:**
- Runtime errors are fatal (there is no recovery in this phase)
- Each error produces a descriptive message
- The process is terminated with exit code != 0

**JVM:** May use Java exceptions in the future
**Native:** Exit syscall with an error message

---

### 3.9 Primitive Erasure / Nullable Box ABI

`Nullable(primitive)` is a real value domain on every target —
`Absent | Present(T)`. Absence is **not** a stolen sentinel: `Absent` is a
distinct value from `Present(0)`, `Present(false)` and `Present(0.0)`.

| Target | Physical representation of `T?` |
|---|---|
| JVM | wrapper reference (`Integer`/`Boolean`/…) or `null` |
| Script | host value or `null` |
| JS | dynamic value or `null` |
| Native | `RuntimeErasureBox*` or pointer `0` |

The `T → T?` boundary is the shared `kof_box` call
(`CompilerEmissionHelpers.emitErasureBox`), never `Wrapper.valueOf` directly.
`Script`/`JS` lower `kof_box` to the identity, so the value stays a host
primitive there.

**Box layout (Native):** 24 bytes, filled by the x86-64 runtime
(`RuntimeErasureBox.java`); RISC-V mirrors it in `NativeRiscvAsmRtB49.java`,
AArch64 inherits through the cross translator.

```text
+0   MAGIC   0x4B4F46425F425801
+8   tag
+16  value
```

| tag | payload |
|---|---|
| 0 | Int / Char / Short / Byte |
| 2 | Long |
| 3 | Bool |
| 4 | Double |
| 5 | Float |

**Operations**

| Function | Contract |
|---|---|
| `kof_box_int` / `_long` / `_bool` / `_float` / `_double` | build a box from the raw primitive |
| `kof_unbox_<t>` (strict) | accepts only a valid box of the expected tag. A malformed box or tag mismatch is an **honest runtime error** — never `wrong type → default` |
| `kof_unbox_<t>_soft` | box → opens it; raw → passes through; `null` → usage error |
| `kof_box_equals(l, r)` | lifted **value** equality: `null == null` → `1`; `null` vs present → `0`; box vs box → payload (cross-tag Int/Long compare equal) |
| `kof_box_to_string` | understands tags 0, 2, 3, 4, 5; for a **non-box reference** it passes a Kof `String` through (type_id 1) and otherwise dispatches the object's own `toString` (see below) |

**Reference face (N2, 23/09):** a value typed `Object` may hold a boxed
primitive **or** a real reference (record/class). `kof_box_to_string` decides
by the same discriminator `kof_instanceof` uses — the 4-byte `type_id` at
offset 0:

- `null` → `"null"` (JVM `String.valueOf(null)` parity);
- `*(u64) == MAGIC` → primitive-box tag dispatch (table above);
- `type_id == 1` → already a Kof `String`, passed through;
- otherwise → `kof_tostring_table[type_id]` (a program-side `.quad` table
  emitted next to `kof_super_table`) is tail-called with the object as
  `this`; a `0` entry (class without `toString`) keeps the passthrough. No
  second ABI: the table only stores the addresses of the `toString`
  functions the class vtables already reference.

This is what makes `println(Point(1,2) as Object)` and
`var o: Object = Point(1,2); println(o)` print by content on Native instead
of an empty line (JVM oracle: `Point[x=1, y=2]`).

`Char?` carries no tag of its own — tag 0 is shared with Int/Short/Byte, so
`kof_box_to_string` alone cannot tell `Int(65)` from `Char('A')`. This needs no
second ABI: the lowering still knows the static type, and the `Char` face
unboxes at int width and formats as a character.

**JVM:** `kof_box`/`kof_unbox` are the wrapper `valueOf`/`xxxValue` pair and the
box is the JDK wrapper itself — the layout above is Native-only.

---

## 4. Calling Convention (Native)

NativeBackend uses the System V AMD64 ABI:

| Register | Use |
|-------------|-----|
| %rdi | 1st argument (this in instance methods) |
| %rsi | 2nd argument |
| %rdx | 3rd argument |
| %rcx | 4th argument |
| %r8 | 5th argument |
| %r9 | 6th argument |
| %rax | Return value |

**Contract:**
- `this` is passed as the first argument (%rdi)
- Return values in %rax
- Caller-save: %rax, %rcx, %rdx, %rsi, %rdi, %r8, %r9, %r10, %r11
- Callee-save: %rbx, %rbp, %r12, %r13, %r14, %r15

---

## 5. Type Table

The compiler generates a type table that maps type_id to metadata:

```
TypeTable:
  - types: Array<TypeEntry>
    - name: String (internal type name)
    - size: int (total object size in bytes)
    - field_count: int
    - fields: Array<FieldEntry>
```

**Contract:**
- type_id 0 is reserved for "unknown"
- type_id is unique per type
- The type table is generated by the compiler and embedded in the binary

**JVM:** Not needed (the JVM has reflection)
**Native:** Embedded in the `.data` section of the assembly

---

## 6. Architectural Decisions

### 6.1 Heap vs Stack
- **Decision:** Objects are allocated on the heap via `kof_alloc`
- **Reason:** Allows references, inheritance, GC mark-sweep (implemented 03/09, manual)
- **Exception:** Local primitive values remain on the stack

### 6.2 UTF-8 vs UTF-16
- **Decision:** Strings are UTF-8
- **Reason:** Compatibility with C/POSIX, lower memory usage
- **Trade-off:** Codepoint index operations are O(n)

### 6.3 String Immutability
- **Decision:** Strings are immutable
- **Reason:** Safety, hash consistency, interning
- **Trade-off:** Concatenation requires a new allocation

### 6.4 Direct Dispatch (for now)
- **Decision:** Method dispatch is direct (not virtual)
- **Reason:** Simplicity; the current subset does not need virtual dispatch
- **Future:** The object model allows adding a vtable later

### 6.5 Fatal Error Handling
- **Decision:** Runtime errors are fatal
- **Reason:** Simplicity; there is no try/catch in the language yet
- **Future:** Exceptions can be added with the same ABI

---

## 7. Boundary between Compiler and Runtime

| Responsibility | Compiler | Runtime |
|-----------------|----------|---------|
| Object size | Computes via ClassLayout | Uses the size |
| Field offset | Computes via FieldLayout | Uses the offset |
| Allocation | Generates kof_alloc call | Executes kof_alloc |
| Initialization | Generates <init> call | Executes constructor |
| Field access | Generates code with offset | — |
| Method call | Generates call with mangled name | — |
| Runtime error | — | Generates message and exits |

---

> **Updated (0.0.5):** virtual dispatch (vtable), real exceptions
> (JVM table + Native frame chain), generics by erasure and `spawn` (JVM)
> were implemented. Still out: GC (Native), reflection, serialization.
>
> **Updated (0.2.6-beta, 31/08):** `spawn`/`await` in Native was
> implemented (CONC001 closed — `pthread_create` + trampoline +
> `pthread_join` + thread-safe allocator with futex). Automatic GC on exhaustion
> ✅ landed 19/09 (D1-A, §260 CLOSED; mark-sweep implemented 03/09 + manual
> `kof_gc_collect_now`; free-list `kof_free_head` reuses `mmap`).

## 8. NOT included in this ABI

- Automatic garbage collection (Native — free-list `kof_free_head` with
  `mmap` reuse; memory returned to the OS on the `munmap` fallback/exit)
- Reflection
- Serialization
