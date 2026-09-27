[English](makealive-plan.md) | [Português](makealive-plan.pt_BR.md)

# `Kof Makealive` — infrastructure as typed code (design plan · Stage 3 · rows 3.1–3.8)

**Type:** design plan — **COMPLETE** (all rows 3.1–3.8 landed; closed 21/09). **Q1–Q4
ANSWERED 20/09 (§6)**; the core (3.1), 3.2, 3.3 and 3.8 shipped (**3.2 LANDED
21/09 `966c86a4`** — `D-MAKEALIVE-SYNTAX`, pure sugar over `design()`), **3.7
CLOSED 21/09 as runtime-only**; the last rule-6 item, **3.6 (secrets via
`kof.security`), LANDED 21/09 `32285136`** (`D-SECRETS` face 1, tracked in
`secrets-plan.md`). Moved to `docs/architecture/` per the three-states rule
(nothing pending).
**Tracker:** [`IMPLEMENTATION-UNIVERSAL-PLATFORM.md`](../development/IMPLEMENTATION-UNIVERSAL-PLATFORM.md)
Stage 3 (rows 3.1–3.8). **Companion (vision):**
[`docs/architecture/UNIVERSAL-PLATFORM-VISION.md`](UNIVERSAL-PLATFORM-VISION.md)
§4.2 — the domain name, the imperative-vs-declarative verdict and the pipeline
are THERE-decided; this file is the executable decomposition.
**Owner lane:** `.18` (development), maintainer directive 19/09
("assume a frente do kof makealive no plano da plataforma universal").

---

## 1. Objective

Infrastructure as **typed Kof code** with plan/apply/state/reconciliation —
the conceptual replacement for Terraform (VISION §4.2): the code *brings to
life* the declared state. The pieces that make this pure Kof **today** are
already shipped: records/classes (data), `Map`/`List` (the graph), `throw`
(the honest error), `spawn`/`await`/`scheduler` (reconciliation),
`kof.io`/`kof.db` (state), `kof.http`/`kof.process` (providers as interop),
`kof.security` (secrets). Makealive **composes**; it invents no engine
(INTEROP-FIRST, R9) and grows no core (8.6 golden).

## 2. Proposal — a Kof-level stdlib package, NOT new syntax

The **canonical model is imperative-turned-data** (the VISION §4.2 verdict,
"A/B — and it is where the language shines"): typed resources + a builder +
normal functions. The declarative block `infra "prod" { ... }` is row **3.2**
— **DECIDED 21/09 (`D-MAKEALIVE-SYNTAX`) as pure sugar over `design()`** (R4 ✅
landed 21/09 removed the codegen-hook blocker; `infra` stays an identifier) —
**LANDED 21/09 (`966c86a4`, proof `InfraSyntaxE2ETest`)**; v1 did not wait for it.

Surface sketch (flat host idiom, like `kof.workflow`/`kof.supervisor —
DD-OTP-01 option A`; **shapes to be measured by the 3.0 recon before they
become a face**, never assumed):

- `Resource(kind, name)` — **record**: the typed unit of desired state.
- props: `Map<String,String>` (v1; richer types = follow-up, no struct ABI).
- `Infrastructure(nome)` — builder class: `resource(kind, name, props)` and
  `requires(child, parent)` build the graph; duplicates refused.
- `plan(desired, current)` — **pure diff**: `creates` / `updates` / `deletes`
  in deterministic (topological, then declaration) order. No side effects.
- `apply(design, provider)` — converge: create/update what plan says, persist
  state only on success; `destroy(design, provider)` — reverse topological.
- **provider = function values** (the Kof idiom for an interface, precedent
  `KofWfJob.corpo`): `read: (Resource) -> Map` + `set: (Resource, Map) -> Void`
  + `delete: (Resource) -> Void`. No compiler primitive, no new runtime.
- **v1 provider: local FS** (resources materialize as marker files under a
  state dir) — makes plan/apply/state/destroy/idempotency **measurable
  end-to-end with zero cloud credentials and zero FFI**; concrete clouds ride
  §3.5 as interop (REST via `kof.http`, CLI via `kof.shell`), never in-core.

