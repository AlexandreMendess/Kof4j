[English](status.md) | [Português](status.pt_BR.md)

# Kof Project Status

last: native-record-equality
doing: 0.5.0-release-prep
next: post-0-5-0-kof-libraries
location: status
state: active
constraint: pr619-maintainer-only
decision: D-KOF-FIRST-IMPL

**Last updated:** September 24, 2026
**Version:** 0.5.0-beta (pom `revision`)

**24/09 — CONSOLIDATION OF THE 21/09→24/09 WINDOW (measured against `git log`/tips; full-reactor suite 3818 run / 0F / 0E on 24/09; the `beta-0.5.0 → main` release PR **#619** is OPEN and is the MAINTAINER'S to merge — new rule 10 of AGENTS.md: no agent merges/approves/closes it under any circumstance).**
>
> - **Record equality on Native is COMPLETE (the §104b/§114 family closed)** — 24/09, lane compiler/nat 9092 + issues lane: `List.contains`/`Set.contains`/`set.add` dedup compare records by CONTENT (tag 2 → `kof_obj_equals` + `kof_equals_table`, `4cce594e7`); `Map` keys by content (`f31ac11f4`, `mapKeyTag`); `containsValue(record)` by content — value-side tag 7 landed from contributor **Publio Santos' PR #616** (verified RED 3/3 → GREEN 3/3 on x86-64+riscv64+aarch64, `b7c13ba1f`, **#615 closed on the official evidence path**); synthesized `equals` handles nested record fields (`523dfabb5`); `hashCode` faces String/Double/nested-record all by content (`67acf5a77`/`e2f0629f6`/`2e90aa7c2`). E2E oracle `NativeRecordCollectionEqualityE2ETest` + `NativeRecordHashCodeE2ETest` byte-identical JVM≡Native on 3 arches.
> - **Windows test-harness parity closed (contributor Jonas Rocha's issues, fixed by the issues lane)**: **#612** — every child-JVM `-cp` in tests now uses `java.io.File.pathSeparator` (18 files) + a static guard (`ClasspathSeparatorGuardTest`) that fails naming `file:line` on regression (`cdbf45145`); **#603** — `KofOrmE2ETest` 18 Windows reds fixed: `kofPath()` puts forward slashes in Kof string literals (raw `\` collapsed as escapes by the documented lexical rule — the language was right, the harness wasn't), `/tmp` → `tempDir`, child stderr now IN the failure message, Postgres assumes by real credentials (`970c847a`); **#617** — `File.mkdir()` is no longer a silent no-op + `ClassFormatError`: unknown members on kof.io builtins are a clean compile-time **SEM102** with the `Directory.createDirectories()` hint (`aed5fe7b4`); **#618** triaged NOT-a-bug (kof run's file argument = its directory is the module, by design; documented EN+PT in `learn/32-cli-tooling`). **Zero open issues.**
> - **Native hardening**: §485 — channel `receive` drained the queue without resetting `tail` → deterministic NULL SIGSEGV on x86_64+riscv64+aarch64, repro single-threaded (`7f5a2e054`); §486 closed (covariant-return bridges, both faces); **B-3b baremetal: the real Kof payload RUNS through the BIOS path under SeaBIOS** (`KO-BIOS PAYLOAD`, `e3644d596` — 5 bugs found+fixed in the measurement, `RuntimeMemory` BIOS arena allocator included).
> - **Tech-debt lifecycle opened and closed in 48h (maintainer-owned)**: the `tech-debt.md` ledger opened 23/09 with 6 live §NNN — **all six measured ✅ by 24/09** (§205 boxed-print, §248 default methods on 4 targets, §271 generic-interface bridges, §278 Android `kof.security` JCA face + `kof.gpu` stub, §283 aarch64 scheduler exit, §423 channels ported to cross) and `check_500` green → **maintainer killed the ledger, the `debt-scout` tooling (38 files: contract, detectors, 13 test scripts, scheduled workflow) and the `technical-debt/` folder** (`f4a987166`). DECISIONS keep the history annotated KILLED.
> - **Docs organization (three-states rule applied by maintainer order)**: the two RATIFIED proposals left `docs/development/` — `PROPOSAL-1.0-EXIT-GATE` → `docs/` (the normative 1.0 contract) and `PROPOSAL-VERSIONING-RELEASE` → `docs/distribution/` beside the operative `VERSIONING.md` (materialized by PR #582 on 22/09); X5/X6 type-variance marked IMPLEMENTED (release-gate cond.2 GREEN); `check_release_050_gate` allowlist tightened → `loose_docs GREEN`.
> - **Contributor flow (all closed with proof, authors credited)**: #608/#614/#609 (Publio's record+generic-interface JVM face landed by hand as `081202e0c` after rebase conflicts, authorship preserved), #610/#611 (conflicting-default diamond → SEM101 + arity overloading, `374b2b4bb`), #604/#605 (CodeQL gate counts only CodeQL), #613 (native face of the bridge family, independent-verifier HIGH-risk path).
> - **Release prep (0.5.0)**: `release-beta-0.5.0-prep` tracks the cut; the branch tip's CI bots (quality/security/warning) green; remaining gate state per `scripts/check_release_050_gate.sh` (parity/bugs_gaps = environment/measured-lane, decisions GREEN, edges GREEN).

>
> **20/09 — CONSOLIDATION OF THE 19/09→20/09 NIGHT (docs lane, measured against `git log`/tip `1080238f`; CI `Build+Tests` green on the night tips, full-reactor tally ~3034 tests 0F/0E per `.18`'s 23:5x run).**
>
> - **D-TROOL CLOSED — `Bool` stays 2-valued; `Troolean` (`true/false/null`) is the language's Kleene three-valued type** (maintainer decision 19/09, roadmap 2.6.5–2.6.7 ✅, DECISIONS §D-TROOL; landed `916b9fb7`/`d61836eb`/`5f0757e8`): user-written `Bool?` is rejected (SEM095) with the Troolean pointer; `&&`/`||`/`!` desugar to strict-Kleene chains on the boxed machine (backends untouched); `if (t)` ≡ `if (t == true)`; law test `TrooleanLawE2ETest` 13/13; corpus synchronized (`training/language/types.md`, fake-idioms, CHANGELOG, backend-parity) — EN+PT.
> - **The JVM erasure river landed (families §355/§356/§357, `16f16081`/`c8d55a10`/`0aa6a307`)** — 8 issues closed on proof (#399/#363/#375/#385/#366/#365/#295/#368): type variables carry their bound to every emit site, generic interfaces lower with erased descriptors and covariant bridges scan parents AND interfaces (primitive box/unbox at the boundary), field `T[]`/`as T[]` erase honestly, and the JVM-only decision `SEM098` names the primitive-array-into-erased-slot case. §358 FIXED 21/09 with the honest `NAT004` refusal (was the PRE-EXISTING native gap of `toString` on unbounded `T` cross link — measured identical at the clean tip, not a regression; proof `NativeGenericDispatchGapE2ETest` 2/2).
> - **20/09 morning (measured by the docs lane on the shipped jar): §362 gate landed** (`57a0d5f0` — phantom constructor calls now SEM023/SEM014 at the call-site, 7/7 + 4-target probe green) **and the fatia-2 sweep found §371/#550** — from the distributed CLI the runtime-slice loader (CWD-relative source read) never prunes, forcing `usesDb`+dynamic link and breaking `kof build --target native.risc|arm` with COMP001 even for `hello.kf` outside the module tree; E2E suites run with cwd=module, so only the real jar exposes it (P0-class, routed to the nat cluster).
> - **20/09 afternoon (docs lane, clean-jar rule + E2E of the owners): §370 closed the loop** — owner's `35bfaec1` re-measured with the md5 rule: `extern` numeric coercion (Int→Float, Double→Float, Int→Double) byte-identical JVM≡Native, SEM014 on non-convertible; #549 closed with the lane's proof (5749129761).> - **§373/#443 landed (`d969bc3a`)** — bare `List`/`Set`/`Map` in a DECLARED position resolves to the builtin collection at the ONE convergence point (`qualifyDeep` 2b, §243 shadow preserved); phantom `LList;` dead, `BareCollectionFieldE2ETest` 8/8 (RED 6/8) and the same print on the 4 targets. **X7-5 attached, real on JVM+Native (`81401629`)** — raw JDWP rebuilt against the JDK 25.0.4.1 wire measured byte-by-byte (§376; oracles: own jdb + src.zip `JDWP.java`), false-green fixed with genuine E2E (§377).> - **Afternoon harvest of process bugs:** §371/#550 (shipped-CLI cross-build breaks on the loader CWD), §372/#551 (§368 gate regression over the erasure river, bisected), #552 (`BuiltinCallTyper` 612≥600), §373's Q4 hunt → §374/#553 (bare-collection arg boxing) and §378/#554 (the EN×PT gate is blind to single-language FIXED). All routed to the compiler/native/tooling lanes with the proof test defined in the DOING watchers.
 The same sweep bisected a **regression from §368's gate** (`5cd078c1`): SEM012 fires before the erasure river (§355-357) and masks SEM098 — `GenericFieldArrayEraseE2ETest` 3F+1E + `MakealivePrimitivesE2ETest` 1F, all green at `5cd078c1^` (§372/#551); and CI's ≤500 gate is red because `BuiltinCallTyper.java` crossed to 612 lines under fatia-1+§362 (#552).
> - **§360 FIXED — `process.spawn` handle ops were DEAD CODE on the JVM** (`4ed9bb2f`): the dispatcher never routed a `Long` receiver to the handle branch, so `readLine/write/exitCode/kill/alive` lowered to a raw `invokevirtual java/lang/Long.readLine` and died at RUNTIME. Routing fixed; then the JS face landed (`081a48f8` + `2d20e5d4` `runWith(argv,cwd,env)` JVM+JS): spawn+handle+pipeline on JS through `KofJsProcessBridge` with byte parity — `ProcessSpawnE2ETest` 4/4, `ShellE2ETest` 15/15; tracker 2.2 is ✅ CLOSED (last residual: pipeline JS).
> - **X7 (debug info) real state measured: x86 DWARF already existed and now the CROSS targets do too** — `.file/.loc` line tables (`NativeDwarfCrossTest`, `5d9c855c`/`23b7ecf7` rework proved `.debug_line` in the real riscv/aarch64 ELF) and CU/subprogram DIEs with per-ABI `frame_base` (`890b58bf`); source maps JS ✅; `kof profile` honest at process level. The §19.5/X7 lines were corrected against code (they had been marked open while already implemented).
> - **X10 CLOSED (`27826838`)** — the LSP signature catalog reached **32/32 namespaces** (json table entry + behavioral lock at the real `MemberCallNamespaces` SEM025), hover + `signatureHelp` + `completionItem/resolve` from ONE generated source (`scripts/gen_signatures.py`), 265 members / 282 forms stable.
> - **Two new OPEN bugs catalogued with 4-target matrices (docs lane; fixes belong to the `.22` cluster)**: **§361** — nullable-primitive FIELD writes store the raw int where the boxed slot is required (`b.n = 42` → JVM `VerifyError` at LOAD, Native SIGSEGV, Script/JS print `42`; root pointer `ExpressionFieldAssignLowerer` box-gate missing `isNullablePrimitive` — the un-mirrored twin of §295(b)); **§362 / issue #545** — calls to a NON-EXISTENT constructor are silently accepted (`P(1,2)`, `C(1)`, `C("s")` → JVM dies at RUNTIME, Script prints `0`, JS prints `1`, Native fails `ld` with an undefined ctor symbol; expected: a compile-time SEM, the #469/#470 precedent family). The §361 fix landed (`e293c4a5`, 9/9 test) but the docs lane RE-MEASURED it on the rebuilt tip across 4 targets and it is PARTIAL: the `Char?` write face still dies (JVM/Script `VerifyError` at the `putfield`, Native runtime cast error at the read; JS fine) and a fresh §365 was opened (never-written nullable field reads `0` on JS vs `null` elsewhere — pre-existing, not from the fix); routed on #278 with the matrices. Two more measured the same pass: **§366** (Script drops `process.run` stdout silently — JVM `x`/Script empty; **#547**) and **§367** (`println(result)` leaks `KofRuntime$ProcessResult@<hash>` — Java identity on the Kof surface, rule 8; **#548** with the `KofJsHostlessRuntimeTest` precedent); `training/idioms/stdlib` corrected to the real JS story (`spawn`/`pipeline` are REAL under the Kof JS host `KofJsRunner`, bare node = honest diagnostic — `94eb4433`).
> - **Ledger discipline upgrades (multi-agent lessons of the night)**: the §NNN rule now demands taking the number from the REMOTE TIP (double collision §355 renumbered twice → §359/§360 settled with provenance notes); stash-marker/Conflict garbage committed by a lane was surgically re-cleaned (`029ac684`/`515414db`, all three sides preserved); `check_known_bugs_status.sh` learned the 🔓 token + `--selftest` case; `docs-lang` holds 100%.
> - **Decision record grew (§7dfc6441, DECISIONS EN+PT)**: `D-MAKEALIVE` (compiler that compiles itself), `D-KOF-AS-CLOUD` (platform posture), `D-BOOTSTRAP`, `D-DB-GAPS` (ORM queue: 13 native `kof_orm_*` entries measured missing — recon done, slices planned), and the **Simplicity Law** promoted to rule 11 of AGENTS.md (anything reaching the language surface must be short/idiomatic/intent-first). Three-state doc rule applied: the concluded workflow/shell plans were PROMOTED `development/`→`docs/` in the same push that closed them.
>
> **18/09 — R3 FFI (JVM) generalized — `extern` binds the full scalar ABI
> (owner = 192.168.100.18, development lane).** `CompilerPipeline.isExternBound`
> now accepts **any arity** over {Int, Long, Float, Double, Boolean, String} in
> every position and return, with a `String` result read back from the native
> `char*`. One FFM downcall `kof_ffi(lib, name, sig, Object[])` replaces the old
> `kof_ffi_i`/`_si`/`_dd` helpers; the lowering packs args into an `Object[]`
> (`KofNewArray`, boxing primitives) and the JVM emitter unboxes/checks the boxed
> return (`emitKofRuntimeCall`). `FfiE2ETest` 8→9 (adds `atol(String):Long` →
> `labs(Long):Long` → `9`, proving the `Long` layout end-to-end — Kof has no `long`
> literal so the `Long` value is produced by `atol`) + new `FfiSignatureTest` 4/4
> locking the full scalar → FFM-layout → `Type` mapping incl. `Float`/`Boolean`,
> whose generic downcall path is already exercised by the Int/Long/Double/String/void
> e2es (`pow` 2.0^10 → `1024.0`, `strstr("hello world","wor")` → `world`;
> `srand(Int)` default `void` via
> `kof_ffi_void`). **JS parity CLOSED (slice 3.6, same day):** the same scalar ABI
> now binds on the JS target through a host FFM bridge `KofJsFfiBridge` (`extern`→
> `kofFfi`→`kof_platform.ffi` `ProxyExecutable` on the GraalJS/node runner); `FfiE2ETest`
> adds 7 `assertJvmJsParity` cases proving byte-for-byte JVM↔JS equality (doubles
> `3.0`/`1024.0`, `Long` via `atol`→`labs`, `char*`→String, `void`); a browser has no
> host → honest runtime degrade (R7), and non-scalar signatures still `FFI002`. **Callbacks
> bind on the JVM *and* the JS host runner (slice 3.4, C1→C3, same day):** an `extern` with a
> function-typed parameter
> lowers to `kof_ffi` (nested `(<ret><params>)` token) and the runtime builds a C function
> pointer via `Linker.upcallStub` over the Kof function value — `JvmFfiCallbackE2ETest`
> computes `42/42/6.0/7.5` across Int/Long/Double/mixed callback ABIs, byte-for-byte JVM↔JS
> (`jvmAndJsCallbacksMatchByteForByte`); on JS the function value is a `Lambda…` **object**, so
> the runner bridge calls `fn.getMember("invoke").execute(...)`; synchronous/non-
> escaping contract, primitive-only ABI.
> Parity not yet reached: struct/pointer (D6),
> variadics and opaque handles stay `FFI001`; Native `FFI001` (§61) remains an honest
> per-target gap (R7). Full decomposition in §R3-slices / §R3-3.4 of the
> universal plan.
>
> **18/09 — §132 CLOSED (#83-JS) — KofJS runs the OTP supervisor to parity
> (owner = 192.168.100.18, development lane).** `time.sleep` is now an **await-point**
> on the JS backend: the compiler colors a method that reaches `kof_time_sleep` async
> via the existing `computeAsyncColoring` fixpoint (the one that already governed
> `await`) and emits `await kof_time_sleep(ms)`; `kofTimeSleep` returns a Promise
> (node/browser: real `setTimeout`; embedded GraalJS: a sleeper queue drained by the
> `KofJsRunner` host pump, which is the minimal event loop a single JS thread cannot be
> on its own). Because the host can sleep AND advance microtasks, sibling/child spawned
> tasks now run while a task sleeps — the `while(!done(h)){ time.sleep(10) }` idiom
> makes progress and the supervisor worker fires. `OTP002` is lifted (JS `kof.supervisor`
> no longer refused at compile-time); `KofSupervisorE2ETest#supervisorJsParity` runs
> `APP` → `restarts=2 escaladas=2 fabrica=3 parou vivos=0`. Real clock preserved
> (`time.now()`/`Date.now()` unchanged) — `KofTimeE2ETest` stays honest. riscv/aarch
> remain `OTP001` (§129 cross-thread unwinding, nat lane). Proof: `AsyncSleepJsE2ETest`
> 3/3 + full reactor green.
> **15/09 — §129 CLOSED (DECISIONS §2 option B) — Native x86 unwinds per thread
> (owner = 192.168.100.18, development lane).** The `kof_exc_chain` is now **TLS
> per-thread** (`.section .tbss,"awT",@nobits` + `%fs:kof_exc_chain@tpoff`) instead
> of a `.data` global, and `kof_spawn_trampoline` installs a per-worker handler
> frame: a `throw` with no inner handler publishes the cause on the handle instead
> of longjmp-ing into the main thread's `try` (the cross-thread crash/hang). The
> consumer's `await`/`awaitTimeout`/`selectAny` rethrow the cause (JVM parity).
> `CompilerSupervisor` now emits `OTP001` only for riscv/aarch (raw `clone`, no
> TLS). This **unblocks OTP S2-Native** — the pure-Kof supervisor now runs on
> Native x86. Proof: `KofSupervisorE2ETest` 15/15
> (`supervisorNativeParityX86`/`supervisorNativeS2ParityX86`), `KofConcurrency2Test`
> 40/0 incl. 4 new native worker-throw cases, `ExceptionsE2ETest` 11/0,
> `NativeE2ETest` 65/0. Suite 1777/1 (the 1 = pre-existing
> `[ifexpr-heterogeneous-direct]` Native SIGSEGV, §205, other lane). **Update 15/09: that red was CLOSED the same day by §205 fatia 1** (`97d08e54`, branch-by-branch lowering on the direct print — `conformanceCoreControl` 1/1 measured on the tip; the `via-var`/`as Object` residual stays as PARTIAL, catalogued in §205).
> **19/09 — §129 CLOSED on riscv64/aarch64 too; `OTP001` REMOVED (lane nat/compiler `.17`).** The cross port uses a **per-TID chain table** `kof_exc_slots` (256 × 16 B `[tid, chain]`, key `gettid`=a7 178, linear probe) + a `kof_exc_slot()` helper — the maintainer's "real TLS via `clone`" mechanism was implemented and proven **ABI-unsafe** (overwriting `tp`/`TPIDR_EL0` breaks libc TLS → SIGSEGV in `snprintf`/`strtod`; riscv `crossNativeConcurrencyHelpersRun` regression), so it was abandoned for the safe second option. `CompilerSupervisor` no longer emits `OTP001`; `kof.supervisor` now runs on all Native targets. `planning-otp-supervision.md` (+PT) promoted to `docs/` (planning concluded). Proof: `KofConcurrency2Test` 48/0 (`spawnWorkerThrowIsolatedFromSiblingsCrossArch` + `spawnWorkerThrowUnhandledPropagatesCrossArch` green on both arches), `KofSupervisorE2ETest` 16/0 (`crossGateOtp001` now runs `APP` under qemu on riscv/aarch), riscv/aarch E2E 45/0 each.
>
> **14/09 — RELEASE-STABILIZATION BASELINE (owner = 192.168.100.17, docs/stab
> lane).** Clean 4-module run (`rm -rf */target`): **1819 tests, 3 failures,
> 0 errors, 7 skips** (compiler 1552 + script 37 + kof-c 5 + cli 225). All 3
> reds are **cross-arch in other lanes**, catalogued with repro + root-cause
> pointer (NOT this lane, NOT to be attacked without owner):
> **§181 residual** — `riscv64CastSaturation` + `aarch64CastSaturation`, the
> ONLY diverging golden row is `(-inf) as Int` (`0` vs `-2147483648`; the rest
> re-confirmed green on HEAD with `67db6c50` merged), nat lane active (fix
> 22:42 13/09); **§192** — `KofMathTest.parseOrDefaultCrossArch` HANGS on riscv
> (a throwing `parse*OrDefault` before a `parseDouble` corrupts `kof_exc_chain`
> via the B41 `sd a1,24(sp)` default/chain slot alias → non-converging exponent
> scaling loop; 2-line repro + loop PC in known-bugs §192), stdlib/nat lane.
> Everything else is green (JVM/Script/JS/x86 parity holds). **The release is
> NOT freezable until these 3 go 0-failures** — gate = 0 FAILURE outside the
> missing-`node` (13) + external-DB (5) + toolchain guards. §176 closed
> (`a5eedbe2`, root cause = stale constant-fold of the JS web slice). Decompiler
> stays deprioritized (maintainer decision — meta = stabilize the release).
>
> **12/09 — §107 SCALAR face CLOSED on the 3 native targets** (`println(<collection>)`
> printed pointer garbage; `kof_{list,set,map}_to_string` + compile-time tag
> x86 `f3b3821c` + cross riscv/aarch B39 `411e9ce5`, golden JVM byte-identical
> under qemu; record/nested stays HONEST `?` until §104b-ii, FP-collection on cross
> FLT001 at compile time — never silence; collateral bug §138 unblocked the build
> cross). **12/09 — issue #97 (tree-shaking, maintainer front): S-1/T0
> DONE** `a3996600` — `ArtifactSize` (pure-Java ELF64 parser) + `ArtifactSizeTest`
> (anti-bloat gate 5% locked to measured numbers: hello x86 138,928B/627
> unreachable symbols, JS runtime 177KB, riscv .bss 260KB) + `kof build
> --print-sizes`; plan `PLAN-TREE-SHAKING.md` promoted `future/`→`development/`
> (S-2 = provides/needs map per slice, next step). **12/09 —
> doc-vs-reality re-audits:** `native-multiarch.md` said "JSON/HTTP/net/
> collections x86 only" (false — all run under qemu, measured `8caa14c1`);
> top table of `known-bugs.md` carried an apocryphal sweep from 08/09 (39/62/63/
> 64 already ✅) — fixed with the real queue of 16 open, each with a measurable
> blocker (decision/lane/frozen) `42c716ed`.
> **11/09 — OTP core (issue #83) delivered on JVM+Script:** virtual package
> `kof.supervisor` (pure-Kof host, `import kof.supervisor`), with observe-failure,
> individual restart (new factory), restart limit + escalate and controlled stop
> (`KofSupervisorE2ETest` 6/6). Native/JS blocked at compile-time
> with a clear diagnostic (`OTP001`/`OTP002`, §129/§132 — never silence). Blocker
> §130 (false SEM024 in a re-analyzed method) fixed along with it.
> **11/09 — asynchronous fetch in KofJS (§133):** `spawn http.get(url)` + `await`
> now resolves the real body in Node/browser (fetch→Promise through the existing
> `Handle<T>` machine; zero new AST). The silent `return ""` fallback of the
> JS runtime is gone — the synchronous face in pure JS stays honest (raw Promise,
> HTTP003); GraalJS/KofJsRunner intact. Proof: `KofHttpE2ETest` 8/8.
> **11/09 — MATH001 closed** (`kof.math` Double on the 5 targets; §120 + §105 in the remote series).
> **11/09 — TIME002 closed** (`addDays`/`diffDays` on the 5 targets).
> **11/09 — SG-011B closed (top-level overloading, JVM oracle):** functions
> with the same name and different signatures coexist and resolve at the call site;
> exact duplicate/return-only collision → SEM047, ambiguous → SEM057. Each
> backend references the candidate by its SIGNATURE (JVM descriptor, symbol
> suffixed on Native, name suffixed on JS, dispatch by type in the interpreter)
> — byte-identical output on the 6 targets (`TopLevelOverloadE2ETest` 5/5; §136,
> contract ratified in §135). **Class METHOD overloading (§131)
> CLOSED 13/09** (`18a64d45`, 4 backends: `MethodSet` in the symtable +
> selection by signature in the typer + own slot/symbol per overload on
> Native + JS mangle; `CoreRegressionE2ETest.methodOverloadByArity`).

---

## Build

``` 
mvn clean package    → PASS
mvn test             → 3225 tests (2762 kof-compiler + 50 kof-script + 7 kof-c-compiler + 406 kof-cli), 0 failures / 0 errors, 221 skip (cross runs in the dedicated qemu job; external DB/toolchain guards + §255 sysroot guard; `node` present — all `*Js` green) — CI Build+Tests job of tip `404d8be6` on 20/09 ~18:14 (first green on `beta-0.5.0`, reactor 0.5.0-beta) (previous 2411 = 17/09 ~15:49 clean run on tip `f276e966`)
kof build            → PASS (--target jvm|native|js|native.risc|native.arm) [--release]
kof run              → PASS (jvm|native|js|native.risc|native.arm) [--release]
kof serve            → PASS (native web.app() + legacy handle() API)
kof check            → PASS
kof test             → PASS (structured suite `test "name" { }` on the 3 targets)
kof bench            → PASS (harness: compile, run, validate, metrics, baseline)
kof debug            → PASS (DAP MVP on the JVM target)
kof info             → PASS
kof lsp              → PASS (hover/completion/references/rename + real diagnostics)
kof install          → PASS
kof c                → PASS (KofCcompiler native-only C subset → ELF x86_64 via kof_c)
kof script           → PASS (KofScript top-level `var`/`val` → KofScriptGlobals, repl, --watch)
tests/run-golden.sh  → 16/16 (8 cases × jvm+native)
tests/run-integration.sh → 9/9 (CLI + serve + kof test)
scripts/package.sh   → PASS (dist layout + tar.gz/zip + SHA256SUMS + jars)
```

---

## 02/09 — Idiomatic philosophy review

- **`Set<T>` as a declared type on the JVM**: descriptor `kof.Set` → `java/util/HashSet`
  (`NoClassDefFoundError: kof/Set` closed); class member parser with
  generic return (`Set<Int> foo()`, `List<String> bar()`).
- **Null-safety narrowing on the JVM fixed**: `if (s != null) { s.length }` /
  `s.substring(...)` emitted `getfield "?".length`/`"".substring` (invalid
  bytecode); `if (x != null)` used `if_icmp*` on a reference. `mapOf(k1,v1,...)`
  infers the type from the first pair. The prefixed form `String? s = null` now
  parses.
- **honest stdlib**: `File.readText`/`readFile` → `String?` (Native returns
  `null` instead of terminating); `File.size()` throws instead of the `-1`
  sentinel; `Map.get` → `V?` for reference values; `readLine()` → `String?`
  (`null` at EOF, JVM and Native).
- See `CHANGELOG.md` [0.2.6-beta] 02/09 and `docs/backend-parity.md`.

---

## Performance & Benchmarks (docs/architecture/performance.md)

- **IR optimizer** (`Optimizer.java`, always active): constant folding,
  branch simplification (constant conditions → direct jumps), dead stack
  effects (push+pop, dup+pop, load/store round trips), unreachable code
  elimination (CFG reachability with try/catch regions preserved),
  jump-to-next elimination, arithmetic identities (x+0, x*1, x/1, ...).
  Debug positions preserved (surviving ops).
- **debug/release profiles**: `kof build|run --release` removes debug
  metadata (SourceFile/LineNumberTable on the JVM, source map on JS).
- **`kof bench`**: `kof bench [paths...] [--target jvm|native|js]
  [--iterations N] [--quick] [--baseline <file>] [--update-baseline <file>]
  [--threshold <ratio>] [--json] [--fail-on-regression]`.
  Compiles → runs → validates stdout against `expected.txt` → measures time
  (median) and RSS (Linux, `/usr/bin/time -v`) → compares baseline →
  flags `PERFORMANCE REGRESSION`.
- **`benchmarks/` structure**: 37 benchmarks in 17 categories (micro,
  algorithms, collections, strings, math, objects, inheritance, interfaces,
  generics, json, io, concurrency, startup, memory, stress, applications).
- **Baselines**: `benchmarks/baselines/<target>-<version>.json` (33 jvm,
  29 native, 32 js).
- **CI**: `.github/workflows/benchmark.yml` — runs jvm+native with
  `--fail-on-regression --threshold 1.20`.
- `scripts/run-benchmarks.sh` — full suite + baseline update.
- New feature rule: docs/architecture/performance.md §40-§41 (Definition of Done
  includes benchmark, stress, memory, resource and debug metadata).

### Backend fixes discovered by the benchmarks and E2E

| Bug | Fix |
|-----|----------|
| Interface calls with a primitive return generated an `Object` descriptor (`()Ljava/lang/Object` + `iadd` = invalid bytecode) | `analyzeInterface` now defines the symbols in `members()` (they were invisible to `resolveInHierarchy`) |
| `l.get(i)`/`l.remove(i)`/`l.size`/`l.contains(...)` as a statement did not emit `KofPop` → unbalanced stack at merge points (Frame.merge crash / VerifyError) | `hasReturnValue` covers List methods that leave a value |
| `if (long > long)` / `if (float > f)` / `if (double > d)` generated `IF_ICMP` over non-ints (stack underflow) | `KofConditionalJump` gained `operandType`; JVM emits `LCMP`/`FCMPL`/`DCMPL` + 1-operand jumps |
| `while (longExpr < intLiteral)` generated `LCMP` over [long, int] (stack underflow) | comparison shortcut widens the operands (`emitComparisonShortcut`) |
| JS: call with effect discarded in a statement with Pop (e.g.: `users.remove(0)` silently did not execute) | `KofPop` handler in JsBackend preserves `JsCall`/`JsSequence` as a statement |
| `Box<Int>` / `Box<T>` with `b.get()` returning `T` printed `T` as `String` on Native → segfault `0x7` (`NativeE2ETest.execGenericClass`) | `ExpressionTyper.inferExprType` (`ExpressionTyper.java:14`) substitutes `T` via `CompilerTypes.substituteTypeVariableIn` (`CompilerTypes.java:423`); native `println` via `kof_int_to_string` (`NativeRiscvCrossOps.java:201`) |
| `record Ponto` `hashCode()` reported a false-positive `SEM025` | `MemberResolver.java:65` ignores `isObjectMethod(hashCode/equals/toString)` |
| **Regression `dc849f6` (01/09):** `kof_list_add` without `POP` on the JVM (assumed the IR would emit `KofPop`) → `hasReturnValue` treats `add` as void, so the `ArrayList.add` boolean stayed on the stack → frame crash (`Index out of bounds`) in 15 tests | POP restored in the `kof_list_add` emit + `hasReturnValue` shields collection `add/push/append/set/clear/put` (no double `KofPop`) + `cache` is only a namespace if it is not a local/param (`c7b23a1`…`7c6aca9` + POP `7c6aca9`) |
| **Surefire: `NativeDebugTest2/3/4/5` never ran in the suite** — the default pattern `*Test.java` does not match `…Test2.java` (only an explicit `-Dtest` caught them) | `<includes>*Test*.java</includes>` in the `kof-compiler` surefire — suite back to 752 |
| **`spawn { lambda w/ capture }` → `VerifyError`/`ClassFormatError`** (JVM) / wrong value (Native): the `SpawnStmt` lowering created the lambda with `List.of()` (zero captures), so the body resolved the outer variable to `this` | `SpawnStmt` (JVM + Native) now collects via `collectCaptures(le, locals)` and emits the lambda constructor with the capture loads (same pattern as the generic case) — `SpawnE2ETest.spawnLambdaCapturesOuterLocal` |
| **`&&`/`||` without short-circuit on JS**: JsBackend emitted `KofBinaryOp.AND/OR` as `&`/`|` (bitwise), which evaluates BOTH sides → side effects on the short-circuited side were not executed | boolean `&&`/`||` (operandType `bool`) now become JS `&&`/`||` (native short-circuit); bitwise `&`/`|` intact — `KofJsE2ETest.logicalAndOrShortCircuit` + `bitwiseAndOrStillWorks` |
| **`Channel<T>` rejected as a function parameter**: the parameter type came out as `ClassType(package="")` and `isChannel` required `kof.concurrent` → dispatch fell into the generic path → invalid bytecode (JVM), `undefined reference Channel_receive` (Native), nonexistent `c.receive()` (JS) | `Type.of`/`toType` treat `Channel` as builtin (`kof.concurrent`, parity with `List`); `JvmTypeMapper` maps `Channel` → `java/util/concurrent/LinkedBlockingQueue` (descriptor+internalName) — `KofConcurrency2Test.channelAsFunctionParameter{Jvm,Native,Js}` |
| **`println`/`print` before `spawn` → SIGSEGV on Native** (`pthread_create`): the args-by-stack convention (push) arrived 8 bytes misaligned at the `call pthread_create` site (`rsp%16==8` vs `0` required by the SysV ABI) → glibc segfaulted in `pthread_attr_copy` writing to the frame | stack alignment at the C call: `andq $-16, %rsp` before the `call pthread_create` in `kof_spawn_handle_new`, preserving `r15` (callee-saved) and restoring the caller's frame — `SpawnE2ETest.nativePrintBeforeSpawnDoesNotSegfault` |
| **AES-GCM on JS ignored tampering with the ciphertext** (`SECN002`, 01/09): `kofSecB64Decode` tolerated a size not a multiple of 4 (remaining bits silently discarded), so `decryptAesGcm(ct + "AA")` decoded and the tag mismatch went unnoticed — diverging from the JVM's `java.util.Base64` (which throws) | `kofSecB64Decode(s, strict)`: `strict=true` rejects a size %4 ≠ 0; `decryptAesGcm` passes `strict=true` on `iv` and `ctTag`; JWT (b64-url without padding) continues with `strict=false` — `KofSecurityTest.aesGcmJsRoundTrip` (tamper+wrong key) + cross-target parity JVM↔JS |
| **OBS002: histogram/metrics on Native** (01/09): implemented in asm — bugs found in the smoke test: (1) appender fell into the main flow after the seed (no `jmp`) → crash in `kf_memcpy` with garbage len; (2) export loop with inverted comparison (`cmpq %idx, %len; jge` exited immediately) → empty string; (3) `kf_free` clobbered `%rsi` (fragment) in the middle of the append → `kf_string_concat` with corrupted ptr; (4) `call` with `rsp%16==8` (SysV ABI) | store `.Lkof_obs_histograms` (32B: name+sum+count) + `kof_observability_metrics` via `kof_string_concat`; appender with alignment `pushq` + fragment in `%r10` (scratch); `cmpq %len, %idx` fixed — `KofObservabilityTest.observabilityNative` (content parity with the JVM, byte-identical in the smoke test) |
| **`transaction {}` on Native gave a link error** (`kf_db_transaction` did not exist; the `KofDb.supportedOn` gate already allowed Native) + rollback did not undo (01/09): (1) the lambda did not have `rdi` (=this, where the captures live) before `call *%rax` → read `db` from the wrong field; (2) `r12` (handle) clobbered by the lambda on the throw path; (3) KofStrings for BEGIN/COMMIT/ROLLBACK without a trailing NUL → `sqlite3_exec` read "begin\x01" (error ignored) and autocommit persisted the inserts | `kf_db_transaction` in asm: BEGIN via `kf_db_execute`, `movq %rbx, %rdi` (this) before the invoke, COMMIT/ROLLBACK **reload the handle from BSS** (`.Ldb_default_handle`, written in `connect`), re-throw via `kf_throw_string` (the chain points to the outer try); KofStrings with `.asciz` (NUL) — `KofDbE2ETest.nativeTransaction{Commits,RollsBackOnFailure}` |
| **MQ001: kof.mq on Native** (01/09): pub/sub + in-process queues implemented in asm | store `.bss` (topics/queues/seq) + 40B nodes `[next, KofString*, KofList*, _, _]`; `kof_mq_find_topic`/`_queue` (lookup by `kof_string_equals`, callee-saved `rbx/r12` because `kf_string_equals` clobbers `rdi/rsi/rax`); `subscribe`/`push` create the node the first time and `kof_list_add`; `publish` iterates the subs with `kof_list_get` + invoke-with-arg (`rdi`=fn, `rsi`=msg); `unsubscribe` compares by **identity** of the fn object; `queue()` = `"mq-<n>"` (seq); `pop` removes head (`null` if empty) — `KofMqE2ETest` 4/4 (JVM+Native+JS, output parity) |
| **`Set<T>`/`Map<K,V>` as a class field/return → `NoClassDefFoundError: kof/Set`** (01/09): two bugs. (1) `JvmTypeMapper.classDescriptor`/`toInternalName` mapped only `List`→`ArrayList` and `Channel`→`LinkedBlockingQueue`, but **not** `Set`/`Map` → the field/return descriptor stayed `Lkof/Set;`/`Lkof/Map;` (nonexistent class) while the real runtime is `java.util.HashSet`/`HashMap`; (2) the class member parser (`ClassMemberParser.parseClassMember`) only recognized `Type name(` for a method (2-token lookahead), so a **generic** return `Set<Int> all(` fell into the field branch and broke at `(` (PARSE016/020-023/044) | `JvmTypeMapper`: `Set`→`Ljava/util/HashSet;`, `Map`→`Ljava/util/HashMap;` (desc + internalName); `ClassMemberParser.parseClassMember`: new branch `isGenericReturnTypeAhead()` + `consumeGenericTypeArgs()` before the field fallback (same shape as the top-level `parseFunctionDeclaration`) — `KofMapSetTest.setMapAsFieldAndReturn` (3 targets, class field + constructor param + method return) |

---

## Security (kof.security, docs/stdlib/security.md)

- **`kof.security` implemented** (v1): `passwords`, `crypto`, `jwt`,
  `secrets`, `security`, `auth` — secure by default, target gaps with
  a clear compile-time diagnostic (SECN001/002/003).
- **JVM**: PBKDF2-HMAC-SHA256 (600k iterations), SHA-256/512, HMAC, AES-GCM,
  SecureRandom, JWT HS256 (sig/exp/iss/aud), env secrets, constant-time,
  redaction, web context `auth.*` (Bearer JWT).
- **Native**: SHA-256, SHA-512 and HMAC in pure assembly (x86-64, no libc,
  FIPS 180-4 / RFC 2104 — values identical to the JVM), PBKDF2-HMAC-SHA256,
  AES-GCM (E2E round-trip `aesGcmNativeRoundTrip`), JWT HS256, random via
  `getrandom`, secrets via `/proc/self/environ`, constant-time, redaction.
- **JS**: SHA-256/512 and HMAC in pure JS, PBKDF2 with delegation to the platform
  (embedded runner), JWT, secrets, constant-time, AES-GCM (01/09, SECN002).
- **Tests**: `KofSecurityTest` — 27 tests (unit + E2E on the 3 targets +
  adversarial: tamper, expiration, algorithm confusion, malformed token,
  wrong key, issuer/audience).
- **Benchmarks**: `benchmarks/security/` (password-hash, jwt, hash-speed,
  aes-gcm).
- **Docs**: `docs/stdlib/security.md` (audit + matrix + architecture + state),
  `docs/stdlib/stdlib.md`, `learn/36-security.md`, `training/language/security.md`,
  `training/examples/security.kf`.

---

## Database + ORM (kof.db / kof.orm)

### kof.db — persistence as part of the language

```kof
main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    db.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR)")
    var rows = db.query("SELECT * FROM users WHERE id = ?", 1)
    transaction {
        db.execute("INSERT INTO users VALUES (1, 'Mel')")
        db.execute("UPDATE users SET name = 'Melissa' WHERE id = 1")
    }
    db.close(db)
}
```

- **JVM**: idiomatic JDBC (`db.connect`, `db.execute`, `db.query`,
  `query<T>` typed by record/entity, optional credentials,
  `transaction {}` with real commit/rollback).
  - **Native**: SQLite via direct linking of the `.so` (no JDBC driver) — real
    E2E roundtrip (`nativeSqliteRoundtrip`). **`transaction {}` with real
    commit/rollback** (01/09): `kof_db_transaction` in asm — BEGIN via
    `kof_db_execute`, invokes the lambda (vtable[0]=invoke, `rdi`=this for
    captures), COMMIT on success, ROLLBACK + re-throw on error (EH
    `kof_exc_chain`/`kof_throw_string` — the exception reaches the handler with
    `%rdi` and the chain pointing to the outer try; default connection = last
    opened, parity with the JVM's `KOF_DB_DEFAULT`).
    MySQL/MariaDB via wire protocol over
    native sockets: **handshake + auth scramble SHA-1 + auth-switch
    (mysql_native_password) + COM_QUERY + resultset parsing (coldefs + rows
    + EOF) + `?` binds (client-side literal substitution, `nativeMysqlWireProtocol`
    — 31/08)**. Prepared statements via COM_STMT_PREPARE (binary) pending.
- **JS** (16/09, DB001 fechado): `connect`/`connect2`/`close`/`execute`/`query`/`transaction` delegate to `kof_platform.db*` on the GraalJS host (`KofJsRunner`+`KofJsDbBridge`) — same JVM/classpath as the JDBC path, so `DriverManager` sees h2/sqlite-jdbc exactly as the JVM target does; output byte-parity (`KofDbE2ETest.js*` 4 cases). **`db.query<T>` typed = `DB002` CLOSED 18/09** (the wire is untyped — the host bridge has no `Class.forName` for JS classes — but the guest binds each JSON row via the same `__kof_decode_<T>` helper `json.decode<List<T>>` uses; `KofDbE2ETest.jsTypedQuery*` byte-parity with the JVM).
- **riscv64/aarch64**: SQLite closed 15/09 — link-by-use `libsqlite3` + `kof_db_*` runtime slices `RtB46/RtB47` (`KofDbE2ETest.crossNativeSqliteRoundtrip` under qemu); `sqlite:` DSN only, transaction via EH chain (the §129 chain is per-TID since 19/09; concurrent transactions in different workers still share the global `.Ldb_tx_handle` slot — residual catalogued in `NativeRiscvAsmRtB47`).
- DSNs: `jdbc:*` (JVM), `sqlite:` (JVM/Native), `mongodb://` (ORM).

### kof.orm — the language's own ORM

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}

main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    orm.create<User>(db)                                  // schema DDL
    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))       // insert/update
    var u = orm.find<User>(db, 1)                         // PK
    var adultos = orm.where<User>(db, "age", 30)          // query by field
    var veteranos = orm.where<User>(db, "age", ">", 30)   // operators: > < >= <= != LIKE
    orm.saveAll<User>(db, l)                              // batch (upsert by PK)
    var pg = orm.page<User>(db, 20, 40)                   // pagination (limit, offset)
    println(orm.count<User>(db))
    orm.delete<User>(db, 1)
    orm.migrate(db, "add-phone", "ALTER TABLE user ADD phone VARCHAR")
}
```

- Schema declared in the language (`entity`) — the compiler knows fields,
  types and constraints at compile-time (never reflection to discover
  schema); `generated`, `unique`, non-numeric PK.
- SQL backends: H2/SQLite/MySQL/MariaDB/PostgreSQL via JDBC (JVM).
- Full CRUD + queries: `saveAll` (batch), `where` with operators
- **Column typing (P3-10)**: `where`/`where_op`/`count` with a literal column
  that is not an entity field → `ORM003` at compile-time (JVM); dynamic
  column (variable) remains allowed
  (`"="`, `">"`, `"<"`, `">="`, `"<="`, `"!="`...), `count` with filter,
  `page` (limit/offset) and `deleteAll`.
- **MongoDB**: `save/find/all/where/delete/count` over the official driver via
  compatible reflection (`Bson`/`Class`, no ClientSession); E2E test with a
  real container (conditional skip; Mongo service in CI).
- Versioned migrations: table `kof_migrations`, each migration runs once.
- Native CLOSED 24/09 (cross riscv64/aarch64 over SQLite + MySQL wire, D-DB-GAPS S5.5 `RtB76`–`RtB81`; x86-64 real since 22/09 F2d1–F2d7); JS CLOSED 18/09 (`KofJsOrmBridge`, same SQL as JVM, byte-parity E2E).
 - Tests: `KofDbE2ETest` (9), `KofOrmE2ETest` (31; MariaDB/PostgreSQL/MongoDB
   with conditional skip when the container is not up).
 - Docs: `docs/stdlib/DATABASE_VISION.md` (levels 0-4 implemented, including
   level 3 = typed query DSL `User.query(db){ where; orderBy; limit }` — 01/09).

---

## Distribution infrastructure

- `VERSION` as the single source; `<revision>` in Maven; `KofVersion` with
  `version.properties`; `scripts/bump-version.sh`.
- CLI (26 commands): `build, run, serve, check, test, script, repl, c, fmt,
  config gen, bench, profile, inspect, decompile, translate, compare, migrate,
  debug, info, lsp, install, deps, editor, new, init, version`.
 - `kof lsp` — Language Server via stdio (initialize, didOpen/didChange/
   didClose → publishDiagnostics from the real frontend, hover, completion,
   **references + rename** — word-boundary, single-file; `LspServerTest` 4/4).
- `bin/kof` (Unix) and `bin/kof.bat` (Windows) launchers with embedded JDK
  (Temurin 25 — toolchain baseline D-BASELINE 14/09; Kof programs still emit
  V21 bytecode, so the language floor remains JVM 21+).
- `scripts/package.sh` — official distribution layout, `--jdk` for embedded
  JDK, SHA256SUMS.
- GitHub Actions: `ci.yml` (PR — tests, golden, integration, multiplatform)
  and `release.yml` (main → tests → bump → package 3 platforms → changelog
  → GitHub Release).
- Editor support: `editor/kof.tmLanguage.json` (TextMate grammar).

---

## Targets

| Target | Backend | Execution | Status |
|--------|---------|----------|--------|
| `jvm` | `JvmBackend` (ASM) | V21 bytecode, exception table, virtual threads | stable |
| `native` | `NativeBackend` (x86_64) | ELF x86_64, syscalls, free-list alloc + mark-sweep GC (03/09; auto-collect ✅ landed 19/09 — §260 CLOSED, D1-A) | stable |
| `native.risc` | `NativeBackend` (riscv64) | ELF riscv64 via `riscv64-linux-gnu-as/ld` + qemu (core+stdlib 02-05/09, 26/26 — see `docs/native-multiarch.md`) | stable (core) |
| `native.arm` | `NativeBackend` (aarch64) | ELF aarch64 via `aarch64-linux-gnu-as/ld` + qemu (core+stdlib 03-05/09, 26/26 via translation — see `docs/native-multiarch.md`) | stable (core) |
| `js` | `JsBackend` + `KofJsRunner` | ES Modules via GraalJS, `kof.http` via `Java HttpClient` interop | alpha |
| `kofc` | `KofCcompiler` | C subset (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`) → native x86_64 | native-only |

The same frontend and the same Kof IR feed the three backends.

---

## Language State

### Function syntax (no `fun`)

```kof
main() { ... }                       // entry point, implicit void
String saudacao() { ... }            // return before the name
despedida(): String { ... }          // return after the parameters
void fazIsso() { ... }               // explicit void
Bool positivo(Int x) = x > 0         // expression body
```

### Implemented features

| Feature | JVM | Native | KofJS |
|---------|-----|--------|-------|
| println / print | ✅ | ✅ | ✅ |
| variables, arithmetic, bitwise, hex literals | ✅ | ✅ | ✅ |
| if/else, if-expr | ✅ | ✅ | ✅ |
| while, for, do-while, for-in, break/continue | ✅ | ✅ | ✅ |
| switch | ✅ | ✅ | ✅ |
| functions (all forms) | ✅ | ✅ | ✅ |
| classes, fields, methods | ✅ | ✅ | ✅ |
| `constructor(...)` and primary `class X(...)` | ✅ | ✅ | ✅ |
| records (toString/equals/hashCode) | ✅ | ✅ | ✅ |
| inheritance, `super`, override, virtual dispatch | ✅ | ✅* | ✅ | Native: `super.metodo()` = SUP001 |
| interfaces | ✅ | ✅ | ✅ |
| generics by erasure | ✅ | ✅ | ✅ |
| lambdas `(x: Int) -> expr` + captures | ✅ | ✅ | ✅ |
| real exceptions (try/catch/finally + unwinding) | ✅ | ✅ | ✅ |
| `assert(cond[, msg])` | ✅ | ✅ | ✅ |
| `spawn` (concurrency, implicit join) | ✅ | ✅ (pthread, 31/08) | ✅ |
| strings (concat `+`, `==`, indexOf, trim, split...) | ✅ | ✅ | ✅ |
| arrays | ✅ | ✅ | ✅ |
| `List<T>`, `listOf`, `map/filter/reduce` | ✅ | ✅ | ✅ |
| `Box<T>` generics with primitive/Boxed `T` (e.g.: `Box<Int>`) | ✅ | ✅ | ✅ | 25/08 fix `substituteTypeVariable` |
| JSON encode/decode (objects/records on the JVM) + native arrays | ✅ | ✅ | ✅ |
| JSON decode `List<User>` (nested objects) | ✅ | — | ✅ |
| kof.io (File/Path/Directory, readFile, writeFile) | ✅ | ✅ | ✅ |
| kof.time (now/sleep/interval) | ✅ | ✅ (now/sleep/**interval** — reuses the scheduler, SCHED001) | ✅ (now/sleep/**interval** — cooperative queue pumped by `time.sleep` in GraalJS; `setInterval` in the browser/Node, TIME001 closed 02/09) |
| kof.web (`web.app()`, routes, middleware, WebSocket/SSE, `configure`/`stats`) | ✅ | — | — |
| kof.http (`http.get/post/put/delete/status` + `timeout/retry/circuit`) | ✅ | ✅ **HTTP002 closed 03/09** (`NativeHttpRuntime` — HTTP/1.1 asm, IPv4; https → clear throw; retry/circuit no-op) | ✅ (27/08 JS via `Java HttpClient` interop; 30/08 retry/circuit parity) |
| kof.config (env, files, profiles, typed) | ✅ | ✅ (own asm) | ✅ |
| kof.mq (publish/subscribe/queue) | ✅ | ✅ (01/09, pub/sub + in-process queues, asm) | ✅ |
| kof.log (`log.info/warn/error/debug`) | ✅ | ✅ (asm; UTC, no JSON) | ✅ (LOG001 closed 01/09) |
| kof.security (passwords, crypto, JWT, secrets) | ✅ | ✅ | ✅ |
| kof.db (JDBC, query<T>, transaction) + native SQLite | ✅ | ✅ (SQLite + transaction; MySQL wire x86-64 real, ORM 13 faces 22/09; **riscv64/aarch64 ✅ 15/09** link-by-use libsqlite3) | ✅ 16/09 (untyped `connect/execute/query/close/transaction` on the GraalJS bridge) + ✅ 18/09 typed `query<T>` (`DB002` closed — guest-side bind via `__kof_decode_<T>`) |
| kof.orm (entity, CRUD, where, migrate, MongoDB) | ✅ | ORM001 | ✅ CLOSED 18/09 |
| String.toInt/toLong/toDouble/toFloat | ✅ | ✅ | ✅ |
| kof.ui (Color, Palette, Theme, Window) | ✅ | ✅ (JS render) | ✅ |
| default parameters in functions | ✅ | ✅ | ✅ |
| `readLine()` | ✅ | ✅ | ✅ |
| `KofCcompiler` C subset → native | — | ✅ (27/08) | — |
| `KofScript` top-level `var`/`val` → `KofScriptGlobals` (direct execution via `KofInterpreter`) | ✅ | ✅ | ✅ |

### Concurrency (`spawn`)

```kof
spawn processarFila()
spawn {
    println("background")
}
```

- JVM: virtual threads; the program waits for the tasks (implicit join).
- Native: pthread_create + trampoline + `await`/pthread_join + thread-safe
  allocator (futex) + `done`/`poll`/`cancel`/`cancelled`/`selectAny` — ✅ 31/08 (CONC001 closed).
- JS: real concurrency via GraalJS's `async`/`await`/`Promise` — `CONC003`
  **actually closed 03/09** (the previous marking `7402101` was about dead
  code in the lowering, not the feature; `spawn`/`await`/`channel<T>()` now
  truly defer via microtask, `KofJsRunner` drains `kofActiveTasks` until
  all tasks finish — see `docs/language-reference/concurrency.md` section 4,
  `docs/targets/KOFJS.md`).
- Zero platform API exposed (Thread/Runnable are runtime internals).
- **Memory model (SG-020)**: happens-before spec in
  `docs/language-reference/concurrency-memory-model.md` — SC on all targets,
  6 HB edges (spawn/await/channel/cancel/locals/race), proofs
  `staticsAreSequentiallyConsistent`/`noWordTearingOnLong` in
  `KofConcurrency2Test`.
- See: `docs/language-reference/concurrency.md`.

### HTTP (`kof serve`)

Legacy API (top-level handler):

```kof
handle(String method, String path, String body, String query, String headers): String {
    if (path == "/hello") {
        return "{\"msg\": \"hi\"}"
    }
    return null   // 404
}
```

Native web stack (Phase 1 — independence from Spring):

```kof
record User(String name, Int age)

main() {
    var app = web.app()
    app.use {
        if (header("x-auth") == "secret") {
            return null
        }
        return "{\"error\": \"unauthorized\"}"
    }
    app.get("/hello") {
        return "Hello from Kof"
    }
    app.get("/users/:id") {
        return "user " + param("id") + " q=" + query("name")
    }
    app.post("/user") {
        var user = json.decode<User>(body())
        return json.encode(user)
    }
    app.listen(8080)
}
```

- `web.app()` + routes with trailing lambda; path params (`:id`), query,
  headers, body, `method()`, `path()`; middleware `app.use { ... }`.
- HTTP engine generated inside the program runtime (no servlet container,
  no Spring); each connection on a virtual thread.
- `app.configure(...)` / `app.stats(...)` (JVM, 04/09): connection cap,
  configurable limits and SSE/WebSocket counters.
- `kof serve <file.kf>` detects `main()` and runs `web.app()` apps;
  the legacy `handle(...)` API keeps working.
- See: `docs/stdlib/stdlib-web.md` and `KofWebE2ETest` (9 E2E tests with real sockets).

### Media (`kof.media`) — files, not strings

The language does NOT carry image/audio as a giant `String` (neither base64
literal in the source, nor a hand-pasted data-URI — the pattern that
Kof-editor-theme-maker was forced to adopt with `pageCss(): String` and
`kofPngData(): String`). The app handles the FILE:

```kof
main() {
    var app = web.app()
    app.serveDir("/img", "assets")      // GET /img/logo.png → bytes from disk, image/png
    app.get("/thumb") {
        var img = Image.open("assets/logo.png")   // javax.imageio
        img.saveAs("assets/thumb.jpg", "jpeg")
        return "w=" + img.width() + " h=" + img.height()
    }
    app.get("/rec") {
        var m = Mic.record(2)            // javax.sound.sampled (16kHz mono PCM)
        m.saveWav("assets/gravacao.wav")
        return "ms=" + m.durationMs()
    }
    app.get("/clip") {
        var v = Video.open("assets/clip.mp4")
        return "ms=" + v.durationMs() + " " + v.format()
    }
    app.serveDir("/media", "assets")      // Range 206 for <video> in the browser
    app.listen(8080)
}
```

- **`Image`** (`ImageData`): `open` (PNG/JPEG/GIF/BMP), `width/height/format`,
  `save`, `saveAs(path, fmt)`, `bytes`/`bytesAs`, `dataUri` (optional, at
  runtime — never a literal in the source), `close`.
- **`Audio`**: `openWav`/`saveWav` (WAV RIFF PCM 16-bit), `sampleRate`,
  `durationMs`, `pcmBytes`.
- **`Mic`**: `record(seconds)` from the default microphone, `list()`.
- **`Video`**: `open` + container metadata (`path/size/format/durationMs`,
  MP4/MOV read from the `mvhd` box; other containers → 0) + `bytes`/`close`.
  The app does NOT decode frames — no external lib on the JVM (honest gap); the API
  serves the file (serveDir + Range) for the browser to play.
- **`app.serveDir(prefix, dir)`** (`web`): fallback for dynamic routes —
  returns the FILE in binary with content-type by extension (HTML/CSS/JS/
  images/audio/**video**/fonts/PDF...), `Cache-Control`, protection against
  path traversal and **Range requests** (`206 Partial Content` + `Content-Range`
  + `Accept-Ranges: bytes`, `416` for an invalid range) — required for
  `<video>`/`<audio>` to navigate/seek in the browser. Without it, the app only
  had `String` per route → CSS/HTML/images became concatenated strings and
  base64 pasted into the source.
- Relative paths resolve against the project root (`-Dkof.root`,
  defined by the CLI `run`/`serve` as the directory of the `.kf`).
- **Targets**: JVM (javax.imageio + javax.sound; video as container +
  streaming). **Honest gaps**: video frame decoding (no external lib),
  camera (MEDIA002), mic without hardware (MEDIA003), Native/JS parity
  (MEDIA001 — ART without javax.imageio; Android app runs in the KofJS
  WebView).
- See: `KofMediaE2ETest` (16 tests: byte-by-byte binary serving,
  content-type, blocked traversal, 404, real dimensions, PNG→JPEG
  conversion, WAV info/copy, mic without hardware, MP4 metadata, Range
  206/416/200).

### Native configuration (`kof.config`)

```kof
main() {
    var port = config.int("server.port", 8080)
    var url = config.str("database.url", "jdbc:h2:mem")
    var debug = config.bool("app.debug", false)
    var home = config.env("HOME")
    if (config.has("database.url")) { ... }
}
```

- Precedence: explicit file (`KOF_CONFIG`) > env `KOF_<KEY>` >
  profile (`kof.<KOF_PROFILE>.config`) > default file (`kof.config`).
- Compile-time typing; missing/invalid values → default.
- Native: complete own asm implementation — full precedence
  (KOF_CONFIG > env KOF_<KEY> > profile > kof.config), typed with default
  on an invalid value, trim and comments (`NativeConfigE2ETest`, 8 tests).
  ✅ (JVM/Native/JS — CONF001 closed; see `docs/stdlib/stdlib-config.md`).
  Proof: `KofConfigE2ETest` 11/11 (JVM + Native + JS, real file/env/profile).

### Native logging (`kof.log`)

```kof
log.debug("detail")
log.info("request started")
log.warn("slow response")
log.error("failed: " + message)
```

- Format `timestamp LEVEL message`; info/debug → stdout, warn/error →
  stderr; level via `KOF_LOG_LEVEL` (debug < info < warn < error < off).
- Works inside web handlers. **Native**: own asm implementation
  (Hinnant civil date, own env scan) — UTC timestamp and `KOF_LOG_JSON`
  with no effect for now; JS `console.*` (LOG001 closed 01/09). Docs: `docs/stdlib/stdlib-logging.md`
  (`KofLogE2ETest` incl. JS + `NativeLogE2ETest` 7).

### Language tests (G6 — structured suite)

```kof
test "simple sum" {
    assert(2 + 2 == 4)
}

test "string equal" {
    assert("kof" == "kof", "equal strings")
}

main() { /* ignored by kof test */ }
```

- `test "name" { }` becomes a function at compile-time (desugar → `kof_test_N`);
  the runner is synthesized by the compiler — zero reflection.
- `kof test <file.kf|dir> [--target jvm|native|js]` reports
  `PASS name` / `FAIL name: message` + summary; exit code ≠ 0 if there is a
  failure. Each test runs isolated (try/catch per test).
- Files without `test` blocks keep the old contract (PASS/FAIL by
  exit code of the whole program).
- **process.exit(code)**: new primitive on the 3 targets (JVM System.exit,
  Native syscall, JS sentinel in KofJsRunner) — no stack trace.
- G7 closed: `jwt.*` has an explicit entry in the target matrix — Native
  reports `SECN004` at compile-time (before: silent link error).
- See: `learn/23-testing.md`, `StructuredTestE2ETest` (11 tests).

---

## Tests (3225 = 2762 kof-compiler + 50 kof-script + 7 kof-c-compiler + 406 kof-cli — full suite green, 0 failures / 0 errors, 221 skip; measured 20/09 ~18:14 by the CI Build+Tests job of tip `404d8be6`; §252 native flake silent again. Host without qemu: cross → honest skip)

| Suite | Count | Coverage |
|-------|-----------|-----------|
| CompilerDriverTest | 252 | compilation, semantics, phases, isolation |
| NativeE2ETest | 65 | real execution of native binaries |
| KofJsE2ETest | 40 | real JS execution (GraalJS) + `&&`/`||` short-circuit vs bitwise |
| JvmE2ETest | 31 | real execution of JVM bytecode |
| KofSecurityTest | 28 | kof.security: passwords, crypto, JWT, secrets, adversarial |
| OptimizerTest | 22 | IR optimization passes |
| KofOrmE2ETest | 32 | kof.orm: entity, CRUD, where (+ORM003 typed column validation, P3-10), **Query DSL `User.query(db){ where; orderBy; limit }` (level 3, ORM001)**, migrate, unique, MongoDB (3 conditional skips) |
| KofConcurrency2Test | 33 | spawn stmt/expr, selectAny, cancel/cancelled, done/poll, awaitTimeout, channel (+`Channel<T>` as a function parameter, 3 targets) |
| IoE2ETest | 16 | kof.io multiplatform (+ honest `readText`/`size` contracts 02/09) |

| ComponentCoreE2ETest | 14 | kof.ui Component: view/onMount/onDispose |
| CoreRegressionE2ETest | 50 | real-usage regressions (BOM, toInt, ARITH001...) |
| JsonE2ETest | 15 | JSON JVM + Native |
| UiE2ETest | 29 | kof.ui: widgets, styling, bindings, multiple windows, Table/Ul/Ol/Form/Fieldset/Event (JVM+Native link) |
| AndroidInteropE2ETest | 12 | android: Java interop (external classpath) |
| KofConfigE2ETest | 11 | kof.config: env, file, profiles, precedence, typed, CONF001 |
| KofWebWsE2ETest | 11 | WebSocket RFC 6455: handshake + frame + lifecycle |
| StructuredTestE2ETest | 11 | test "name" {} on the 3 targets + process.exit |
| BackendParityTest | 16 | JVM/Native/JS parity |
| KofLogE2ETest | 11 | kof.log JVM: levels, stderr, off, JSON, correlation |
| KofPatternMatchingTest | 12 | switch case String s / Point(x,y) 3 targets |
| KofWebE2ETest | 12 | native web stack (web.app, routes, JSON, middleware, `app.health` bypass) |
| ExceptionsE2ETest | 9 | try/catch/finally JVM + Native |
| KofDbE2ETest | 24 | kof.db: JDBC, query<T>, transaction, rollback, native SQLite, Native transaction (commit+rollback), **DB001 no JS (bridge GraalJS 16/09: roundtrip js + transaction commit/rollback/nested byte-parity with the JVM)**, **DB002 closed on JS (18/09: typed `query<T>` guest-side bind via `__kof_decode_<T>`, byte-parity with the JVM)**, **cross SQLite roundtrip riscv64+aarch64 (qemu) 15/09** |
| KofHttpServerTest | 8 | serve engine (real sockets) |
| KofMediaE2ETest | 16 | kof.media + serveDir: Image/Audio/WAV/Video(MP4), Range 206/416, binary content (not base64) |
| NativeConfigE2ETest | 8 | kof.config Native (asm): precedence, typed, comments |
| SpawnE2ETest | 10 | spawn (JVM/Native pthread/JS seq) + implicit join + **lambda w/ capture** + **println before spawn** + **`spawn→await→spawn`** (stack alignment in `pthread_create`) |
| IdiomaticE2ETest | 7 | consolidated idioms (chaining, primary ctor) |
| JsonCompleteE2ETest | 7 | complete JSON: Float/Double, array decode (JVM) |
| KofAwaitTest | 8 | typed spawn/await Handle<T> (JVM) |
| KofWebSseE2ETest | 7 | SSE: sse.send/event/close (real sockets) |
| KofWsFrameTest | 7 | frame codec RFC 6455: mask, limits, ping/pong |
| NativeLogE2ETest | 7 | kof.log Native (asm): levels, stderr, civil format, off |
| IdiomaticCoreE2ETest | 6 | field initializers, \u810810, listOf<T>() |
| PackagesE2ETest | 12 | multi-file packages/modules (import a.b.C + moduleRoot from the LCA, P1-4) |
| AssertE2ETest | 5 | assert JVM + Native |
| FloatingPointGapE2ETest | 5 | FP XMM: encode/decode/arrays (FLT001) |
| KofCacheE2ETest | 5 | E2E/compilation suite |
| KofHigherOrderTest | 5 | higher-order functions (map/filter/reduce) |
| KofIntOverflowNativeTest | 5 | 32-bit Int arithmetic on Native |
| KofTimeE2ETest | 12 | time now/sleep/interval (JVM/Native/**JS** — TIME001 closed 02/09: cooperative queue pumped by `time.sleep` in GraalJS) |
| KofWebTlsTest | 5 | TLS/HTTPS: listenSecure + kof.http over TLS |
| KofObservabilityTest | 7 | health/metrics/histogram/requestId/traceId+spanId (W3C) (JVM/Native/JS) |
| FunctionSyntaxTest | 12 | function declaration forms |
| KofEnumSwitchTest | 4 | exhaustive switch over enum + SEM031 |
| KofEnumTest | 4 | enum: values/valueOf/name, SEM030, JVM mapping |
| KofHttpE2ETest | 8 | kof.http client (real sockets, JVM + JS) |
| KofMqE2ETest | 5 | kof.mq publish/subscribe/queue (JVM+Native+JS — MQ001 closed 01/09) |
| KofWebStreamE2ETest | 4 | WebSocket/SSE end-to-end (persistent-conn) |
| LambdaE2ETest | 17 | lambdas + if-expr |
| RouterE2ETest | 4 | kof.ui Router Phase 7: go/replace/back/forward |
| StdlibE2ETest | 4 | now/readFile/writeFile |
| KofJsBrowserE2ETest | 22 | **KofJS in a real browser** (headless Chrome + HTTP + DOM) — kof.ui really renders: widgets/Event/canvas/forms (skips if Chrome is missing) |
| KofJsSourceMapTest | 1 | **KofJS source map V3** (real VLQ mappings, line level: generated function → Kof line via `KofDebugInfo`; before it was a stub `"mappings":""`) |
| ConfigGenTest | 3 | kof config gen: kof.config template from the code |
| KofHttpResilienceE2ETest | 3 | kof.http timeout/retry/circuit (JVM + JS parity) |
| KofMapSetTest | 11 | Map/Set 3 targets (own asm on Native) + `Set<T>`/`Map<K,V>` as a class field/return (JVM: `NoClassDefFoundError` → `HashSet`/`HashMap`; class method parse w/ generic return) + `Map.get` → `V?` (02/09) |
 | KofObservabilityTest | 7 | health/metrics/histogram/requestId/traceId+spanId (W3C) (JVM/Native/JS; Native histogram = gap OBS002) |

| KofSecurityG9Test | 3 | web security: rateLimit/session/apiKey |
| KofValidationTest | 34 | 13 validation predicates (3 targets) |
| TetrisEasterEggTest | 3 | hidden easter egg registration |
| TuringCompleteE2ETest | 3 | Turing completeness (loops/while/recursion) |
| WindowE2ETest | 3 | Window: size, close-to-exit |
| DebugInfoE2ETest | 2 | SourceFile + LineNumberTable (JVM) |
| IRStatisticsTest | 2 | IR observer + optimization statistics |
| NativeDebugTest | 1 | native debug harnesses |
| NativeDebugTest2 | 1 | native debug harnesses (2) |
| NativeDebugTest3 | 1 | native debug harnesses (3) |
| NativeDebugTest4 | 1 | native debug harnesses (4) |
| NativeDebugTest5 | 1 | native debug harnesses (5) |
 | NativeDwarfLineInfoTest | 1 | **native DWARF**: real `.debug_line` in the binary (`objdump --dwarf=decodedline` → Kof file + line per instruction) |
| NullSafetyE2ETest | 7 | `String?` narrowing JVM + readLine EOF null (02/09) |
  | NativeRiscv64E2ETest | 42 | **real riscv64 (qemu)**: runtime in **pure asm** (raw syscalls, no C; static `as`+`ld`) — core (println, var, if/else, arithmetic, classes, arrays, List, switch, try/catch, pattern matching, String methods, recursion) + **stdlib 05/09**: JSON (encode/decode incl. int/long/bool/string scalars), HTTP, spawn/await, cache, time.now, mq (queue/pub-sub), Map/Set, higher-order (map/filter/reduce), String.toInt, metrics `# TYPE`, FP (conversions; `println(double)`→FLT001), honest gates SECN000/SCHED001/TIME001 (DB001 closed on cross 15/09 + JS 16/09) |
  | NativeAarch64E2ETest | 42 | **real aarch64 (qemu)**: runtime in **pure asm** via riscv→aarch64 translation (`translateRiscvToAarch64`), raw syscalls — same core + stdlib as riscv64 (quote-aware translator for strings with `#`) |
 | **kof-compiler Total** | **823** | |
 | kof-script | 8 | KofScriptGlobals / repl / --watch |
 | kof-c-compiler | 5 | KofC C subset → ELF |
 | kof-cli | 4 | LSP references + rename (mock) |
 | **Total** | **840** (+31 conditional skips: Mongo/MySQL/Postgres, windows/mac; check the total in CI on every release) | |
## Idiomatic consolidation (guidelines 0.0.5)

Principle: `intention → Kof → compiler → backend` — never platform
details leaking into the language.

| Guideline | State |
|-----------|--------|
| `User(...)` without `new` (backward compatible) | ✅ |
| Primary constructor `class User(String name)` | ✅ |
| `this` not required | ✅ |
| Field initializers applied in the constructor | ✅ (0.0.5) |
| Method resolution independent of textual order | ✅ |
| Escapes `\n` `\t` `\r` `\u810810` | ✅ (0.0.5) |
| empty `listOf<T>()` preserves the type | ✅ (0.0.5) |
| `List<User>` + typed for-in | ✅ |
| `++`/`--` on fields | ✅ |
| bare `return` in void | ✅ |
| lambdas with captures | ✅ (no dedicated tests yet) |
| CLI args (`main(args)`) | ✅ |
| default parameters | ✅ |
| multi-file modules | ✅ (unified resolution: import a.b.C + moduleRoot from the LCA) |
| `Process` API | ✅ (`kof.process` + `kof_process_run`) |
| `kof.shell` (19/09 MVP `34e4344f`) | ✅ (`cmd`/`run`/`ok` JVM+JS, `pipeline` JVM real; JS/Native pipeline = PROC001 honesto) — sugar sobre `kof.process` |

See the full guidelines in the session's todo.

---

## Kof Debugger (in progress)

Principle: the programmer debugs **Kof code**, never the backend artifact.

| Phase | State |
|------|--------|
| 1 — DebugInfo in the IR (source location per op) | ✅ |
| 2 — JVM: SourceFile + LineNumberTable + LocalVariableTable | ✅ |
| 3 — `kof-debug` MVP (DAP over stdio + raw JDWP): launch, breakpoints by Kof line, `stopped`, stack trace with Kof functions/lines, continue, disconnect | ✅ |
| 4 — Kof Editor (breakpoints, toolbar, variables) | planned |
| 5 — Native (DWARF) | ✅ partial 02/09 (real line info: `.debug_line` via GAS `.file`/`.loc` — Kof file + line per instruction, `objdump --dwarf=decodedline`; `NativeDwarfLineInfoTest`. Local variables/expressions and DAP breakpoints on native pending) |
| 6 — JS (source maps) | ✅ partial 01/09 (line-level V3 source map: generated function → Kof line, `KofJsSourceMapTest`; columns/expressions pending) |
| 7 — Advanced: locals per frame, stepping, exception breakpoints, evaluation | planned |

`kof debug app.kf` already opens a working DAP session on the JVM target:
the session compiles with debug metadata, launches the JVM with JDWP and responds to
`initialize` / `launch` / `setBreakpoints` / `configurationDone` /
`continue` / `threads` / `stackTrace` / `disconnect` — the breakpoint
stops at the Kof line and the call stack shows Kof functions and lines.

Docs: `debugger-architecture.md`, `debugging.md`, `debug-adapter.md`,
`debugging/debugging-jvm.md`, `debugging/debugging-native.md`, `debugging/debugging-js.md`.

---

## Remaining Bugs (real)

> **Complete list with reproduction + suggested fix: `docs/bugs-and-gaps/known-bugs.md`**
> (23+ bugs verified 02/09 — user round 3: kof-ui reuses a widget ID
> after remove, lambda→lambda and lambda-in-list invoked break, PKG005
> rejects equal names in different packages, Native loses the constructor of a
> class from another package (undefined reference), ExternalClasspath does not resolve
> a superclass outside the entries).

1. ~~Automatic GC on Native~~ — ✅ real sweep 03/09 (`kof_gc_sweep` closed);
   **auto-collect ✅ landed 19/09** (D1-A, §260 CLOSED — the trigger on an
   exhausted free-list is now SOUND: blanket-spill of the 15 GPRs in
   `kof_gc_collect_now`, `kof_spawn_count==0` gate, one-shot flag; `a904317e`).
   `kof_gc_collect_now` remains available for explicit use
2. ~~`spawn` on Native: CONC001~~ — ✅ closed 31/08: pthread_create + trampoline + await/pthread_join + thread-safe allocator (futex) + implicit join + `done`/`poll`/`cancel`/`cancelled`/`selectAny` (cooperative cancel by TID + selectAny polling 1ms; `SemanticAnalyzer` disambiguates `cancel(Handle<T>)→Bool` vs `scheduler.cancel(String)→VOID`)
   - ✅ ~~SEPARATE pre-existing bug: `spawn→await→spawn` SIGSEGV on the 2nd `pthread_create`~~ — **resolved 01/09**: same mechanism as println-before-spawn. The `call pthread_create` site requires `rsp ≡ 0 (mod 16)` by the SysV ABI; after `pthread_join` (from `await`) the stack arrived 8 bytes misaligned and glibc segfaulted in `pthread_attr_copy`. Stack alignment at the C call (`andq $-16, %rsp` in `kof_spawn_handle_new`, preserving `r15` + caller frame). `SpawnE2ETest.nativeSpawnAwaitSpawnDoesNotSegfault` (without the fix: SIGSEGV 3/3; with it: ok 3/3). **Note**: alignment had already been audited "per the ABI" and ruled out as a cause in a previous session — the measurement now pins down that the `call pthread_create` site effectively arrived misaligned in the cases with output/join before spawn.
3. ~~JSON of objects/records on Native: JSN002~~ — ✅ closed (compile-time composition)
4. ~~JSON Float/Double: JSN001~~ — ✅ closed 31/08 (full FP parser: fraction+exponent, Double[] arrays)
5. ~~JSON array decode~~ — ✅ JSN003 closed: Int[]/Long[]/Bool[]/String[]; JSN001 closed Double[]/Float[] (31/08)
6. ~~Lambdas without capture~~ — ✅ capture implemented (mutable via `BoxN` box; `Lambda0`/`Box0`)
7. ~~Generics `Box<T>` with native println~~ — ✅ 25/08 `Box<Int>`/`T` substituted + `kof_int_to_string`
8. ~~`SEM025` false positive on `hashCode/equals/toString`~~ — ✅ `isObjectMethod` on 25/08
9. ~~`await`/join~~ — ✅ on the 3 targets (JVM virtual threads, JS event-loop, Native pthread)
10. ~~`kof fmt`: planned (P5)~~ — ✅ implemented: `kof fmt` via the real parser
    (`KofFormatter`), idempotent (2c3e794)
11. ~~Map/Set~~ — ✅ `List.map/filter/reduce` + `Map/Set` JVM/Native/JS (26/08)
12. Pattern matching: ✅ `switch (x) { case String s: ... }` + `case Point(x,y)` in `Parser/Semantic/CompilerDriver` + `Native rbx→rcx` + `JS typeof` (27/08 `Point(x,y)` `JVM:30 Native:30 JS:30` `KofPatternMatchingTest 10/10` + `KofWebE2ETest 9/9`)
13. Null safety `String?`: ✅ basic `String?` `Int?` `?`-check at compile-time `Type.NullableType` `JvmBackend:110` `SemanticAnalyzer:1637` `isAssignable` `var s:String?=null` `s==null` `t="hello"` `jvm: null/hello native: null/hello js: null/hello` (27/08)
14. ~~Multi-file modules imports lost in large projects~~ — ✅ 27/08 `CompilerDriver.java:243` `import a.b.C` file import `+` `a.b` dir import, `largeproj` `a/b/C.kf` `decls=2` `Main.class+a/b/C.class` ok
15. ~~Native `List.get`~~ — ✅ verified `listOf(1,2,3).get(1) → 2` native `kof_list_get` bounds OK (the `List.of` case was `listOf`)
16. Web: custom status codes/headers per handler: ✅ `kof.web.status(201, body)` + `headerSet("X","y")` in `KofWeb.java:248` `kof_web_status` + `JvmWebCoreRuntime.java:20` `KOF_WEB_STATUS` + `JvmRuntimeWebDispatch.java:173` `kof_web_dispatch` `+wired` `kof_web_build` headers `+wired` `status_text 201 Created 202 Accepted` `JVM: 201/hellox 202/value` `KofWebE2ETest 9/9` (27/08)
17. ~~Web: native kof.web without a server~~ — ✅ closed 03/09 WEB002 (T1-T4 `NativeWebRuntime.java`): HTTP/1.1 accept loop with request-line parsing, literal route match, handler dispatch via vtable[0] trampoline, request body(); suite `KofWebNativeE2ETest` 4/4. Pending: path params `{id}`, `param()/query()/header()`, keep-alive (Connection: close per request), SSE/WS (WEB003/4), TLS (WEB002-secure).
18. ~~MySQL/MariaDB on Native: wire protocol~~ — ✅ 31/08: handshake + SHA-1 scramble + auth-switch + COM_QUERY + resultset (coldefs/rows/EOF) + **`?` binds** — and ✅ 03/09: real **binary prepared statements (COM_STMT_PREPARE/EXECUTE)**. `kof_db_mysql_prepare`/`kof_db_mysql_exec`/`kof_db_mysql_prep_query` in `NativeDbPrepared.java` (new module, ≤500 lines): PREPARE (0x16) → OK + drain metadata (params coldefs + EOF, cols coldefs + EOF, capturing name+type), EXECUTE (0x17, null-bitmap + type pairs + raw values Int 4B/8B, lenenc strings); binary-row parsing in the resultset. `db.execute`/`db.query` with binds use the binary; COM_QUERY substitution fallback only if PREPARE fails. Binds with quotes/SQL-injection intact (no manual escape). Validated against a real MySQL 8.0 (127.0.0.1:13306), strace confirms 0x16/0x17 on the wire. `KofDbE2ETest` 12/12 (+ `nativeMysqlPreparedBinary`). (01/09 reverse; 03/09 resolved with `NativeDbPrepared.java` ≤500 lines).
19. ~~`kof_sec_secret_get` on Native~~ — ✅ resolved: rewritten in the linear pattern of the others; segfault and wrong fragments eliminated.
20. ~~Floating point on Native~~ — ✅ FLT001 closed: FP is real XMM (`vcvtsi2sd`, `mulsd`); aligned snprintf dtoa; `kof_string_to_double` full parse (fraction+exponent).
21. ~~same~~
22. riscv64/aarch64 **core complete** — ✅ **02/09 real riscv64 + 03/09 real aarch64**: `Target.NATIVE_RISCV64`/`NATIVE_AARCH64` + CLI `native.risc`/`native.arm` + dispatch + **real lowering** (stack machine: riscv64 `sp`/`s11`/`ra`, aarch64 `sp`/`x29`/`x30` via line-by-line translation) + **runtime in pure asm** (raw syscalls `write` 64 / `exit` 93, bump allocator, no C — static binaries via `as`+`ld`; Kof is Kof) + qemu; `NativeRiscv64E2ETest 26/26` + `NativeAarch64E2ETest 26/26` (core: println String/Int, var, if/else, arithmetic, classes virtual/fields, arrays, List, switch, try/catch/throw, pattern matching, String methods, recursion). **Stdlib parity 05/09**: JSON (encode/decode scalars+lists), HTTP, spawn/await (clone+futex), cache, time.now, mq, Map/Set, higher-order, String.toInt, metrics `# TYPE`, FP conversions — parity sweeps with **0 divergences** on the 3 targets. **UPDATE 19/09 — NATIVE002 CLOSED**: DB001 cross (SQLite, 15/09), FP/§107 record+nested cross (FLT001 + face (4), 19/09), per-arch parity columns + cross CI closed; remaining honest per-domain refusals: SECN000 (R11 non-goal), OTP001 (§129 TLS decision registered, nat lane), JSN004, `kof.ui` — gap codes, never silent. **Implementation record: `docs/native-multiarch.md`** (promoted from `development/` 19/09)
23. ~~Native `kof.cache`: segfault in `set_ttl` (`%rax` index clobbered) + `get/ttl` (exp in `%rdi` clobbered) + `println(null)` segfault~~ — ✅ 30/08: registers preserved (`%r14/%r13/%r15`), expiration `jle` branch fixed, `kof_print_string` guards null, `find_slot` overwrites an existing key; `KofCacheE2ETest 5/5 x3 targets`
24. ~~`spawn`-statement (fire-and-forget) on Native was not joined~~ — ✅ 01/09: `kof_spawn` (stmt) created the thread but **did not register** the handle in the list that `kof_spawn_join_all` walks; and `join_all` was only emitted in the `!endsWithReturn` block, which never runs for main (the driver always closes main with `KofReturnVoid` → `endsWithReturn`). Result: the process exited before the worker printed. Fix: `kof_spawn` now delegates to `kof_spawn_result` (registers the handle) and `join_all` is emitted in the **return epilogue** of main (idempotent — clears the list). `SpawnE2ETest 4/4`
25. **`throw <non-String>` / `catch <non-String>` generates invalid bytecode on the JVM** (documented 02/09) — `throw 42` compiles but the `.class` fails at load (`ClassFormatError`, disguised as a "JavaFX launcher error"). Exceptions are Strings; the compiler should **reject** `throw <non-String>` at compile-time. Reproduction + likely files in **`docs/bugs-and-gaps/known-bugs.md` #1**.
26. **Mutable capture on Native: reading a boxed variable INSIDE the lambda after EXTERNAL mutation produces garbage** (documented 02/09) — `var f = (x) -> x + offset; offset = 20; f(5)` returns a pointer/offset on Native (JVM correct). The "lambda writes" direction works. `NativeBackend.resolveFieldOffset` resolves the box layout against the lambda class (fallback HEADER_SIZE). Reproduction + files in **`docs/bugs-and-gaps/known-bugs.md` #2**.

---

## Next Steps (order P1→P5)

**P1 — Language (in progress):**
1. ✅ `Map/Set` + `enum` + `await` + `List.map/filter/reduce` (JVM/Native/JS)
2. ✅ `Pattern matching` — `switch (x) { case String s: ... }` + `case Point(x,y)` `JVM/Native/JS` `30` `10/10`
3. ✅ `Nullability` `String?`/`Int?` + `?`-check `Type.NullableType` `jvm/native/js null/hello` (27/08 basic)
4. ✅ `Multi-file modules` — `kof build <dir>` with unified resolution: `import a.b.C` file fix done + `moduleRoot` derived from the **lowest common ancestor** of the sources (3-arg `compileSources` resolves cross-directory imports without an explicit root; `PackagesE2ETest` 6/6)

**P2 — Full web (next short list):**
5. ✅ Rich response `status(201, body)`/`headerSet("X","y")` `JVM` `201 Created 202 Accepted` `X-Custom/X-Test` `KofWebE2ETest 9/9` (27/08) **`Native partial` (03/09 — base server returns 200+body; `status()`/`headerSet()` context-fns are `WEB001` gaps)** `JS ✅ 03/09` (real GraalJS HttpServer — `status`/`headerSet` context-fns 16/09)
6. ✅ `kof.cache` `get/set/set(key,v,ttl)/ttl/delete/clear` — ✅ JVM/Native/JS (30/08; native fix: `%rax/%rdi` clobber in `set_ttl/get/ttl` + `println(null)` segfault; `KofCacheE2ETest 5/5 x3 targets`)
7. ✅ `WebSocket` `app.ws("/chat") { }` + `SSE` `sse.send/event/close` — ✅ JVM (30/08; PRs 14-17: persistent-conn/route-kinds, SSE, RFC 6455 handshake, frame codec+mask; `KofWebSseE2ETest 7/7` `KofWebWsE2ETest 11/11` `KofWsFrameTest 7/7`; hardening/limits/counters 04/09 — `KofWebHardeningTest 6/6`)
8. ✅ `Scheduler` `every(ms) { }`/`cancel(id)` (`at(cron)` = real 5-field UTC cron on JVM/JS, Native compile-time gap `CRON001` — §274, 17/09) — ✅ JVM (`ScheduledExecutor`, 27/08) + JS (`setInterval`) + **Native SCHED001** (31/08: thread per job — `usleep` ms→us trampoline + `active` flag with futex — cooperative `cancel(id)`; `KofConcurrency2Test` `schedulerEveryNative/Jvm`)
9. ✅ `kof.http` `timeout`/`retry`/`circuit breaker` — ✅ JVM+JS (30/08; retry repeats on exception+HTTP 5xx, circuit opens after N failures for 30s with fail-fast, `circuit(0)` recovers; `KofHttpResilienceE2ETest 3/3` JVM+JS) — `HTTP/2` missing

**P3 — Production data:**
10. ✅ Typed Query DSL (level 3) — **typed** column validation in `orm.where<T>`/`where_op`/`count` (`ORM003`) + **syntax `User.query(db) { where age > 25; orderBy name desc; limit 10 }`** (01/09): the compiler lowers the block to `db.query<T>` (SQL prepared at compile-time from the entity schema, quoted identifiers, values as `?` binds; multiple `where` → `AND`; nonexistent columns → `ORM003`, where without comparison / unsupported operator / >4 binds → `ORM004`; the lowering is target-agnostic (emits the same `db.queryN` on the JVM and on Native) and the E2E runs on the JVM (H2) — the entity workflow on Native follows `ORM001` — `KofOrmE2ETest` 22)
11. Connection pooling + `kof.db`/`kof.orm` outside the JVM (JS via WASM, Native ORM over SQLite)
12. ~~Native MySQL/MariaDB (handshake+query)~~ — ✅ 31/08 (wire protocol: handshake+scramble+auth-switch+COM_QUERY+resultset); ✅ 03/09 **binary prepared statements** (COM_STMT_PREPARE/EXECUTE + binary-row parsing; see #18)

 **P4 — Observability:**
 13. ✅ `histogram` metrics + `/metrics` endpoint (Prometheus) — ✅ 01/09: `observability.histogram(name, value)` (sum+count) + `observability.metrics()` exporting counters/gauges/histograms in **text exposition format** (JVM + JS + **Native** — `OBS002` closed: 32B asm store + export via `kof_string_concat`, content parity with the JVM). The app exposes it via `app.get("/metrics") { return observability.metrics() }` — no special endpoint.
  14. ✅ Health `app.health("/health")` + lightweight tracing — ✅ 01/09 `app.health(path)` (built-in, responds `{"status":"UP","ready":true,"alive":true}` **before the middlewares** — a load balancer probe does not go through auth); `observability.health()/readiness()/liveness()` (3 targets). **W3C tracing**: `observability.traceId()` (32 hex) + `observability.spanId()` (16 hex) — pure IDs, no store, **3 targets** (JVM `SecureRandom`, JS `Math.random`, Native `getrandom`); **spans with timing** `spanStart/spanEnd` (JSON {traceId, spanId, durationMicros}, 3 targets — 01/09) + **lifecycle** `application { onStart/onShutdown }` (desugar → main prologue/epilogue, 3 targets — 01/09); `KofObservabilityTest.tracingJvmNativeJs` + `spansWithTiming` + `applicationLifecycle*`. **OpenTelemetry** (full export/propagation) pending

 **P5 — DX:**
 15. ✅ `kof fmt` (real parser) + `kof init` + `REPL` — ✅ all implemented (`Fmt.java`, `init` in `Main.java:211`, `repl` in `CmdScript.java:100`); `fmt` idempotent
  16. ✅ LSP hover/completion/**references**/**rename** + Native DWARF/JS source maps Debugger + VS Code extension — LSP hover/completion ✅ + `textDocument/references` + `textDocument/rename` (word-boundary, single-file; `LspServerTest` 4/4). **JS source maps V3 (line level) ✅ 01/09** (`KofJsSourceMapTest`); Native DWARF + VS Code pending

## Roadmap — State by Phase (31/08)

### Done — Available

- Compiler foundation — Lexer, Parser, AST, Type system foundation, Semantic analysis, Kof IR
- JVM backend; Native backend (x86_64); JS backend (GraalJS)
- classes, records, inheritance, interfaces, constructors (overloading), exceptions, generics, collections, string operations, control flow
- `kof build`, `kof run`, `kof serve`, `kof test`, `kof debug` (JVM MVP, DAP over stdio), `kof bench` (37 benchmarks + baselines), `kof fmt` (real parser, idempotent)
- `kof.web` — routes and middleware (JVM); WebSocket RFC 6455 + native SSE (JVM, 0.2.6-beta); TLS/HTTPS `web.listenSecure` (JVM); limits/observability `configure`/`stats`
- `kof.db` — JDBC + native SQLite; `kof.orm` — entity, CRUD, migrate, MongoDB (JVM)
- native `kof.log`; `kof.config` (file > env > profile, typed, `${key}`, 3 targets); `kof.mq` pub/sub (JVM)
- HTTP client (JVM) + JS via `Java HttpClient` interop + **Native 03/09** (`NativeHttpRuntime.java` — HTTP/1.1 asm: URL parse, socket+connect, request parse, status; https throw; DNS↦127.0.0.1 fallback) + retry/circuit (3 targets, 30/08)
- `kof.security` v1 (JVM/Native/JS); web security G9 — rateLimit, sessions, API keys (3 targets)
- `kof.validation` (13 predicates, 3 targets); `kof.observability` (health/metrics/request IDs, 3 targets); `kof.ui` widgets with KofJS render
- `kof.process` execution of external processes; `process.run`/`process.exit` on JVM+JS; `process.spawn` (live stdin/stdout) is **JVM-only** — an honest `PROC001` on Native *and* JS (the JS backend binds no `kof_process_spawn`; measured + gated 18/09, `DomainGapCodesTest`)
- **Concurrency**: `spawn`/`await` JVM (virtual threads) + **Native (pthread — CONC001 closed 31/08)** + **Android (platform threads — AND001 closed 31/08, ART without virtual threads → fallback)** + JS event-loop (CONC003 closed 03/09); non-blocking `done`/`poll`; cooperative `cancel`/`cancelled` (JVM + Native by TID); `selectAny` (JVM + Native + JS); `awaitTimeout(r, ms)` — value within the deadline, catchable exception on timeout (JVM + Native + JS `kofAwaitTimeout` deadline poll); `channel<T>()` with `send`/`receive` (JVM LinkedBlockingQueue + Native FIFO futex + JS array); `scheduler.every/cancel` (JVM `ScheduledExecutor` + JS `setInterval` + **Native SCHED001**: thread per job with `usleep` ms→us trampoline + `active` futex flag); `at(cron)` is a real 5-field UTC cron on JVM/JS (Native `CRON001`; §274) — `KofConcurrency2Test` 15/15, `SpawnE2ETest` 5/5
- **`kof.media` (31/08)** — multimedia file management without literal base64: `Image.open/save/saveAs/dataUri` (javax.imageio, PNG/JPEG/GIF/BMP), `Audio.openWav/saveWav` (WAV RIFF PCM 16-bit), `Mic.record` (javax.sound.sampled), `Video.open` (MP4/MOV container metadata + streaming); `web` `app.serveDir(prefix, dir)` serves a FILE from disk with the correct content-type + **Range requests (206/416)** for seekable video + path-traversal protection; app root via `-Dkof.root` (CLI `run`/`serve`). Gaps: video frames (no external lib), camera (MEDIA002), no mic hardware (MEDIA003), Native/JS parity (MEDIA001) — `KofMediaE2ETest` 16/16
- **KofAndroid Phase 2 (31/08; pipeline hardened + CI-proven 18/09, §299/X9)** — standalone `--apk` (aapt2/d8/zipalign/apksigner straight from the CLI; build-tools **≥35** pinned for d8 class-major-65/Java-21, `keytool` CWD fixed — e2e `androidWithSdkPackagesApk` runs without skip on CI ubuntu; without SDK = honest `DEP001` failure, R6) + release signing `--keystore/--storepass/--keypass/--alias` + label/permissions derived from the program (`detectAppLabel`/`@Permissions`)
- enum on the 3 targets + exhaustive switch (SEM031); Map/Set on the 3 targets (COL001 closed)
- IR optimizer always active; pattern matching (switch with types + destructuring, 3 targets); basic null safety (`String?`, 3 targets); higher-order on collections (map/filter/reduce, 3 targets); multi-file modules (`import a.b.C`)
- KofScript — top-level `var`/`val` (`KofScriptGlobals`, repl, `--watch`); KofC compiler — C subset → ELF x86_64 (`kof c`)
- LSP with hover/completion + real diagnostics; return widening
- Native GC — mark-sweep 03/09 ✅: `kof_gc_mark` (conservative stack+bss) + `kof_gc_sweep` (clears dead entries to the free-list; flag bit1 @24) + `kof_gc_collect_now`; **auto-collect on exhaustion ✅ 19/09** (D1-A, §260 CLOSED — the trigger is now SOUND: blanket-spill of the 15 GPRs + `kof_spawn_count==0` gate + one-shot flag; `a904317e`). `KofGcE2ETest` 3/3
- Real floating point on Native (FLT001 closed 31/08 — XMM); JSON objects/records on Native (JSN002 closed) + FP arrays (JSN001/003)
- multiplatform releases (2 jobs: `test-and-bump` → `package-and-release`; linux-x86_64 / macos-arm64 / windows-x86_64)

### In development

- Standard Library (contracts stabilizing)
- Async / Concurrency: ~~real JS async over Promises (CONC003)~~ — ✅ 03/09 (GraalJS `async`/`await`/`Promise`, async coloring by fixpoint in the compiler, `KofJsRunner` drains the microtask queue — see `docs/language-reference/concurrency.md`); ~~Android `AND001`~~ — ✅ 31/08 (platform threads on ART, fallback when `Thread.startVirtualThread` is absent); ~~pre-existing `spawn→await→spawn` bug~~ — ✅ resolved 01/09 (stack alignment in `pthread_create` — see "Remaining Bugs" #2)
- ~~KofAndroid Phase 2~~ — ✅ 31/08 (standalone `--apk` + `--keystore` release signing + label/permissions derived from the program) — pipeline real/CI-proven 18/09 (§299: build-tools ≥35 + keytool CWD + split ≤500)
- ~~`kof.media` residual (31/08)~~ — ✅ 31/08: **video** (`Video.open` + container metadata + streaming) and **Range requests** (206/416) closed; camera remains (MEDIA002 — no external lib on the JVM; **label, not an emitted code** — `KofMedia` emits MEDIA001/MEDIA003) and Native/JS parity (MEDIA001 — ART without javax.imageio; the Android app runs in the KofJS WebView)
- Native MySQL/MariaDB — **wire protocol ✅ 31/08** (handshake + SHA-1 scramble + auth-switch + COM_QUERY + resultset; `?` binds via client-side substitution; `nativeMysqlWireProtocol`) + **binary prepared statements ✅ 03/09** (COM_STMT_PREPARE/EXECUTE + binary-rows, `NativeDbPrepared` — see "Remaining Bugs" #18)
- `native.risc` (riscv64) + `native.arm` (aarch64) **core complete (02-03/09)** — plumbing + codegen/runtimes in pure asm + qemu, `NativeRiscv64E2ETest 26/26` + `NativeAarch64E2ETest 26/26` (core + stdlib 05/09: JSON/HTTP/spawn/cache/time/mq/Map/Set/higher-order/toInt/metrics; gates DB001/SECN000/SCHED001/TIME001) — **record: `docs/native-multiarch.md`** (NATIVE002 CLOSED 19/09, doc promoted)
- Debugger — JVM MVP (DAP over stdio) + **line-level V3 JS source maps (01/09)**; Native DWARF pending
- KofJS — web platform in the browser (ES Modules via GraalJS already in alpha)
- Package manager: **`kof deps` transitive dependencies ✅ 16/09** — `kofdeps.lock` + Maven delegation (R9: never reimplement the Maven graph resolver); honest degradation when `mvn` is absent; `DepsTransitiveTest` 10/10 incl. real-Maven E2E; **registry MVP ✅ 19/09 (D2-A)**: publish (`kof deploy --publish` = Release + tar.gz + SHA256SUMS) + pull (`owner/repo[@ver]` → asset da release, `SHA256SUMS` verificado antes de instalar, cache `~/.kof/deps/kof/`, `latest` pinna; REG001–004 honestos; `DepsRegistryTest` 6/6)

### Planned

- complete language specification; conformance suite
- full web platform (declarative frontend + routing/forms/SSR)
- **gRPC in `kof.web`** (31/08) — gRPC RPC communication as a first-class part of the web platform: `app.grpc { service ... }` (stubs from `.proto`, server streaming + unary over HTTP/2 on the JVM) + client `grpc.call(endpoint, method, msg)`; `.proto` codegen → IR; JVM parity first (see `docs/development/roadmap.md` § web)
- self-hosting (compiler written in Kof)

Full roadmap: `docs/development/roadmap.md`; execution: `docs/development/roadmap.md` §23 (ex-plan-platform-completion)
