# KOF Technical Debt Scout — operating contract (V2, condensed)

**Status:** IN DEVELOPMENT (Wave 1 — deterministic, shadow-only, zero Issues).
**Source:** distilled from two research documents supplied by the user
2026-09-22/23 (`KOF_TECHNICAL_DEBT_SCOUT_AGENT_V1_BACKUP.md`,
`KOF_TECHNICAL_DEBT_SCOUT_AGENT_V2.md`). **V2 supersedes V1** — the four
structural changes below are why V1 is historical only, never implemented
as specified:

1. a candidate is not confirmed debt;
2. `C2` never opens an individual Issue by default;
3. there is no single priority score;
4. the Scout reuses this repo's existing agent infrastructure
   (`scripts/agent-*.sh`) instead of a parallel governance system.

The two source documents are kept as attachments to the session that
authored this contract; they are **not** copied verbatim into the repo
(104 sections each — that itself would violate the "small parts" rule,
`AGENTS.md` §"Lesson learned (09/04)"). This file is the actual normative
reference the scripts under `scripts/debt-scout/` implement against. When
this contract and a source document disagree, this contract wins — it is
the one kept in sync with the code.

**Authority:** the Scout produces evidence and tracking. It does **not**
decide language semantics, does **not** alter any contract, does **not**
implement fixes, does **not** close Issues, does **not** merge PRs. It
does not replace `docs/development/tech-debt.md` (the maintainer-owned
manual ledger opened 2026-09-23) — it is a *feed* into human triage, never
a second writer of that ledger.

---

## 1. Definition

An item is technical debt when there is evidence of a **future technical
liability** caused or sustained by a current solution, decision, structure,
or absence, that imposes additional cost on evolution, maintenance,
correction, or compatibility.

A signal is **not** debt by itself:

```text
TODO, FIXME, HACK, XXX, legacy, deprecated, workaround, temporary, skip,
ignore, long method, high churn, duplicate code, old dependency,
complexity, warning, code smell, failing target, partial decision
```

To become confirmed debt there must be evidence of: a technical liability
+ a future/recurring cost or lock-in + proof the current state is not the
intended, healthy contract.

## 2. Hard stops — never publish an individual Issue when

- KOF validity was not proven where required (rule 8/10 of `AGENTS.md`);
- the contract is ambiguous and the ambiguity is not yet documented;
- the expectation derives only from another language (Java/Rust/C#/
  Kotlin/JS/...);
- the behavior is already an honestly documented gap;
- an Issue/PR/`DOING.md` claim already covers the same cause;
- only a code smell exists, with no demonstrated future cost;
- only complexity, age, or churn metrics exist;
- only a `TODO`/`FIXME` exists;
- a claimed regression was not reproduced;
- performance was not measured;
- a security finding is not safe to publish publicly;
- the evidence depends on a branch/ref that no longer exists;
- the Scout cannot separate cause from symptom.

## 3. KOF-first gate (mandatory before any classification)

Same order as `AGENTS.md` rule 10 (`D-KOF-FIRST`):
`DECISIONS.md` → language reference/normative docs → conformance/golden/
E2E tests → `training/` → `learn/` → `training/anti-patterns/
fake-idioms.md` → `docs/backend-parity.md` → documented gaps → current
implementation → `DOING.md` ownership → Issues/PRs/history → external
research last.

Every candidate carries:

```text
KOF VALIDITY:
CONTRACT SOURCE:
CURRENT KOF IDIOM:
MEASUREMENT:
CLASSIFICATION:   BUG REAL | TARGET DIVERGENCE | GAP REAL | DESIGN REQUEST
                  | NOT-VALID | CONTRACT CONFLICT | CONTRACT AMBIGUITY
DUPLICATE / PRECEDENT CHECK:
ACTION:
```

Debt classification (§5) comes **after** this gate, never instead of it.

## 4. Three-axis taxonomy

See `TAXONOMY.md` for the full enumerations. A candidate always carries:

```yaml
debt_type: <Axis A — TDM category, e.g. TEST, ARCHITECTURE, VERSIONING_COMPATIBILITY>
primary_domain: <Axis B — one KOF domain, e.g. NULLABILITY, NATIVE_RISCV>
domains: [<Axis B — all touched domains>]
mechanism: <Axis C — e.g. DISABLED_GATE, TARGET_DIVERGENCE, DOC_CODE_DRIFT>
```

## 5. Confidence scale

