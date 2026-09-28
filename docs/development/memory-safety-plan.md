[English](memory-safety-plan.md) | [Português](memory-safety-plan.pt_BR.md)

# Memory safety — ownership, lifetime, borrowing, aliasing (D-MEMORY-SAFETY)

last: phase-4-slice-4.2-pinned (spawn-capture-return 4-target, #659)
doing: memory-safety
next: phase-4.3-callback-faces (medir antes de prometer)
location: memory-safety-plan
state: active
intent: compiler-provable-memory-safety
constraint:
  - core-edits-wait-for-queue
  - no-foreign-borrow-checker
decision: D-MEMORY-SAFETY

Success criterion: the compiler can prove a program cannot produce a class of error (use-after-free, double-free, dangling reference, invalid lifetime escape, unsafe mutable aliasing, unexpected null, accidental data race) — or that it lives behind an explicitly named boundary. NOT a feature named `ownership`.

## Hard constraints

- Kof already has null safety — never reinvent/replace/duplicate it; only study nullability × ownership × lifetime × borrowing.
- Kof-first: no assumption Kof works like Rust, C++, Java, Kotlin, Swift or Zig (rules 8/10).
- Architecture before code; implementation waits for the current queue.
- Simplicity Law (rule 11): strong guarantees without endless lifetime annotations.
- Cross-target by construction (JVM/Native/JS/WASM same semantics; GC never excuses divergence; FFI defines the owner per crossing).
- Diagnostics and tests are part of the feature (valid/invalid/expected-diagnostic/regression/per-backend).
- Forbidden: copying Rust's borrow checker, inventing syntax (`let`/`const`/foreign move markers), a null-safety rewrite, big-bang refactors, single-backend ownership, hiding ownership in the runtime.

## Phases

| Phase | Deliverable | State |
|---|---|---|
| 0 Investigation | `docs/spec/memory-safety-investigation.md` (EN+PT): state, risks, § ledger sweep, proposal, alternatives, compatibility, incremental plan; 20 questions answered with file:line | closed 25/09 |
| 1 Specification | `docs/spec/memory-safety.md` (EN+PT): Ownership/Lifetime/Borrowing/Aliasing/Mutability/Move/Copy/Clone/Drop/Escape/Closure Capture/Concurrency/FFI/Unsafe Boundaries, each classified allowed/forbidden/sync-required/compile-time/runtime/type-dependent | closed 25/09 (maintainer option A) |
| 2 Compiler infrastructure | `OwnerKind`/`MemRule`/`ManagedResource`/`CaptureMode`/`MoveDetector`+`MoveTransfer` in `dev.kof.compiler.memory`; `MemoryModelTest` 8/8; zero behavior change | closed 26/09 (`9bcddfe90`, queue exhausted) |
| 3 First guarantees | use-after-move; dangling; invalid escapes; mutable aliasing; double ownership/destruction | in progress (slices below) |
| 4 Closures & async | closure capture; callbacks; async/futures; iterators/generators | 4.1+4.2 landed 28/09 (#658/#659); callbacks face pending (4.3) |
| 5 Native & FFI | pointers/allocation/C ABI; Kof↔C↔Rust↔JVM↔Python ownership table | pending |
| 6 JVM / JS / WASM | same semantics every backend | pending |

## Phase 3 slices (emission surface = what exists in the user surface)

| Slice | Face | State |
|---|---|---|
| 1 Straight-line | O-01/`MEM001` double claim + O-02/`MEM002` use-after-claim via alias — `OwnershipPass` wired in `StatementAnalyzer.analyzeBody` (shared frontend = same analysis on 4 targets); `MemorySafetyE2ETest` | landed 26/09 (`c23dcb30d`) |
| 2 Flow crossing | conditional claim/read (if/while/try/switch) — branch snapshot inherited, result does NOT propagate (anti-false-positive by construction); unconditional `BlockStmt` propagates | landed 26/09 |
| 3 Escape/dangling | L-04/`MEM013` (capture extends life) + dangling faces of spec §3 | landed 26/09 |
| 4 Mutable aliasing | B-05/`MEM022` — size-changing mutation (`add`/`remove`/`clear`/`addAll`) of the collection being iterated in its own `for-in`; WARNING + zero-FP by construction (`for-in` is an index loop re-reading `size` each turn) | landed 26/09 |
| 5 Containers & unclosed | O-03/`MEM003` (container release — RESOLVED: `D-MEMORY-CLEAR` option a; runtime guarantee, no compile face) + L-05/`MEM014` db connection (web landed 3.1b; `kof.io` has no close-bearing file handle) | db `MEM014` landed 26/09; O-03 FIXED 27/09 (`NativeX86MemClearTest` + `NativeRiscvMemClearTest` + `MemoryClearE2ETest` 4 targets) |
| 3.2 | MEM021 spawn mutable aliasing — `spawn` capturing a mutable object the parent also mutates, with no `await`/`join_all` between (spec B-04/C-03); ERROR on the clear race, silent elsewhere; zero false positives required | landed 28/09 (ported onto `lab`: `MemorySafetyE2ETest` 40/40 local, CI cert pending) |

- MEM005 (FFI ownership) is ALREADY SATISFIED at the boundary: Native rejects record/array/out-buffer externs with `FFI001` at the decl line; JVM/JS copy-back; String returns boundary-copied. Slice 3.3 documents O-05's compile face — no duplicate diagnostic invented (rule 11).
- Spec-corrected: reading the CLAIMER after its own close is L-02/`MEM011` (RUNTIME, GC-free native), not MEM001 — corrected against spec §3 rows 84/87.
- Evidence: `ResourceLeakE2ETest` 5/5 (web), `DbResourceLeakE2ETest` 4/4 (db), `MemorySafetyE2ETest` 29/29 (9 new faces). `kof.io` has no close-bearing handle (stateless by path) — absence, not a gap.
- Reordering 26/09: slice 4 was specified as B-03/`MEM020` + B-04/`MEM021`, both moved out (named, not accepted gaps): B-03 needs the FFI borrow surface (phase 5); B-04 needs closure/async capture (phase 4).

## Phase 4 slices

| Slice | Face | State |
|---|---|---|
| 4.1 | B-06 closure-capture parity — every capture face (basic, outer-mutation `15/25`, lambda-writes, lambda-returns-lambda, triple-nested) pinned on the 4 targets: JVM/Native as before + Script via `KofInterpreter` + JS via `KofJsRunner`, golden-identical (`LambdaE2ETest` 36/36; #658) | landed 28/09 — behavior change ZERO: parity was already true (measured, not assumed); the pin is the product, precedent O-03/`D-MEMORY-CLEAR` ("guarantee proven by test") |

| 4.2 | async/futures capture parity — §46 face `var h = spawn { return n * 2 }; await h` (COM captura) pinned on the 4 targets (`SpawnE2ETest.jvm|script|js...` + the pre-existing Native pins), golden `42` — the "interpreter/JVM/JS → 42" claim lived only in §46 prose until measured; the independent-verifier pass then caught that ALL these faces only READ the capture (read-only capture lowers WITHOUT the box: JVM `LambdaTask0.<init>(I)`, by value), so `SpawnE2ETest.*AwaitMutatedCapture` (golden `44`, 4 targets) joined the matrix — and the verifier's SECOND pass caught that 44 alone still passes under a by-value snapshot (the parent never touches `n`), so `*VisibleToParent` (`44/22`, 4 targets) pins the child→parent box visibility through join — `SpawnE2ETest` 21/21; its RACE probe (parent reassigning a captured scalar after spawn, no await between) compiles silently: MEM021's MUTATORS are object-level (add/remove/clear/addAll) — scalar-race scope escalated to the maintainer (rule 6), never decided here | landed 28/09 (#659) — zero behavior change (parity was already true); §46 historical prose corrected EN/PT |
- MEM023 has NO compile face today: lowering boxes every mutated capture by construction (`mutatedCapturedNames` → `CapturedVarBox`, `StatementLowererLocalBoxing.java:37`), so the forbidden shape (unboxed mutating capture escaping) is unconstructible — guarantee proven by the 4.1/4.2 batteries, precedent O-03/`D-MEMORY-CLEAR` (no diagnostic invented, rule 11). Recorded in spec §3.2 note.
- 4.3 (pending, measure-first): callback faces (lambdas passed to stdlib/`job`-style APIs); "iterators/generators" from the phase row got its verdict on 28/09: NO generator surface exists (no `yield` token in the Kof lexer — `for-in`/collections are the iterable face) → absence, not a gap.

## Decision requests (rule 6)

- **O-03/`MEM003` container release** — RESOLVED 27/09 by `D-MEMORY-CLEAR` (option a): `clear()` MUST null every slot before shrinking, so the guarantee is a runtime property proven by test, never a compile face; no `MEM003` diagnostic is created. Implemented in the native runtimes (`RuntimeList`/`RuntimeMap` x86, `NativeRiscvAsmRtB0`/`NativeRiscvAsmMapset0` cross); JVM (`ArrayList`/`HashSet`/`HashMap.clear`) and JS (`length=0`/`clear()`) already drop references. Proof: `NativeX86MemClearTest` + `NativeRiscvMemClearTest` read the backing slots from memory after `clear()` (list via real `add`, map with planted key/val pairs) — RED 1/1+2/2 pre-fix, GREEN post-fix; `MemoryClearE2ETest` pins the uniform empty+reusable behavior on JVM/Script/JS/Native + riscv64/aarch64.
- **O-02 move pattern** resolved 26/09 by `D-COMPLETE-FIRST`: `var a = b; b = null` collided with N-02/SEM048 (null literals forbidden) → re-express O-02 without a null literal, as the complete package (pass + emission + per-target proof).
- **B-04/`MEM021` scalar re-assignment (impossibility NOT reached — maintainer, rule 6; #660)** — the parent re-assigning the SAME captured SCALAR local after `spawn`, with no `await`/`join_all` between, is neither caught by `MEM021` nor carries a gap code: `SpawnCaptureScanner.MUTATORS` lists only OBJECT mutators (`add`/`remove`/`clear`/`addAll`), and spec B-04 speaks of a "mutable object" — the scalar case is neither forbidden nor registered. Measured (triage, reproduced on `lab`): `var n = 21; var h = spawn { n = n + 1; return n * 2 }; n = 100; println(await h); println(n)` compiles with NO diagnostic on JVM/JS/Script and races (`202` then `101`, exit 0). Decision required: (A) extend `MEM021` to the scalar path; (B) register as an accepted silent race in spec B-04 with an explicit note + honest `XXX00x` code; (C) other. Blocked on the maintainer — no agent edit until decided.

## Package DoD

Pass + wiring + `MemorySafetyE2ETest` per target (JVM/Script/JS/Native same sources, same diagnostics) + corpus note in `training/idioms/concurrency.md`+`interop.md` when emission lands; each slice lands complete or does not land (`D-COMPLETE-FIRST`).

## Definition of done (whole front)

The 12 §27 questions answered in the spec, the impossible-bug-classes list explicit, and the implementation matching the spec with the §22 safety matrix green per backend — "structures named `Ownership`/`Borrow`/`Lifetime`" is NOT done.

Relationships: `DECISIONS.md` §`D-MEMORY-SAFETY`; `PARITY-GAPS.md` (the blocker this front queues behind); rule 6 (frozen semantics — any ownership semantics changing evaluation order or operator contracts is a maintainer decision, never an agent edit).
