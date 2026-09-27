[English](PLANNING-FUTURE-AUDIT.md) | [Português](PLANNING-FUTURE-AUDIT.pt_BR.md)

# Audit: `planning-future` × `docs/development/future` (09/07)

> Question: did the branch finish implementing what the plan documents?
> **Answer (updated 09/08): partially — Phase D (Type Recovery)
> DONE in beta (`367d6c4`), FFI TIER 2.1 ported (`178c71c`), R1 ported
> (`7c7a19b`/`84c4804`). Remaining: R2 (kof.toml — collision AppManifest ×
> KofProjectConfig, design decision) and R5 (inspect --java +
> switch/athrow recovery).**
>
> Original answer (09/07): NO — it delivered most of the legacy
> migration platform (Phases A/B/C-partial/E/F/G/H, 33 tests), but Phase D did not
> exist, FFI/Codegen were DISCARDED in a merge, and the branch HEAD DID NOT
> COMPILE against the current beta.

## 1. What the branch has (125 unique commits, 16 code files)

### Legacy migration platform (Tiers 3–5, Phases A–H) — implemented

| Phase | Delivered | File | Proof |
|---|---|---|---|
| A — `kof inspect` | ✅ `.class`/`.jar` structure + IR statistics | `Inspect.java` (221) | via MigrateTest |
| B — Bytecode IR | ✅ decoder with size table, branch targets | `BytecodeReader.java` (206) | DecompileTest |
| C — Control Flow | ⚠️ PARTIAL: basic blocks, CFG, back-edge/loop, try/catch/finally; **switch/athrow opaque** (javadoc: "for now") | `BytecodeReader` + `BytecodeDecoder` (763) | `recoversWhileLoop`, try/catch |
| D — Type Recovery | ❌ NOT IMPLEMENTED *(snapshot 09/07; **✅ DONE 09/08 `367d6c4`** — `Signature` attribute + `Type.fromJvmSignature`/`fromJvmDescriptor`, see R4 below)* | `ClassFileParser` + `Type.java` | `ClassFileE2ETest.genericSignatureRecovery` |
| E — Decompiler | ✅ structural skeleton + body recovery (arithmetic, comparison, if/else-return, while, try/catch/finally); honest stub outside the subset ("never invents") | `Decompile.java` (220) | `DecompileTest` **15** |
| F — Java Translator | ✅ real subset: classes/fields/methods/records/if/while/for/strings; `equals`→`==`, no `new`, static→top-level | `Translate.java` (834) | `TranslateTest` **9** |
| G — Differential | ✅ stdout/exit/stderr + file side-effects | `Compare.java` (222) | `CompareTest` **6** (includes `divergenceDetected`) |
| H — Migration Reports | ✅ traceable report (recovered vs manual review) | `Migrate.java` (174) | `MigrateTest` **3** |
| Confidence Model | ✅ enum EXACT/WITH_METADATA/INFERRED/HEURISTIC/UNKNOWN (`7e6fbe8`) — **LOST in merge `c9fcd41`** (not in HEAD) | `Confidence.java` | used in Decompile/Migrate |

CLI registered in `Main.java`: `inspect`/`decompile`/`translate`/`compare`/`migrate` + `new` (scaffold with kof.toml).

### AppManifest / `kof new` (I1–I4 of the former APPLICATION_MODEL — decisions in `docs/development/DECISIONS.md` §D-APP)
`AppManifest.java` (182) + `CmdNew.java` (141): generates a project with `kof.toml`,
`[system]`/`[dev.ports]`, `serve` reads the toml, `build` packages.
⚠️ **Collision with beta**: beta has `KofProjectConfig` (my F2) — TWO
kof.toml parsers. Reconcile in the merge (one calls the other, or one replaces the other).

### Bug fixes (already in beta or equivalent)
#37/#40 (in beta), #47 native HTTP retry/circuit (beta has it), #48
json.decode<List<Record>> interpreter (in beta `41d989a`), #49 nested try
KofJS (in beta `5d6e68a`).

## 2. What the plan documents and the branch does NOT have

1. **Phase D — Type Recovery** (former `LEGACY_IR.md`, today `LEGACY_MIGRATION.md` §4; Tier 4.2): analysis of
   `instanceof`/`checkcast`/`new` + data flow → **zero code**.
