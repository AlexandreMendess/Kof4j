# X2 — `interop` official engine (Python/R) — implementation plan

**Status:** `UNDER DEVELOPMENT` — claimed 26/09 by lane compiler 9092 (same commit
as this doc). **Decision authority:** `D-COMPLETE-FIRST` item 2
(`DECISIONS.md` §`D-COMPLETE-FIRST`, maintainer 26/09) — the FULL package is the
decision; stubs, facades and "accepted gaps" are not options (rule 11 applies to
every face that reaches the language surface).

## Landing log (measured deltas against the table — the table stays as the claim; this is what shipped)

**Fatia 5 — corpus/DoD LANDED 27/09 (docs-only; matrix `8aa5883c9`, idioms `eae0ac18e`+`06319455f`,
learn `745d1ed0f`, coverage `6b70bbaff`, flips this commit).** What the corpus work measured:

1. **Matrix (EN+PT):** `KofR` row + `timeout`/`cancel`/reuse row added after the `KofPy` row —
   mechanisms written EXACTLY as measured (R cancel parent-named; KOFPID wire; child-side
   deadlines), §514 cross and the refusal targets stay DECLARED rows with ledger anchors.
   **The lane's own pollution was cleaned here:** the EN file carried a duplicate `KofPy` row
   in PT wording (autostash residue of `c87dcfa32`, incident `1041f4bb5` family) — removed
   (rule 5 shielding; deleting it is ours because the pollution is ours).
2. **Idioms (EN+PT):** section **(e) Engines** (same-face contract, `callJson` round-trip via
   the platform's own JSON, the anti-hang `timeout` idiom, cancel semantics on both engines,
   the named `INTEROP004`–`008` table) + three RUIM→BOM rows (hang/watchdog-hack → timeout;
   pid archaeology → `spawn`+`cancel`; exit-code-as-reason → named string). The PT file's
   header is `## RUIM → BOM`, not the EN spelling — anchor pinned by the mirror commit.
