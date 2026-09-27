[English](PHASE_F_COMPLETE.md) | [Português](PHASE_F_COMPLETE.pt_BR.md)

# PHASE_F_COMPLETE.md — Phase F Complete

**Date:** August 21, 2026
**Status:** Phase F — Runtime + Object Model COMPLETE

---

## Summary

Phase F implemented the Kof runtime and object model completely and consistently between JVM and Native.

| Subphase | Status | Tests |
|---------|--------|--------|
| F.1 String Model | ✅ | 10 |
| F.2 Array Model | ✅ | 25 |
| F.3 Inheritance | ✅ | 20 |
| F.4 Virtual Dispatch | ✅ | 11 |
| F.5 Interfaces | ✅ | 13 |
| F.6 Exceptions/Runtime Errors | ✅ | 14 |
| F.7 Memory Management | ✅ | — |
| **Total** | **✅** | **142 tests** |

---

## Final Object Model

### Header (16 bytes)

```
offset 0:  type_id (4 bytes)
offset 4:  flags (4 bytes)
offset 8:  method_table_ptr (8 bytes)
```

### Object Layout

```
+---------------------+
| type_id (4 bytes)   |
+---------------------+
| flags (4 bytes)     |
+---------------------+
| method_table_ptr    |
+---------------------+
| field_0             |
+---------------------+
| field_1             |
+---------------------+
| ...                 |
+---------------------+
```

### KofString (24-byte header)

```
offset 0:  type_id (= 1)
offset 4:  flags
offset 8:  method_table_ptr
offset 16: length (byte count)
offset 20: padding
offset 24: UTF-8 data + \0
```

### KofArray (24-byte header)

```
offset 0:  type_id (= 2)
offset 4:  flags
offset 8:  method_table_ptr
offset 16: length (element count)
offset 20: elem_size
offset 24: elements data
```

---

## Runtime ABI

### Runtime Functions

| Function | Purpose |
|--------|-----------|
| `kof_alloc(size)` | Allocates memory (mmap) |
| `kof_free(ptr)` | No-op (memory reclaimed by the OS) |
| `kof_panic(msg)` | Fatal error with message |
| `kof_null_error()` | Null pointer access |
| `kof_bounds_error(i, len)` | Array index out of bounds |
| `kof_print(ptr)` | Prints a null-terminated string |
| `kof_println(ptr)` | Prints string + newline |
| `kof_print_int(val)` | Prints an integer |
| `kof_string_from_literal(data, len)` | Creates a KofString |
| `kof_string_length(str)` | Returns byte length |
| `kof_string_concat(s1, s2)` | Concatenates strings |
| `kof_string_equals(s1, s2)` | Compares strings |
| `kof_print_string(str)` | Prints a KofString |
| `kof_println_string(str)` | Prints KofString + newline |
| `kof_array_alloc(len, elem_size)` | Allocates an array |
| `kof_array_length(arr)` | Returns length |
| `kof_array_get(arr, index)` | Reads an element |
| `kof_array_set(arr, index, val)` | Writes an element |
| `kof_init_object(ptr, type_id, vtable)` | Initializes the header |
| `kof_memstats()` | Prints statistics |
| `kof_memcpy(dest, src, n)` | Copies n bytes |

---

## Inheritance

- `ClassLayout.buildWithSuper()` includes inherited fields
- `SemanticAnalyzer.resolveInHierarchy()` walks the complete hierarchy
- Constructor chaining with `super(args)`
- Inherited fields with correct offsets

---

## Virtual Dispatch

- Method tables generated per class
- Override keeps the slot in the vtable
- New methods receive new slots
- Dispatch via `method_table_ptr` in the header
- JVM uses `INVOKEVIRTUAL`

---

## Interfaces

- `KofCallKind.INTERFACE` in the IR
- `INVOKEINTERFACE` in the JVM
- Dispatch via vtable in Native
- `resolveInHierarchy()` walks interfaces

---

## Exceptions/Runtime Errors

- `throw` → JVM: `ATHROW`, Native: `kof_panic`
- `try/catch/finally` → parsed and analyzed
- Runtime errors: `kof_null_error`, `kof_bounds_error`

---

## Memory Management

- `kof_alloc` with allocation tracking
- `kof_free` is a no-op (memory reclaimed by the OS)
- `kof_memstats` for debugging
- Model: short-lived program, OS reclaims memory

> **Updated (0.2.6-beta, 31/08):** `kof_alloc` uses the free-list
> `kof_free_head` (`mmap` reuse); mark-sweep GC implemented 03/09 and auto-GC
> disabled after a hang (memory returned only on the `munmap` fallback);
> thread-safe allocator (futex) for `spawn` on pthreads.

---

## Files Created/Modified

### Created
- `docs/runtime/ARRAY_MODEL.md`
- `docs/runtime/INHERITANCE_MODEL.md`
- `docs/runtime/VIRTUAL_DISPATCH.md`
- `docs/runtime/INTERFACES_MODEL.md`
- `docs/runtime/EXCEPTIONS_MODEL.md`
- `docs/runtime/MEMORY_MODEL.md`
- `docs/runtime/PHASE_F_COMPLETE.md`

### Modified
- `ClassLayout.java` — HEADER_SIZE=16, buildWithSuper()
- `NativeRuntime.java` — complete runtime functions
- `NativeBackend.java` — virtual dispatch, interfaces, throw
- `CompilerDriver.java` — inheritance, virtual dispatch, interfaces, exceptions
- `SemanticAnalyzer.java` — resolveInHierarchy(), isInterfaceType()
- `IRNodes.java` — KofCallKind.INTERFACE, TryCatchRegion
- `Parser.java` — try/catch/finally, ClassName varName = value
- `AstNodes.java` — TryStmt, CatchClause, NewArrayExpr, ArrayAccessExpr

---

## Remaining Limitations

1. No GC (memory is not freed during execution)
2. No default methods in interfaces
3. No static methods in interfaces
4. No generics
5. No collections
6. No checked exceptions
7. No stack traces
8. No type casting (instanceof)
9. No boxing/unboxing

---

## Completion Criteria

| Criterion | Status |
|----------|--------|
| String Model working | ✅ |
| Array Model working | ✅ |
| Inheritance working | ✅ |
| Constructor chaining working | ✅ |
| Superclass fields working | ✅ |
| Virtual dispatch working | ✅ |
| Overrides working | ✅ |
| Basic interfaces working | ✅ |
| Runtime errors working | ✅ |
| Exception model implemented | ✅ |
| Memory management coherent | ✅ |
| JVM E2E passing | ✅ |
| Native E2E passing | ✅ |
| Zero regression | ✅ |
| Documentation updated | ✅ |
| ABI documented | ✅ |
| Object Model documented | ✅ |
| IR remains backend-agnostic | ✅ |
| No hidden hacks | ✅ |
