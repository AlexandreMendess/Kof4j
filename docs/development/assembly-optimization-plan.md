[English](assembly-optimization-plan.md) | [Português](assembly-optimization-plan.pt_BR.md)

# Cross-Target Assembly Optimization

**Status:** UNDER DEVELOPMENT — promoted from `future/` to `docs/development/` 13/10 (`D-FUTURE-PROMOTION` + `D-ASSEMBLY-OPT-GO`; lane docs/lab `192.168.15.130:9093`). **Current slice: Phase C — register allocation (slice 1, liveness, landed).** Phase A (structured-optimizer layer over the EXISTING linear IR optimizer) and Phase B (constant/copy propagation + dead-store elimination, wired into the production `Optimizer` with a measured instruction reduction) are complete; Phase C slice 1 adds CFG-based liveness. The linear `Optimizer.java` keeps its own constant folding + dead stack-effect elimination + reachability + jump-to-next on a FLATTENED op list (`IRBasicBlock` is a single flat block today); the Phase A foundation (control-flow graph / basic-block partition / successor edges), the Phase B passes and the Phase C analyses sit alongside it, zero behavior change for existing backends.
**How to finish:** Phase A (CFG + virtual registers + use/effect analysis + pass pipeline), then Phase B (basic optimizations) → Phase C (register allocation) → Phase D (target lowering) → Phase E (ISA) → Phase F (benchmarks); each phase's criterion per §21. The per-target ABI is a scope decision before Phase D (R7 honest scope).
**Location:** `docs/development/assembly-optimization-plan.md`
**Nature:** architecture, contracts, implementation strategy and promotion criteria
**Scope:** Kof Native backend on multiple ISAs
**Targets:** x86_64, aarch64, riscv64 and future Native backends
**Principle:** prefer optimizing before the ISA-specific assembly whenever possible; specialize to the backend only when the architecture requires it.

last: promoted 13/10 (`D-FUTURE-PROMOTION`; the only `future/` plan whose gate `D-ASSEMBLY-OPT-GO` is already decided, no new syntax, additive compiler-internal)
doing: Phase A COMPLETE 13/10 + Phase B COMPLETE 13/10 + Phase C slice 1 (liveness) LANDED 13/10 — Phase A landed all six §21 items: `ControlFlowGraph.java` (blocks + successor edges), `EffectAnalysis.java` (effect + local def/use), `VirtualRegisters.java` ((slot,type) numbering), `PassPipeline.java` + `IrPass.java` (ordered, fixpoint, deterministic). Phase B slices 1–3 (`ConstantPropagation`, `CopyPropagation`, `DeadCodeElimination`) are WIRED into the production `Optimizer.passes()` (after constant folding, before dead stack-effect elimination); three compiler-internal invariants were measured (RED-first): (1) `KofStatementIf` is a control boundary (folding a condition orphaned the JS structural-reconstruction marker — `KofJsE2ETest` 38/38 after); (2) an immediate `store(slot); load(slot)` read-back stays verbatim (the backends pattern-match on it — `#switch`/`#recv`; disabling the guard broke `SwitchLongDoubleSupportE2ETest` 3/5); (3) constant propagation emits a DISTINCT literal instance per rewrite site (sharing one instance made identity-based dead-store elimination delete a surviving read with a dead producer — `Raster.kf` → ASM frame crash). Measured reduction: a dead store drops 2 ops (`ds1` 68→66), two drop 6 (`ds2` 74→68); proof `OptimizerPhaseBReductionTest` 3/3. Phase C slice 1 = `LivenessAnalysis.java` — backward dataflow (`liveOut(b)=∪liveIn(s)`, `liveIn(b)=use∪(liveOut\def)`) over the Phase A CFG, with a conservative exception edge (try→handler); per-block `liveIn`/`liveOut` + per-op `liveAfter`; deterministic; ADDITIVE (pure read, no behavior change); proof `LivenessAnalysisTest` 7/7
next: Phase C slices 2+ — interference graph + allocation (linear scan) + spills + caller/callee-saved handling per §5/§21; criterion = reduction in artificial loads/stores + intact ABI on all targets
state: active
owner: `192.168.15.130:9093` (lane docs/lab)

---

## 1. Goal

Establish an optimization strategy for the Kof Native backend capable of producing small, efficient and predictable assembly on different architectures without turning each backend into an independent compiler.