3. **learn/21 (EN+PT):** new Engines section — until today NO learn/ file mentioned the
   engines. Out-of-lane pollution FOUND and registered, untouched (another lane's front):
   `learn/21-java-interoperability.md` carries a duplicated "Reflection at the boundary"
   section (lines 83/116) introduced by `29b8af404` (X6) — review request to the X6 owner.
4. **ecosystem-coverage (EN+PT):** `kof.interop` row added to the §2.2 real-surface table
   (the table had no engines row at all).
5. **Status per R5:** engines are **experimental** until §514 lands — the note is on all four
   fronts; the promotion-to-stable DoD therefore still owes fatia 4 (native lane), and
   session state remains a declared cut-out. CHANGELOG was already synchronized by
   fatias 1–3 (no new entry needed for a docs-only corpus).

**Proof:** per-commit gates green (`check_live_records`, `check_doc_refs` 678+182,
`check_ledger_anchors` 0 broken, `scripts/tests/doc-refs-test.sh` walking real history);
no code changed — battery state is the fatia-3 one (`Interop*`+`VoidAwaitStackFrameE2ETest`).

**Fatia 3 — timeout / cancel / reuse LANDED 27/09 (code `d7328c036` + §527 `9b4fa30f5`, tests in the same commits; cross face still §514).** Deltas measured while building:
1. **The deadline lives in the CHILD, not the parent.** A parent-side timeout is impossible in pure Kof (the `KofProcess` handle is an internal `Long` that never crosses the Kof surface, and `readLine` blocks) and a parent-side kill would leave the interpreter spinning as an orphan; so the engine's own language throws the stop (python `signal.setitimer` SIGALRM→`TimeoutError`, SIGINT→`KeyboardInterrupt`; R `setTimeLimit(elapsed=)`→'reached elapsed time limit') and the wire carries `KOFTIME`→`INTEROP007`. Zero exit-code guessing, zero compiler surface (rule 12).
2. **The wire grew to 3 lines**: line 1 `KOFPID <pid>` (the child's own pid) feeds `cancel()` = `process.run("kill","-2",pid)`; statuses on line 2 `KOFOK`/`KOFERR`/`KOFTIME`/`KOFCANCEL`(py self-stop)/`""`(dead), payload on line 3. **R cancel is parent-named, not wire-named**: the CI's first real-R run (27/09) measured that the in-R `tools::signalHandler` cannot emit `KOFCANCEL` from the SIGINT exit context — the child just dies on the SIGINT (POSIX default). So `cancel()` sets a parent-side flag + sends the SIGINT, and the call site maps the resulting EOF: flag set → `INTEROP008`, a reply that landed first wins, EOF without the flag stays the honest `INTEROP004`. Feature COMPLETE on both engines, no gap (`D-COMPLETE-FIRST`), no exit-code guessing. Internal protocol — not external contract.
3. `timeout(Int ms)` default **30000** (the §418 bounded-wait precedent); `0` = no timer, declared (R7). Reuse after 007/008 is by construction (replay = new child) and pinned by test.
4. **session state stays a declared cut-out** of this slice (the table's claim survives): long-lived interpreter state across calls is a different contract than one-shot replay, and it doesn't change what 007/008 mean.
5. **§527 found in the path**: the cancel E2E was the first suite to await VOID tasks with `try/catch` around it at scale — void-await left the runtime `Object` on the JVM stack and the verifier killed the class at LOAD. Fixed at the single lowering site + `VoidAwaitStackFrameE2ETest` 4/4.

**Proof:** `InteropTimeoutE2ETest` 4/4 (007 wall-clock bounded + engine reused after; JVM≡x86 on 007; 008 from a spawn task — JVM-declared scope; idle `cancel()` no-op idempotent); `InteropTimeoutScriptE2ETest` 1/1 (SCRIPT ≡ JVM golden byte-identical); `InteropRE2ETest` +2 R-gated (007/008 against real R in CI) + the 3-line generation pinned WITHOUT R on every host (`efa8805ab` lesson). Refusal host mirrors `timeout`/`cancel` (`INTEROP005`, never unknown-method).

**Fatia 2 — records↔JSON LANDED 26/09 (commit `f73c284cd`, §520; tests in the same commit).** Deltas measured while building:
1. **Face frozen as planned: `String callJson(fnName, argsJson)`** — a raw JSON passthrough of the result line; the user composes `json.encode(args)` and `json.decode<R>(wire)`. Zero new compiler surface (rule 11): the scalar `decode<Record>` fold already existed in the 4 engine targets.
2. **Four root causes found between the plan and reality (§520):** the schema collector skipped the WHOLE table for any record with a String field (the nested-class check swallowed `java.lang.String`); the native decode fold fed the UN-QUOTED `find_value` body to `decode_string` (quoted-literal contract — every String field decoded empty); `encode_string` left control bytes raw (JVM ≠ x86 wire, measured); and the interpreter had no tag-4 branch in `encode_list` (the `KofObj` met a reflection walker reading real instance fields → `{}`). All four fixed at the root, not masked.
3. **Wire = the canonical COMPACT separators** (`json.dumps(separators=(",",":")) in the prelude): `": "` was breaking the token search of every native scalar decode over a remote result.
4. **Proof:** round-trip arg+result JVM≡x86≡JS≡SCRIPT byte a byte with quote/newline inside the String face (`InteropPyRecordE2ETest` 3/3 + `InteropPyRecordScriptE2ETest`); remote failure named `INTEROP006`; nested-collection element refused `JSN002` at compile time (R6). `List<Record>` DECODE on x86 STAYS `JSN004` (declared, unchanged). REMAINING in fatia 2: the R engine (`Rscript` absent on this host — `assumeTrue` + measure CI availability), then fatia 3 (cross) with §514.

**Fatia 2b — R engine `KofR` LANDED 27/09 (`996777923`), CERTIFIED in CI on tip `9ec0a4eb9` (Build+Tests completed/success with real `Rscript` + `jsonlite` on the runner: JVM≡x86≡JS executed against real R; the record round-trip golden is byte-identical to the Python one — the engine is a detail, the wire is the contract). Fatia 2 is now COMPLETE (Py + records + R), 4/4 targets certified. Deltas measured:**
1. **The pipe channel did not survive the transplant to R** — two causes only CI-with-R could surface (the R-less dev host skipped them): (a) the JVM `kof_process_spawn` redirects the child's stdin to `/dev/null` (`JvmRuntimeCore.java:304`) — `h.write` is a no-op there (Py never died on this because its spec travels by argv); (b) `Rscript -e <expr>` treats a positional argument as a FILE to source, never as data. Final channel, frozen in the host: the spec travels **embedded in the `-e` expression as an escaped R string literal** (`kofREscape`: backslash→backslash-backslash first, then quote→backslash-quote — the order proven by the golden), with `exprGenerationIsVerifiableWithoutR` pinning the expression build on ANY host, R present or not.
2. **Source re-applied by `eval(parse(text=s$source))` inside the expression** — replay model (session = source) identical to Py; same `callJson` face, same `INTEROP004/005/006`.
3. **Escape lesson from SCRIPT (CI caught what local skipped):** the test program's Java text block ate one escape layer (`PARSE043`); fixed at the source with a single source of truth for the program + the never-skipping guard `scriptProgramParsesWithoutR` — the program must PARSE even where R is absent.

**Fatia 1 LANDED 26/09 (commit `X2-f1`, tests in the same commit).** Deltas
measured while building:
1. **Model = stateless replay over `-c`, not a long-lived `python -`.** Measured:
   `python3 -` on a pipe executes NOTHING before stdin EOF, and the F10 handle has
   no `closeStdin` — the RPC-over-stdin design was physically dead on arrival. The
   engine therefore runs the whole program per call: `process.spawn("python3",
   "-u", "-c", source + prelude, spec)` — the session IS the source (definitions
   persist across calls; mutated globals do not — declared contract, not a hidden
   stub). A live-session face needs a named handle type = new compiler surface =
   rule 6, recorded as a future slice, NOT improvised.
2. **Targets = {JVM, NATIVE x86, JS, SCRIPT}.** SCRIPT was expected to refuse and
   instead runs the REAL engine — the interpreter resolves `kof_process_spawn` by
   reflection in the same generated `KofRuntime` (construction-parity, measured:
   `InteropPyScriptE2ETest` golden ≡ JVM). riscv64/aarch64 refuse `INTEROP005`
   because the cross asm never received `kof_json_encode_double`/`encode_long`
   (JSN001 closed x86-only — catalogued §514, OPEN, owner lane native);
   ANDROID/MCU/RISCV32 refuse until their process face is EXECUTED and proven (R7).
3. **§513 found and fixed at the root in the same commit:** the engine's
   `List<Double>` arg exposed that `json.encode` collapsed raw Double/Long slots
   to `encode_int` (x86 list+map walkers) and that JVM `List<Bool>` cast
   `Boolean`→`Integer`. Proof: `JsonNativeEncodeFpE2ETest` (JVM oracle + JVM≡x86,
   would fail on pre-fix code). `Float` lists stay tag-0 — catalogued in §513.
4. **Surface as shipped (rule 11 gate):** `var py = KofPy(source)` +
   `py.callInt("sq", listOf(5))` / `callDouble` / `callBool` / `callString` —
   the type of the RESULT is the method name, the args are a homogeneous typed
   Kof list; no argv strings, no manual JSON, no reader loops in user code.
   Record args/results = fatia 2 (X6 fold synergy — `json.decode<Record>` scalar
   paths already exist on the JVM side, measured 61-62 of `JsonCompleteE2ETest`).

**Contract (verbatim from the decision):** typed bidirectional marshalling
(`Int`/`Double`/`Bool`/`String`/`List`/`Map`/`record` ↔ JSON), real process
management (spawn, stdin/stdout, timeout, exit, cancel), session state, named
`INTEROP00x` errors, E2E per target, corpus synchronized. Born `experimental`
(R5).

## KOF-first design (measured 26/09, not memory)

- **Zero new namespaces.** The `interop` namespace + HOST_IMPORT `kof.interop`
  already exist (X6, `D-INTEROP-REFLECT`; stdlib-boundary ledger line 68, layer
  `interop experimental`) — the engine EXTENDS it. `scripts/stdlib_boundary.sh`
  stays green with no new row.
- **Engine written in Kof, not Java** (`D-KOF-AS-CLOUD`/`D-BOOTSTRAP` precedent
  `interop-host.kf`): the RPC loop, marshalling and session state live in a
  compiler-injected host (`dev/kof/interop-py-host.kf`), composed from the
  platform that already exists (iron rule 2): `kof.process` (JVM
  `ProcessBuilder`, x86 `RuntimeProcess` pipe2/execvp, cross `Rt` ports —
  measured alive on all executable faces) + `kof.json` (encode/decode, tagged
  maps, sorted-key determinism §106).
- **Codecs are the boundary, never the foundation** (X6 principle, reused):
  scalar/List/Map/record marshalling goes through `json.encode`/`json.decode` —
  no hand-rolled parser (anti-pattern table).
- **Named diagnostics (R6):** free codes measured 26/09 — `INTEROP001`/`002`
  (X6 schema) and `INTEROP003` (§510 non-JVM external static) are taken; the
  engine claims **`INTEROP004`** (interpreter not found at spawn — honest
  runtime error, never a silent empty result), **`INTEROP005`** (target/face
  not backed — compile-time refusal, the §510 gate pattern), **`INTEROP006`**
  (remote-side failure — engine error / traceback surfaced, named). Codes get
  parity-matrix rows + `DomainGapCodesTest` pins as they ship.
- **Surface (rule 11 gate before landing):** the user writes intention —
  `py.call("area", r)` — not mechanism (no argv strings, no manual JSON, no
  reader loops in user code). Exact names are frozen in fatia 1 with the
  training row; the Simplicity Law applies at the surface commit.

## Fatia 2 RECON — DONE 26/09 (measured, scratch probe compiled+run on the three compile targets)

Facts measured (JVM oracle `[{"x":1,"y":2}]\n5\n6`):
- `json.encode(listOf(record))`: JVM ✓, JS ✓, **x86 BROKEN — dumps the raw object
  pointer** (§516, catalogued OPEN, owner = this lane/fatia 2). SCRIPT interprets the
  same generated runtime (parity by construction — to re-measure in the E2E).
- `json.decode<Record>` SCALAR: works everywhere BECAUSE the compiler already folds
  it at compile-time (JSN002 in `ExpressionJsonCallLowerer`: per-field
  `kof_json_find_value` + scalar decoders + canonical constructor). Scalar
  `json.encode(record)` is likewise folded (string-concat + field reads).
- The fix machinery for §516 already exists unused: `NativeJsonSchema` emits
  `.Lsch_<Name>` (token, offset, typeCode, aux) + `.Lsch_registry` +
  `kof_json_schema_find` — zero consumers today.

Frozen surface for fatia 2 (rule 11 gate — no new compiler surface):
- Args `List<record>`: host keeps `json.encode(args)`; needs §516 fixed first
  (tag 4 + `kof_json_encode_object` walker ~40 lines, map walker same patch).
- Result `record`: face `String KofPy.callJson(fn, args)` (the raw payload line —
  INTEROP004/006 identical) + the user composes the EXISTING idiom
  `json.decode<MyRecord>(payload)` (one line, pure Kof, per-field fold = zero
  runtime reflection, output identical on the 4 targets). A typed `callRecord<R>`
  inside the host is impossible without a new compiler face (erased type-var cannot
  select the fold) = rule 6 — NOT improvised.
- Proof plan: `JsonNativeRecordListE2ETest` (JVM oracle + JVM≡x86, RED pre-fix on
  §516) + `InteropPyRecordE2ETest` (round-trip record arg + record result via
  callJson+decode<record>, goldens measured JVM/x86/JS/SCRIPT; Q3 edges: String
  field with quotes/newlines, Bool/Double fields, empty list args, INTEROP006 on
  remote error with a record-returning function).

## Slices (each a complete vertical cut with proof — never a facade)

| # | Slice | Scope of COMPLETE delivery | Proof |
|---|---|---|---|
| 1 | **Python engine on JVM** | host `.kf` + typer/lowerer wiring; session = long-lived `python3 -u` over `kof.process`; typed args → JSON line on **stdin**, typed result ← JSON line on stdout (RECON first: measure `kof.process` stdin-write surface — if the Kof API lacks it, fatia 1 extends `kof.process` honestly for ALL its targets, it is platform work, not interop work); `INTEROP004` on missing interpreter; `INTEROP006` names the remote failure | E2E with `assumeTrue(python3 present)` (node/qemu precedent); round-trip per type incl. record; idempotency; error edges |
| 2 | **R engine** | same host machinery over `Rscript` (host lacks it → `assumeTrue` guard; CI ubuntu availability measured in-slice) | E2E + source binding via existing `interop.schema` (X6 synergy, zero new reflection) |
| 3 | **timeout / cancel / session state** | ✅ **LANDED 27/09** (timeout/cancel/reuse; `INTEROP007`/`INTEROP008`, engine-side deadline — see landing log); session state = declared cut-out; cross face waits §514 | E2E edges landed: hang bounded, cancel named, reuse pinned |
| 4 | **Native / JS / Script faces** | MEASURE per face: real port where the platform backs it, otherwise `INTEROP005` compile-time refusal (JVM-first is R7, and §510 proved the honest-refusal face is complete delivery for a target) | per-target goldens or refusal pins |
| 5 | **Corpus + promotion DoD** | ✅ **LANDED 27/09** (all four fronts shipped — see landing log). Claim was: `training/idioms/interop.md` EN+PT, `learn/` section, parity-matrix rows, `ecosystem-coverage`, CHANGELOG discipline; R5 stability review | docs-lang/refs gates |

## Relation to the connector ecosystem (`future/kof-connector-ecosystem-plan.md`) — "keep an eye on it", not promotion (maintainer 26/09)

- The connector plan is **NOT pulled into development** — opening that front needs the
  explicit `D-CONNECTORS` decision (rule 6 + three-states). What is registered here is the
  RELATIONSHIP, so no lane treats the future Core as a rewrite of the landed work.
- The X2 engines ARE the **process form** of the connector catalogue's §5.5 (Python) /
  §5.6 (R) — the connector plan's own inventory (§2, added 26/09) lists `KofPy`/`KofR` as
  landed substrate. The catalogue's non-goal ("process+stdout is not the *model*", §0.2) is
  respected as written: `KofPy`/`KofR` are ONE connector each, never the whole model; the
  embedding/CPython-C-API/R-C-API path remains unbuilt and is NOT part of X2.
- If `D-CONNECTORS` ever opens: the X2 wire protocol + faces continue as the process
  connector's adapter (the Core consumes it, never rewrites it for principle);
  `INTEROP00x` codes, goldens and tests carry over intact.
- Slices 3–5 (timeout/cancel/session, cross §514, corpus/DoD) are UNAFFECTED by this
  relation — they remain the complete delivery of item 2 (D-COMPLETE-FIRST).

**Closure:** item 2 CLOSES when every slice above has shipped evidence — then
`DECISIONS.md` gets the LANDED note, the roadmap row flips ✅ and this doc moves
to `docs/` (three-states rule).

**Do NOT touch:** `CompilerDriver.java`/`NativeRuntime.java` beyond the minimal
wiring hunks (golden rule); other lanes' IN PROGRESS files (memory/, media
cross); PR #619 (rule 10).