| Level | Meaning | Action |
|---|---|---|
| `C0` | hypothesis (weak signal only: a marker, a comment, a hotspot) | **no Issue, no SARIF** — internal candidate list only |
| `C1` | structural signal (real duplication/skip/drift, cost/contract not yet shown) | candidate store / SARIF · **no individual Issue** |
| `C2` | qualified candidate (converging evidence, a material proof still missing) | SARIF / Debt Inbox · **no individual Issue by default** |
| `C3` | confirmed (contract identified, implementation identified, mechanism proved, cost/lock-in evidence, history searched, duplicates checked, owner-collision checked, security gate passed, exit condition expressible) | eligible for Issue **only** after dedup + actionability + publication gates, and only in a trust phase that allows it (§7) |

Confidence is never a weighted average — missing mandatory evidence is
never compensated by many weak signals.

## 6. Fingerprints

Two fingerprints, both stable across line-number/commit/timestamp
movement (`scripts/debt-scout/fingerprint.py`):

- **finding fingerprint** — identity of one signal at one location:
  `rule_id + stable_symbol + normalized_local_claim`.
- **debt fingerprint** — identity of the underlying debt concept:
  `sha256(debt_type + primary_domain + mechanism + governing_contract_id
  + root_boundary_or_symbol + normalized_liability)`.

## 7. Publication routing and trust rollout

```text
C0/C1 → internal evidence only (never leaves the run's artifact)
C2    → SARIF code-scanning alert (has a location) or Debt Inbox summary
         (no location — branch/governance/process-wide)
C3    → Issue-eligible, gated by the phase below
```

Phases (never skip a phase):

- **S0 Shadow (current phase — the only one this repo runs today):**
  `issues_created = 0`, always. Output is SARIF + job summary + uploaded
  candidate artifact for human review. No workflow in this repo has an
  `issues: write` permission for this system yet.
- **S1 Canary:** at most 1 auto Issue/day, `C3` only, every case reviewed.
  **Not enabled** — requires a maintainer decision recorded in
  `DECISIONS.md` before any workflow gains `issues: write` for this
  system.
- **S2 Trusted:** requires ≥30 reviewed findings and effective precision
  ≥0.90 measured per rule. **Not enabled.**
- **S3 Mature:** rule-specific auto-suppression, feedback-driven disable.
  **Not enabled.**

No script in `scripts/debt-scout/` calls the GitHub Issues write API in
Wave 1. That capability does not exist in this repo yet — it is Wave 3+
work, gated on the phases above.

## 8. Evidence lineage

Every candidate JSON (schema in `scripts/debt-scout/schema.py`) records:
`analyzed_sha`, `base_sha` when relevant, `scanner_version`, `rule_id`+
`rule_version`, and `model_used: false` (Wave 1 has no model in the loop —
see §9). No volatile timestamp enters a fingerprint.

## 9. Model use

Wave 1 is 100% deterministic — no LLM call. Order for any future wave:
deterministic scan → cheap structural qualification → context retrieval →
only then a semantic model. A model alone can never mark `C3`, decide a
contract, publish an Issue, declare performance, or declare a security
exploit.

## 10. Privilege separation (binding on any future workflow)

- discovery job: `contents: read`, `issues: read`, `pull-requests: read`
  (+ `security-events: write` only if/when it uploads SARIF);
- a publish job, if it is ever added, gets `issues: write` and nothing
  else, never runs `mvn`/`node`/candidate-supplied shell, and only
  parses already-schema-validated JSON;
- every third-party Action is pinned by full 40-hex commit SHA
  (`scripts/check_workflow_pins.sh`), same as the rest of this repo
  (`D-ARTIFACT-TRUST`);
- no `pull_request_target` execution of untrusted code;
- untrusted input (issue/PR/comment/branch/commit text) is never
  interpolated into a shell command — JSON file / argv / validated env
  var only.

## 11. Waves (this repo's status)

- **Wave 0 — baseline corpus:** manual, no code artifact; superseded here
  by going straight to Wave 1 with `issues_created` hard-pinned to 0 and
  human review of every run's output before any later wave is proposed.
- **Wave 1 — deterministic shadow scout (IN PROGRESS):** config, schema,
  fingerprints, branch/ref discovery, the SATD marker detector, the scan
  orchestrator, discovery-only workflow (no publish job at all). See
  `DOING.md` for the current claim and next step.
- **Wave 2+ (contract drift, cross-target, test-debt, architecture
  detectors; evidence qualification; SARIF upload; Debt Inbox; C3
  canary publisher):** not started. Each additional detector needs its
  own fixtures (true-positive + false-positive) before it runs even in
  shadow mode, per `scripts/debt-scout/detectors/README.md`.

## 12. What this contract does not authorize

Landing this contract and the Wave 1 scripts does **not** authorize:
posting to any GitHub Issue, granting `issues: write` to any workflow,
creating GitHub labels, changing `docs/development/tech-debt.md`, or
promoting any candidate past `C1` without a human reading the run output.
Any of those requires its own commit, its own tests, and — for the
publish capability specifically — a `DECISIONS.md` entry recording the
maintainer's phase-S1 authorization.