2. **Formalized FFI (TIER 2.1)** — the branch's own reconciliation doc
   (`planning-future-reconcile.md`) claims it delivered (`extern`,
   FFI001/FFI002, `NativeFfiRuntime`/`JvmFfiRuntime`, commit `dd07cb0`),
   but **it does not exist in HEAD**: merge `c9fcd41` ("favor beta")
   discarded it. It is only in history.
   **UPDATE 21/09:** formalized FFI **landed on the platform lane (R3)** —
   `extern`/`PARSE090`, `JvmFfiRuntime`/`NativeFfiRuntime`, `FfiE2ETest`;
   roadmap §23 2.1.x ✅. The branch's reconcile claim is superseded by this
   real landing (the branch code still is not in HEAD — and does not need to be).
3. **Codegen hook (TIER 2.2)** — `CodegenStep`/`runCodegen`: same, it is not
   in HEAD. (ct-eval 2.3: `OptimizerConstantFold` already existed in
   beta — not their delivery.)
   **UPDATE 21/09:** the hook **was formalized by R4** (`CodegenStep`/
   `CodegenStepPipeline`, `CodegenStepPipelineTest` 6/6) — the branch's
   `runCodegen` is not in HEAD, but roadmap §23 2.2.2 is now ✅.
4. **`kof inspect --java`** (Java-Inspect-CLI task of the former IMPLEMENTATION_PLAN, today `roadmap.md` §23):
   not implemented (Inspect only reads `.class`).
5. **Decompiler-Confidence** (task: IR marks inferred vs exact): the enum
   existed (`7e6fbe8`) but was lost in the merge — `Decompile.java` in HEAD
   imports `dev.kof.compiler.Confidence` which **does not exist**.

## 3. Merge state (CRITICAL)

