[English](MEMORY_MODEL.md) | [Português](MEMORY_MODEL.pt_BR.md)

# MEMORY_MODEL.md — Kof Memory Model

**Date:** September 2, 2026
**Status:** Implemented — Phase F.7 + 0.0.5 evolution (allocator with header) + 0.4.0-beta (free-list `kof_free_head` 27/08; mark-sweep implemented 03/09)

---

## 1. Overview

The Kof memory model uses an **allocator with a block header** over mmap.

```
kof_alloc(size)
    ↓
mmap (size + 16 header)
    ↓
+0  total_size (16 bytes)
+16 payload (object/array/string) — aligned to 16
    ↓
kof_free(ptr) → munmap(exact block)
```

The language user never calls `free` — management is a runtime/compiler
decision. Kof code is semantically independent of the target's memory
mechanism.

---

## 2. Strategy

### Allocation

- `kof_alloc(size)` uses `mmap` (`MAP_PRIVATE | MAP_ANONYMOUS`)
- Each block has a 16-byte header: **total mapped size** (header + payload)
- Payload returned to the caller, aligned to 16 bytes
- Global counters: `alloc_count`, `free_count`, `alloc_bytes`, `free_bytes`

### Deallocation

- `kof_free(ptr)` reads the size from the header and runs `munmap` on the exact block
- `kof_free(null)` is a safe no-op
- Counters updated in real time

### Ownership

- Objects are allocated via `kof_alloc`
- References are direct pointers
- No reference counting (future)
- No weak references (future)

---

## 3. Object Lifetime

| Type | Lifetime | Deallocation |
|------|----------|--------------|
| Object | While referenced | `kof_free` / GC mark-sweep (03/09, manual `kof_gc_collect_now`) |
| Array | While referenced | `kof_free` / GC mark-sweep (03/09, manual) |
| String | While referenced | `kof_free` / GC mark-sweep (03/09, manual) |
| Method Table | Whole program | OS on exit |

No GC in this phase: memory is returned to the OS on process exit.
The allocator already has the structure (header with size + counters) for
future evolution: arenas, reference tracking, generational GC.

---

## 4. Root References

Roots are:
- Local variables on the stack
- Static fields (if any)
- Registers during execution

Objects referenced by roots remain valid throughout execution.

---

## 5. Runtime Functions

| Function | Purpose |
|--------|-----------|
| `kof_alloc(size)` | Allocates an mmap block with a 16-byte header |
| `kof_free(ptr)` | `munmap` of the exact block (reads size from header) |
| `kof_memstats()` | Prints real `allocs`, `frees`, `live bytes` |

---

## 6. Object Header

```
offset 0:  type_id (4 bytes)
offset 4:  flags (4 bytes)
offset 8:  method_table_ptr (8 bytes)
```

The object header does not contain memory information (no mark bits,
no forwarding pointer) — the allocation header sits 16 bytes before the object.

---

## 7. Files

| File | Role |
|---------|-------|
| NativeRuntime.java | `kof_alloc`, `kof_free`, `kof_memstats`, counters |
| NativeBackend.java | Generates calls to runtime functions |
| ClassLayout.java | Object size calculation |

---

## 8. Limitations

1. ~~No automatic GC on exhaustion~~ ✅ **landed 19/09** (D1-A, §260 CLOSED — the trigger is now SOUND: blanket-spill of the 15 GPRs + `kof_spawn_count==0` gate + one-shot flag; manual `kof_gc_collect_now` also available;
   free-list reuses `mmap`, memory returned only on the `munmap` fallback — see §9)
2. No reference counting
3. No weak references
4. No object finalization
5. No memory leak detection (counters only)
6. `kof_free` is not yet called by the generated code (foundation for GC)

---

## 9. Future

- Phase G: tracing GC (mark-and-sweep) over the existing header
- Internal arenas for short-lived allocations
- Reference counting for objects without cycles
- Weak references
- Memory compaction

> **Updated (0.2.6-beta, 31/08):** the GC evolution started from the header
> allocator toward a **free-list** (`kof_free_head`) that reuses blocks already
> `munmap`ed/reactivated via `mmap` — reduces the cost of `mmap` per small
> allocation (bottleneck #1 in `language-state.md`). **Mark-sweep is pending**:
> `kof_gc_collect` exists, but automatic GC was **disabled after a hang**
> during execution; memory continues to be returned to the OS on the
> `munmap` fallback (and on exit). The `spawn`/`await` of 31/08 (pthread) required
> the allocator to become **thread-safe** (futex), since multiple program
> threads allocate/contend over the heap.
