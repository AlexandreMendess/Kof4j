[English](semantics.md) | [Português](semantics.pt_BR.md)

# Semantics — Execution Model

**Status:** Stable (except where labeled) · **Evidence:** `CompilerDriver.java`, `StatementLowerer.java`, `ExpressionLowerer.java`, per-target runtime

This document defines **the meaning of a Kof program** — what happens
when it runs — independently of the backend.

---

## 1. Start and end of execution

1. The program starts at `main` (exactly one per module — PKG002).
2. `main` is called with `String[]` (empty if the program does not declare `args`).
3. `application { onStart }` blocks run **before** the body of the user's
   `main`; `onShutdown` **after** (desugaring, `CompilerDesugar.java:47`).
4. `main` statements execute **sequentially**, in source order.
5. **Pending `spawn` tasks are awaited before the program ends**
   (implicit join — `SpawnStmt` javadoc, `AstNodes.java:63-64`).
6. The program ends; the exit code is 0 (unless an uncaught exception or a
   failing `assert` in the test harness).

> The `Int main()` form was **removed** (SG-018): the entry point is only
> `main()` (no return type) — `Int main()` → `SEM044`. The process exit code is
> always 0 unless an uncaught error occurs.

---

## 2. Evaluation order

- **Left to right** on binary operands (`emitExpression` emits
  `left` before `right`, `ExpressionLowerer.java:177-185`).
- **Arguments** of a call are evaluated in order, left→right.
- **`&&`/`||`** are short-circuit on **all targets** (the right side may not be
  evaluated) — JS included (SG-006 ✅ FIXED 09/09).
- **Postfix chaining** (`a.b().c()[d]`) is evaluated from left to
  right, receiver before the member.
- **Side effects in assignment**: the right side is evaluated before
  writing to the left side.

---

## 3. Scope and lifetime

- A **block** `{ … }` opens a scope; declarations live until the end of the block.
- Local **`var`/`val`**: block lifetime. No heap-allocation of
  locals (they are frame slots), except when captured by a lambda (then they live
  as long as the lambda lives — snapshot or Box).
- **Object fields**: live as long as the object lives.
- **Memory management**: **delegated to the target**.
  - JVM: the JVM's GC.
  - Native: its own allocator (free-list / atomic bump + `kof_gc_collect`
    mark-sweep on x86_64; riscv64: bump + no full GC — **Target-specific**).
  - JS: the engine's GC.
  - **The language does not specify** when an object is collected. **Unspecified**
    (intentional — it is the host's GC).

---

## 4. Value vs reference semantics

- **Primitives** (`bool byte short int long float double char`): value.
- **`string`**: value by content (immutable; `==` compares content).
- **`record`**: value by content (`==` compares field by field).
- **`class`**: reference (identity; `==` compares reference; mutable fields).
- **`enum`**: the value is a real enum instance (a singleton per constant). `==`/`!=` is identity. An enum value is not a String: `Dir.N == "N"` is `SEM062` (D-ENUM207). Relational `<`/`<=`/`>`/`>=` between two enum values is `SEM105` (use `a.compareTo(b)`).
- **Collections** (`List/Map/Set`): reference (mutable object).
- Passing to a function: **by value** (for reference, the value is the reference —
  mutating the object is visible; reassigning the parameter is not).

---

## 5. Exceptions

- **Exceptions are `String`** (`throw "msg"`). There is no exception class.
- `throw` propagates to the nearest `catch (String e)` on the call stack.
- `finally` always executes (including during propagation).
- Uncaught exception:
  - JVM: `RuntimeException(msg)` → stack trace + exit ≠ 0.
  - Native: `kof_panic` → message + exit ≠ 0.
  - JS: throwing the string → uncaught → error in the runner.
  - **Target-specific** in representation, **Stable** in effect (aborts with
    a message and exit ≠ 0).
- **There are no** checked exceptions. The `throws T` clause is **type-checked**
  (each name must be a known type → `SEM045`, SG-019); the declared-vs-actual
  throw set is not enforced.

---

## 6. Concurrency

- `spawn` creates a **concurrent task** (thread on the JVM via virtual thread;
  `pthread_create` on Native x86_64; `clone(220)` on riscv64; `Promise`/worker
  on JS). **Implementation-defined** the mechanism.
- `await h` **blocks** the caller until `h` (Handle<T>) produces a value.
- `awaitTimeout(h, ms)` throws an exception if it expires.
- `Channel<T>`: `send`/`receive` (buffered/unbuffered — **Unspecified** the
  default capacity).
- **Memory model is formalized** (SG-020): sequentially consistent on all
  targets, with total happens-before — 6 rules (spawn/await/channel/cancel/
  locals/race) defined in `concurrency-memory-model.md`.

---

## 7. Input/output effects

- `println` writes to stdout (with newline). **UTF-8** on the 3 targets (verified
  in the R6 sweep).
- `print` without newline (if it exists — **Unspecified**; `println` is the documented one).
- `println` order between `spawn` threads: **not guaranteed** without
  synchronization (on riscv64 an atomic `writev` was done to reduce interleave
  — **Implementation-defined**).

---

## 8. Determinism

- For a program **without concurrency and without I/O**, the result is deterministic
  on all targets (same IR, same op order).
- **Floating-point arithmetic** follows the host's IEEE-754 — **may diverge**
  in extreme cases between targets (FLT001 documents the state). **Target-specific.**
- `hash`/order of `Map.keys`/`Set`: **Unspecified** (depends on the structure of the
  target runtime — JVM uses `HashMap`, Native uses a linear list).

---

## 9. What the language does NOT define (summary of Unspecified)

| Point | State |
|---|---|
| When objects are collected | Unspecified (host GC) |
| Exit code of `Int main()` | Removed: `main()` has no return type; `Int main()` → `SEM044` (SG-018) |
| Concurrent memory model | Defined — sequentially consistent + 6 happens-before rules (`concurrency-memory-model.md`, SG-020) |
| Map/Set iteration order | Unspecified |
| `throws` clause | names type-checked (`SEM045`); declared vs actual throw set not enforced (SG-019) |
| Semantics of nested classes | Absent — parse error `SEM042` (SG-016) |
| Top-level function overloading | Stable — distinct signatures coexist, ambiguous call → `SEM057` (SG-011) |
| Value of `x++` as an expression | Implementation-defined |
| `<`/`>` on non-numeric references | not supported |