**The branch HEAD DOES NOT COMPILE even on its own** (proven in an isolated worktree
09/07 — the CLI-files break against the branch's OWN parser):
- merge `c9fcd41` ("favor beta") replaced the branch's **rich** parser
  (514 lines, with `returnTypeName()`, `parameterTypeNames()`,
  `instanceofCount`, `checkcastCount`, `m.code`/`CodeAttribute`,
  `constantPool[]`, `Instruction`, `BasicBlock`, `disassemble`) with beta's
  **poor** parser (196 lines, only `magic/thisClass/methods/fields/...`)
  — the two are byte-identical in HEAD;
- the consumers (`Decompile`/`Inspect`/`Migrate`) kept referencing
  the lost rich API → ~8 `cannot find symbol` errors + stale import
  (`dev.kof.compiler.ClassFileParser` → the SOLID move moved it to `parser.`) +
  `Confidence.java` lost (import does not resolve);
- the rich parser exists only in history: `34ded81` (Type Recovery) and
  `42d51cc` (decompiler fix), BEFORE the SOLID move `190b393`.

**Consequence for R1:** porting the platform requires restoring the rich
parser (from `34ded81`, relocated to `parser.`) + `Confidence.java`
(`7e6fbe8`) + adjusting the imports of the 5 CLI-files — it is not just "fix the import".

## 4. Update 09/07 (afternoon) — the agent went further and was declared dead

New commits on the branch (`f86d02e`/`f44b086`/`f1d6211`): only **conformance
batch 3** (`spawnawait`/`channel-fifo`/`spawnvoid` in the matrix) + DOING.
Validation (isolated worktree, 09/07):

- `spawnawait` + `channel-fifo`: ✅ pass (but they are **duplicate** — beta
  already closed F9 batches 1-3 with 45 deterministic cases, including
  `spawnawait-fn/two` and `channel-samethread/spawn/spawn-two`, and
  `KofConcurrency2Test` covers fire-and-forget with loose assertions).
- `spawnvoid` (`spawn { println("fire") }` + `println("done")` expecting
  `done\nfire` on the 4 targets): ❌ **FLAKY/BROKEN — fails 5/5 running
  isolated** (SCRIPT returns `fire\ndone`). And it contradicts beta's own
  DOCUMENTED decision (`ConformanceMatrixTest:573-574`: "the order of
  fire-and-forget is NON-deterministic by design and stays in
  KofConcurrency2Test with loose assertions"). **Do not port.**
- Migration platform: **unchanged and still broken** (DecompileTest
  does not compile in the new HEAD — same `cannot find symbol`).

**Owner status:** orphan `EM CURSO` → **DEAD AGENT** (AGENTS.md rule:
"orphan EM CURSO is ABERTO in disguise"). Its F9/conformance lane is already
CLOSED in beta (DOING beta line 416: batches 1-3 + CI gate
`ConformanceMatrixDocTest` + bugs 48-52). Nothing from the new commits needs
to be ported.

## 5. Verdict and next steps

**Verdict:** real and quality work (33 tests, R6 honesty —
"never invents", stubs with Confidence), but **incomplete** (Phase D zero,
FFI/Codegen lost) and **not mergeable as is** (HEAD broken against
beta). The final batch 3 is a duplicate of beta with a flaky case that violates
a documented decision — do not port.

**Reconciliation (queue, in order):**
1. **R1** ✅ **DONE 09/07** — port the migration platform to
   beta (`7c7a19b` R1.1 + `02faca0` R1.2): `Confidence.java` restored,
   `parser.ClassFileParser` enriched (CodeAttribute/bytecode/
   exceptionHandlers/returnTypeName/parameterTypeNames/instanceofCount/
   checkcastCount/constantPool; dead code disassemble/analyze/
   BasicBlock/OPCODES not ported — 514→316 lines, gate ≤500 OK),
   `Type.describe/fromJvmDescriptor` (34ded81), CLI (Inspect/Decompile/
   Translate/Compare/Migrate/BytecodeReader/BytecodeDecoder + 4 tests =
   33) + dispatch in Main. **3 bugs in the branch's parser fixed in the
   port** (never ran on the branch): Long/Double (tags 5/6) 8 bytes/2
   slots; MethodHandle (tag 15) 1 byte ref_kind + 1 short; tags 16/18/
   19/20 (Dynamic/InvokeDynamic/Module/Package) missing. Suite
   1142/0/64-skip green. **Rest of R1:** ✅ split 09/07 (`84c4804` — Translate 834→390 +
   TranslateLexer/TranslateExpr; BytecodeDecoder 763→395 +
   BytecodeStatements; 33/33 migration green, suite 1149/0/64-skip).
   **R1 CLOSED (1.1+1.2+1.2b).**
2. **R2** — reconcile kof.toml: `AppManifest` (branch) × `KofProjectConfig`
   (beta) — a single parser (likely: AppManifest consumes KofProjectConfig,
   or vice versa; design decision if merging semantics).
3. **R3** — decide FFI: re-port `dd07cb0` (extern/FFI001/FFI002 +
   runtimes) or register it as lost and rewrite (TIER 2.1 of the plan).
   **✅ R3 CLOSED 09/08** — the FFI was PORTED via merge `main→beta`
   (`333e385`/`b7ff7c9` + my SOLID port `6afa209`): `extern` parse
   (parser/Parser), SEM015 resolution (BuiltinCallTyper), lowering
   `kof_ffi_*` (ExpressionMethodCallLowerer), FFI001/FFI002 (R6),
   JvmFfiRuntime (FFM). **Two fixes in the port** (main's FFI never
   ran — same lesson as the rich parser): (a) `Arena.allocateUtf8String`
   (preview JDK 21 = CI) × `allocateFrom` (final JDK 22+) resolved by
   `Runtime.version()` (`178c71c`); (b) NATIVE `dlopen` segfaults on the
   raw `_start` binary (glibc without init) → honest FFI001 instead of a
   broken binary, `NativeFfiRuntime` (dead asm) removed, **bug 61**
   registered. Proof: `FfiE2ETest` 5/5 on JDK 21 AND on 25.
4. **R4** — Phase D (Type Recovery) — ✅ **DONE (09/08, `367d6c4`)**:
   `ClassFileParser` reads the `Signature` attribute at the 3 levels +
   `Type.fromJvmSignature` (recursive JVMS 4.7.9.1 parser: generics,
   wildcards, type-variables, arrays) + multi-param descriptor fix
   (`Type.parseJvmDescriptorAt`). Decompiler uses signature (EXACT) over
   descriptor. Proofs: `ClassFileE2ETest.genericSignatureRecovery` +
   `DecompileTest` 16/16; suite 1206/0/64-skip.
5. **R5** — `inspect --java` + switch/athrow recovery (complete C).
