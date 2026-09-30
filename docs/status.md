[English](status.md) | [Português](status.pt_BR.md)

# Kof Project Status

last: #702 `Scroll(children)` layout widget LANDED 30/09 (`docs/ui/architecture.md` §2.8; KofJS `div.kof-scroll` `overflow:auto`, JVM/Native no-op; `UiLayoutRenderE2ETest#scrollRendersScrollableContainer` RED-first + UI battery 74/74; KOFUI-AUDIT inventory 36→37 types) + 1i zip LANDED 30/09 (managed GREEN + native reference-elements byte-identical; primitive-element native zip → honest `NAT008` via `D-MULTIPARADIGMA-ZIP-NATIVE`; `ListZipE2ETest`/`DomainGapCodesTest` 47/0F) + §542 FIXED 29/09 (x86-64 host contiguous arena, now adaptive to `RLIMIT_AS` — try 4 GiB, halve to a 64 MiB floor, + O(1) GC block bitmap; `CrossHeapParityE2ETest` 6/6, `KofGcE2ETest` 4/4) + #688 FIXED 29/09 (function-call args check generic type-args via the nominal `isAssignable`; `GenericCallArgCheckTest` 8/8) + #686 FIXED 29/09 (statement `switch` exhaustiveness sealed/Bool; `SwitchStmtExhaustivenessTest` 7/7) + D-PARITY-050-SCOPE target-matrix invariant (4785eb8ef) + #673 living-records §535/§536 registered + #672 ledger-selftest-PT-proven + #671 stale-cells-purged (JS-FFI + WASM-scope) + #670 ownership-table landed (spec §7, 4 boundaries + Rust absence) + #669 D-vs-DECISIONS gate + #666 phase-5-unit-1 pinned (FFI x spawn) + #664/#665 evidence-chain hardened + #660 D-MEM021-SCALAR landed (c65f9ba18) + kof-file streaming slice 2 (all-target golden + JS gap IOJS001) + slice 2.1 TextStream (UTF-8 lines, all-target BMP golden; astral Native divergence §537) + test-architecture phase-2 quick-win slice 1 (hygiene baseline 186→185 keys, KofWebHardeningTest 6/6) + slice 2 (shared readiness fixture TestServerFixture, baseline 185→182, 4 web classes 49/49) + slice 3 (fixture extended to Media/OAuth/Hardening/Sse/Ws + maxConnections race de-flaked, baseline 182→179, 45/45) + slice 4 (TCP-only awaitPort + Native/WebJs/Blog loops migrated, baseline 179→176, 106/106) + slice 5 (bounded awaitTrue polls replace Hardening/Ws settles, baseline 176→174, 106/106) + slice 6 (custom-probe await + Tls/Log loops & settles migrated, baseline 174 — 2 sleeps off, 2 foreign dupname in; 124/124) + slice 7 (CLI readiness/teardown sleeps → CliAwaitFixture awaitTrue/awaitExit, baseline 174→171, 10/10) + slice 8 (KofTimeE2ETest scheduler sleeps → bounded polls, baseline 171→170, 44/44) + slice 2.2 CsvReader/CsvWriter (streaming CSV/TSV read+write, all-target golden; JS IOJS001) + slice 2.3 JsonLines (JSON Lines/NDJSON seam over kof.json, untyped by design; §538 catalogued, live 5->6; all-target golden; JS IOJS001) + test-architecture `--citations` (Phase 3 split cost per oversized class measured; cheapest-first ArrayBoundsStressTest/KofSetEqualityTest = 2; all 43 cited >= 2x; audit selftest 14/14) + test-architecture Phase 3 splits (KofSetEqualitySupport from KofSetEqualityTest 21/21; KofMathSupport from KofMathTest 29/29 — both zero citation drift; oversized 43->41, baseline 170->168) + development-queue doc-truth (three stale README cells: test-architecture/kof-file state + #665 gate implemented as #669) + #651 fatias A1/A2/B Buffer(U8) surface + FFI token B on x86-64 AND cross riscv64/aarch64 (`D-BUFFER-INOUT-NATIVE`, B 29/09) + phase-5 unit 2 (`#667` Script×extern `FFI001` at the decl line + `#668` `MEM020` compile face; `D-SCRIPT-EXTERN-REFUSE`/`D-MEM020-COMPILE`) + phase-5 unit 3 pinned (`Buffer(U8)` INOUT × `spawn`/`await` runtime parity measured) + slice 2.4 XmlReader (streaming XML pull reader, documented subset; all-target golden + JVM error cases; JS IOJS001) + slice 3.1 Ini (streaming INI config reader; sections/global/comments/quotes/last-wins, all-target golden + error cases; JS IOJS001) + slice 2.5 XML namespaces (scoped xmlns declarations, localName/namespaceUri/attributeNamespaces, unbound-prefix error; name verbatim; all-target golden + error cases; JS IOJS001) + slice 3.2 Toml (streaming TOML config reader; comments/dotted keys/tables/escapes/integer-float-bool/single-line arrays, typed accessors; all-target golden + error cases; JS IOJS001) + slice 3.3 Yaml (streaming YAML config reader; block mappings/scalar sequences/escapes/null, flattened dotted keys, typed accessors; all-target golden + error cases; JS IOJS001; Phase 3 config complete) + kof-file plan CONCLUDED 28/09 (promoted scope landed; moved to `docs/stdlib/kof-file-plan.md`; documents/archives deferred) + image-vision front promoted (`D-IMAGE-VISION-GO`) slice 1 (pure-Kof `libs/image/` metadata: format/dimensions PNG/GIF/BMP/JPEG/WEBP; `ImageMetadataE2ETest` 7/7 JVM + Native x86-64/riscv64 + Script; JS `IOJS001`; §540 catalogued; slice 1b TIFF/ICO + `isImage`)
doing: #688 + #686 FIXED 29/09 (type-system lane: call-arg generic check + statement-switch exhaustiveness) / §542 FIXED 29/09 (x86-64 host contiguous arena, adaptive to `RLIMIT_AS`; `CrossHeapParityE2ETest` 6/6, `KofGcE2ETest` 4/4; GC/native battery green) / memory-safety (phase-5 unit 2 LANDED, unit 3 pinned, #651 COMPLETE; 30/09 maintainer batch `D-MEM030-BORROW-RUNTIME`/`D-MEM-PHASE6-4BACKENDS`/`D-MEM-FFI-CROSS-FULL` — closing front: B-03 runtime borrow-state + FFI cross total; parity lane) / connectors (fatias 1–16 LANDED in pure-Kof `libs/interop/`: manifest+validation, `InteropType`/`InteropOwnership`/`InteropString`/`InteropCost`/`InteropLibrary`/`InteropCompatibility`/`ForeignModule`/`ConnectorSpi`/`ConnectorCatalogue`/`InteropCore`; `D-CONNECTORS-GO`, issues/tooling lane — queue 0g; Core+SPI+manifest surface complete, next gated on rule 6) / test-architecture (phases 3/4 CONCLUDED: 43→19 oversized, 146→132 keys, `--dups`) / multiparadigma (1a–1i LANDED 30/09: `zip` managed GREEN + native reference-elements byte-identical; primitive-element native `zip` refused with `NAT008` per `D-MULTIPARADIGMA-ZIP-NATIVE`; `D-MULTIPARADIGMA-ZIP` Pair collision resolved option A) / §539 FIXED 29/09 (PR #677 merged 398f73664, #676 closed) / image-vision (metadata + raster decode PNM/farbfeld/BMP/QOI/GIF/PNG/WebP-VP8L **complete lossless path** — simple+normal-Huffman+LZ77+color-cache+predictor/color/color-indexing transforms+meta-Huffman groups LANDED; slice 3i 30/09; **§34.5 raster cap 16384→262144 + VP8L guard unified + §34.4 encode/write + `kof.vision` (histogram/Otsu + equalization + Sobel edges + connected components + morphology) LANDED 30/09**; official package)
next: #685 FIXED 30/09 (enum `List.sort()`/`sorted(cmp)` accept enum in ordinal order — `8f9610f73` + the `check_500` split `d7d56faaa`; `EnumSortE2ETest` 3/3 all targets incl. cross qemu; issue #685 CLOSED) / §524 FIXED 30/09 (qemu-aarch64 harnesses share one bound+retry `QemuRun`; under-load confirmation remains the CI `Native cross` job) + §544 OPEN 30/09 (riscv64 VP8L decode of a >16384-px lossless WebP aborts array-index-out-of-bounds; JVM/x86 OK; owner native/GC, issue #700) + §541 FIXED 29/09 (x86 heap reused dirty memory → PNG x86 un-quarantined) + §543 FIXED 29/09 (x86 call-site scratch >32 locals) + §542 residual (x86 free-list first-fit O(F), out of scope) / image-vision next (WebP lossy `VP8 `/AVIF; `kof.image` surface DECIDED `D-IMAGE-SURFACE` — reuse `Raster`, JPEG interop landed; VP8L lossless complete) / maintainer decisions (§539 encoding rule; multiparadigma sorted/groupBy/zip — zip resolved 30/09 via `D-MULTIPARADIGMA-ZIP-NATIVE` `NAT008`) / connectors rule-6 stop (Phase 2 C-ABI needs authorization; §3.7 error model + §3.9 ABI policy need maintainer) / #651 COMPLETE (A1/A2/B landed 29/09) + phase-5 unit 4 = B-03 runtime borrow-state on `Buffer(U8)` (`D-MEM030-BORROW-RUNTIME`, 6-face proof) + FFI cross total `String[]`/structs/callbacks/out-buffer (`D-MEM-FFI-CROSS-FULL`) / future/ re-sweep on trigger (`D-FUTURE-BATCH-2809` authorized every plan; promotion one-at-a-time per `D-FUTURE-PROMOTION`)
last: #702 `Scroll(children)` layout widget LANDED 30/09 (`docs/ui/architecture.md` §2.8; KofJS `div.kof-scroll` `overflow:auto`, JVM/Native no-op; `UiLayoutRenderE2ETest#scrollRendersScrollableContainer` RED-first + UI battery 74/74; KOFUI-AUDIT inventory 36→37 types) + §542 FIXED 29/09 (x86-64 host contiguous arena, now adaptive to `RLIMIT_AS` — try 4 GiB, halve to a 64 MiB floor, + O(1) GC block bitmap; `CrossHeapParityE2ETest` 6/6, `KofGcE2ETest` 4/4) + #688 FIXED 29/09 (function-call args check generic type-args via the nominal `isAssignable`; `GenericCallArgCheckTest` 8/8) + #686 FIXED 29/09 (statement `switch` exhaustiveness sealed/Bool; `SwitchStmtExhaustivenessTest` 7/7) + D-PARITY-050-SCOPE target-matrix invariant (4785eb8ef) + #673 living-records §535/§536 registered + #672 ledger-selftest-PT-proven + #671 stale-cells-purged (JS-FFI + WASM-scope) + #670 ownership-table landed (spec §7, 4 boundaries + Rust absence) + #669 D-vs-DECISIONS gate + #666 phase-5-unit-1 pinned (FFI x spawn) + #664/#665 evidence-chain hardened + #660 D-MEM021-SCALAR landed (c65f9ba18) + kof-file streaming slice 2 (all-target golden + JS gap IOJS001) + slice 2.1 TextStream (UTF-8 lines, all-target BMP golden; astral Native divergence §537) + test-architecture phase-2 quick-win slice 1 (hygiene baseline 186→185 keys, KofWebHardeningTest 6/6) + slice 2 (shared readiness fixture TestServerFixture, baseline 185→182, 4 web classes 49/49) + slice 3 (fixture extended to Media/OAuth/Hardening/Sse/Ws + maxConnections race de-flaked, baseline 182→179, 45/45) + slice 4 (TCP-only awaitPort + Native/WebJs/Blog loops migrated, baseline 179→176, 106/106) + slice 5 (bounded awaitTrue polls replace Hardening/Ws settles, baseline 176→174, 106/106) + slice 6 (custom-probe await + Tls/Log loops & settles migrated, baseline 174 — 2 sleeps off, 2 foreign dupname in; 124/124) + slice 7 (CLI readiness/teardown sleeps → CliAwaitFixture awaitTrue/awaitExit, baseline 174→171, 10/10) + slice 8 (KofTimeE2ETest scheduler sleeps → bounded polls, baseline 171→170, 44/44) + slice 2.2 CsvReader/CsvWriter (streaming CSV/TSV read+write, all-target golden; JS IOJS001) + slice 2.3 JsonLines (JSON Lines/NDJSON seam over kof.json, untyped by design; §538 catalogued, live 5->6; all-target golden; JS IOJS001) + test-architecture `--citations` (Phase 3 split cost per oversized class measured; cheapest-first ArrayBoundsStressTest/KofSetEqualityTest = 2; all 43 cited >= 2x; audit selftest 14/14) + test-architecture Phase 3 splits (KofSetEqualitySupport from KofSetEqualityTest 21/21; KofMathSupport from KofMathTest 29/29 — both zero citation drift; oversized 43->41, baseline 170->168) + development-queue doc-truth (three stale README cells: test-architecture/kof-file state + #665 gate implemented as #669) + #651 fatias A1/A2/B Buffer(U8) surface + FFI token B on x86-64 AND cross riscv64/aarch64 (`D-BUFFER-INOUT-NATIVE`, B 29/09) + phase-5 unit 2 (`#667` Script×extern `FFI001` at the decl line + `#668` `MEM020` compile face; `D-SCRIPT-EXTERN-REFUSE`/`D-MEM020-COMPILE`) + phase-5 unit 3 pinned (`Buffer(U8)` INOUT × `spawn`/`await` runtime parity measured) + slice 2.4 XmlReader (streaming XML pull reader, documented subset; all-target golden + JVM error cases; JS IOJS001) + slice 3.1 Ini (streaming INI config reader; sections/global/comments/quotes/last-wins, all-target golden + error cases; JS IOJS001) + slice 2.5 XML namespaces (scoped xmlns declarations, localName/namespaceUri/attributeNamespaces, unbound-prefix error; name verbatim; all-target golden + error cases; JS IOJS001) + slice 3.2 Toml (streaming TOML config reader; comments/dotted keys/tables/escapes/integer-float-bool/single-line arrays, typed accessors; all-target golden + error cases; JS IOJS001) + slice 3.3 Yaml (streaming YAML config reader; block mappings/scalar sequences/escapes/null, flattened dotted keys, typed accessors; all-target golden + error cases; JS IOJS001; Phase 3 config complete) + kof-file plan CONCLUDED 28/09 (promoted scope landed; moved to `docs/stdlib/kof-file-plan.md`; documents/archives deferred) + image-vision front promoted (`D-IMAGE-VISION-GO`) slice 1 (pure-Kof `libs/image/` metadata: format/dimensions PNG/GIF/BMP/JPEG/WEBP; `ImageMetadataE2ETest` 7/7 JVM + Native x86-64/riscv64 + Script; JS `IOJS001`; §540 catalogued; slice 1b TIFF/ICO + `isImage`)
doing: #688 + #686 FIXED 29/09 (type-system lane: call-arg generic check + statement-switch exhaustiveness) / §542 FIXED 29/09 (x86-64 host contiguous arena, adaptive to `RLIMIT_AS`; `CrossHeapParityE2ETest` 6/6, `KofGcE2ETest` 4/4; GC/native battery green) / memory-safety (phase-5 unit 2 LANDED, unit 3 pinned, #651 COMPLETE; 30/09 maintainer batch `D-MEM030-BORROW-RUNTIME`/`D-MEM-PHASE6-4BACKENDS`/`D-MEM-FFI-CROSS-FULL` — closing front: B-03 runtime borrow-state + FFI cross total; parity lane) / connectors (fatias 1–16 LANDED in pure-Kof `libs/interop/`: manifest+validation, `InteropType`/`InteropOwnership`/`InteropString`/`InteropCost`/`InteropLibrary`/`InteropCompatibility`/`ForeignModule`/`ConnectorSpi`/`ConnectorCatalogue`/`InteropCore`; `D-CONNECTORS-GO`, issues/tooling lane — queue 0g; Core+SPI+manifest surface complete, next gated on rule 6) / test-architecture (phases 3/4 CONCLUDED: 43→19 oversized, 146→132 keys, `--dups`) / multiparadigma (1a–1i LANDED; `Pair`/`D-NOT-JAVA` collision RESOLVED 29/09 option A — `zip` landed managed `80dd32b50`; native leg blocked by the generic `List.get` backend gap, §271 erasure ABI) / §539 FIXED 29/09 (PR #677 merged 398f73664, #676 closed) / image-vision (metadata + raster decode PNM/farbfeld/BMP/QOI/GIF/PNG/WebP-VP8L **complete lossless path** — simple+normal-Huffman+LZ77+color-cache+predictor/color/color-indexing transforms+meta-Huffman groups LANDED; slice 3i 30/09; **§34.5 raster cap 16384→262144 + VP8L guard unified + §34.4 encode/write + `kof.vision` (histogram/Otsu + equalization + Sobel edges + connected components + morphology) + WebP lossy DECIDED (VP8 pure-Kof slice 1 `Vp8Bool`) LANDED 30/09**; official package)
next: #685 FIXED 30/09 (enum `List.sort()`/`sorted(cmp)` accept enum in ordinal order — `8f9610f73` + the `check_500` split `d7d56faaa`; `EnumSortE2ETest` 3/3 all targets incl. cross qemu; issue #685 CLOSED) / §524 FIXED 30/09 (qemu-aarch64 harnesses share one bound+retry `QemuRun`; under-load confirmation remains the CI `Native cross` job) + §544 OPEN 30/09 (riscv64 VP8L decode of a >16384-px lossless WebP aborts array-index-out-of-bounds; JVM/x86 OK; owner native/GC, issue #700) + §541 FIXED 29/09 (x86 heap reused dirty memory → PNG x86 un-quarantined) + §543 FIXED 29/09 (x86 call-site scratch >32 locals) + §542 residual (x86 free-list first-fit O(F), out of scope) / image-vision next (WebP lossy `VP8 `/AVIF; `kof.image` surface DECIDED `D-IMAGE-SURFACE` — reuse `Raster`, JPEG interop landed; VP8L lossless complete) / maintainer decisions (§539 encoding rule; multiparadigma sorted/groupBy/zip DECIDED 29/09 — see doing) / connectors rule-6 stop (Phase 2 C-ABI needs authorization; §3.7 error model + §3.9 ABI policy need maintainer) / #651 COMPLETE (A1/A2/B landed 29/09) + phase-5 unit 4 = B-03 runtime borrow-state on `Buffer(U8)` (`D-MEM030-BORROW-RUNTIME`, 6-face proof) + FFI cross total `String[]`/structs/callbacks/out-buffer (`D-MEM-FFI-CROSS-FULL`) / future/ re-sweep on trigger (`D-FUTURE-BATCH-2809` authorized every plan; promotion one-at-a-time per `D-FUTURE-PROMOTION`)
location: status
state: active
constraint: maintainer-gated-promotion
decision: D-KOF-FIRST-IMPL

