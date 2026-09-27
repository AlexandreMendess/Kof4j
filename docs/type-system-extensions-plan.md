[English](type-system-extensions-plan.md) | [Português](type-system-extensions-plan.pt_BR.md)

# Type-system extensions — incremental plan (X5 variance + sealed · X6 interop reflection)

last: x6-parity-docs
doing: closed
next: none
location: type-system-extensions-plan
state: done
decision: D-TYPE-VARIANCE

> **IMPLEMENTED 22/09 — all X5/X6 slices landed with proof** (exec = compiler lane). The maintainer voted X5 = option C and X6 incremental, answering the X5 surface questions (`DECISIONS.md` §D-TYPE-VARIANCE, §D-INTEROP-REFLECT, §D-X5-SURFACE); the text below is the measured spec. Queue: `roadmap.md` §2.8.4/§2.8.5. Rules: rule 6, rule 11, `D-KOF-FIRST`. Both fronts touch frozen core, so spec precedes code; every slice is additive with its own proof. Type-classes remain a permanent non-goal.

## X5 — variance + sealed

Goal: `sealed` = a class/record whose subtype set is closed and compile-time-known, so the typer proves a `switch` exhaustive (no `default`); variance = `out`/`in` on generic params so `List<Dog>` assigns to `List<Animal>` with compiler-proven safety.
Non-goals: no type-classes/higher-kinds/effect system; variance is erased, sealed is compile-time (identical JVM/Native/JS output).

Frozen surface (`D-X5-SURFACE`): (a) keyword `out`/`in`; (b) `sealed` on `class`/`record`/`interface`; (c) use-site projection (`List<out T>`) in v1, not deferred; (d) `SEM0xx` diagnostics.

| # | Slice | State + proof |
|---|---|---|
| X5.0 | spec + cells | frozen 21/09 — conformance cells `sealed`/`variance` + `training/idioms` draft |
| X5.1 | `sealed` declaration | done 21/09 — contextual `sealed` before `class`/`record`/`interface`; `SEM080` (direct subtype outside the sealed type's compilation unit); `SealedTypeE2ETest` 6 (JVM/Script run, JS/Native compile, red-first `SEM080`, retro-compat) |
| X5.2 | exhaustive `switch` | done 21/09 — `SEM081` (missing direct subtype, no `default`); `SealedTypeE2ETest` JVM/Script/JS + red-first `SEM081` + default control |
| X5.3 | declaration-site variance | done 21/09 — `TypeParser` + `TypeParams.variance` per type; `TypeChecker.genericArgsCompatible` applies `out`/`in`/invariant (§270) on same-raw args; `SEM082` soundness (`VarianceChecks`); `SemExpressionTyper` aligned to emit; erasure intact. `TypeVarianceE2ETest` 9 |
| X5.3b | variance in heritage | done 21/09 — `VarianceChecks.checkHeritage` + `SEM083` (incompatible variance passed to a supertype param); conservative v1 (simple type-param arg). `TypeVarianceE2ETest` 13 (4 new: out→in, in→out, out→invariant, matching OK) |
| X5.4 | use-site projection | done 21/09 — `TypeParser.parseTypeRef` preserves `out`/`in` in type-args; `Type.of` → `Type.WildcardType`; `MemberResolver` validates the bound; `CompilerTypes.qualifyDeep`; projection by USE (`List<out Animal>` accepts `List<Dog>`; `List<in Dog>` accepts `List<Animal>`); erasure reuses `WildcardType`. `UseSiteVarianceE2ETest` 5 |
| X5.5 | parity + docs | done 21/09 — conformance batch 4 (`sealedswitch`/`variance`/`useproj`, 4 targets); `backend-parity` EN+PT rows; `training/idioms/classes` + `fake-idioms` (only `permits` stays fake); `learn/10-inheritance`/`15-pattern-matching` drift fixed; `lexical-structure` SG-002; roadmap 2.5/2.8.4; docs-lang 100% |

Risks: variance soundness with mutable collections (`List<T>.add`) — the typer must reject the unsound assignment; exhaustiveness × `when`/`else`/nullable subjects needs explicit rules; erasure must keep the ABI byte-identical (no accidental boxing).

## X6 — interop reflection

Goal: a read-only view of a type's structure (field names/types) available only at the interop boundary, so external data (Arrow/Parquet/ML schemas) binds to Kof records without hand-written mappers.
Non-goals: never a language foundation — no runtime metaprogramming, dynamic dispatch, annotations-as-framework, reflection in user control flow; no write path; no `Class.forName`-style loading.

Frozen surface (`D-INTEROP-REFLECT`): `interop.schema(R)` — a compile-time intrinsic in `interop`, `R` a `record`, resolving to an immutable `List<Field>` where `record Field(String name, String type)` is compiler-provided in component order. Zero runtime reflection → same output every target, so no `REF001` gap. Boundary-only.

| # | Slice | State + proof |
|---|---|---|
| X6.0 | spec | done 21/09 — surface frozen in `D-INTEROP-REFLECT` |
| X6.1 | intrinsic + fold | done 22/09 — `import kof.interop` injects host `record Field`; typer → `List<Field>`; lowerer folds to `listOf(Field("n","t"),…)` ops (no runtime reflection, no per-backend code). `InteropSchemaE2ETest` 7/7 |
| X6.2 | diagnostics | done 22/09 — single entry `CompilerInterop.lowerNamespaceCall` from `ExpressionStaticCallLowerer` with shadowing guards; unknown member `INTEROP002`; arity≠1 `INTEROP001`; value/class/enum/interface arg `INTEROP001` (precise message); undefined name `SEM011` only; `recordComponents` covers `entity`. `InteropSchemaE2ETest` 17/17 |
| X6.3 | parity + docs | done 22/09 — binding E2E `InteropSchemaE2ETest#bindingE2eDrivesAnArrowShapedMapper`; conformance cell `interopschema` (4 targets); `training/idioms/interop.md` + `learn/21-java-interoperability.md` + `docs/backend-parity.md`. `InteropSchemaE2ETest` 18/18 |

Implementation notes: the compiler knows `R`'s components, so `interop.schema(R)` folds in the lowerer into the exact ops `listOf(Field("n","t"),…)` emits (`kof_list_new`+`kof_list_add`; record ctor via the normal path) — no per-backend code. `record Field` is a host-injected record (like `kof.supervisor`/`kof.workflow`), so member access (`f.name`/`f.type`) and codegen unchanged. Explicit `import kof.interop` on import (maintainer 21/09).
Risks: temptation to grow into general reflection — the boundary fence must be enforced/documented; reflection must not leak into hot paths or change record layout.

## Sequencing / dependencies

`X5.0 and X6.0 (specs) → maintainer review → X5.1–X5.5 and X6.1–X6.3`. Independent of the critical path; a queue, not current work.

## Evidence

- Decisions: `DECISIONS.md` §D-TYPE-VARIANCE, §D-INTEROP-REFLECT, §D-X5-SURFACE.
- Queue: `roadmap.md` §2.8.4/§2.8.5; `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` rows X5/X6.
- Non-goals: `docs/philosophy.md`, `training/anti-patterns/fake-idioms.md`.