The goal is not merely to emit fewer instructions.

The goal is to preserve the intent of the program through the pipeline:

```text
Kof
 ↓
AST
 ↓
Kof IR
 ↓
semantic analysis
 ↓
ISA-independent optimizations
 ↓
lowering
 ↓
ISA-specific optimizations
 ↓
register allocation
 ↓
instruction selection
 ↓
assembly
```

The same Kof construct must have an intermediate representation rich enough to allow common optimizations, while each backend can exploit features of the destination architecture.

---

# 2. Principles

## 2.1 Assembly is not the primary optimization level

Kof should not rely on transforming already-emitted assembly as its main optimization mechanism.

Preferred:

```text
AST
 ↓
IR
 ↓
optimized IR
 ↓
backend
 ↓
assembly
```

and not:

```text
AST
 ↓
bad assembly
 ↓
assembly optimizer
 ↓
less bad assembly
```

The final assembly may have peephole optimization, but that should be a last layer.

---

## 2.2 Optimization should be cross-target by default

A transformation should be implemented only once when its validity is independent of the architecture.

Examples:

* constant folding;
* constant propagation;
* dead-code elimination;
* unreachable-code elimination;
* common subexpression elimination;
* algebraic simplification;
* temporary elimination;
* copy propagation;
* branch reduction;
* effect analysis;
* elimination of redundant operations.

The architecture should only participate when the transformation depends on the ISA, ABI or memory model.

---

## 2.3 The backend should not know the whole language semantics

The backend should receive a representation already sufficiently reduced.

The backend should answer:

> “How do I express this operation on this ISA?”

and not:

> “What did the program mean?”

This keeps the border:

```text
Kof IR
   │
   │ semantics
   ▼
Optimizer
   │
   │ reduced operation
   ▼
Target Lowering
   │
   ▼
ISA
```

---

# 3. Optimization layers

The pipeline must be divided into explicit layers.

## O1 — semantic optimizations

Architecture-independent.

Examples:

```text
1 + 2
↓
3
```

```text
x * 0
↓
0
```

```text
if (false) {
    ...
}
↓
remove block
```

```text
x = y
return x
↓
return y
```

These transformations should operate on the IR.

---

## O2 — structural optimizations

Transform control flow and the representation of operations.

Includes:

* basic block simplification;
* branch folding;
* block merging;
* unreachable block removal;
* jump threading;
* loop simplification;
* loop invariant code motion;
* strength reduction;
* induction variable simplification.

Example:

```text
if (condition) {
    return 10;
} else {
    return 20;
}
```

must reach the backend already represented as a minimal flow.

---

# 4. O3 — memory optimizations

Kof should reduce unnecessary loads and stores before ISA-specific lowering.

Example:

```text
store x
load x
```

when no operation is able to change `x` between the two points:

```text
value already available
```

The backend should not need to emit:

```asm
store
load
```

and then try to eliminate both instructions afterward.

The analysis should happen on the IR.

---

# 5. O4 — register allocation

Kof should have an explicit register-allocation step.

Goal:

```text
IR temporaries
       ↓
virtual registers
       ↓
register allocation
       ↓
physical registers
```

The initial implementation may use a simple, deterministic strategy.

Later these may be introduced:

* liveness analysis;
* interference graph;
* linear scan;
* graph coloring;
* spill heuristics;
* register class constraints;
* caller/callee-saved awareness.

The choice of strategy should not be part of the language contract.

---

# 6. O5 — target lowering

After the cross-target optimizations, abstract operations must be converted into ISA-specific operations.

Example:

```text
ADD
```

may become:

```text
x86_64 → add
aarch64 → add
riscv64 → add
```

But some operations may require completely different sequences.

The lowering should encapsulate those differences.

---

# 7. O6 — ISA-specific optimizations

Each backend may have its own optimizations.

## x86_64

Possible optimizations:

* `lea` for suitable arithmetic operations;
* choice between `mov`, `lea` and combined operations;
* efficient use of flags;
* reduction of instructions for constants;
* selection of lower-cost instructions;
* future SSE/AVX usage once supported.

Conceptual example:

```text
a + b * 4
```

may be expressed as:

```asm
lea rax, [rdi + rsi*4]
```

when the situation allows.

---

## AArch64

Possible optimizations:

* use of immediate forms;
* addressing modes;
* shift + arithmetic combinations;
* appropriate choice among `add`, `sub`, `lsl`, etc.;
* exploitation of temporary registers;
* sign-extension instructions folded into the memory access.

Conceptual example:

```text
load 32-bit signed value
extend to 64-bit
```

may be expressed directly with a load instruction using proper extension when available.

---

## RISC-V 64

Possible optimizations:

* use of immediate forms;
* reduction of constant-materialization instructions;
* addressing sequences;
* appropriate choice among `addi`, `slli`, `add`, etc.;
* use of available extensions when supported by the target;
* explicit control of sequences affected by linker relaxation.

The backend should not assume that an optimization valid on x86 exists on RISC-V.

---

# 8. Constant folding

Constant expressions must be resolved before assembly generation.

Example:

```kof
var x = 10 * 20
```

should result in:

```text
x = 200
```

and not:

```asm
mov ...
mov ...
imul ...
```

The optimization should happen on the IR.

---

# 9. Constant propagation

When a value is known:

```text
x = 10
y = x + 2
```

it must become:

```text
y = 12
```

unless an effect prevents the propagation.

---

# 10. Dead code elimination

Code whose result has no observers must be removed.

Example:

```text
x = expensive()
return 42
```

If `x` is never used and `expensive()` has no observable effects:

```text
return 42
```

The effect analysis must be explicit.

Kof must not remove calls that may:

* mutate observable memory;
* perform I/O;
* mutate global state;
* touch FFI;
* trigger effects defined by the runtime.

---

# 11. Copy propagation

Sequences like:

```text
a = b
c = a
```

may be reduced to:

```text
c = b
```

This also lowers register pressure.

---

# 12. Strength reduction

Costly operations may be replaced when semantically equivalent.

Example:

```text
x * 2
```

may become:

```text
x << 1
```

when the type semantics allow it.

But the transformation must not be applied blindly.

The real cost of the operation depends on the ISA and the context.

---

# 13. Peephole optimization

A small final step may observe specific instruction sequences.

Example:

```asm
mov rax, rbx
mov rcx, rax
```

may be reduced to:

```asm
mov rcx, rbx
```

when safe.

Peephole optimization should remain small.

It must not become a second implementation of the compiler.

---

# 14. ABI and optimization

The ABI is part of the backend contract.

Optimizations must never incorrectly change:

* argument registers;
* return registers;
* caller-saved;
* callee-saved;
* stack alignment;
* parameter layout;
* struct layout;
* return rules;
* external calling conventions.

Especially for FFI:

```text
Kof
 ↓
ABI lowering
 ↓
C / foreign function
```

must produce exactly the convention expected by the target.

An optimization that breaks the ABI is incorrect even if it appears to produce better assembly.

---

# 15. Structs and layout

Optimizations must preserve the layout defined by the ABI contract.

For:

```c
struct Pair {
    int a;
    int b;
};
```

the backend must keep:

```text
a = 32 bits
b = 32 bits
total = 64 bits
```

Optimizations may eliminate redundant loads/stores but must not change the layout observable via FFI.

---

# 16. Cross-target equivalence

The same Kof program should produce semantically equivalent results on:

```text
x86_64
aarch64
riscv64
```

The assembly does not need to be identical.

It is necessary that:

```text
results
observable effects
ABI
layout
semantics
```

are equivalent.

So:

```text
same IR
   ↓
┌──────────┬──────────┬──────────┐
x86_64     AArch64    RISC-V
 ↓           ↓          ↓
asm A       asm B      asm C
```

is expected.

---

# 17. Benchmark-driven optimization

No ISA-specific optimization may be promoted just because “it looks better in assembly”.

Each relevant optimization must have:

1. minimal case;
2. expected assembly or expected properties;
3. benchmark;
4. before/after comparison;
5. correctness validation;
6. coverage on the supported targets.

The goal is to avoid cosmetic optimizations.

Smaller assembly does not necessarily mean faster code.

---

# 18. Metrics

The backend should track, when possible:

* `.text` size;
* instruction count;
* load count;
* store count;
* branch count;
* spill count;
* call count;
* register usage;
* runtime;
* final ELF size.

The metric should be chosen according to the optimization’s goal.

---

# 19. Debuggability

The optimizations should not completely destroy debuggability.