### 2.1 The R1 collision — MEASURED 19/09 (this is why Q1 exists)

The tracker names the namespace `kof.infra` (row 3.1). The R1 machine gate
**hard-denies it**: probe `check_stdlib_boundary.sh` against a temp source
holding the literal `"kof.infra"` → **rc=1**,
`"VIOLATION: 'kof.infra' is a HARD-DENY heavy domain — forbidden in base
stdlib/platform (R1/AGENTS invariant 1; official package only)"`. The deny
list was drafted from invariant 1's `infra-<cloud>` (the *providers* are the
heavy domain) but the bare `infra` token also blocks the Makealive **core**.
Resolution is a maintainer decision → **Q1** (§6). Nothing ships until it is
answered; a ledger line cannot override a hard-deny (measured).

## 3. Contract

- **Idempotency by construction**: apply of an already-converged design is a
  no-op (the plan is empty) — the acceptance golden, not a slogan.
- **plan has no side effects**; only apply touches the world and the state.
- **cycles are refused at graph-build** with an actionable `throw` naming the
cycle (workflow run() precedent, 4/4 targets); compile-time cycle detection
is row 3.7 — ✅ **CLOSED 21/09 as runtime-only** (addendum to
`D-MAKEALIVE-SYNTAX`): with 3.2 as pure sugar the compiler sees only generic
calls, so a static graph would give the block its own semantics (§7/rule 11);
the 3.1 runtime refusal names the cycle members.
- **state advances only on success**: a failed apply leaves the previous state
  intact and names the resource that failed (R6, never a silent partial).
- **secrets are references only**: v1 stores a secret *name* (resolved at
  apply time by the caller); redaction/`Secret` is Stage 5 (row 3.6 🟡), never
  plaintext material in the state file.
- **no target gate in the composition layer** (workflow lesson): the host is
  sequential pure Kof; honest gaps (`CRON001`, `ORM001`, `PROC001`) surface at
  the CALL-SITE when the provider body crosses a runtime boundary.

## 4. Per-target ABI — to be MEASURED in the 3.0 recon, not assumed

| Shape | Expected | Proof plan |
|---|---|---|
| record `Resource` + accessors | all targets | recon JVM+JS run, Native compile |
| class + `Map<String,String>` field + iteration | JVM/JS/Script run; Native compile | recon |
| topological fixpoint + deterministic order | proven pattern (workflow) | cited, re-locked in resource shape |
| cycle → `throw` String | 4 targets | recon |
| `kof.io` file round-trip (state) | matrix row `kof.io` ✅✅✅ (lines 72/74) | recon JVM+JS parity; Native compile |
| JVM==JS byte parity of plan output | Q-parity rule | golden at 3.1 |

**§4 status 19/09 — MEDIDA by `MakealivePrimitivesE2ETest` (recon 3.0.1), 5/5 GREEN**
(JVM==JS byte-parity runs + Native compile pins; the throw/catch form also runs on
SCRIPT): every row above is locked today. Corrections measured: (i) `kof.io` is **not**
a static facade — the contract is `File("path")` constructor + instance methods
(`f.writeText/readText/delete/exists`; `IoE2ETest.fileTextRoundTrip` is the golden —
the "Static forms" line in `docs/stdlib/IO.md` is drift, flag to the docs lane, do not
rewrite it here); (ii) `mapOf().keys()` iteration order is target-dependent — the
design must drive iteration from an explicit `List` (locked); (iii) no ternary `?:` and
no `for (i in 0..n)` range in `.kf` — if/else and while-with-index or element `for`
(locked); (iv) `record` with `String` fields and `mapOf(k, v, ...)` class-field
initializers compile and run identical (locked).

