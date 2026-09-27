[English](OBJECT_MODEL.md) | [Português](OBJECT_MODEL.pt_BR.md)

# OBJECT_MODEL.md — Kof Object Model

**Date:** August 21, 2026
**Status:** Definition — Phase F

---

## 1. Overview

The Kof object model defines how objects are represented in memory, both for the JVM and for Native.

---

## 2. Object Layout

### 2.1 Generic Object

```
+-------------------+
| type_id (4 bytes) |  ← type identifier
+-------------------+
| flags (4 bytes)   |  ← mark bits, GC flags
+-------------------+
| field_0           |  ← first field (size varies)
+-------------------+
| field_1           |
+-------------------+
| ...               |
+-------------------+
| field_n           |  ← last field
+-------------------+
```

- **Header size:** 8 bytes (type_id + flags)
- **Alignment:** 16 bytes total (header + fields padding)
- **Field order:** Declaration order in the source code

### 2.2 Flags

| Bit | Name | Description |
|-----|------|-----------|
| 0 | MARKED | Used by future GC |
| 1 | PINNED | Cannot be moved |
| 2-31 | Reserved | For future use |

---

## 3. Primitive Types

Primitive types are NOT objects. They are direct values on the stack:

| Type | Size | Representation |
|------|---------|---------------|
| bool | 4 bytes | 0 or 1 |
| byte | 1 byte | signed |
| short | 2 bytes | signed |
| int | 4 bytes | signed |
| long | 8 bytes | signed |
| float | 4 bytes | IEEE 754 |
| double | 8 bytes | IEEE 754 |
| char | 4 bytes | UTF-32 codepoint |

**Note:** On the stack, all values are treated as 64-bit slots for alignment.

---

## 4. Reference Types

### 4.1 Record

Records are immutable and have user-defined fields:

```kf
record Point(Int x, Int y)
```

**Layout:**
```
+-------------------+
| type_id           |  → Point
+-------------------+
| flags             |
+-------------------+
| x (8 bytes)       |  → offset 8
+-------------------+
| y (8 bytes)       |  → offset 16
+-------------------+
```

**Total:** 24 bytes (8 header + 2 × 8 fields)

### 4.2 Class

Classes are mutable and have fields + methods:

```kf
class User {
    String name
    Int age
}
```

**Layout:**
```
+-------------------+
| type_id           |  → User
+-------------------+
| flags             |
+-------------------+
| name (8 bytes)    |  → offset 8 (pointer to String)
+-------------------+
| age (8 bytes)     |  → offset 16
+-------------------+
```

**Total:** 24 bytes

### 4.3 String

Strings are immutable with UTF-8 representation:

```
+-------------------+
| type_id           |  → String
+-------------------+
| flags             |
+-------------------+
| length (4 bytes)  |  → number of codepoints
+-------------------+
| padding (4 bytes) |  → alignment
+-------------------+
| bytes[]           |  → UTF-8 data + null terminator
+-------------------+
```

### 4.4 Array

Arrays have a header + contiguous elements:

```
+-------------------+
| type_id           |  → Array
+-------------------+
| flags             |
+-------------------+
| length (4 bytes)  |  → number of elements
+-------------------+
| elem_size (4 bytes)| → size of each element
+-------------------+
| elements[]        |  → contiguous data
+-------------------+
```

---

## 5. Type ID

Each Kof type has a unique type_id assigned at compile-time:

| type_id | Type |
|---------|------|
| 0 | Reserved (unknown/null) |
| 1 | String |
| 2 | Array (base) |
| 10+ | User-defined types |

**Contract:**
- type_id is constant at runtime
- type_id is unique per compilation unit
- type_id 0 means "unknown type" or "null"

---

## 6. Native Representation

### 6.1 Object Header (x86-64)

```c
struct KofObject {
    uint32_t type_id;   // 4 bytes
    uint32_t flags;     // 4 bytes
    // fields follow...
};
```

### 6.2 String (x86-64)

```c
struct KofString {
    uint32_t type_id;   // 4 bytes (= 1)
    uint32_t flags;     // 4 bytes
    int32_t length;     // 4 bytes
    uint32_t _padding;  // 4 bytes (alignment)
    char bytes[];       // UTF-8 data + \0
};
```

