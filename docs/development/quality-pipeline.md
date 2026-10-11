[English](quality-pipeline.md) | [Português](quality-pipeline.pt_BR.md)

# Quality pipeline — executable branch policy

last: automation-enforcement — the per-commit auto-cascade LANDED (auto-promote.yml + auto_cascade.sh, 11/10)
doing: 14.4-rulesets+dispatch-on-main (mantenedora)
next: first-week live observation of the auto-cascade; the canonical windowed path stays promote.yml (maintainer)
location: docs/development
state: active

intent: executable-branch-quality-policy

constraint:

* lab is the only development entry point
* every promotion is explicit, unidirectional and auditable
* no direct push to a later stage
* any failure returns to lab
* fail closed when the state cannot be determined
* deterministic and idempotent automation

## Pipeline

`lab → testing → prerelease → stable → release/x.y.z → tag`

| Stage | Role | Gate to leave it | Protected |
|---|---|---|---|
| `lab` | development + fixes | all required checks PASS | no |
| `testing` | integration/QA | all required checks PASS, 0 blocking issues | yes |
| `prerelease` | public candidate | 7-day window, 0 related issues, checks PASS | yes |
| `stable` | closed contract | all required checks PASS | yes |
| `release/x.y.z` | packaging only | all required checks PASS, version not tagged | yes |
| tag `kof-*` | public contract | — | immutable |

The `≥80%` denominator was **dropped** (`D-QUALITY-PIPELINE-2609`, maintainer
28/09/2026): every promotion is 100%.

## States

`LAB → TESTING → PRERELEASE → PRERELEASE_OBSERVATION → STABLE → RELEASE → TAGGED`

Failure never opens a parallel flow: `TESTING|PRERELEASE|STABLE|RELEASE → BLOCKED → LAB`.

The machine is code, not convention: `scripts/pipeline/pipeline_state.py`. A
transition not in `ALLOWED`, an unknown branch, or a no-op is **refused** (fail
closed). `lab→prerelease`, `lab→stable`, `testing→stable`, `prerelease→release`,
`lab→release` and `stable→prerelease` are blocked by test.

## Promotion Gate

`scripts/pipeline/promotion_gate.py` evaluates one step: it reuses the state
machine, then requires 100% of the fixed mandatory checks (Build + Tests, Native
cross, kof.io multiplatform, Structural quality gates, CodeQL Gate, bots), the
complete observation window with 0 related issues (`prerelease→stable`), and
idempotent tag creation. It is a pure function of its inputs — deterministic, no
writes, no network — so it is safe to re-run.

Report fields: from/to stage and state, status, per-check results, commit,
version, timestamp, blocking/related issues, reasons, and the action
(`return_to: lab`) on failure.

## Enforcement

* Rulesets (server side): `pipeline-stages` blocks direct push, force-push and
  deletion on `main`/`testing`/`prerelease`/`stable`/`release/*`, and requires a
  PR + status checks; `release-tags` makes `kof-*` tags immutable. `lab` is open.
* Workflow `.github/workflows/promote.yml` (manual, idempotent): validates the
  transition, runs the FULL SUITE on the exact dispatched tip as the first formal
  gate (`lab` has no per-push CI by design — the suite is the measurement, never a
  stale check-run), merges that verdict with the tip's check-runs and MEASURED
  issue counts (`promotion_evidence.py`: blocking = open `bug` issues; related =
  issues updated since `promoted-at`), runs the gate, and opens an auditable
  promotion PR only when it PASSES. It never pushes to a stage, and the counts
  are never defaulted — omitting them is refused (opinion is forbidden).
* Concurrency is grouped per target stage; duplicate runs are no-ops.

## The per-commit auto-cascade (automation-enforcement, 11/10)

Maintainer live order: *"era pra cada commit na lab avaliar se iria ter promoção
automatica e merge com as proximas"*. Workflow `.github/workflows/auto-promote.yml`
(on every push to `lab`; `scripts/pipeline/auto_cascade.sh` is the driver):