**§4 status 20/09 (2) — ESTADO face MEDIDA by `MakealiveDbStateE2ETest` (sonda
3.1.1), 2/2 GREEN:** the `.kf` frontend resolves `orm.create/save/find/all/page/
where` on top of `db.connect` — **`delete/count/deleteAll/saveAll` do NOT resolve
yet** (runtime-side exists in `KofOrm.functions()`; the call-site wiring is the
GAPS-DB lane's F1/F2 slices, `5cd078c1` cluster). Consequences locked for the
state slice: (i) entities are immutable (SEM038) — an "update" = a NEW generation
row, never a re-save of the same key; (ii) state architecture = `key =
design/name#gen` unique + `all`/`where` filtered in the host (no delete needed);
(iii) JVM==JS byte parity holds through the `kof_platform.db*` JDBC delegate on
the same Graal host (DB001, 16/09); (iv) NATIVE refuses by name (`ORM001`) — the
disjunctive probe flips to strict parity when the sibling's native ORM lands, and
the host db slice must be gated per target exactly like `workflow-ckpt-host.kf`
(+`.native.kf` honest stub).

## 5. Step queue

- **3.0.0 [plan + claim — 0 surface]** — this file (EN+PT), tracker Stage 3
  flip to 🟡, `DOING.md` claim. ✅ 19/09.
- **3.0.1 [recon — 0 code]** — `MakealivePrimitivesE2ETest` locks the §2/§4
  shapes on the 4 targets; outcome feeds back into this file (workflow 2.1.0
  discipline). ✅ 19/09 — 5/5 (JVM==JS parity, Native compile, SCRIPT throw/catch);
  achados devolvidos na nota "§4 status" acima — including the measured kof.io
  constructor+instance face (the sibling head's coordination line `c122d266` carried
  the same finding; my run clobbered their in-flight untracked file before I read it —
  the fix came from their DOING note, credited here). 🔵 next: 3.0.2 (⛔ Q1–Q4).
- **3.0.2 [design sign-off — ⛔ rule 6]** ✅ DONE 20/09 — maintainer poll
  answered Q1–Q4 (§6, `DECISIONS.md` §D-MAKEALIVE: `kof.makealive` /
  complete generic providers / kof.db day-1 / flat+EN). Front opened.
- **3.1 [core]** ✅ 20/09 owner `.18` — **COMPLETE slice (MK-1, poll 20/09 — not a
  core-only fragment)**: virtual-namespace injector (`CompilerMakealive`) +
  `makealive-host.kf` + ledger line (layer per Q1) + the generic REST/CLI
  providers (3.5 folded here) + the `kof.db` state surface (3.4 folded here —
  Q3: the store is kof.db **from the first apply**) + `MakealiveE2ETest`
  (plan/apply/destroy idempotency golden, JVM==JS byte parity, Native compile
  pin, the §3 guards). The kof.db goldens run where `kof.db` is real (JVM/JS);
  Native state awaits D-DB-GAPS and fails with the honest `DB001`/`ORM001`,
  never silent (R6). **LANDED 20/09 — MK-1 complete:** core `9e8be985`+`f5256f8f`
  (→ origin `3be16f88`), db state face `c2850373`, fs provider `4ee3a5c9`(0.4.0)/`f62206e0`(0.5.0),
  CLI+REST providers `13b44c6c`, stdlib docs `docs/stdlib/makealive.md` EN+PT; Makealive battery
  20/20 on the tip (JVM==JS byte parity incl. cross-engine shared-world goldens). 🔵 next in
  this plan's queue: **3.3 reconcile**.
- **3.3 [reconcile]** ✅ 20/09 — `reconcile(design, provider, intervalMs)` delegating
  to `scheduler.every` (tick = `apply` inside a `spawn`; stop = `scheduler.cancel(jobId)`).
  **Correction measured while landing:** the planned `CRON001` Native stub was UNNECESSARY —
  CRON001 gates `scheduler.at` (cron expression); `every` is real on ALL targets since
  SCHED001 (05/09). Slice `makealive-recon-host.kf` ships to every target; Native gets the
  compile pin (`MakealiveReconcileE2ETest` 1/1 x3, JVM==JS byte).
- **3.4 [state]** — **folded into 3.1 by MK-1 (20/09)**; kept as the
  verification item: per-target `kof.db` state goldens (JVM/JS real; Native
  honest `DB001`/`ORM001` until D-DB-GAPS closes).
- **3.5 [providers as interop]** — **folded into 3.1 by MK-1 (20/09)**:
  generic REST provider (`kof.http`) + CLI provider (`kof.shell`) ship with
  the core slice; concrete clouds stay **official packages**
  (`infra-<cloud>`, R1 — never a compiler literal).
- **3.2 [syntax `infra "prod" {}`]** — ✅ **LANDED 21/09** (`D-MAKEALIVE-SYNTAX`):
  pure sugar over `design()` (`InfraDeclarationNode` + parser dispatch like
  `test`/`application` + lowering to `design(): Infrastructure`; no
  keyword/token/type/runtime — `LanguageCoreSurfaceTest` 6/6 green by
  construction). Proof: `InfraSyntaxE2ETest` 2/2 — the block and its hand-written
  `design()` twin produce byte-identical `plan` output (JVM==JS), Native compiles,
  an invalid body errors by name (R6).
- **3.7 [compile-time cycle]** — ✅ **DECIDED 21/09: runtime-only (CLOSED)** —
  addendum to `D-MAKEALIVE-SYNTAX`. With 3.2 as pure sugar the compiler sees only
  generic calls, so a static graph would give the block its own semantics (§7/rule
  11); the runtime refusal (3.1) names the cycle members — that is the contract.
- **3.8 [`kof infra` CLI]** — ✅ **REAFFIRMED 21/09 (`D-MAKEALIVE-SYNTAX`)**: only
  `kof makealive`; `kof infra` is not added.
✅ DECIDED + SHIPPED 20/09 (D-MAKEALIVE-CLI): `kof makealive plan|apply|destroy` landed: verb `makealive` (Q1), `design()`+`provider()` convention, MARK protocol,
  h2 state via `--state` (gen=max+1, `mkMaxGen`); script/native honest refusals (R7).

## 6. Open questions (maintainer decisions — do NOT resolve in code)

**Q1–Q4 ANSWERED 20/09 by the maintainer (chat poll — `DECISIONS.md`
§D-MAKEALIVE; re-create via the same multiple-choice decision if ever
revisited — rule 6):**

- **Q1 — ANSWERED: `kof.makealive`** (option A) — the name IS the decided
  domain (VISION §4.2); passes hard-deny untouched; ships as the
  workflow/shell pattern (virtual namespace + pure-Kof host + ledger
  `platform` line). The tracker literal `kof.infra` stays HARD-DENY
  (measured, §2.1) — the tracker row is updated, never the gate.
- **Q2 — ANSWERED: the COMPLETE generic surface** — local-FS + REST
  (`kof.http`) + CLI (`kof.shell`) all ship in v1 as interop (R9); concrete
  clouds stay official packages (`infra-<cloud>`, R1).
- **Q3 — ANSWERED: `kof.db` from day one** (the non-recommended option) —
  state persists over kof.db; wherever kof.db is gated, the state is gated
  with it (Native = the honest `DB001`/`ORM001` gaps until the GAPS-DB front
  closes them — D-KOF-AS-CLOUD makes that closing a path item, not a
  permanent degrade).
- **Q4 — ANSWERED: flat injected host + English faces** —
  `resource`/`requires`/`plan`/`apply`/`destroy` confirmed; the 3.1 golden
  freezes these names.

## 7. What NOT to do

- **No HCL inside Kof** — the declarative form (3.2), when it comes, desugars
  over records; it never gets its own semantics.
- **No provider repository for everything** — AWS/Azure/GCP are interop
  (REST/CLI) or official packages; the core knows no cloud by name.
- **No new compiler/runtime primitive** — every capability routes to an
  existing namespace and inherits its gap codes.
- **No language-core growth** — row 8.6's golden (`LanguageCoreSurfaceTest`)
  must stay green by construction, not by editing the golden.
- **No plaintext secrets in state** — reference only until Stage 5 ships the
  redacting `Secret`/`KeyHandle`.