### 6.3 Array (x86-64)

```c
struct KofArray {
    uint32_t type_id;   // 4 bytes (= 2)
    uint32_t flags;     // 4 bytes
    int32_t length;     // 4 bytes
    int32_t elem_size;  // 4 bytes
    uint8_t elements[]; // contiguous data
};
```

---

## 7. Field Access

### 7.1 Compile-time

The compiler computes the offset of each field using the ClassLayout:

```
offset = HEADER_SIZE + sum(sizes of preceding fields)
```

### 7.2 Native Code

```asm
# Load field "x" from object in %rax
movq 8(%rax), %rbx    # offset 8 = header(8) + 0

# Store field "y" to object in %rax
movq %rcx, 16(%rax)   # offset 16 = header(8) + 8
```

### 7.3 JVM Code

The JVM uses `GETFIELD`/`PUTFIELD` with a computed descriptor:
```
GETFIELD Point.x I    # int x
GETFIELD Point.name Ljava/lang/String;  # String name
```

---

> **Updated (0.0.5):** inheritance (F.3), virtual dispatch via vtable (F.4)
> and interface dispatch (F.5) are implemented in JVM and Native.
> The real header is 16 bytes: type_id(4) + flags(4) + method_table_ptr(8).
>
> **Updated (0.2.6-beta, 31/08):** objects are allocated on the free-list
> `kof_free_head` (`mmap` reuse); the allocator is **thread-safe** (futex)
> because of `spawn` on pthreads. Mark-sweep GC implemented 03/09 (manual; see
> MEMORY_MODEL.md §9) — auto-collect on exhaustion ✅ landed 19/09 (D1-A, §260 CLOSED; `a904317e`).

## 8. Inheritance (Historical — implemented in F.3)

When implemented:

```
+-------------------+
| type_id           |  → Dog
+-------------------+
| flags             |
+-------------------+
| Animal fields...  |  → superclass fields
+-------------------+
| Dog fields...     |  → subclass fields
+-------------------+
```

**Rules:**
- Superclass fields come before subclass fields
- The type_id identifies the object's real type
- Method dispatch uses vtable (future)

---

## 9. Virtual Dispatch (Historical — implemented in F.4)

When implemented, each class has a vtable:

```
VTable:
  - entries: Array<FunctionPointer>
    - [0] = method_0
    - [1] = method_1
    - ...
```

**The object does not have a pointer to the vtable in the header.** The vtable is consulted by the compiler at compile time to determine the correct offset.

**Future alternative:** If virtual dispatch is needed, add `vtable_ptr` to the header:
```
+-------------------+
| type_id           |
+-------------------+
| flags             |
+-------------------+
| vtable_ptr        |  → pointer to vtable
+-------------------+
| fields...         |
+-------------------+
```

---

## 10. GC Future

> **Implemented 03/09:** mark-sweep exists (`kof_gc_mark`/`kof_gc_sweep`,
> manual `kof_gc_collect_now`). **Auto-collect on exhaustion ✅ landed 19/09**
> (D1-A, §260 CLOSED: blanket-spill of the 15 GPRs in `kof_gc_collect_now` +
> `kof_spawn_count==0` gate + one-shot flag; `a904317e`).
> The mark bit below is tracked in the allocator block prefix.

The object model MUST support GC:

- **Mark bits** in the flags for mark-and-sweep
- **Pinned objects** for objects that cannot be moved
- **Forwarding pointer** can be added to the header

Do NOT implement GC in this phase. Only ensure that the layout allows it.

---

## 11. Comparison with the JVM

| Aspect | JVM | Kof Native |
|---------|-----|------------|
| Object header | klass ptr + mark word (16 bytes) | type_id + flags (8 bytes) |
| Field layout | Determined by the JVM | Determined by the compiler |
| Method dispatch | vtable in each class | vtable (implemented) |
| String | java.lang.String (internally mutable) | KofString (immutable) |
| Array | JVM native types | KofArray (universal) |
| GC | Generational, concurrent | None (future) |
