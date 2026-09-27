[English](roadmap-audit.md) | [Português](roadmap-audit.pt_BR.md)

# Roadmap Audit — Implementation Matrix (06/09/2026; resync 12/09, touch 13/09)

> Audit of real code (3 explorations, file:line) + suite run. Rule: REAL state, not what the roadmap says. Baseline `0.3.0-beta`. Test counts = gate 12/09 from surefire-reports (`docs/status.md` §Tests, `e2c03812`); cells changed since 06/09 rewritten with proof (SG-009/WEB001/conformance/LSP/GC). 13/09: P4 marked CLOSED (it contradicted line 25 — SG-009 ✅ since 10/09).

## State matrix

| Item | Real state | Code | Tests | Main gap |
|---|---|---|---|---|
| 1. Standard Library | **PARTIAL (good)** | 23 namespaces in `Kof*.java` with R6 gates (`supportedOn`/`gapCode`) | E2E per area (KofCache/Db/Http/Mq/Time/…) | web JS has a REAL base (GraalJS HttpServer `bc577aa` 03/09 — no longer a silent stub; ws/sse = declared residual `WEB001`, `kofWebStub` only a fallback); sec without cross = honest `SECN000`; db/orm JVM-only; `scheduler.at` fake cron |
| 2. GC auto-collect | **PARTIAL** | real x86_64 mark-sweep (`RuntimeGc.java`); **NO safe-points/root-map**; auto-collect off (`RuntimeMemory.java:121-133`); riscv/aarch bump **without GC** (G-0 `356f33b9`: 32B header-block + OOM guard) | `KofGcE2ETest` 3/3 | face (1) decomposed into **G-1..G-5** (`native-multiarch.md` §decomposition): G-1 free-list riscv next (no prerequisite) → G-2 header flags → G-3 mark (needs `kof_heap_root_end`, S-5-x86 bugfix queue) → G-4 sweep → G-5 aarch inherits |
| 3. Package Manager | **MVP** | `Deps.java` (flat Maven Central, cache `~/.kof/deps`) | `DepsTest` 4/4 | POM/transitives, lockfile, ranges, publish |
| 4. Async | **PARTIAL** | JVM vthreads + Handle/await/timeout; real JS Promise (CONC003 ✅); Native pthread | `KofConcurrency2Test` 33 | timeout/cancel/selectAny closed on 3 targets; remaining: select over channels |
| 5. Concurrency G8 | **PARTIAL (good)** | spawn/await/cancel/selectAny/awaitTimeout/channel/scheduler 3 targets | idem | `scheduler.at` cron = fixed 60s (declared MVP); cancel by TID%256 |
| 6. KofAndroid | **DONE (caveat)** | `Target.ANDROID`; `--apk` (d8/aapt2/apksigner); `AndroidProjectWriter` (Maven) | depends on ANDROID_HOME | lifecycle/ART runtime in Phase 2; consolidate docs |
| 7. Debugger | **MVP** | DAP stdio JVM (`KofDebug.java`), real JDWP breakpoints; DWARF line-only | — | **locals = placeholder** (`"line N"`, `KofDebug.java:197`, verified 12/09); stepping/evaluate; VS Code ext |
| 8. KofJS | **PARTIAL (alpha → functional)** | ESM + V3 source maps + GraalJS + DOM/UI (9 files); real web base (GraalJS HttpServer `bc577aa`) | `KofJsE2ETest` 40 | ws/sse **declared residual WEB001** (honest gap; not a silent stub); serve×JS indirect |
| 9. LSP | **PARTIAL (good)** | real diagnostics via CompilerDriver; textual hover/completion/references/rename + **go-to-definition ✅** (`LspServer.java:323`) | `LspServerTest` 4/4 | hover/completion/rename must use SymbolTable |
| 10. KofScript | **PARTIAL (good)** | shared IR interpreter | `KofScriptTest` 31 + parity gate | globals via fragile multiline regex; REPL re-evaluates everything |
| 11. Language Spec | **PARTIAL (good)** | `docs/language-reference/` 16 files | — | **SG queue COMPLETE** (SG-001–020 + E1–E3 resolved 06–12/09; SG-009 subtyping ✅ SEM021 `StatementAnalyzer.java:154`); non-normative grammar |
| 12. Conformance Suite | **PARTIAL** | `conformance-matrix.md` (07/09), cells locked by `ConformanceMatrixTest` (11) + `ConformanceMatrixDocTest`; `BackendParityTest` 16 cases | — | next batches by category |
| 13. Full Web Platform | **NOT STARTED** | partial routing in kof.ui; validation exists | — | declarative/forms/SSR — depends on 8+9 |
| gRPC | **NOT STARTED** | — | — | planned; do not start before P0-P2 |
| Auto-hosting | **NOT STARTED** | — | — | documented as a gap |

## Critical semantic bugs (P0) — silent UNKNOWN fallbacks

The audit found **12 silent fallbacks** accepting semantically invalid programs. The 4 biggest (all in `SemExpressionTyper`/`MemberCallTyper`):

1. **#7 — biggest**: nonexistent method in a builtin namespace → UNKNOWN without SEM025. Only `process`/List/Map/Set fixed (`7ec8b9d`, bugs 31/34).
2. **#3 —** `obj.campoInexistente()` → UNKNOWN (SemExpressionTyper.java:312).
3. **#6 —** `super.metodoInexistente()` → UNKNOWN (MemberCallTyper.java:92) while the normal class path emits SEM025.
4. **#8 —** UNKNOWN receiver + nonexistent method → no diagnostic (SEM025 only when `isKnownReceiver`, MemberCallTyper.java:384-386).

Plan rule: inference never creates an implicit declaration; a nonexistent identifier must fail.

> **STATUS 07/09:** #7, #3, #6 **FIXED** — `unknownNamespaceMethod` + gate `isKnownReceiver`; proof `SemanticResolutionTest` 6/6. #8 is legitimate error-recovery (keep UNKNOWN). **P0 CLOSED.**

## Execution order (adjusted by the audit)

- **P0**: ~~semantic fallbacks~~ **CLOSED 07/09**.
- **P1**: GC auto-collect — cross face G-1..G-5; G-0 ✅ `356f33b9`, G-1 free-list riscv next; G-3 needs `kof_heap_root_end` (S-5-x86 bugfix queue).
- **P2**: PM lockfile+transitives; debugger locals via JDWP VariableTable; LSP hover/references via SymbolTable (go-to-definition ✅).
- **P3**: real cron (`scheduler.at`); KofScript globals via frontend.
- **P4**: ~~structured conformance suite; spec §subtyping (SG-009)~~ **CLOSED** (matrix + `ConformanceMatrixTest`/`ConformanceMatrixDocTest` CI gate; SG-009 ✅ SEM021 09/10).
- **P5**: web platform, gRPC, auto-hosting (do not start before).

## Raw evidence

See the 06/09 audit report (3 explorations): stdlib (23 areas + per-target parity), tooling (CLI 20 commands, PM, debugger, KofJS, LSP, KofScript), semantics (12 fallbacks, SEM025 coverage, pipeline).