**Version:** 0.5.0-beta (pom `revision`) · **Last updated:** September 30, 2026.

**28/09 — pipeline + library front (branch active = `lab`, `D-BRANCH-PIPELINE`).**
- **memory-safety #651 fatia A1 landed (29/09, `D-BUFFER-INOUT-NATIVE`):** `Buffer(U8)` opens on Native x86-64 — `buffer.alloc(Int)`, `Buffer.bytes()` and direct typed `println(Buffer)` bind with JVM/JS parity (native object `[0..8]=header, cap@16, payload@24`; `kof_buffer_alloc` clamps negative→0 and zero-fills, `kof_buffer_bytes` copies `cap` bytes, `kof_buffer_to_string` prints `Buffer[cap]`; `NativeX86ValueOf` routes nominal prints through the new `RuntimeBuffer`). `KofBuffer.supportedOn` admits `Target.NATIVE` only; riscv64/aarch64 and Script/Android stay `FFI001` for this surface; FFI `B` parameter is A2. Proof `BufferE2ETest#allocBytesAndPrintlnNativeParity` (JVM==x86 byte-for-byte) + `StdParityGapAuditTest`; focused FFI/Buffer suite green; #651 stays OPEN until B (B landed below).
- **memory-safety #651 fatia A2 landed (29/09, `D-BUFFER-INOUT-NATIVE`):** the FFI `B` token binds `Buffer(U8)` as an INOUT `extern` parameter on Native x86-64 — the emitter passes the payload pointer (`obj+24`) directly and, since the Kof Buffer is contiguous memory, the C write is already the copy-back (one INTEGER register, like a `char*`); `FfiStructLayout.bufferPtrType()` + x86-64 branches in `CompilerFfiBinding`/`ExpressionMethodCallLowerer`/`NativeFfiCall`. Cross riscv64/aarch64 stays `FFI001` (fatia B). Proof `BufferFfiE2ETest#bufferInoutCopyInCopyBackNativeParity` (`20/[10, 10]/40/[20, 20]` JVM==Native) + `InteropIdiomsCompileTest#nativeShapeExamplesBindOnX86`; #651 stays OPEN until B (B landed below).
- **memory-safety #651 fatia B landed (29/09, `D-BUFFER-INOUT-NATIVE`):** the `Buffer(U8)` surface AND the FFI `B` token land on the cross riscv64/aarch64 — `NativeRiscvAsmBuffer` ports `alloc`/`bytes`/`to_string` in pure riscv asm (same layout: header 24, `cap@16`, `payload@24`; aarch64 inherits via the translator), `KofBuffer.supportedOn` opens both cross targets, `CompilerFfiBinding`/`FfiStructLayout.crossBindable` accept the buffer param, `NativeFfiCallRiscv` passes the payload pointer (`obj+24`, NULL→NULL) in register and spill paths, and the cross `println`/`valueOf` dispatch routes Buffer to `kof_buffer_to_string`. Proof `BufferE2ETest#allocBytesAndPrintlnCrossParity` (`Buffer[4]`/`[0, 0, 0, 0]`/`Buffer[0]`/`Buffer[0]`, JVM==riscv64==aarch64) + `BufferFfiE2ETest#bufferParamCrossBindsAndMatchesJvm` (libc `memset` → `[7, 7, 7, 7]`, JVM==riscv64==aarch64). #651 complete; Android/Script/riscv32/MCU stay `FFI001`.