When debugging is enabled, the backend should preserve enough information to relate:

```text
Kof source
    ↓
IR
    ↓
machine instruction
```

Optimizations may remove variables or merge operations, but that should be represented correctly in future DWARF information.

---

# 20. Determinism

Assembly generation must be deterministic.

Given:

```text
same source
same target
same flags
same compiler version
```

the result must be reproducible.

This helps:

* tests;
* debugging;
* assembly comparison;
* benchmarks;
* releases;
* regression investigation.

---

# 21. Implementation phases

## Phase A — infrastructure

* define the optimizer contracts;
* define the basic-block representation;
* define virtual registers;
* define use analysis;
* define effect analysis;
* define the pass pipeline.

**Criterion:** no existing backend may lose behavior.

---

## Phase B — basic optimizations

Implement:

* constant folding;
* constant propagation;
* copy propagation;
* dead-code elimination;
* unreachable-code elimination;
* basic block simplification.

**Criterion:** measurable reduction in instructions on representative fixtures.

---

## Phase C — register allocation

Implement:

* liveness;
* virtual registers;
* allocation;
* spills;
* caller/callee-saved handling.

**Criterion:** reduction in artificial loads/stores and intact ABI on all targets.

---

## Phase D — target lowering

Formalize:

```text
Kof IR operation
       ↓
Target lowering
       ↓
Machine instruction
```

Each backend must explicitly declare which operations it supports.

---

## Phase E — ISA optimizations

Add specific optimizations:

```text
x86_64
AArch64
RISC-V
```

without contaminating the IR with unnecessary details of a single architecture.

---

## Phase F — benchmark suite

Create a cross-target suite containing:

* arithmetic;
* loops;
* branches;
* calls;
* structs;
* arrays;
* memory;
* FFI;
* bitwise operations;
* constants;
* cases with high register pressure.

Each benchmark should run on the available targets.

---

# 22. Promotion criteria

An optimization may leave `future/` when:

* it has a clear specification;
* it has an implementation;
* it has correctness tests;
* it has cross-target coverage when applicable;
* it does not change ABI contracts;
* it does not introduce target-dependent behavior in cross-target code;
* it has a benchmark when there is a performance impact;
* it does not significantly degrade another target without explicit justification;
* it has documentation of the expected behavior.

An optimization that improves x86_64 but degrades AArch64 and RISC-V should not be treated as universal.

It must be explicitly classified as target-specific.

---

# 23. Rule of thumb

The backend should prefer:

```text
correct semantics
    ↓
good IR
    ↓
general optimization
    ↓
correct lowering
    ↓
specific optimization
    ↓
efficient assembly
```

and avoid:

```text
naively emitted assembly
    ↓
hundreds of patches
    ↓
infinite peephole
    ↓
unmaintainable backend
```

---

# 24. Expected outcome

The final architecture must let a single Kof implementation produce proper native code for multiple architectures:

```text
                     Kof
                      │
                      ▼
                     IR
                      │
             ┌────────┴────────┐
             │                 │
       cross-target       semantic
       optimizations      analysis
             │                 │
             └────────┬────────┘
                      ▼
                Target Lowering
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
     x86_64         AArch64       RISC-V
        │             │             │
        ▼             ▼             ▼
      ASM           ASM           ASM
        │             │             │
        └─────────────┼─────────────┘
                      ▼
                   linker
                      │
                      ▼
                 executable
```

Kof remains responsible for its own native generation. C, LLVM or other languages/toolchains are not required as mandatory intermediaries.

External tools may continue to exist at the system boundary for assembler, linker, debugging or interop, but they **do not define the Kof compiler’s architecture**.

---

# 25. Out of initial scope

This plan does not initially require:

* automatic SIMD;
* auto-vectorization;
* PGO;
* JIT;
* superoptimization;
* advanced instruction scheduling;
* speculative optimization;
* aggressive floating-point optimization;
* per-microarchitecture code generation;
* replacement of the system assembler/linker.

These capabilities may be added later when evidence of need exists.

---

## Desired end state

The Native backend should produce assembly that is:

**correct, small, predictable, ABI-compatible, cross-target and competitive**, without sacrificing Kof’s architectural simplicity.

The fundamental rule stands:

> **Kof does not need to produce the most complex assembly possible. It needs to produce the necessary assembly, correctly and without accidental complexity.**
