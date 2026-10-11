[English](workflow-plan.md) | [Português](workflow-plan.pt_BR.md)

# `kof.workflow` — jobs, pipelines, retry, checkpoints, dead-letter (design plan · Stage 2 · TIER 2.1)

> **Status: CONCLUDED 19/09 — all five slices (2.1.0–2.1.4) landed with proof
> (`WorkflowE2ETest` 24/24, byte-parity JVM==JS, Native real face); promoted from
> `docs/development/` per the 3-state rule.** Originally SIGNED-OFF (19/09, maintainer
> poll) — front opened, owner lane `.18`.** Q1–Q4
> answered: **stdlib composition ✓ / MVP minimal (job/dag/after/run/Report) ✓ / retry as an
> ADDITIVE helper, `kof.http` migrates in a later signed slice ✓ / dead-letter ships BOTH
> faces (in-memory + durable via `kof.orm`) ✓**. Zero code in this file; it stays design.
> It exists because `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` row **2.1** was a bare `🔵` line
> with owner `—` until this sign-off; every other greenfield front in `future/`
> (`value-records`, `scoped-resources`, `shell-plan`, …) has a concrete plan. It does **not**
> implement anything: `kof.workflow` is not in the lexer, the parser, any backend, or the
> stdlib today (measured 18/09). Grounded in the **real, measured** primitives it would
> compose (§4).

## 1. Objective
One typed, composable idiom for **long-running, retryable, inspectable work**: define a
job, declare its dependencies (DAG), run it (now or on schedule), retry on failure with
backoff, checkpoint completed steps, and park terminal failures in a dead-letter queue
— **without** every app re-inventing a `while (!ok && tries<N)` loop. Today the pieces
exist but are disconnected (measured, §4): `kof.scheduler.every/at` schedules *functions*,
`kof.supervisor` restarts *workers* (pure-Kof, ratified DD-OTP-01), `kof.process` runs
*commands*, `kof.orm` persists *rows*, channels/`selectAny` coordinate *concurrency*. What is
missing is the **glue that gives all five one contract**: `workflow` is a
composition layer, **not** a new runtime primitive — it inherits behavior from the
primitives it wires, exactly the way `shell` is sugar over `process` (see
[`shell-plan.md`](shell-plan.md)).

## 2. Proposal — a Kof-level stdlib package, NOT new syntax
Measured fact: the parser has no `workflow`/`task`/`pipeline` productions, and `roadmap.md`
§"KofJS — Web" is explicit that JS is not a second-class surface (rule 5). So `kof.workflow`
is proposed as **ordinary Kof code** in the `stdlib` tree (like `kof.supervisor` already is
per DD-OTP-01 option A), **not** a new keyword or grammar form. Sketch of the idiom (shape
only; every *shape* below is parseable Kof, verified at 2.1.0 by `WorkflowPrimitivesE2ETest`
— lambdas are argument-position `(x: T) -> expr`, lists are `listOf(...)`, there is no `[]`
list literal, no `{}` map literal and no `1s` duration suffix in Kof; the *decisions* are
the `Q`s answered in §6):

```
import kof.workflow

// MVP 2.1.2 SHIPPED surface — flat, no `workflow.` prefix (the supervisor
// idiom; the prefix was sketch-level, corrected at landing):
var build = job("build", () -> process.run("make", listOf("-j")).exitCode == 0)
var image = job("image", () -> process.run("docker", listOf("build", "."))
                                     .exitCode == 0).after(build)

var flow   = dag(listOf(build, image))  // dag(...) takes listOf — Kof has no variadics
var report = flow.run()                 // Report: succeeded/failed/skipped/errors,
                                        // allOk(), summary()

// 2.1.3 bundle (SHIPPED 19/09 — the chain sketched here landed; see §5. Historical
// sketch kept per rule 8 — the design shifted at landing: `schedule` delegates to
// `scheduler.at`; checkpoint/supervision became separate slices 3a/3b):
//   flow.retry(...).checkpoint(...).deadLetter(...); flow.schedule("0 3 * * *")
```

> **Sign-off note (19/09):** the §2 surface is the full idiom, but per Q2 the **v1/MVP
> ships only `job`/`dag`/`after`/`run`/`Report`**; `retry` (Q3: **additive** helper),
> `checkpoint` and `deadLetter` (Q4: **both** in-memory and durable faces) arrive together
> in 2.1.3. The sketch's builder-chain, positional arguments and `1000`-ms backoff unit are
> display-level: **resolved at 2.1.0** — signatures must parse as real Kof (no named
> arguments, no duration literals of that form; `WorkflowPrimitivesE2ETest` negative-pins
> the rejections and §2 was rewritten to the parseable forms).

