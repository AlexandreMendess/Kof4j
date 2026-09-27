[English](codegen-step-2.2.3-assessment.md) | [Português](codegen-step-2.2.3-assessment.pt_BR.md)

# 2.2.3 assessment — "Migrate DDL/runner to the `CodegenStep` hook" (RESOLVED · option B)

> **✅ RESOLVED 21/09/2026 — option B decided (`DECISIONS.md` §D-DESUGAR-STEP) and
> IMPLEMENTED (`85779f20`).** Opened 21/09/2026 by the docs/plataforma lane after
> the maintainer asked for an **investigation + proposal** (not an edit); it
> closed the measurement gap of roadmap §23 line **2.2.3**. Moved to
> `docs/architecture/` per the 3-state rule (see Resolution below).

**Owner (record):** docs/plataforma lane · **Decision:** maintainer (`D-DESUGAR-STEP`)

---

## What 2.2.3 says

`roadmap.md` §23 line 2.2.3 ("Migrate DDL/runner to the formal hook") was opened
after **2.2.2 / R4 landed** (`CodegenStep`/`CodegenStepPipeline`, `D-CODEGEN-STEP`
21/09). It assumes the two named pieces — the ORM **DDL** (entity→record+schema)
and the test **runner** (`test` declaration synthesis) — are candidates to move
onto that hook.

## What was measured (21/09, this lane)

1. **The hook runs on the OPTIMIZED IR**, after typing and lowering, right before
   emit/interpret (`CompilerPipeline.java:332`). Its registry
   (`driver.codegenSteps`, `CompilerDriverState.java:100`) is **empty**, has **no
   registration API** (only the package-private list), and **no production user** —
   the only caller is `CodegenStepPipelineTest`. The seam is the **identity** today.
2. **The four implicit codegen points live at other phases:**
   - the test **runner** (`desugarTests`), `desugarApplication`, `desugarInfra`
     (3.2) and `desugarNestedFunctions` are **AST desugars**, run **before analysis**
     (`CompilerPipeline.java:301-304` → `CompilerDesugar`);
   - the ORM **DDL** (entity→record+schema) is inside **lowering** —
     `ExpressionOrmCallLowerer.java:70` → `KofOrm.schemaString` — not a separable pass.
3. **3.2 is the proof:** the declarative sugar the hook was meant to host
   (`infra "prod" {}`) landed as `desugarInfra` (AST, commit `966c86a4`), **not**
   through the hook — because the frontend must see the lowered form **before
   typing**. A post-IR hook is structurally **too late** for source desugaring.

## Conclusion

2.2.3 as written is a **phase mismatch**: the DDL is lowering, the runner is an AST
desugar; neither can move to the post-IR hook without changing observable behavior
(and the AST phase is where the frontend *needs* them). Two honest options:

- **A (recommended, minimal):** **reclassify 2.2.3 as obsolete/closed** — the AST
  desugar seam (`CompilerDesugar`) **is** the formal seam for source desugars; the
  IR hook stays as a documented, tested identity seam for *future IR-level* passes
  (none today). No code; update roadmap + tracker.
- **B (if a first-class desugar seam is wanted):** add a **`DesugarStep` registry
  at the AST phase**, mirroring `CodegenStep`, and migrate the four desugars into
  registered steps — **behavior-free**, proven by the same suite + golden E2E per
  target. This is **code**, a separate unit **after** the decision.
- The DDL (entity→schema) is **not** a candidate for either registry — it is
  part of lowering.

## Outcome (21/09)

Option **B** was chosen and landed: `DesugarStep` + `DesugarStepPipeline` +
`DesugarSteps.defaults()` (the four desugars as ordered steps) drive the AST
phase, and `CompilerPipeline:303` runs them instead of the four direct calls.
Proof: `DesugarStepPipelineTest` 7/7 + the four-desugar batch 331/0F/0E, with
`mvn -o -pl kof-compiler -am compile` green (commit `85779f20`). The post-IR
`CodegenStep` hook remains the documented identity seam for future IR-level
passes (none today).

## Impact if option A

- `roadmap.md` line **2.2.3** → reclassified (closed/obsolete, with this rationale).
- `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` R4 wording drops "and DDL/runner migration".
- No code; `CodegenStep`/`CodegenStepPipeline` remain (with their test).

## Impact if option B

- New `DesugarStep` interface + `DesugarStepPipeline` at the AST phase + registry.
- `CompilerDesugar`'s four entry points become registered steps (or thin delegates).
- Proof: full suite + golden E2E per target, unchanged output (freeze rule 3).

---

## Resolution (option B — IMPLEMENTED)

- **Decided:** `DECISIONS.md` §D-DESUGAR-STEP (maintainer 21/09) chose **option B**.
- **Implemented:** `85779f20` — `DesugarStep` + `DesugarStepPipeline` at the AST
  phase; `DesugarSteps.defaults()` registers the four desugars (`tests`,
  `application`, `infra`, `nested-functions`) and `CompilerPipeline:303` runs
  them through the registry. `CompilerDesugar` is now only the delegate each step
  calls (no direct call sites remain).
- **Proof:** `DesugarStepPipelineTest` 7/7; behavior-free (freeze rule 3).
- **DDL:** stays in lowering (`ExpressionOrmCallLowerer:70`) — not a registry
  candidate (rule 11).
- **Moved** here from `docs/development/` (3-state rule; the release gate
  condition 3 `loose_docs` tracked it).