1. **evaluate** — cheap, fail-closed gates on the exact tip: the five CI
   structural steps verbatim (version-sources + `bump-version.sh`, `check_500`,
   stdlib boundary, signature catalog, agent tests), the release rc-gate, 0 open
   `bug` issues, and divergence (tree-equality per hop). Any RED = honest skip;
   the cut slips (`D-LAB-STABILITY`).
2. **suite** — `safe-suite.sh` on the exact tip, certified by
   `stability-report.sh` (`STABILITY: GREEN`, SHA-stamped). `lab` has no
   per-push CI by design, so this run IS the measurement; no retry machinery —
   a red suite never cascades.
3. **cascade** — the flow the maintainer demonstrated by hand twice on 11/10
   (PRs #799–#801 and #802–#804): `lab→testing→stable→prerelease`, one
   idempotent promotion PR per hop (tree-equality guard, fail-closed), each
   merged only after the measured verdict. This compressed order is a recorded
   maintainer decision of 11/10 — the state machine's canonical windowed path
   (`lab→testing→prerelease→stable`) remains available through `promote.yml`
   for a maintainer-driven cut with the 7-day observation window.
4. **publish** — `release.yml` dispatched on `prerelease` ONLY when
   `kof-${VERSION}*` has no tags yet; an already-published version waits for
   the next bump (honest skip, recorded in the run log).

`main` is NEVER touched by the automation: the `main-gate` ruleset (11/10)
carries main's protections unchanged — PR + the two required checks + 1 review
— with no relaxation, so the date-less final release stays the maintainer's
merge + dispatch. `pipeline-stages` now covers `testing/prerelease/stable/release/*`
with the two status checks still REQUIRED: the automation posts them with the
measured verdict and its provenance (direct on the measured tip;
tree-identical merge commits carry the note), and the never-used human-review
requirement is dropped there (every stage merge to date was a human `--admin`
bypass; the review requirement had never once been satisfied normally). The
bot's write scopes are job-level and justified in `scripts/workflow-permissions.txt`.

## Issue linkage

Blocking is deterministic: a promotion is refused when the count of blocking
issues (`testing→prerelease`) or of related issues in the observation window
(`prerelease→stable`) is non-zero. Association is by version/commit/tag, never by
free-text heuristics.

## Release cadence and lab stability

The pre-1.0 release plan is a **weekly minor cadence to `0.9.0`**
(`D-RELEASE-CADENCE`, maintainer 02/10): one minor per weekend, each cut through
this pipeline from a STABLE `lab`. If `0.9.0` is reached without the full 1.0
exit gate green, the line continues as `1.0.0-RC-1`, `1.0.0-RC-2`, … — each RC
treated as a minor (its own weekend cut through this pipeline) until stable, then
the real `1.0.0`. The version number at a cut is the maintainer's call.

**`D-LAB-STABILITY` (maintainer 02/10):** a minor only enters `lab → testing` from
a STABLE `lab` — full suite green, structural/quality gates rc=0, 0 blocking
entries in `scripts/release-blockers.tsv`, no open `1.0-blocks`. Otherwise the cut
**slips** to the next weekend; it is never forced through with a waiver and no
stage is skipped. The Promotion Gate below is the executable form of this rule.

## Correction policy

> Found a problem? It returns to `lab`.

A fix is never made in `testing`, `prerelease`, `stable` or `release/x.y.z` and
then merged sideways. It is born in `lab` and re-traverses the whole pipeline; a
new pre-release starts its own observation window.

## Contract

`D-QUALITY-PIPELINE-2609` / `D-BRANCH-PIPELINE`, `D-RELEASE-CADENCE`,
`D-LAB-STABILITY` (`docs/development/DECISIONS.md`).
Cutover announcement and migration instructions: issue #647.