Design invariants (inherited from existing precedent, not invented here):
- **DAG, not linear list.** Cycles are rejected **at run time** by default (same class of
  honesty as `@Deprecated`-vs-`SEM` diagnostics — an actionable message, never a silent
  skip). Compile-time cycle detection, if wanted, is a separate row (**3.7** is that
  decision for the infra graph; workflow mirrors it).
- **Jobs are Kof functions `(Ctx) -> R`.** `Ctx` carries a logger (over `kof.log`), a
  `checkpoint` store (over `kof.db`/`kof.orm`), and a `state: Map[String,Any]` for the
  next job's input. No bespoke job-IR — reuse the function type (D-FNTYPE / §155/§157 are
  the caution this stays out of).
- **Retry/backoff ships as an ADDITIVE helper inside workflow first** (Q3, signed 19/09).
  Today the pattern lives only inside `kof.http` (`NativeHttpCore.java`,
  `JsRuntimeUiLayout.java`, `RuntimeConcurrency.java` — measured); refactoring it into a
  shared helper would edit other lanes' in-flight files (rule 8), so `http` migrates onto
  workflow's helper in a **later, separately signed slice**. Same vocabulary
  (`retry(times, backoff, when)`), no divergence in semantics, one migration test then.
- **Checkpoint = `Result`-shaped record stored via the existing `kof.orm`** — same ABI
  as `shell` reusing `process.Result` (one type, no second shape). No new persistence
  backend.
- **Dead-letter = an `Iterable` view of failures** — the in-memory implementation is a
  `List[Failed]`; the durable one is a `kof.orm` table. No new queue subsystem.
  **LANDED 19/09 (refinement measured):** in-memory = `Report.dead` (`"nome: motivo"`,
  always present); durable = an opt-in USER SINK `(nome, motivo) -> Bool` per job
  (`dag.deadLetter(job, sink)`) — persistence is user code (e.g. `kof.orm` in their
  body). A host-side `kof.orm` reference would reject the WHOLE host on Native
  (ORM001 gate is static, like CRON001 — measured); the sink keeps the host
  target-neutral. Sink refusal (`false`) or throw fails LOUD with the job name.
- **Scheduling reuses `kof.scheduler.at(cron)`.** Workflow does **not** implement cron
  parsing. If `at` raises `CRON001` on a target (native), `flow.schedule` raises
  `CRON001` too — same honest gap, no papering over.
  **LANDED 19/09:** `schedule(expr, dag)` delegates to `scheduler.at` (durations
  D-SCHED-DURATION or cron) and fires the dag inside `spawn` per fire (CONC003-JS-01:
  `run()` may `time.sleep` on backoff = async-marked; `spawn` is the blessed bridge).
  Native slice = a stub that fails LOUD at runtime citing `CRON001` (a scheduler.at
  reference in the injected host rejects the whole host at compile time — measured);
  the user's DIRECT `scheduler.at` keeps the compile-time refusal.
- **Checkpoint via `kof.db`/`kof.orm`.** **LANDED 19/09:** same conditional-slice
  mechanism as schedule (host-core holds function-value hooks `ckLoad`/`ckSave` — no
  `kof.orm` reference in the main host, Native stays compiling; the ck slice injects
  `checkpoint(d, dbConn, dagName)` with entity `KofWfCk`/`orm.where`/`orm.save`; the
  Native slice is a stub failing LOUD with `ORM001`). Restored = `succeeded` without
  re-running bodies (WorkflowE2ETest proves the counter does not advance). PARSER EDGE
  discovered (measured): a function-type field after a `List<...>` field fails with
  `PARSE023`; workaround = plain `String` field in between + 2-arg hooks
  `(dagName, jobName)` — grammar change is rule-6 territory.
- **Supervision reuses `kof.supervisor` (OTP, DD-OTP-01 option A).** A workflow run is a
  supervised tree; restart policy = the supervisor's policy. `workflow` does **not**
  re-implement restart semantics.

## 3. Contract
- Pure composition over existing namespaces (`process`, `scheduler`, `supervisor`, `orm`,
  `concurrency` channels, `Result`, `retry`/`backoff`) — no new `Kof*` runtime call, no
  new backend hook, no new lexer token, no new `Target`.
- **Additive language surface only** (a new `kof.workflow` namespace); no change to the
  behavior of any existing primitive (rule 6: minimal, reversible).
- Deterministic per target: a workflow the target **cannot fully run** (e.g. one that
  needs `at(cron)` on native, or `db.checkpoint` on native) fails at **definition** time
  with that primitive's **existing** gap (`CRON001`/`PROC001`/`ORM001`), never at some
  later surprise in `run()`. If the workflow only uses `run`-only `process`, it works
  wherever `process.run` works.
- Cross-target parity (rule 5): the **same source** must produce the same `Report`
  on every target where the underlying pieces exist. Where they don't, the **same**
  honest code (`ORM001`/`CRON001`/`PROC001`) surfaces on that target.

