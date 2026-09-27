[English](PLAN-TREE-SHAKING.md) | [Português](PLAN-TREE-SHAKING.pt_BR.md)

# PLAN-TREE-SHAKING.md — stdlib by reachability: the compiler includes only what the program uses

**Owner:** PLATFORM lane (front designated by the maintainer 11/09; execution on the development lane) · **Status:** S-1 (T0) ✅ 12/09 · S-2/S-2.5 ✅ 12/09 · S-3 (T1a.2, x86 pruning) ✅ 12/09 · S-4 (T1a.3, riscv64 pruning + aarch inherits) ✅ 12/09 · S-5 (T1b, gc-sections) ✅ CROSS PART 12/09 · S-6 (T2 JS) ✅ 12/09 — **S-6.1 ✅ 12/09** (hello JS 177,412→6,873 B, merged `0104f6d6` PR #106) · **S-7 ✅ 13/09** (consolidated in `docs/stdlib/stdlib-loading.md`, linked from `docs/stdlib/stdlib.md`) · **PLAN COMPLETE — move to `docs/`** · **Created:** 12/09 · **Issue:** #97

> **Fundamental rule:** the developer declares what they intend to use; the
> compiler includes **only** what is really necessary to run
> the program. No micromanaging dependencies, no
> `--include=json.parser`, no manual list to avoid bloat.

This plan is **measured analysis** (code + binaries from this session, cross
toolchain active), not memory. Every statement below has reproducible evidence.
**Acceptance given by the maintainer via issue #97 (12/09)** — the briefing "plan
BEFORE implementing" was fulfilled (this doc + measured numbers) and the issue
opened with the plan in scope IS the acceptance; steps T0–T1b do not change the
language contract (additive). **S-1 (T0) ✅ DONE 12/09 (S-2 onward, queue below).**

---

## 1. The problem, measured today (12/09, `beta-0.4.0` @ `c14c1808`)

Minimal program — `main() { println("hello") }` — compiled for each target:

| Target | Artifact | Size | `kof_*` symbols in the binary |
|---|---|---|---|
| Native x86_64 | Static ELF | **138,776 B** (`size`: text 75845 / data 19437 / bss 71224) | **605** (unique strings) |
| Native riscv64 | ELF | **143,832 B** | **254** (`nm`), `.bss` ≈ 260 KB |
| JS | `kof-runtime.mjs` | **173,366 B** + 287 B of the program | the whole file |
| JVM | `Main.class` | **425 B** | n/a (see §2.4) |

The x86_64 "hello" binary contains, verified by `strings`: `kof_jwt_*`,
`kof_sec_*` (AES-GCM, PBKDF2, BCrypt…), `kof_vk_*` (Vulkan stubs), `kof_web*`
(HTTP server), `kof_mq_*`, `kof_channel_*`, `kof_cache_*`, DB, URI,
math-double, random, uuid, observability — **none of this is reachable from the
program**. It is exactly the scenario the maintainer's briefing wants to
eliminate, and the risk table of `docs/architecture/UNIVERSAL-PLATFORM-VISION.md:1168` already
anticipates it: *"Bloated stdlib → capability/link by usage"*.

The 11/09 numbers in the cross E2E (riscv 34/0, aarch 34/0) hold for
*correctness*; for *size*, nothing changed since then.

## 2. Real state of the architecture (verified in the code, not in the doc)

### 2.1 Imports (`CompilerImports.expandKofImports`)

`import kof.json` is **not** a builtin namespace: it is resolution by
**directory** in the module filesystem (`moduleRoot/kof/json/*.kf`), with a
transitive closure by BFS and wildcards (user `.java` importable, PKG006 for what
does not resolve, `--classpath` for external dependencies — §134). For the
**embedded stdlib** (`kof.math`, `kof.json` as builtin, `kof.security`…), the
import is decorative: the constructs already resolve by name in the typer/lowerer
(`KofStd`/`KofMath`/`KofSecurity`…), with no gate by import. Measured: the same
`json.decode/encode` program compiles **with and without** `import kof.json`
(JVM/Native/JS) — the import neither enables nor includes anything.

**Consequence for the plan:** "imported ≠ used" is already true today in the
frontend — the import is not the source of inclusion. Inclusion happens **in the
backend**, and it is unconditional. That is where the work is.

### 2.2 Native x86_64 — unconditional monolithic emission

`NativeBackend` (line 342) emits `NativeRuntime.generateRuntimeAssembly()`
**in full** in every program: ~90 `RuntimeXxx.emit(sb)` calls in a fixed
sequence (print, string-ops, list/map/set, json, alloc/GC, concurrency/channel/
scheduler/mq, net, db, security×12, validation, uri, math, strings, encoding,
uuid, random, observability, vk, ui, web, cache, config, log…). All in a single
`.section .text`.

### 2.3 Native riscv64/aarch64 — same, in concatenated slices

`NativeArchEmitter:119/248`: `RISCV_RUNTIME_ASM` + `RISCV_STRN002_ASM` +
`RISCV_RUNTIME_ASM_B` (B0…B38) + `RISCV_MAPSET_ASM` — **always everything**. The
aarch64 is the riscv text translated instruction by instruction.

**The precedent that makes the plan feasible:** `NativeRiscvSpawn.usesSpawn
(module)` — a scan of the IR looking for `KofCall` with `kof_spawn/kof_spawn_result/
kof_await` — **already conditions** the emission of the spawn slice
(`if (usesSpawn) nb.emitRiscvSpawn(sb)`). The mechanism of *reachability analysis
by IR scan + conditional emission per slice* exists and
is green in the tests. What is missing is generalizing from 1 slice to all.

### 2.4 JVM — not bloated by construction

`JvmRuntime.generate` creates a `KofRuntime.java` with **static methods per
family**; the class is compiled with the program and the JVM loads/resolves
methods on demand (bytecode is lazy per method — the "hello" `Main.class` is
425 B). `hasRuntimeFn` (29 prefixed families) decides the **dispatch** in the
emitter, not the inclusion. The only cost is the `KofRuntime` `.class`
(methods not called remain in the jar). Low priority; see §6.4.

### 2.5 JS — full copy of the module

`JsArtifactWriter:26-62`: writes `kof-runtime.mjs` concatenating **all**
`JsRuntimeUi*.JS` sources (core, components, widgets, forms, layout, web,
support, security, crypto, validation, stdlib, random, math-double, net, uuid,
**ws** (indent/dedent — S3.3), events). 173 KB per program. ESM with `export
function` per function — **a bundler/rollup would tree-shake for free**; the
selection by usage in the writer is the path with no new dependency.

### 2.6 Linker — DCE impossible by construction

`NativeAssembler:36-45` calls `ld` without `--gc-sections`, and the asm is emitted with
a bare `.section .text` (without `"funcName"` per section) — therefore, even with the flag,
nothing would be prunable: **1 section for everything**. The global `.space` (pools) stay
in `.bss`/`.data` just the same. The `-lc` and `-dynamic-linker` are hardcoded
in the dynamic path — which today **prevents** the embedded/bare-metal target (§6.5).

### 2.7 Conservative GC — the coupling that pruning must respect

`RuntimeGc` scans **`kof_heap_root_start .. _end`** as static roots
(x86: `:50-52`; the start marker is emitted at the top of
`generateRuntimeAssembly`). A pool in `.bss`/`.data` of a module **not included**
has no live pointers (the module never runs) — the scan remains safe
if the pruning is by **whole slice** (the pruned area disappears from the interval). But
this needs its own test (T0), because a runtime `.data` **with
initializers** (e.g., crypto tables with pointers to strings) can become an orphan
root if the pruning separates data from code in the wrong way. The safe rule:
**code and data of a slice enter/exit together**.

---

## 3. Strategy — three layers that add up (you do not choose one)

```
Kof source ──frontend──▶ IR ──reachability──▶ inclusion plan ──emitter──▶ .s per slice
                              (T1a)                                              │
                                                                       ld ── --gc-sections ──▶ binary
                                                                              (T1b, safety net)
```

- **T1a — selection at the source (slice).** The compiler scans the IR, collects the
  `kof_*` names called (and the synthetic `KofCall`s already named: `kof_json_*`,
  `kof_sec_*`, …), closes the **transitive closure** over a *dependency map
  per slice* and emits only the live slices. It is `usesSpawn` generalized: the
  dependency map is **data declared by each slice's emitter**
  (e.g., `RuntimeSecurity1` declares `provides: kof_sha256…`, `needs:
  kof_alloc, kof_string_concat…`), never string-sniffing in the asm.
- **T1b — safety net in the linker.** Each emitted function gets
  `.section .text.kof_jwt_create, "ax"` (a pattern already used in asm of
  compiler-generated code today does not exist — it is a change in the emitter, not in the
  handwritten asm) and `ld --gc-sections`. It caught what T1a's analysis got wrong
  (over-inclusion), prunes at link. It never prunes what is necessary:
  `--gc-sections` starts from the reachable `globl` symbols of `_start`/entry.
- **T1b does not replace T1a:** the pools' `.bss` is reserved by `.space` in
  the slice body — its own section solves it, but the useful size comes from knowing
  *which* pools exist (an mq of 64 handles is not allocated if there is no mq).

## 4. Import ≠ included ≠ reachable — how the semantics turn out

The maintainer's conceptual request, translated to the real architecture:

| Layer | Today | Target |
|---|---|---|
| `import kof` / `import kof.crypto` (embedded stdlib) | decorative (builtins resolve without import) | **invariant**: import never enlarges the binary; it only enables names |
| import of a `.kf` package in a directory (`CompilerImports`) | brings the **whole file** that imports it (BFS closes the graph, but per *file*, not per symbol) | coarse grain acceptable in T1; per-symbol is T2 (move unused decls) |
| IR → backend | `usesSpawn` for 1 slice; everything unconditional in the rest | transitive closure per slice (T1a) |
| link | monosection, no gc | sections per function + `--gc-sections` (T1b) |

## 5. Dynamic imports — the three cases, with policy

1. **Static** (99%): nominal call — resolved in the typer → named `KofCall`
   → T1a reachability. Nothing changes for the user.
2. **Knowable at compile time** (finite set): `match`/dispatch over a
   construct of the program itself (e.g., an OTP supervisor calling `fabrica.novo()`
   via vtable) — the target is the **class method**, already in the native
   emitter's vtable graph (`findVirtualMethodIndex`). The user class vtable is already
   emitted per reachable class; the stdlib has no dispatch by string today.
3. **Truly dynamic**: in Kof it **only exists via FFI** — `extern` with a
   symbol name in a string (`ExpressionMethodCallLowerer`, fixed helpers
   `kof_ffi_i`/`kof_ffi_si`/`kof_ffi_dd`). It is not stdlib: the symbol resolves in the
   system `-lc`/`.so` at runtime. Policy: the 3 FFI stubs always
   included (cost ≈ 0), and nothing more.

**If one day** the language gains reflection by string (`sec.getAlgorithm(name)`)
— which today it **does not have** — the plan's rule is R6: either the set of targets is
statically declarable (register by capability, the compiler includes the minimal
registration), or a **compile-time diagnostic** on the target that cannot resolve
dynamically (Native/embedded), never a silent "include everything". There is no
mechanism to preserve: there is no mechanism.

## 6. Implementable steps (order = value/risk)

### T0 — size harness (FIRST, without it nothing is validatable)

- `kof build --print-sizes` (CLI, additive) → JSON: bytes per section + count
  of `kof_*` symbols in the artifact.
- Test `ArtifactSizeTest` (kof-cli or compiler): golden with **regression
  tolerance** — the native `Hello` must fall from 138 KB to ≤ 60 KB in complete
  T1a (goal), ≤ 45 KB in T1a+T1b; JS ≤ 40 KB. Gate: any increase >5%
  in the *hello* or in *json-only* breaks the build (model: `ConformanceMatrixDocTest`
  — doc/measurement locked by test; the lesson of #89: without a guard, the thing rots).
- Utility: it is the instrument that turns "I thought it got smaller" into proof.

### T1a — live slices in Native (highest value, zero asm risk)

1. Map parser: each slice emitter (x86 `runtime/RuntimeXxx`, riscv
   `RtBxx`) starts declaring `provides[]/needs[]` (a `String[]` const in the emitter's
   Java — the `NativeRiscvAsm` chain becomes a list of named slices).
   Mechanical refactor, precedents: `RuntimeStrings.emit` and `usesSpawn`.
2. `Reachability`: scans `IRModule` → `KofCall.methodName()` + mandatory
   entrypoints (`_start`, `kof_alloc`, `kof_panic`, `kof_print*` when the
   program uses print — print **is** called, it enters through the graph) → BFS on the
   map → minimal `Set<slice>`.
3. `NativeBackend`/`NativeArchEmitter`: emit only `Set<slice>` (the current
   order of the `.append`s is preserved per subset). GC roots: the
   `root_start.._end` scan is by real labels of the emitted text — if a slice exits,
   its data label exits too (rule: code+data of a slice are solidary).
4. Proof per slice: E2E test per family (crypto/json/db/mq/web/ui/vk…) with a
   program that uses X and an `nm` assertion that Y **is not** present —
   exactly the briefing's tests (`import kof.crypto` + only AES ⇒ no
   `kof_jwt_*`; only string ⇒ no crypto).

### T1b — gc-sections at link (after T1a is green, low risk)

- `.section .text.<sym>, "ax"` per emitted function (helper in the x86 emitter:
  the `.globl`/label lines are already known — a centralized `emitFunctionPrologue(name)`
  in the ~90 emitters; riscv same, the translator passes it through).
- `ld --gc-sections` (and keep `-lc` in the dynamic path). A slice's `.data`/`.bss`
  get `.section .data.<sym>` likewise.
- Known risk: GC scans `root_start.._end` — if the linker reorders sections
  between the two labels, the interval can swallow dead sections (harmless:
  zeros) or lose live ones (dangerous: a live pointer not marked → UAF). Safety
  measure: the two labels become `.section .gcroots,"a"` first/last
  with `.globl`, and the link script preserves the order — or (simple fallback)
  the root-scan starts using a dedicated `.koroots` with only the real static
  pointers of the emitted slices (the "code+data solidary" rule was already
  implemented in T1a).

### T2 — JS by top-unit reachability

- **Family does not work as a cut** (measured 12/09): the closure of a
  `println("hello")` per block takes 16 of the 18 blocks (89.6% of the bytes). The
  blocks get tangled (core→ui-layout→ui-widgets→ui-web→io; stdlib→security)
  because the cut at 17 constants came from the javac pool's 64 KiB limit,
  not from a semantic boundary — the same vice as `RtB0..B31` in the cross.
- **Cut = top declaration** (`function`/`class`/`const` at column 0 after
  dedenting each block): `js/JsRuntimeSlices` inventories 600 units /
  585 names, with `provides`/`needs` per token and deterministic transitive closure
  (output in inventory order, never in search order). The dedent serves only
  to find the boundary — the emitted text is the original, byte by byte.
- **Seeds = `runtimeImports`/`ioRuntimeImports`** that `JsBackend` already
  accumulated for the module's `import { … }` line: exact, without heuristics.
- **Multi-module:** the shared runtime is the union of the closures, re-read from the
  artifact's `// kof:seeds` header and rewritten when a later module
  requires more (before, the first module decided the content of the following ones).
- **Observable conservative fallback:** `eval(`, `new Function(`, dynamic `import(`,
  `globalThis[`, `window[`, a runtime name inside a literal or an unbalanced
  unit make the whole block enter, with
  `// kof:fallback <block>: <reason>` in the header. Today no guard fires.
- **Result:** hello 177,412→6,873 B (−96.1%); kof.ui 177,125→13,889 B.

### T3 — embedded/MCU (document the route, do NOT promise it now)

The MCU "small binary" target runs today into 3 measured things:
(a) hardcoded `-lc` + `-dynamic-linker`; (b) direct Linux syscalls on the riscv
face (`write`/`mmap`/`futex` — the port to `probe-rs`/semihosting is a project
of its own); (c) bump-pointer heap + mark-sweep GC (`.space` pools). The plan
declares: **no `profile minimal` without T1a+T1b ready**, and even then
real embedded requires an RTOS/bare-metal backend — it stays in `future/` with no
scheduled step. What T1 delivers for it today: the runtime **subsettable per slice**
(the architecture that prevents the monolith), which is exactly the briefing's
requirement ("the solution cannot depend on a monolithic runtime").
**Route now recorded (15/09 maintainer directive):** the three measured blockers
above are decomposed as faces **B-0…B-5** (HAL seam `kof_plat_*` + freestanding
profile + UEFI/BIOS/MCU + x86_64 ring0/ring1) in
`docs/PLAN-BAREMETAL-BOOT.md` — **IN DEVELOPMENT since 22/09**
(`D-BAREMETAL-BOOT`; R12 overridden for this front).

### T4 — JVM (low priority, honesty)

No runtime cost today (lazy per method). The only bloat is the complete
`KofRuntime` in the jar; `jlink`/`ProGuard` solve it externally. The plan **does
not** promise a minimal jar; it records it as out-of-scope with justification.

## 7. What is NOT a decision of this plan (rule 6 — it belongs to the maintainer)

1. **Format of the slice map** (Java const vs data file) — implementation
   decision of the lane, free, no contract.
2. **T1b touching the GC root-scan** — touches the GC mechanism (frozen): if the
   `.koroots` fallback needs to change the current invariant, it is a bump/discussion.
3. **Profiles (`--profile minimal/embedded`)**: they only *complement* the automatic
   analysis (an extra restriction of capabilities), never replace it. Promote the
   CLI surface decision.
4. **Directory import per symbol** (move only what is used, not the file):
   changes the top-level side-effect semantics of a `.kf` package — a decision.

## 8. DoD (what closes this plan) and queue per session

**Delivery unit** = step with green test + full suite (with the
`failure.ignore` flag of the verification rule). Execution order of the queue
after acceptance of the §T:

1. **S-1 (T0)** ✅ **DONE 12/09** — `dev.kof.compiler.ArtifactSize` (pure-Java ELF64
   parser: section→bytes map + count of `kof_*` symbols DEFINED in `.symtab`;
   `jsBytes` sums the `.mjs`), test `ArtifactSizeTest` (3 gates: hello x86 138,928B/627 syms;
   runtime JS 177,412B; hello riscv 144,000B/258 syms `assumeToolchain`; +5% UNILATERAL
   tolerance for bloat — shrinking is the goal, baseline sabotage → FAIL proven) and
   `kof build --print-sizes` (stable JSON, additive — without the flag, build unchanged).
   Numbers of this doc reproduced by automated test ✔ (651 vs 627: the issue counted `nm`
   with imports; the harness defines "DEFINED in the symtab", which T1a will bring down —
   gate locked to the harness measurement).
  2. **S-2 (T1a.1)** ✅ **DONE 12/09** — slice map by **REFLECTION derived from the
   production source** (lane implementation free, §7.1: the map is not a `const` transcribed
   by hand — `dev.kof.compiler.nat.RuntimeSlices` reads the body of
   `NativeRuntime.generateRuntimeAssembly` and extracts the order of the 113
   `RuntimeXxx.emitYyy(sb)` calls; reordering/inserting/removing in the source without
   updating ANYTHING → the parity test breaks. `provides` = `.globl`/`kof_*` labels
   (`#` comments struck out), `needs` = external references; non-slice symbols modeled:
   GC preamble + `programSideSymbols()` (`kof_super_table`, emitted by Main.s). Proof:
   `NativeRuntimeSliceRegistryTest` 5/5 — concatenation **byte-identical** to the production
   `generateRuntimeAssembly()` (stronger than "identical bins": zero change in the `.s`),
   1 owner per symbol, needs closed in the map, and the number that opens S-3: **closure
   of hello (kof-only) = 6 slices / 14 of 611 symbols; the `.L`-aware precursor (12/09)
   unifies the 119 local edges and takes the real floor to 10 slices / 18 symbols —
   proving that kof-only is unsafe** — the ~600 remaining (crypto/web/mq/vk/security…)
   are exactly what the pruning has to reach.
  3. **S-3 (T1a.2)** ✅ **DONE 12/09** — x86 pruning by reachability, **seed by program
   TEXT** (the correction from measured caution: `instanceof`/`checkcast`/array emit
   `call kof_*` as raw text in `NativeMethodEmitter:303`, NOT via `KofCall` — scanning only
   the IR would lose real seeds and break the link). `RuntimeSlices.textKofSeeds/textLocalSeeds/
   keepForProgramText` + `renderSubset(keep)`; `NativeBackend.pruneRuntime` (marks
   `rtStart/rtEnd` in the concatenation region; on the `.s` write it reconstructs:
   head + subset in the SAME order as S-2 + `.section .text` + tail). keep =
   `mandatoryRoots()` (unified floor 10/18, from S-2.5) ∪ `.L`-aware closure. **Safety
   properties:** (a) keep-all → ORIGINAL text byte-identical (fallback = pre-S-3);
   (b) exception in the map → complete runtime + warning (never a silently broken
   link); (c) text seed errs only for MORE (a user literal containing `kof_mq_...`
   over-includes — bigger binary, valid link); a false-negative of a real call site is
   impossible; (d) tail (init/DB/HTTP/Web/methods/start) is NEVER pruned — it is the
   program, not the runtime. **Measured numbers:** hello **13/113 slices, 627→37 syms,
   138,928B→32,520B (−77% binary, −94% symbols)**; crypto-only 15/113 (brings
   `kof_sec_sha256*`, ZERO json/mq/vk/random); json-only 20/113 (13 `kof_json_*`,
   ZERO `kof_sec_sha256`); `coll` 23/113. **Proof:** `NativeE2ETest` 64/64 byte-identical
   WITH pruning on (running > measuring: the SAME x86 goldens come out of the pruned
   binaries) + `ArtifactSizeTest` 4/4 with a NEW baseline locked (unilateral; goes back to
   protecting against regression from 32,520B/37; floor became `<100`) +
   `nativeFamilyAbsenceAfterPrune` (T1a.4: REAL names from the map — anti-vacuum: sabotaged
   with pruning off it FAILS, proven) + riscv/aarch 39/39 intact (their `emit()` returns
   before the x86 site — S-4 handles the cross). Suite 4-module **1569/0** (+2 tests).
   check_500 with no new violator (NativeBackend was already a baseline violator, 621→664:
   extracting `pruneRuntime` to a pruning class stays in the ≤500 queue).
    - **⚠️ DISCOVERY 12/09 (measured — changes the BFS design):** the runtime has **119
    cross-references to LOCAL `.L*` labels** between slices that only work today because
    everything is concatenated into a single `.s` (e.g., `emit_alloc`/`emit_gc` — slices
    20/22 — reference `.Lkof_alloc_count`/`.Lkof_free_*` DEFINED in the `memstats` slice
    (62); `string_to_long/double` (5/6) + `json_encode` (14) use `.Lfmt_float/.Lfmt_double`
    from slice 3; `string_base` (39) uses `.Lkof_null_str` from 34; the json-decode slices
    16/17 share `.Ljad_f64_*`). The `needs[]` of S-2 tracks only `kof_*` (globl) → **a
    purely-kof BFS can prune the owner slice of an `.L` read by a live slice → `as` breaks
    (undefined label). SOLUTION (implemented in S-3 itself, precursor already coded in
    `RuntimeSlices`): the reachability BFS operates on the UNITED kof-needs ∪ local-needs
    graph** (`localNeeds()` = referenced `.L`, `localProvides()` = `.L` defined per line;
    `closureFrom(seeds, includeLocal=true)`) — a slice that reads an `.L` from another
    depends on it even without calling a `kof_` of its own. The pillar test (S-3)
    REQUIRES: for every set of IR seeds, the .L-aware closure is always a SUPERSET of the
    kof closure — and a real case (alloc without memstats) proves that kof-only is
    INSUFFICIENT (see `NativeRuntimeSliceRegistryTest.localLabelEdgesExistAndKofOnlyClosureIsUnsafe`).
    Grouping into super-slices would be the alternative, but it loses granularity — the
    .L-aware BFS is the path.
 4. **S-4 (T1a.3)** ✅ **DONE 12/09** (S-4.1 registry `RiscvSlices` + S-4.2 riscv64
   pruning, aarch64 inherits through the translator) — the riscv port of S-3: marks
   `rtStart/rtEnd` in the runtime concatenation (after `_start`, before the http/spawn
   tail), and on the `.s` write it reconstructs head + `RiscvSlices.renderSubset(keep)` +
   `.section .text` + tail; aarch prunes the `riscvSb` **before** the translator (one
   pruning → both arch). keep = print/panic/alloc floor (7/48) ∪ UNIFIED kof∪.L closure
   (33 cross-piece edges). **Finding of the proof (the port's lesson):** the kof-only model
   of S-4.1 was BLIND to 7 method symbols without a prefix (`String_compareTo`/`String_hashCode`/
   `String_equals`, `kdv_epoch`/`kdv_valid`, `_kof_heap`/`_kof_strings_joinWords`)
   called by the program's lowering AND between pieces → undefined reference in ld
   (4 riscv + 4 aarch tests caught on the first run). Generalized to the complete
   vocabulary in 2 passes (collect globls/labels from ALL pieces, then needs/seed
   by intersection with the vocabulary — safe over-inclusion, false-negative of a real call
   site impossible). Byte-identical when keep-all (pre-S-4 fallback).
   **Measured numbers:** hello riscv 258→**103 syms** (−60%); bytes only fall
   144,000→136,792 (−5%) because the bump heap in `.bss` (~260KB) is FIXED without GC
   mark-sweep — the real riscv drop is in SYMBOLS (≠ x86, where both fell).
   **Proof:** `NativeRiscv64E2ETest`+`NativeAarch64E2ETest` 39/39+39/39 under REAL qemu
   with pruning on (the SAME cross goldens come out of the pruned binaries) +
   `NativeRiscvRuntimeSliceRegistryTest` 6/6 (byte-identical parity of the expanded model;
   floor ≤10) + `ArtifactSizeTest` 5/5 with a NEW riscv baseline locked
   (136792B/103, unilateral) + `riscvFamilyAbsenceAfterPrune` (T1a.4 cross: json-only
   pulls `kof_json_encode_int` and mq/vk/random stay PRUNED; SABOTAGE with pruning off
   → FAIL proven, real list of ~230 syms). Cross suite + gate 4/4→5/5 green.
   check_500 with no new violator (NativeArchEmitter 337, RiscvSlices 375 — both <500).
5. **S-5 (T1b)** ✅ **CROSS PART DONE 12/09** — sections per function +
   `--gc-sections`. `NativeArchEmitter.sectionizeTextFunctions` opens
   `.section .text.<fn>,"ax"` per globl function of the kept subset (S-4);
   `ld --gc-sections` enabled ONLY on riscv64+aarch64. **Measured numbers:**
   hello riscv **103→18 syms (−82%)**, 136,792→133,288B (−2.6%); aarch
   18 syms / 133,112B (aarch64 baseline locked for the 1st time). Bytes only
   fall 2.6% because the bump heap's `.bss` (~260KB) is fixed without mark-sweep —
   the REAL drop is in symbols. **Safety:** there is NO GC in the cross asm
   (bump-pointer), so no hidden conservative root can depend on a
   symbol without reloc; the `ArtifactSizeTest` (cross 39/39+39/39 under qemu +
   size gate) is the proof that nothing live was collected. **x86 PART
   POSTPONED (in this commit):** `--gc-sections` on x86 requires `kof_heap_root_end`
   + moving `emitStaticData` to INSIDE the roots interval (the conservative
   scan scans `root_start.._end`; a `.data`/`.bss` of a dead slice
   deleted outside the interval is a root the collector never sees) — it is the bugfix
   queue, not this lane. gate: `ArtifactSizeTest` riscv/aarch new goals
   locked unilaterally (hello x86 ≤ 45 KB — **ALREADY MET in S-3: 32,520B**; the
   x86 step of S-5 is now optional/extra, the real value was the cross ✔).
6. **S-6 (T2)** JS — **DESIGN APPROVED BY THE MAINTAINER in #97 (12/09):**
   granularity is a **top unit**, not a family (family closes 89.6% of the
   runtime in a hello — it does not work as a semantic cut). Seeds =
   `runtimeImports`/`ioRuntimeImports` that `JsBackend` already accumulates (exact,
   without heuristics); the chunker registers `provides`/`needs` per token (same
   design as the native `RuntimeSlices`); BFS emits only the closure **in the original
   order**; multi-module = **union of the closures + rewrite** of the
   shared runtime (never "first module wins"); **observable conservative**
   fallback (whole non-analyzable unit + reason in the
   build); **deterministic** (same input → same set → same order
   → same artifact; forbidden to depend on HashMap/BFS order). Baselines
   measured by the proposer: hello 177,125→**6,202 B**, UI (worst case)
   13,335 B. Pre-existing bug discovered along the way (`kof_platform`
   cross-module in `-io.mjs`, ReferenceError in uuid/random on Node) goes
   to a separate issue — do not mix it into the T2 PR. Gate: `ArtifactSize.jsBytes`
   + absence per family (hello ⇒ no `kofSec*`/`kofUi*`; crypto ⇒
   `kofSecSha256` without `kofUiWindowNew`), unilateral tolerance.
   - **S-6.1a ✅ 12/09** — `js/JsRuntimeSlices`: inventory per top
     unit; with all seeds live the selection is byte-identical to the
     legacy concatenation of the 17 blocks (`JsRuntimeSliceRegistryTest` 6/6).
   - **S-6.1b ✅ 12/09** — writer enabled: multi-module union via the header
     `// kof:seeds`, observable header (`kof:units`/`kof:fallback`), hello
     **177,412 → 6,873 B** locked in `ArtifactSizeTest` (the assertion
     `js > 100_000` became `js < 30_000`), `JsRuntimePruneWriterTest` 6/6.
     `kof_platform` went to issue #104 — pruning preserves the behavior.
   - **S-6.1c ✅ 12/09** — branch×base gate + test probe (`JsRuntimeTestSupport`
     declares the probe's imports as seeds through the multi-module union;
     `KofJsBrowserE2ETest` 22/22 real Chrome, `KofJsE2ETest` 40/40) —
     merged `0104f6d6` (PR #106) + addendum #107 (comment removal,
     merge `97ad0a9c`).
   - **Remaining:** nothing — S-7 consolidated into `docs/stdlib/stdlib-loading.md` 13/09.
7. **S-7** ✅ **DONE 13/09** — docs consolidated in
   `docs/stdlib/stdlib-loading.md` (mechanism per target, locked numbers,
   honest limits, code references) + link in
   `docs/stdlib/stdlib.md` §2; this plan goes to `docs/` (rule of the three
   states: completed work).

Each session: 1 step, commit, DOING.md updated in the SAME commit.

## 9. Expected result of the briefing → where this plan answers

| Request | Answer (section) |
|---|---|
| 1. How imports are resolved today | §2.1 (measured: import of embedded stdlib is decorative; `.kf` directory closes per file; external `--classpath` §134) |
| 2. Points of excessive inclusion | §1 (numbers) + §2.2/2.3/2.5/2.6 (unconditional emission; `ld` without gc-sections; JS copies everything) |
| 3. Reachability strategy | §3+§2.3-precedent (generalize `usesSpawn`: IR→closure per slice; declarative map per emitter) |
| 4. Effective implementation this cycle | this doc + issue #97; **zero code without §7 acceptance** — the briefing mandates planning BEFORE implementing; steps T0–T1a are ready for execution in the 1st session with acceptance |
| 5. Dynamic imports | §5 (the 3 cases; in Kof only FFI is dynamic; policy R6: never include everything silently) |
| 6. Automated tests | §6-T0 (size gate with tolerance, model `ConformanceMatrixDocTest`) + T1a.4 (per family: `import kof.crypto`+AES ⇒ absence of `kof_jwt_*`) |
| 7. Evidence that broad does not bloat | §1 (current state, honest: it BLOATS) — the goal with the per-step goals; final proof will be the green T0 harness with the new numbers |
| 8. Consolidated docs | §8-S-7 |
| 9. Depends on future work | §6-T3 (real embedded: without `-lc`, RTOS backend, predictable GC — out of scope, declared route), §7.3–7.4 (profiles and import per symbol: maintainer decisions), §6-T4 (minimal JVM jar: out-of-scope with justification) |

**Non-cascade:** this doc is ONE plan, with measured numbers and an executable
queue — there is no other "plan audit" or "meta-plan" doc. The next
artifact is code (S-1).