- **Quality pipeline landed:** cutover `lab → testing → prerelease → stable → release/x.y.z → tag` (`D-QUALITY-PIPELINE-2609`); state machine (`scripts/pipeline/pipeline_state.py` 21/21), promotion gate (`promotion_gate.py`, 100%), `promote.yml`, rulesets (`pipeline-stages`, `release-tags`); `beta-*` frozen.
- **`http-policies` CONCLUDED** `future/` → [`docs/stdlib/http-policies-plan.md`](stdlib/http-policies-plan.md) (`D-HTTP-POLICIES`, `D-FUTURE-PROMOTION`): additive HTTP/Web policies (global + `app.policy` prefix + endpoint opts + `responses` + per-route rate-limit key), JVM-first, Native/JS `WEB006`; F0–F6 landed 28/09 (`KofHttpPoliciesE2ETest` 10/10).
- **`scoped-resources` CONCLUDED** [`docs/scoped-resources-plan.md`](scoped-resources-plan.md) (`D-SCOPED-RESOURCES-GO`, `D-FUTURE-PROMOTION`): slices 1–6, `UsingDesugarE2ETest` 18/18 (nesting + H2-`db` JVM/Script/JS + cross riscv/aarch + user docs); cross-`db` explicitly out (db lane's matrix).
- **`kof-file` PROMOTED + streaming slices 1–2** `future/` → [`docs/stdlib/kof-file-plan.md`](stdlib/kof-file-plan.md) (`D-KOF-FILE-GO`, `D-FUTURE-BATCH-2809`, `D-FUTURE-PROMOTION`): re-scoped on promotion (Phase 1 File/Path/Text/Binary already exists as `kof.io`); slice 1 = pure-Kof library `libs/file/` (`FileStream` chunked reader over `kof.io.readRange` + `copyStream` constant-memory copy) — no new syntax; slice 2 measures it on JVM + Native x86-64 + riscv64/aarch64 (qemu) + Script against one golden and makes **JS an honest compile-time gap `IOJS001`** (`ExpressionBuiltinInstanceCalls.lowerIo`, mirrors cross `NAT006`) — before, the JS runtime died with a `SyntaxError` (silent fallback). Proof `FileLibraryE2ETest` 7/7 + `DomainGapCodesTest` 29/29; parity-matrix row `IOJS001`.
- **memory-safety phase 4.2 (#659):** spawn-capture matrix pinned on the 4 targets including MUTATED capture (`44`) and child→parent visibility (`44/22`) — gaps caught by the first two independent-verifier passes; scalar-race scope escalated to the maintainer; verdicts recorded: `MEM023` no compile face, no generator surface (absence, not a gap)
- **memory-safety phase 4.1 (#658):** B-06 mutable-capture parity pinned on the 4 targets — `LambdaE2ETest` 36/36 with golden-identical Script (`KofInterpreter`) and JS (`KofJsRunner`) faces; zero behavior change (parity was already true — measured, not assumed).
- **`test-architecture` PROMOTED** `future/` → [`docs/development/test-architecture-plan.md`](development/test-architecture-plan.md) (`D-TEST-ARCHITECTURE-GO`, `D-FUTURE-BATCH-2809`, `D-FUTURE-PROMOTION`): pure test infrastructure (no compiler change); first slice = Phase 1 profiling (`scripts/test-suite-profile.sh` → `docs/testing/TEST-PERFORMANCE.md`).
- **`multiparadigma` PROMOTED** `future/` → [`docs/development/PLAN-MULTIPARADIGMA.md`](development/PLAN-MULTIPARADIGMA.md) (`D-MULTIPARADIGMA-PHASE1A`, `D-FUTURE-PROMOTION`): slices 1a+1b+1c+1d+1e LANDED — `any`/`all`/`none` + `find`/`count(pred)` + `forEach` + `flatMap` + `distinct` (E2E 5/5+3/3+3/3+3/3+3/3 + script parity). `future/` → [`docs/stdlib/pagination-plan.md`](stdlib/pagination-plan.md) (`D-PAGINATION`, `D-FUTURE-BATCH-2809B`, `D-FUTURE-PROMOTION`): first-class windowing — `Window<T>`, in-memory `slice`/`take`/`drop` (P1) → `Window<T>` (P2) → SQL `LIMIT/OFFSET` (P3/P4) → `pageRequest` HTTP (P5); surface locked; P0+P1 landed 28/09 — `List.take/drop/slice` (`kof_list_take/drop/slice`) on all four targets; proof `PaginationSliceE2ETest` 7/7 (cross riscv64/aarch64 run) + `KofScriptStdlibParityTest#paginationSliceParity`; **P2 landed 28/09 — `Window<T>` + `window(...)` as a Kof-written virtual package `kof.pagination` (library-first, explicit-import injection) + nested generic type-arg inference fix (unblocks Native); proof `PaginationWindowE2ETest` 7/7 + neighbors 150/150**.
- **`pagination` PROMOTED** `future/` → [`docs/stdlib/pagination-plan.md`](stdlib/pagination-plan.md) (`D-PAGINATION`, `D-FUTURE-BATCH-2809B`, `D-FUTURE-PROMOTION`): first-class windowing — `Window<T>`, in-memory `slice`/`take`/`drop` (P1) → `Window<T>` (P2) → SQL `LIMIT/OFFSET` (P3/P4) → `pageRequest` HTTP (P5); surface locked; P0+P1 landed 28/09 — `List.take/drop/slice` (`kof_list_take/drop/slice`) on all four targets; proof `PaginationSliceE2ETest` 7/7 (cross riscv64/aarch64 run) + `KofScriptStdlibParityTest#paginationSliceParity`; **P2 landed 28/09 — `Window<T>` + `window(...)` as a Kof-written virtual package `kof.pagination` (library-first, explicit-import injection) + nested generic type-arg inference fix (unblocks Native); proof `PaginationWindowE2ETest` 7/7 + neighbors 150/150**.
- **`D-FUTURE-BATCH-2809B`:** the open design questions of the remaining `future/` plans are resolved (pagination, value-records, entity-history, multiparadigma, graphics, testing-platform, connectors, kof-file, image-vision, bootstrap, wasm D-WASM-01..09).
- **Parity row 11 CLOSED** (`compareToIgnoreCase` on all backends) → `full_parity` GREEN.

**24/09 — window 21/09→24/09 (measured vs `git log`/tips; full reactor 3818 / 0F / 0E). PR `#619` (`beta-0.5.0 → main`) is OPEN and the MAINTAINER'S to merge — AGENTS rule 10: no agent merges/approves/closes it.**
- **Record equality on Native COMPLETE** (§104b/§114 closed): `List/Set.contains` + `set.add` dedup compare records by CONTENT (tag 2 → `kof_obj_equals` + `kof_equals_table`, `4cce594e7`); `Map` keys by content (`f31ac11f4`); `containsValue(record)` value-side tag 7 from **Publio Santos' PR #616** (RED 3/3 → GREEN 3/3 on x86-64+riscv64+aarch64, `b7c13ba1f`, #615 closed via the official evidence path); synthesized `equals` handles nested record fields (`523dfabb5`); `hashCode` String/Double/nested-record all by content (`67acf5a77`/`e2f0629f6`/`2e90aa7c2`). Oracles `NativeRecordCollectionEqualityE2ETest` + `NativeRecordHashCodeE2ETest` byte-identical JVM≡Native on 3 arches.
- **Windows test-harness parity closed** (Jonas Rocha's issues, issues lane): #612 child-JVM `-cp` uses `java.io.File.pathSeparator` (18 files) + `ClasspathSeparatorGuardTest` naming `file:line` (`cdbf45145`); #603 18 `KofOrmE2ETest` Windows reds fixed — `kofPath()` forward slashes (raw `\` collapsed as escapes per the lexical rule; the language was right, the harness wasn't), `/tmp` → `tempDir`, child stderr IN the failure message, Postgres by real credentials (`970c847a`); #617 `File.mkdir()` no longer a silent no-op; unknown kof.io builtin member → clean **SEM102** with `Directory.createDirectories()` hint (`aed5fe7b4`); #618 NOT-a-bug (`kof run` file arg = its dir is the module, by design; documented `learn/32-cli-tooling`). **Zero open issues.**
- **Native hardening**: §485 channel `receive` drained the queue without resetting `tail` → deterministic NULL SIGSEGV on x86_64+riscv64+aarch64 (`7f5a2e054`); §486 covariant-return bridges both faces; **B-3b baremetal: the real Kof payload runs through the BIOS path under SeaBIOS** (`KO-BIOS PAYLOAD`, `e3644d596`, 5 bugs found+fixed).
- **Tech-debt lifecycle opened+closed in 48h**: `tech-debt.md` opened 23/09 with 6 live §NNN — all measured ✅ by 24/09 (§205 boxed-print, §248 default methods on 4 targets, §271 generic-interface bridges, §278 Android `kof.security` JCA + `kof.gpu` stub, §283 aarch64 scheduler exit, §423 channels cross) + `check_500` green → maintainer KILLED the ledger, the `debt-scout` tooling (38 files) and `technical-debt/` (`f4a987166`); DECISIONS annotated KILLED.
- **Docs three-states**: `PROPOSAL-1.0-EXIT-GATE` → `docs/` (normative 1.0 contract); `PROPOSAL-VERSIONING-RELEASE` → `docs/distribution/` (PR #582, 22/09); X5/X6 variance IMPLEMENTED (release-gate cond.2 GREEN); `check_release_050_gate` allowlist tightened → `loose_docs GREEN`.
- **Contributors (closed with proof, credited)**: #608/#614/#609 (Publio's record+generic-interface JVM face landed as `081202e0c` after rebase, authorship preserved), #610/#611 (conflicting-default diamond → SEM101 + arity overloading, `374b2b4bb`), #604/#605 (CodeQL gate counts only CodeQL), #613 (native face of the bridge family, independent-verifier HIGH path).
- **Release prep**: `release-beta-0.5.0-prep` tracks the cut; tip CI bots (quality/security/warning) green; remaining gate per `scripts/check_release_050_gate.sh` (parity/bugs_gaps = environment/measured-lane; decisions/edges GREEN).

**20/09 — night 19/09→20/09 (tip `1080238f`; CI `Build+Tests` green; reactor ~3034 tests 0F/0E).**
- **D-TROOL**: `Bool` stays 2-valued; `Troolean` (`true/false/null`) is the Kleene three-valued type (maintainer 19/09, roadmap 2.6.5–2.6.7 ✅; `916b9fb7`/`d61836eb`/`5f0757e8`): user `Bool?` → SEM095; `&&`/`||`/`!` strict-Kleene on the boxed machine; `if (t)` ≡ `if (t == true)`; `TrooleanLawE2ETest` 13/13; corpus synced.
- **JVM erasure river** (§355/§356/§357, `16f16081`/`c8d55a10`/`0aa6a307`) — 8 issues closed (#399/#363/#375/#385/#366/#365/#295/#368): type variables carry their bound to every emit site; generic interfaces lower with erased descriptors and covariant bridges scan parents AND interfaces; field `T[]`/`as T[]` erase honestly; JVM-only `SEM098`. §358 FIXED 21/09 with the honest `NAT004` refusal (pre-existing native gap at the clean tip, `NativeGenericDispatchGapE2ETest` 2/2).
- **20/09**: §362 gate landed (`57a0d5f0` — phantom ctor calls now SEM023/SEM014 at the call-site, 7/7); fatia-2 sweep found §371/#550 (shipped-CLI loader CWD never prunes → forces `usesDb`+dynamic link, breaks `kof build --target native.risc|arm` with COMP001); §370/#549 closed (`35bfaec1`, extern numeric coercion byte-identical JVM≡Native, SEM014); §373/#443 landed (`d969bc3a`, bare `List/Set/Map` in a declared position → builtin at `qualifyDeep` 2b, `BareCollectionFieldE2ETest` 8/8); X7-5 raw JDWP rebuilt vs the JDK 25.0.4.1 wire (§376, `81401629`; false-green fixed §377). Process-bug harvest: §372/#551 (§368 gate regression over the erasure river), #552 (`BuiltinCallTyper` 612≥600), §374/#553, §378/#554. §360 FIXED (`4ed9bb2f`, process.spawn handle ops were dead on JVM); JS face `081a48f8`+`2d20e5d4` (`ProcessSpawnE2ETest` 4/4, `ShellE2ETest` 15/15). X7 cross DWARF `.file/.loc` (`NativeDwarfCrossTest`, `5d9c855c`/`23b7ecf7`) + DIEs `890b58bf`. X10 CLOSED (`27826838`, LSP 32/32 namespaces, 265 members/282 forms). New bugs catalogued: §361 nullable-primitive field write (`e293c4a5`, PARTIAL — `Char?` write still dies, §365 opened), §362/#545, §366/#547, §367/#548. Ledger discipline: §NNN from the REMOTE tip; `check_known_bugs_status.sh` 🔓 token. DECISIONS grew (`7dfc6441`): `D-MAKEALIVE`, `D-KOF-AS-CLOUD`, `D-BOOTSTRAP`, `D-DB-GAPS`, Simplicity Law = rule 11.
- **18/09 — R3 FFI (JVM) generalized**: `extern` binds the full scalar ABI, any arity over {Int,Long,Float,Double,Boolean,String}, one `kof_ffi(lib,name,sig,Object[])` downcall (`FfiE2ETest` 8→9, `FfiSignatureTest` 4/4); JS parity via `KofJsFfiBridge` (7 `assertJvmJsParity` cases); callbacks on JVM and the JS host (`JvmFfiCallbackE2ETest` 42/42/6.0/7.5). Non-scalars → `FFI002`; Native `FFI001` (§61, honest per-target gap).
- **18/09 — §132 CLOSED (#83-JS)**: OTP supervisor runs on KofJS (`time.sleep` is an await-point via `computeAsyncColoring`; `OTP002` lifted; `AsyncSleepJsE2ETest` 3/3); riscv/aarch stay `OTP001`.
- **19/09 — §129 CLOSED on riscv64/aarch64; `OTP001` REMOVED** (per-TID chain table `kof_exc_slots`, `gettid`=a7 178; "real TLS via clone" proven ABI-unsafe and abandoned; `KofConcurrency2Test` 48/0, `KofSupervisorE2ETest` 16/0, cross E2E 45/0). `planning-otp-supervision.md` promoted to `docs/`.
- **15/09 — §129 CLOSED Native x86** (`kof_exc_chain` TLS per-thread + `kof_spawn_trampoline` per-worker handler; `KofSupervisorE2ETest` 15/15, `KofConcurrency2Test` 40/0, `ExceptionsE2ETest` 11/0, `NativeE2ETest` 65/0; the 1 red was §205, closed same day fatia 1 `97d08e54`).
- **14/09 — release-stabilization baseline** (`192.168.100.17`): clean 4-module `1819 tests, 3F, 0E, 7 skip` — all 3 cross-arch other lanes: §181 residual (`riscv64/aarch64CastSaturation`, only `(-inf) as Int`) and §192 (`KofMathTest.parseOrDefaultCrossArch` hang, chain-slot alias). §176 closed (`a5eedbe2`). Decompiler deprioritized.
- **12/09 — §107 scalar face CLOSED on 3 native targets** (`kof_{list,set,map}_to_string` x86 `f3b3821c` + cross `411e9ce5`); §138 unblocked the cross build. Issue #97 tree-shaking S-1/T0 DONE (`a3996600`, `ArtifactSize` + `ArtifactSizeTest` 5% anti-bloat gate + `kof build --print-sizes`). Doc-vs-reality re-audits `42c716ed`.
- **11/09 — OTP core (#83) JVM+Script** (`kof.supervisor`, `KofSupervisorE2ETest` 6/6; §130 fixed); §133 async fetch KofJS (`KofHttpE2ETest` 8/8); MATH001, TIME002, SG-011B (top-level overload; SEM047/SEM057; `TopLevelOverloadE2ETest` 5/5; §136) closed. **§131 class METHOD overloading CLOSED 13/09** (`18a64d45`, 4 backends; `CoreRegressionE2ETest.methodOverloadByArity`).

---

## Build

```
mvn clean package    → PASS
mvn test             → 3225 tests (2762 kof-compiler + 50 kof-script + 7 kof-c-compiler + 406 kof-cli), 0 failures / 0 errors, 221 skip — CI Build+Tests of tip `404d8be6` on 20/09 ~18:14 (first green on `beta-0.5.0`); cross runs in the dedicated qemu job, external DB/toolchain guards + §255 sysroot guard; `node` present
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

- **`Set<T>` declared type on the JVM**: descriptor `kof.Set` → `java/util/HashSet`; class member parser with generic return (`Set<Int> foo()`).
- **Null-safety narrowing on the JVM fixed**: `if (s != null) { s.length }` no longer emits `getfield "?".length`; `if (x != null)` no longer uses `if_icmp*` on a reference; prefixed `String? s = null` parses.
- **honest stdlib**: `File.readText`/`readFile` → `String?`; `File.size()` throws instead of the `-1` sentinel; `Map.get` → `V?`; `readLine()` → `String?`.
- See `CHANGELOG.md` [0.2.6-beta] 02/09 and `docs/backend-parity.md`.

---

## Performance & Benchmarks (docs/architecture/performance.md)

- **IR optimizer** (`Optimizer.java`, always active): constant folding, branch simplification, dead stack effects, unreachable-code elimination (CFG, try/catch preserved), jump-to-next elimination, arithmetic identities; debug positions preserved.
- **debug/release**: `kof build|run --release` strips debug metadata (SourceFile/LineNumberTable on JVM, source map on JS).
- **`kof bench`**: `[paths...] [--target jvm|native|js] [--iterations N] [--quick] [--baseline <file>] [--update-baseline <file>] [--threshold <ratio>] [--json] [--fail-on-regression]` — compile → run → validate stdout vs `expected.txt` → median time + RSS → baseline compare → flag `PERFORMANCE REGRESSION`.
- **`benchmarks/`**: 37 benchmarks in 17 categories; baselines `benchmarks/baselines/<target>-<version>.json` (33 jvm, 29 native, 32 js).
- **CI**: `.github/workflows/benchmark.yml` (`--fail-on-regression --threshold 1.20`); `scripts/run-benchmarks.sh`.
- New-feature rule: performance.md §40-§41 (DoD includes benchmark, stress, memory, resource, debug metadata).

### Backend fixes discovered by the benchmarks and E2E

| Bug | Fix |
|-----|----------|
| Interface call with primitive return generated `Object` descriptor (`iadd` invalid bytecode) | `analyzeInterface` defines symbols in `members()` |
| `l.get/remove/size/contains` as statement emitted no `KofPop` (Frame.merge crash) | `hasReturnValue` covers List methods that leave a value |
| `if (long>long)` / `float` / `double` generated `IF_ICMP` (underflow) | `KofConditionalJump.operandType` → `LCMP`/`FCMPL`/`DCMPL`; `emitComparisonShortcut` widens |
| JS: call with discarded effect in a Pop statement silently did not execute | `KofPop` handler preserves `JsCall`/`JsSequence` |
| `Box<Int>`/`Box<T>` `b.get()` printed `T` on Native → segfault | `ExpressionTyper.inferExprType` (`ExpressionTyper.java:14`) substitutes `T` via `CompilerTypes.substituteTypeVariableIn` (`CompilerTypes.java:423`) |
| `record Ponto` `hashCode()` false-positive `SEM025` | `MemberResolver.java:65` ignores `isObjectMethod(hashCode/equals/toString)` |
| **Regression `dc849f6`**: `kof_list_add` without `POP` → 15 frame crashes | POP restored + `hasReturnValue` shields collection `add/push/append/set/clear/put` (`c7b23a1`…`7c6aca9`) |
| Surefire never ran `NativeDebugTest2/3/4/5` | `<includes>*Test*.java</includes>` in kof-compiler surefire |
| `spawn { lambda w/ capture }` → `VerifyError`/wrong value | `SpawnStmt` collects via `collectCaptures` (`SpawnE2ETest.spawnLambdaCapturesOuterLocal`) |
| `&&`/`||` on JS emitted bitwise (both sides evaluated) | boolean `&&`/`||` → JS `&&`/`||` (`KofJsE2ETest.logicalAndOrShortCircuit`) |
| `Channel<T>` as function parameter → invalid codegen | `Type.of`/`toType` treat `Channel` as builtin; `JvmTypeMapper` → `LinkedBlockingQueue` (`KofConcurrency2Test.channelAsFunctionParameter{Jvm,Native,Js}`) |
| `println` before `spawn` → SIGSEGV (`pthread_create` misaligned) | `andq $-16, %rsp` before the C call in `kof_spawn_handle_new` (`SpawnE2ETest.nativePrintBeforeSpawnDoesNotSegfault`) |
| AES-GCM on JS ignored ciphertext tamper (`SECN002`) | `kofSecB64Decode(s, strict)`; `decryptAesGcm` passes `strict=true` (`KofSecurityTest.aesGcmJsRoundTrip`) |
| `OBS002: histogram/metrics on Native` (asm: no `jmp`, inverted cmp, `%rsi` clobber, misalign) | 32B store + `kof_string_concat`; `%r10` scratch (`KofObservabilityTest.observabilityNative`) |
| `transaction {}` on Native link error + rollback not undoing | `kf_db_transaction` asm: `%rdi`=this, reload handle from BSS, re-throw; `.asciz` (`KofDbE2ETest.nativeTransaction{Commits,RollsBackOnFailure}`) |
| `MQ001: kof.mq on Native` | asm pub/sub + in-process queues; identity compare on `unsubscribe` (`KofMqE2ETest` 4/4) |
| `Set<T>`/`Map<K,V>` as class field/return → `NoClassDefFoundError: kof/Set` | `JvmTypeMapper` maps `Set`→`HashSet`, `Map`→`HashMap`; `ClassMemberParser` generic-return branch (`KofMapSetTest.setMapAsFieldAndReturn`) |

---

## Security (kof.security, docs/stdlib/security.md)

- **`kof.security` v1**: `passwords`, `crypto`, `jwt`, `secrets`, `security`, `auth` — secure by default, target gaps with a clear compile-time diagnostic (SECN001/002/003).
- **JVM**: PBKDF2-HMAC-SHA256 (600k), SHA-256/512, HMAC, AES-GCM, SecureRandom, JWT HS256, env secrets, constant-time, redaction, web `auth.*`.
- **Native**: SHA-256/512 + HMAC in pure assembly (FIPS 180-4/RFC 2104, identical to JVM), PBKDF2, AES-GCM, JWT HS256, `getrandom`, `/proc/self/environ`, constant-time.
- **JS**: SHA-256/512 + HMAC in pure JS, PBKDF2 delegated to the platform runner, JWT, AES-GCM (SECN002), constant-time.
- **Tests**: `KofSecurityTest` 27 (3 targets + adversarial: tamper, expiration, algorithm confusion, malformed, wrong key, issuer/audience). **Benchmarks**: `benchmarks/security/`. **Docs**: `docs/stdlib/security.md`, `learn/36-security.md`, `training/language/security.md`.

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

- **JVM**: idiomatic JDBC (`db.connect/execute/query`, `query<T>` typed, optional credentials, `transaction {}` real commit/rollback).
- **Native**: SQLite via direct `.so` link (no JDBC) — real E2E; `transaction {}` real (01/09, `kof_db_transaction` asm, EH `kof_exc_chain`/`kof_throw_string`); MySQL/MariaDB wire protocol (handshake + SHA-1 scramble + auth-switch + COM_QUERY + resultset + `?` binds, 31/08) + **binary prepared statements COM_STMT_PREPARE/EXECUTE** (03/09) + **riscv64/aarch64 SQLite closed 15/09** (link-by-use `libsqlite3`, `RtB46/RtB47`; residual: concurrent transactions share the global `.Ldb_tx_handle` slot).
- **JS** (16/09, DB001 closed): `connect/connect2/close/execute/query/transaction` delegate to `kof_platform.db*` on the GraalJS host (`KofJsDbBridge`, same JVM/classpath); **`db.query<T>` typed = DB002 closed 18/09** (`__kof_decode_<T>` bind).
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
    var pg = orm.page<User>(db, 20, 40)                   // pagination
    println(orm.count<User>(db))
    orm.delete<User>(db, 1)
    orm.migrate(db, "add-phone", "ALTER TABLE user ADD phone VARCHAR")
}
```

- Schema declared in the language (`entity`) — the compiler knows fields/types/constraints at compile time (never reflection); `generated`, `unique`, non-numeric PK.
- SQL backends: H2/SQLite/MySQL/MariaDB/PostgreSQL via JDBC (JVM). Full CRUD + `saveAll`, `where` with operators, `count` filter, `page`, `deleteAll`.
- **Column typing (P3-10)**: literal non-entity column → `ORM003` at compile time (JVM); dynamic column allowed.
- **MongoDB**: `save/find/all/where/delete/count` over the official driver (conditional-skip E2E, Mongo service in CI). Versioned migrations (`kof_migrations`).
- Native CLOSED 24/09 (cross riscv/aarch over SQLite + MySQL wire, D-DB-GAPS S5.5 `RtB76`–`RtB81`; x86-64 since 22/09); JS CLOSED 18/09 (`KofJsOrmBridge`, byte-parity E2E).
- Tests: `KofDbE2ETest`, `KofOrmE2ETest` (31, conditional skips). Docs: `docs/stdlib/DATABASE_VISION.md` (levels 0-4).

---

## Distribution infrastructure

- `VERSION` single source; `<revision>` in Maven; `KofVersion` + `version.properties`; `scripts/bump-version.sh`.
- CLI (26 commands): `build, run, serve, check, test, script, repl, c, fmt, config gen, bench, profile, inspect, decompile, translate, compare, migrate, debug, info, lsp, install, deps, editor, new, init, version`.
- `kof lsp` via stdio (real-frontend diagnostics, hover, completion, references + rename; `LspServerTest` 4/4).
- `bin/kof` / `bin/kof.bat` with embedded JDK (Temurin 25; Kof emits V21 bytecode, floor JVM 21+). `scripts/package.sh` (dist layout, `--jdk`, SHA256SUMS).
- CI: `ci.yml` (PR — tests/golden/integration/multiplatform), `release.yml` (main → bump → package 3 platforms → changelog → GitHub Release).
- Editor: `editor/kof.tmLanguage.json` (TextMate).

---

## Targets

| Target | Backend | Execution | Status |
|--------|---------|----------|--------|
| `jvm` | `JvmBackend` (ASM) | V21 bytecode, exception table, virtual threads | stable |
| `native` | `NativeBackend` (x86_64) | ELF x86_64, syscalls, free-list alloc + mark-sweep GC (auto-collect ✅ 19/09, §260 CLOSED) | stable |
| `native.risc` | `NativeBackend` (riscv64) | ELF riscv64 via `riscv64-linux-gnu-as/ld` + qemu (26/26) | stable (core) |
| `native.arm` | `NativeBackend` (aarch64) | ELF aarch64 via `aarch64-linux-gnu-as/ld` + qemu (26/26 via translation) | stable (core) |
| `js` | `JsBackend` + `KofJsRunner` | ES Modules via GraalJS, `kof.http` via `Java HttpClient` interop | alpha |
| `kofc` | `KofCcompiler` | C subset (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`) → native x86_64 | native-only |

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
| println/print; variables, arithmetic, bitwise, hex | ✅ | ✅ | ✅ |
| if/else, if-expr; while/for/do-while/for-in, break/continue; switch | ✅ | ✅ | ✅ |
| functions (all forms); classes/fields/methods; `constructor(...)` + primary class | ✅ | ✅ | ✅ |
| records (toString/equals/hashCode) | ✅ | ✅ | ✅ |
| inheritance/`super`/override/virtual dispatch | ✅ | ✅* | ✅ | Native: `super.metodo()` = SUP001 |
| interfaces; generics by erasure; lambdas + captures | ✅ | ✅ | ✅ |
| real exceptions (try/catch/finally + unwinding); `assert` | ✅ | ✅ | ✅ |
| `spawn` (implicit join) | ✅ | ✅ (pthread) | ✅ |
| strings (concat `+`, `==`, indexOf, trim, split...); arrays | ✅ | ✅ | ✅ |
| `List<T>`, `listOf`, `map/filter/reduce` | ✅ | ✅ | ✅ |
| `Box<T>` primitive/boxed `T` | ✅ | ✅ | ✅ |
| JSON encode/decode + native arrays; `List<User>` nested | ✅ | ✅/— | ✅ |
| kof.io (File/Path/Directory, readFile, writeFile) | ✅ | ✅ | ✅ |
| kof.time (now/sleep/interval) | ✅ | ✅ | ✅ |
| kof.web (`web.app()`, routes, middleware, WebSocket/SSE, configure/stats) | ✅ | — | — |
| kof.http (+timeout/retry/circuit) | ✅ | ✅ HTTP002 | ✅ |
| kof.config; kof.mq; kof.log; kof.security | ✅ | ✅ | ✅ |
| kof.db (JDBC, query<T>, transaction) + SQLite | ✅ | ✅ (cross ✅ 15/09) | ✅ (DB001/DB002) |
| kof.orm (entity, CRUD, where, migrate, MongoDB) | ✅ | ORM001 | ✅ 18/09 |
| String.toInt/toLong/toDouble/toFloat; kof.ui; default params; `readLine()` | ✅ | ✅ | ✅ |
| `KofCcompiler` C subset → native; `KofScript` top-level `var`/`val` | ✅ | ✅ | ✅ |

### Concurrency (`spawn`)

- JVM: virtual threads (implicit join). Native: pthread_create + trampoline + `await`/pthread_join + futex allocator + `done`/`poll`/`cancel`/`cancelled`/`selectAny` (CONC001 closed 31/08). JS: real `async`/`await`/`Promise` (CONC003 closed 03/09, `KofJsRunner` drains `kofActiveTasks`).
- Zero platform API exposed. **Memory model (SG-020)**: `docs/language-reference/concurrency-memory-model.md` — SC on all targets, 6 HB edges; proofs `staticsAreSequentiallyConsistent`/`noWordTearingOnLong` in `KofConcurrency2Test`.
- See `docs/language-reference/concurrency.md`.

### HTTP (`kof serve`)

Legacy top-level `handle(method,path,body,query,headers): String` (null → 404) still works. Native web stack (Phase 1, no Spring):

```kof
main() {
    var app = web.app()
    app.use { if (header("x-auth") == "secret") { return null } return "{\"error\":\"unauthorized\"}" }
    app.get("/hello") { return "Hello from Kof" }
    app.get("/users/:id") { return "user " + param("id") + " q=" + query("name") }
    app.post("/user") { return json.encode(json.decode<User>(body())) }
    app.listen(8080)
}
```

- Routes with trailing lambda; path params, query, headers, body, `method()`/`path()`; middleware `app.use`; HTTP engine generated in the program runtime (each connection a virtual thread); `configure`/`stats` (JVM). `kof serve <file.kf>` detects `main()`.
- See `docs/stdlib/stdlib-web.md`, `KofWebE2ETest`.

### Media (`kof.media`) — files, not strings

The language does NOT carry image/audio as a giant `String` (no base64 literal/data-URI). The app handles the FILE:

```kof
main() {
    var app = web.app()
    app.serveDir("/img", "assets")                 // bytes from disk, image/png
    app.get("/thumb") { var img = Image.open("assets/logo.png"); img.saveAs("assets/thumb.jpg", "jpeg"); return "w=" + img.width() }
    app.get("/rec") { var m = Mic.record(2); m.saveWav("assets/gravacao.wav"); return "ms=" + m.durationMs() }
    app.get("/clip") { var v = Video.open("assets/clip.mp4"); return "ms=" + v.durationMs() + " " + v.format() }
    app.listen(8080)
}
```

- **`Image`**: `open` (PNG/JPEG/GIF/BMP), `width/height/format`, `save`/`saveAs`, `bytes`/`bytesAs`, `dataUri` (runtime), `close`. **`Audio`**: `openWav`/`saveWav` (WAV RIFF PCM 16-bit), `sampleRate`, `durationMs`, `pcmBytes`. **`Mic`**: `record(seconds)`, `list()`. **`Video`**: `open` + container metadata (MP4/MOV `mvhd`), no frame decode (honest gap).
- **`app.serveDir(prefix, dir)`**: binary FILE serving, content-type by extension, `Cache-Control`, path-traversal protection + **Range requests** (206/416) for `<video>`. Relative paths resolve against `-Dkof.root`.
- **Targets**: JVM. Honest gaps: video frames, camera (MEDIA002), mic (MEDIA003), Native/JS parity (MEDIA001).
- See `KofMediaE2ETest` (16).

### Native configuration (`kof.config`)

Precedence `KOF_CONFIG` > env `KOF_<KEY>` > profile `kof.<KOF_PROFILE>.config` > `kof.config`; compile-time typing, default on missing/invalid. Native: complete own asm implementation (`NativeConfigE2ETest` 8, CONF001 closed). Proof: `KofConfigE2ETest` 11/11.

### Native logging (`kof.log`)

Format `timestamp LEVEL message`; info/debug → stdout, warn/error → stderr; `KOF_LOG_LEVEL` (debug<info<warn<error<off). Works inside web handlers. Native: own asm (UTC; `KOF_LOG_JSON` no-op); JS `console.*` (LOG001). Docs `docs/stdlib/stdlib-logging.md` (`KofLogE2ETest`, `NativeLogE2ETest` 7).

### Language tests (G6 — structured suite)

`test "name" { }` desugars to `kof_test_N` (runner synthesized, zero reflection); `kof test <file.kf|dir> [--target ...]` reports `PASS`/`FAIL` + exit code; each test isolated. `process.exit(code)` primitive on 3 targets. G7: `jwt.*` Native `SECN004` at compile time. See `learn/23-testing.md`, `StructuredTestE2ETest`.

---

## Tests (3225 = 2762 kof-compiler + 50 kof-script + 7 kof-c-compiler + 406 kof-cli — full suite green, 0F/0E, 221 skip; measured 20/09 ~18:14 by CI Build+Tests of tip `404d8be6`; §252 native flake silent again; host without qemu: cross → honest skip)

| Suite | Count | Coverage |
|-------|-----------|-----------|
| CompilerDriverTest | 252 | compilation, semantics, phases, isolation |
| NativeE2ETest | 65 | real native binary execution |
| KofJsE2ETest | 40 | real JS (GraalJS) + `&&`/`||` short-circuit vs bitwise |
| JvmE2ETest | 31 | real JVM bytecode execution |
| KofSecurityTest | 28 | kof.security + adversarial |
| OptimizerTest | 22 | IR optimization passes |
| KofOrmE2ETest | 32 | kof.orm: entity/CRUD/where (+ORM003), Query DSL, migrate, unique, MongoDB |
| KofConcurrency2Test | 33 | spawn, selectAny, cancel, done/poll, awaitTimeout, channel |
| IoE2ETest | 16 | kof.io multiplatform |
| ComponentCoreE2ETest | 14 | kof.ui Component: view/onMount/onDispose |
| CoreRegressionE2ETest | 50 | real-usage regressions (BOM, toInt, ARITH001...) |
| JsonE2ETest | 15 | JSON JVM + Native |
| UiE2ETest | 29 | kof.ui widgets, styling, bindings, Table/Ul/Ol/Form (JVM+Native link) |
| AndroidInteropE2ETest | 12 | android: Java interop (external classpath) |
| KofConfigE2ETest | 11 | kof.config env/file/profiles/precedence, CONF001 |
| KofWebWsE2ETest | 11 | WebSocket RFC 6455 handshake/frame/lifecycle |
| StructuredTestE2ETest | 11 | `test "name"` on 3 targets + process.exit |
| BackendParityTest | 16 | JVM/Native/JS parity |
| KofLogE2ETest | 11 | kof.log JVM: levels, stderr, off, JSON, correlation |
| KofPatternMatchingTest | 12 | switch `case String s` / `Point(x,y)` 3 targets |
| KofWebE2ETest | 12 | native web stack (routes, JSON, middleware, `app.health` bypass) |
| ExceptionsE2ETest | 9 | try/catch/finally JVM + Native |
| KofDbE2ETest | 24 | kof.db JDBC/query<T>/transaction/rollback/native SQLite, DB001/DB002 JS, cross SQLite riscv+aarch |
| KofHttpServerTest | 8 | serve engine (real sockets) |
| KofMediaE2ETest | 16 | kof.media + serveDir, Range 206/416, binary content |
| NativeConfigE2ETest | 8 | kof.config Native asm |
| SpawnE2ETest | 10 | spawn + implicit join + captures + println-before-spawn + spawn→await→spawn |
| IdiomaticE2ETest | 7 | consolidated idioms (chaining, primary ctor) |
| JsonCompleteE2ETest | 7 | JSON Float/Double + array decode (JVM) |
| KofAwaitTest | 8 | typed spawn/await Handle<T> (JVM) |
| KofWebSseE2ETest | 7 | SSE: sse.send/event/close |
| KofWsFrameTest | 7 | frame codec RFC 6455 |
| NativeLogE2ETest | 7 | kof.log Native asm |
| IdiomaticCoreE2ETest | 6 | field initializers, `\u810810`, listOf<T>() |
| PackagesE2ETest | 12 | multi-file packages/modules (import a.b.C, LCA moduleRoot) |
| AssertE2ETest | 5 | assert JVM + Native |
| FloatingPointGapE2ETest | 5 | FP XMM encode/decode/arrays (FLT001) |
| KofCacheE2ETest | 5 | cache E2E |
| KofHigherOrderTest | 5 | map/filter/reduce |
| KofIntOverflowNativeTest | 5 | 32-bit Int on Native |
| KofTimeE2ETest | 12 | time now/sleep/interval (JVM/Native/JS — TIME001) |
| KofWebTlsTest | 5 | TLS/HTTPS listenSecure |
| KofObservabilityTest | 7 | health/metrics/histogram/requestId/traceId (W3C) |
| FunctionSyntaxTest | 12 | function declaration forms |
| KofEnumSwitchTest / KofEnumTest | 4 / 4 | enum exhaustive switch SEM031 / values/valueOf SEM030 |
| KofHttpE2ETest | 8 | kof.http client (real sockets) |
| KofMqE2ETest | 5 | kof.mq (MQ001) |
| KofWebStreamE2ETest | 4 | WebSocket/SSE end-to-end |
| LambdaE2ETest | 17 | lambdas + if-expr |
| RouterE2ETest | 4 | kof.ui Router Phase 7 |
| StdlibE2ETest | 4 | now/readFile/writeFile |
| KofJsBrowserE2ETest | 22 | KofJS in headless Chrome + DOM (skips if Chrome missing) |
| KofJsSourceMapTest | 1 | KofJS source map V3 (real VLQ) |
| ConfigGenTest | 3 | kof config gen |
| KofHttpResilienceE2ETest | 3 | kof.http timeout/retry/circuit |
| KofMapSetTest | 11 | Map/Set 3 targets + class field/return (02/09) |
| KofSecurityG9Test | 3 | web security rateLimit/session/apiKey |
| KofValidationTest | 34 | 13 validation predicates (3 targets) |
| TetrisEasterEggTest | 3 | easter egg registration |
| TuringCompleteE2ETest | 3 | Turing completeness |
| WindowE2ETest | 3 | Window size, close-to-exit |
| DebugInfoE2ETest | 2 | SourceFile + LineNumberTable (JVM) |
| IRStatisticsTest | 2 | IR observer + optimization stats |
| NativeDebugTest | 1 | native debug harnesses |
| NativeDebugTest2/3/4/5 | 1 each | native debug harnesses |
| NativeDwarfLineInfoTest | 1 | native DWARF `.debug_line` |
| NullSafetyE2ETest | 7 | `String?` narrowing JVM + readLine EOF null |
| NativeRiscv64E2ETest | 42 | real riscv64 (qemu), pure asm runtime, core + stdlib |
| NativeAarch64E2ETest | 42 | real aarch64 (qemu), pure asm via riscv→aarch64 translation |
| **kof-compiler Total** | **823** | |
| kof-script | 8 | KofScriptGlobals / repl / --watch |
| kof-c-compiler | 5 | KofC C subset → ELF |
| kof-cli | 4 | LSP references + rename (mock) |
| **Total** | **840** (+31 conditional skips: Mongo/MySQL/Postgres, windows/mac) | |

---

## Idiomatic consolidation (guidelines 0.0.5)

Principle: `intention → Kof → compiler → backend` — never platform details leaking into the language.

| Guideline | State |
|-----------|--------|
| `User(...)` without `new`; primary constructor; `this` not required; field initializers in ctor | ✅ |
| Method resolution independent of textual order | ✅ |
| Escapes `\n` `\t` `\r` `\u810810`; empty `listOf<T>()`; `List<User>` + typed for-in | ✅ |
| `++`/`--` on fields; bare `return` in void; lambdas with captures | ✅ |
| CLI args (`main(args)`); default parameters; multi-file modules | ✅ |
| `Process` API (`kof.process` + `kof_process_run`) | ✅ |
| `kof.shell` (MVP `34e4344f`) | ✅ (`cmd`/`run`/`ok` JVM+JS; `pipeline` JVM real, JS/Native `PROC001` honest) |

---

## Kof Debugger (in progress)

Principle: the programmer debugs **Kof code**, never the backend artifact.

| Phase | State |
|------|--------|
| 1 — DebugInfo in the IR (source location per op) | ✅ |
| 2 — JVM: SourceFile + LineNumberTable + LocalVariableTable | ✅ |
| 3 — `kof-debug` MVP (DAP stdio + raw JDWP): launch, breakpoints by Kof line, stopped, stack trace, continue, disconnect | ✅ |
| 4 — Kof Editor (breakpoints, toolbar, variables) | planned |
| 5 — Native (DWARF) | ✅ partial 02/09 (`.debug_line` real, `NativeDwarfLineInfoTest`; locals/expressions pending) |
| 6 — JS (source maps) | ✅ partial 01/09 (line-level V3, `KofJsSourceMapTest`; columns/expressions pending) |
| 7 — Advanced: locals per frame, stepping, exception breakpoints, evaluation | planned |

`kof debug app.kf` opens a working DAP session on the JVM (compiles with debug metadata, launches with JDWP). Docs: `debugger-architecture.md`, `debug-adapter.md`, `debugging/debugging-{jvm,native,js}.md`.

---

## Remaining Bugs (real)

> **Complete list with reproduction + suggested fix: `docs/bugs-and-gaps/known-bugs.md`.**

Closed (✅): #1 auto GC on Native (real sweep 03/09; auto-collect 19/09, §260); #2 `spawn` on Native (CONC001 31/08) and the `spawn→await→spawn` SIGSEGV (stack alignment in `pthread_create`, 01/09); #3 JSN002; #4/#5 JSN001/JSN003; #6 lambdas without capture; #7 `Box<T>` native println; #8 `SEM025` false positive; #9 `await`/join; #10 `kof fmt`; #11 Map/Set; #12 pattern matching; #13 null safety; #14 multi-file imports; #15 Native `List.get`; #16 web status/headers; #17 native kof.web (WEB002, 03/09); #18 MySQL/MariaDB wire + binary prepared statements; #19 `kof_sec_secret_get`; #20 FP on Native (FLT001); #22 riscv64/aarch64 core (NATIVE002 closed 19/09); #23 Native `kof.cache`; #24 `spawn`-statement not joined.

Open:
- **#25 `throw <non-String>` / `catch <non-String>`** generates invalid JVM bytecode (`ClassFormatError`, disguised as a "JavaFX launcher error"). Exceptions are Strings; the compiler must reject at compile time. Detail `known-bugs.md` #1.
- **#26 Mutable capture on Native** — reading a boxed variable INSIDE the lambda after EXTERNAL mutation produces garbage (`offset = 20; f(5)`); JVM correct. Detail `known-bugs.md` #2.

---

## Next Steps (order P1→P5)

**P1 — Language:** ✅ `Map/Set`+`enum`+`await`+`List.map/filter/reduce`; ✅ pattern matching (3 targets, `KofPatternMatchingTest` 10/10); ✅ basic nullability (27/08); ✅ multi-file modules (LCA `moduleRoot`, `PackagesE2ETest` 6/6).

**P2 — Web:** ✅ rich response `status(201,body)`/`headerSet` (JVM `KofWebE2ETest` 9/9; Native partial `WEB001`; JS ✅ 03/09); ✅ `kof.cache`; ✅ WebSocket + SSE; ✅ scheduler `every`/`cancel` (`at(cron)` JVM/JS real, Native `CRON001` §274); ✅ `kof.http` timeout/retry/circuit (HTTP/2 missing).

**P3 — Data:** ✅ typed Query DSL level 3 (`User.query(db){ where; orderBy; limit }`, ORM003/ORM004, `KofOrmE2ETest` 22); pooling + `kof.db`/`kof.orm` outside JVM; ✅ Native MySQL/MariaDB.

**P4 — Observability:** ✅ `histogram` + `/metrics` (OBS002 closed); ✅ `app.health` + W3C tracing (`traceId`/`spanId`/`spanStart`/`spanEnd`, `application` lifecycle); OpenTelemetry pending.

**P5 — DX:** ✅ `kof fmt`/`kof init`/REPL; ✅ LSP hover/completion/references/rename + JS source maps V3 (Native DWARF + VS Code pending).

---

## Roadmap — State by Phase (31/08)

### Done — Available

- Compiler foundation (Lexer/Parser/AST/Type system/Semantic/Kof IR); JVM, Native x86_64, JS (GraalJS) backends; classes, records, inheritance, interfaces, exceptions, generics, collections, string ops, control flow.
- `kof build/run/serve/test/debug/bench/fmt`; `kof.web` (routes/middleware, WebSocket RFC 6455, SSE, TLS, `configure`/`stats`); `kof.db` (JDBC + native SQLite); `kof.orm` (entity/CRUD/migrate/MongoDB); native `kof.log`; `kof.config` (3 targets); `kof.mq`.
- HTTP client JVM + JS + Native (03/09) + retry/circuit; `kof.security` v1 + web security G9; `kof.validation`; `kof.observability`; `kof.ui`.
- `kof.process` (`process.spawn` JVM-only, `PROC001` Native/JS, 18/09).
- **Concurrency**: `spawn`/`await` JVM + Native (CONC001) + Android (AND001) + JS event-loop (CONC003); `done`/`poll`; cooperative `cancel`/`cancelled`; `selectAny`; `awaitTimeout`; `channel<T>()`; `scheduler.every/cancel`; `at(cron)` (Native `CRON001`).
- **`kof.media`** (files, not base64) + `serveDir` Range 206/416; **KofAndroid Phase 2** (`--apk` standalone, release signing, label/permissions; CI-proven 18/09 §299/X9).
- enum 3 targets + exhaustive switch (SEM031); Map/Set (COL001); IR optimizer; pattern matching; null safety; higher-order; multi-file modules.
- KofScript; KofC; LSP + real diagnostics; Native GC mark-sweep + auto-collect (§260); FP on Native (FLT001); JSON objects/records + FP arrays (JSN001/002/003).
- multiplatform releases (2 jobs: `test-and-bump` → `package-and-release`).

### In development

- Standard Library (contracts stabilizing).
- Async/Concurrency: JS async (CONC003 ✅), Android (AND001 ✅), `spawn→await→spawn` (✅).
- KofAndroid Phase 2 ✅; `kof.media` residual ✅ (video + Range closed; camera MEDIA002, Native/JS parity MEDIA001 remain as labels).
- Native MySQL/MariaDB ✅ (wire + binary prepared). `native.risc`/`native.arm` core complete (NATIVE002 closed 19/09, record `docs/native-multiarch.md`).
- Debugger — JVM MVP + JS source maps; Native DWARF pending.
- KofJS web platform in the browser (GraalJS alpha).
- Package manager: `kof deps` transitive ✅ 16/09 (`kofdeps.lock` + Maven delegation, R9); registry MVP ✅ 19/09 (D2-A: `kof deploy --publish` + pull, `DepsRegistryTest` 6/6).

### Planned

- complete language specification; conformance suite.
- full web platform (declarative frontend + routing/forms/SSR).
- **gRPC in `kof.web`** (`app.grpc`, `.proto` codegen → IR, JVM parity first; `docs/development/roadmap.md` § web).
- self-hosting (compiler written in Kof).

Full roadmap: `docs/development/roadmap.md`; execution: `docs/development/roadmap.md` §23 (ex-plan-platform-completion).