## 4. Per-target ABI (R7 honest scope) — MEASURED at 18/09, not assumed
Everything `kof.workflow` composes already exists; `workflow` itself adds nothing new to
any backend. Read from the source files (not memory), each column cites its own file:

| primitive | source file | JVM | ANDROID | JS | NATIVE |
|-----------|-------------|:---:|:-------:|:--:|:------:|
| `process.run` → `Result{stdout,stderr,exitCode}` | `KofProcess.java`, `ExpressionProcessCallLowerer.java` | ✅ | ✅ | ✅ (`kof_platform.processRun`) | ❌ `PROC001` |
| `process.spawn` (handle) | same, `spawnCall` | ✅ | ✅ | ❌ `PROC001` (gated 18/09) | ❌ `PROC001` |
| `scheduler.every(fn, ms)` / `.cancel(h)` | `KofScheduler.java` `supportedOn` | ✅ | ✅ | ✅ | ✅ (`Rt B` cross) |
| `scheduler.at(cron5f, fn)` | same, `gapCode` | ✅ | ✅ | ✅ (parser landed 17/09) | ❌ `CRON001` (honest, R6) |
| `kof.orm.{create,save,find,all,where,page,migrate,delete,count,deleteAll,saveAll}` | `KofOrm.java`, JS `KofJsOrmBridge` | ✅ | ✅ | ✅ (JS closed 18/09) | ❌ `ORM001` |
| channels + `spawn` + `selectAny` | `KofInterpreterConcurrency.java`, JS/`native` conc (`CONC003` closed 03/09, §132 resolved 18/09) | ✅ | ✅ | ✅ | ✅ (workers = clone 220 + spinlock) |
| `Result` type (already reused by `shell-plan`) | `KofProcess.RESULT` | ✅ | ✅ | ✅ | ✅ (as type) |
| `retry(times, backoff)` | today only inside `kof.http` (`NativeHttpCore.java`, `RuntimeConcurrency.java`, `JsRuntimeUiLayout.java`) | ⚠️ not general | ⚠️ | ⚠️ | ⚠️ |
| `supervisor.one_for_one` (OTP) | `docs/planning-otp-supervision.md` §Decision status (ratified 13/09, DD-OTP-01 option A pure-Kof) | ✅ | ✅ | ✅ | ✅ (×3 ✓ 19/09 — §129 cross port) |

**Consequences for `kof.workflow`:** on JVM + ANDROID + JS (with Graal host), a full
workflow (jobs + DAG + retry + checkpoint + cron-schedule + dead-letter) is available from
day one — every primitive is green there. On **Native**, `at(cron)` (`CRON001`) and
`orm.checkpoint` (`ORM001`) surface the existing honest gap; **a checkpointless
cronless `flow.run()` still works there** (jobs + DAG + retry + in-memory dead-letter, all
green on native). The plan **does not** paper over native's `PROC001`/`ORM001`/`CRON001`
gaps — it inherits them verbatim.

## 5. Step queue (the executable todo this doc exists to produce)
Owner: **lane `.18`** (assigned by the 19/09 maintainer greenlight).

- **2.1.0 [recon — 0 code]** ✅ DONE 19/09 — `WorkflowPrimitivesE2ETest` (6/6): the
  per-target honesty pins already exist test-backed (`DomainGapCodesTest` PROC001,
  `KofTimeE2ETest` CRON001, `KofOrmE2ETest` ORM001, `CoreRegressionE2ETest` process JVM+JS)
  — cited, not duplicated. Parser resolved: bracket lists `["x"]`, brace maps `{"k":v}` and
  the `1s` suffix are **not** Kof syntax (negative-pinned); lists are `listOf(...)`, maps
  `mapOf(...)`, lambdas are argument-position `(x: T) -> expr`, method chaining works. The
  recon found and fixed a real bug in the process's own unit: a lambda returning
  `process.run(...)` leaked a bare `LResult;` descriptor (the `CompilerLambdaClass` string
  round-trip drops the package; `JvmTypeMapper` had no `kof.process/Result` entry — now
  mapped like `Handle` #31). No shipped surface.
- **2.1.1 [design sign-off — ⛔ rule 6]** ✅ DONE 19/09 — maintainer poll: Q1 stdlib ✓,
  Q2 minimal MVP ✓, Q3 additive retry ✓, Q4 both dead-letter faces ✓. Front opened.
- **2.1.2 [MVP — pure-Kof stdlib, minimal, JVM+JS]** ✅ DONE 19/09 — pure-Kof host
  `dev/kof/workflow-host.kf` injected FLAT by `CompilerWorkflow` on `import kof.workflow`
  (supervisor mechanism, DD-OTP-01 option A — hence no `workflow.` prefix; §2 corrected at
  landing). Surface exactly Q2: `job`/`dag`/`after`/`run`/`Report` (succeeded/failed/
  skipped/errors, `allOk()`, `summary()`). No target gate (sequential fixpoint, no runtime
  boundary). Golden `WorkflowE2ETest` 7/7 exact-stdout JVM==JS + Native compile pin. Found
  and worked around three parser/typer edges (documented in `docs/stdlib/workflow.md` §5).
- **2.1.3 [add-ons — one bundle]** — **LANDED 19/09, all five faces.** retry ✅ (Q3 honored:
  the helper is the workflow's OWN — `dag.retry(job, times, exponential(base, factor))` +
  `retryFixed` + `Report.retries`; `kof.http` untouched, its migration stays a separate
  signed slice); deadLetter ✅ BOTH faces (in-memory `Report.dead` always + opt-in durable
  sink per job — Q4); schedule ✅ (delegates to `scheduler.at`, loud `CRON001` stub on
  Native); checkpoint ✅ 3a (store over `kof.db`/`kof.orm`, loud `ORM001` stub on Native);
  supervision ✅ 3b — `runSupervised(dag, nome, maxReinicios)`: every job becomes a
  `transient` child of a `kof.supervisor` whose per-child watcher IS the one_for_one
  (only the failed child restarts); restart policy stays the supervisor's (§3), and the
  former rule-6 question ("how is a one-shot job expressed as a worker?") was ANSWERED by
  the maintainer 19/09 ("pode ir pra fatia 3") — the answer is exactly the core's
  `!falhou && politica != permanent` clean-stop idiom, so no new surface beyond the
  composing function. Proof: `WorkflowE2ETest` 24/24 (JVM==JS byte-parity + Native pin).
- **2.1.4 [docs]** ✅ DONE 19/09 (same session as 2.1.2) — idiom doc
  `docs/stdlib/workflow.md` (+PT), `backend-parity` matrix row + 19/09(2) delta (EN+PT),
  tracker 2.1 `🔵→🟡` and 2.5/2.6 `🔵→⏳` (EN+PT), this file promoted out of `future/`
  (`a71a4f51`). Plan queue **CLOSED 19/09**: 2.1.3 landed in full (all five faces,
`WorkflowE2ETest` 24/24) — what remains are only the catalogued honest gaps (Native
`CRON001`/`ORM001`) and the `kof workflow run` surface decision (§2.6, maintainer).

## 6. Open questions (maintainer decisions — do NOT resolve in code)
**All four ANSWERED 19/09 by the maintainer poll** (re-create via the same multi-choice
decision if ever revisited — rule 6):

- **Q1 — ANSWERED: Kof-level stdlib package** (`stdlib/workflow.kf`, like `kof.supervisor`
  per DD-OTP-01 option A) — not a compiler builtin. The entire surface composes already
  typed primitives; scheduling semantics are reused (`kof.scheduler.at`), not changed.
- **Q2 — ANSWERED: minimal v1** — `job`/`dag`/`after`/`run`/`Report`; `retry`, `checkpoint`
  and `deadLetter` move to 2.1.3 as one add-on bundle (see §5).
- **Q3 — ANSWERED: additive layer first** — workflow ships its own `retry(times, backoff,
  when)` helper; `kof.http` migrates onto it in a **later, separately signed slice**
  (refactoring now would edit `NativeHttpCore.java`/`RuntimeConcurrency.java`/
  `JsRuntimeUiLayout.java` — other lanes' territory, rule 8).
- **Q4 — ANSWERED: both faces ship together** — in-memory `Iterable` AND durable
  (`kof.orm` table) at the same release; the `ORM001` honest gap on Native is already
  catalogued and stays honest.

## 7. What NOT to do
- No new lexer/parser token or grammar production (this is **not** a `pipeline` keyword).
- No new runtime primitive on any backend — every action routes to an existing namespace
  (`process`/`scheduler`/`orm`/`supervisor`/`concurrency`) and inherits its gap codes.
- No fork of `retry`/`backoff` semantics — per Q3 the additive helper is workflow's own first;
  `kof.http` migrates onto it later in a separately signed slice (no silent duplicate that
drifts).
- No new persistence layer for checkpoints — reuse `kof.db`/`kof.orm` (`ORM001` on native
  stays honest, do **not** write a native `checkpoint` in asm to sidestep it).
- No re-implementation of restart/supervision — `kof.supervisor` (DD-OTP-01) is that
  home; `workflow` composes it, never duplicates it.
- No papering over `PROC001`/`CRON001`/`ORM001` on native — the honest gap is the
  contract.
- No stdlib `.kf` before **2.1.0** recon confirms the §2 shapes parse as real Kof — DONE
  19/09 (2.1.1 sign-off also DONE 19/09); no claiming a target until its underlying primitive is
  green there (measured in §4, re-verified by `WorkflowPrimitivesE2ETest` per 2.1.0).
