[Português](release-beta-0.5.0-prep.pt_BR.md) | [English](release-beta-0.5.0-prep.md)

# Release 0.5.0 — preparation (branch `beta-0.5.0`)

Decision: `DECISIONS.md` §D-BRANCH-0.5.0 (20/09). Active branch `beta-0.5.0`; `beta-0.4.0` only receives in-flight landings + release prep. Stays in `docs/development/` until the cut (three-states rule).

## Checklist (ordered — version number and tag are the maintainer's call, rule 6)

1. [x] In-flight landings: §374/#553, §371/#550, §378/#554 — **ALL THREE LANDED 20/09 ✅ FIXED** (`BareCollectionFieldE2ETest` 8/8; `ShippedCliCrossSmokeTest` 2/2 + `RuntimeSourceLoaderTest` 6/6; §378 EN×PT open-set cross-check). Docs lane ff `beta-0.5.0` after every landing on `beta-0.4.0`.
2. [ ] CodeQL debt (#555): **TRIAGE CLOSED 20/09 (§385)** — 40 in the window: 13 fixed with targeted tests, 26 dismissed with a reason (25 test-harness + FP JEP 443 #876), #938 by tooling awaiting `main` re-scan. Gate is BASELINE-driven (`scripts/codeql-baseline.txt`: only NEW alerts block; `CODEQL_GATE_SKIP` needs a reason, prints a banner, logs to `.git/codeql-gate-skips.log`, ignored in CI); `scripts/codeql-gate.sh --fast` GREEN rc=0. To tick [x]: ff `q555` + first re-scan pruning the 14 tolerated baseline ids; #563 follows its own queue.
3. [ ] Version: **DECIDED 20/09 — ships as `0.5.0-beta`** (`D-RELEASE-0.5.0-GATE` addendum); `VERSION`/`pom.xml` already at `0.5.0-beta`; only CHANGELOG/tag remain. Audit 21/09: clean (remaining `0.4.0` hits are provenance comments / `beta-0.4.0` as a branch name, none hardcode the artifact version).
4. [~] CHANGELOG cut (EN+PT): a `0.5.0` section + `AGENTS.md`(+PT) `Version:` in the same commit. **DRAFT LANDED 22/09:** header `## [0.5.0-beta] - unreleased (branch beta-0.5.0)`. The cut itself (date + tag) still waits on the seven conditions.
5. [ ] Tally: `'Current build: **N**'` in `docs/backend-parity.md`(+PT) from the first GREEN hosted CI Build+Tests on the release tip (measured from the job log, never memory).
6. [ ] Stability proof: full suite 0F/0E + 5/5 conformance matrix MEASURED on the tag candidate (AGENTS §Stability — tag only after green).
7. [ ] Tag + release notes (EN+PT); declare `beta-0.4.0` closed except the residual-fix list. **`main` stays frozen until this release** (20/09): the 12 pre-fix CodeQL alerts on `main` are ported on release day, not before; the gate measures `beta-0.5.0`.

## Open issues that travel to `beta-0.5.0`

#555 (CodeQL umbrella — the only one still open; #550/#553/#554 landed 20/09). Announced on each issue and via the `DOING.md`(+PT) banner.

## Release gate (`D-RELEASE-0.5.0-GATE`, 20/09, maintainer directive)

Cut only when **all seven conditions** hold, each **measured** (never by eye). The checklist is the tactical queue; these seven are the acceptance.

| # | Condition | How it is measured | State (measured — never by eye) |
|---|---|---|---|
| 1 | 100% parity between targets | per-target matrix + golden byte parity where the contract requires; divergence = bug or diagnosed `XXX00x`. Auto-measured by `check_release_050_gate.sh` (`target-matrix.sh` → `PARITY: 100%`) | GREEN (21/09 `29198ea8`: `PARITY: 100%` jvm/x86-64/riscv64/aarch64/JS/Script vs the JVM oracle; jar rebuilt+stamped by `build-kof-jar.sh`; rootless cross toolchain via `setup-cross-toolchain.sh`; kofc=EG-9, android=EG-10 delegated) |
| 2 | No pending decision | `DECISIONS.md` has no open question changing the surface | NEEDS-REVIEW (**not RED**) — **2** approved `State: OPEN` fronts in flight (`D-TYPE-VARIANCE`/X5, `D-INTEROP-REFLECT`/X6); `D-SECRETS` now `DECIDED` (landed 21/09 `04473bbe`); an approved OPEN front does not block the cut |
| 3 | All loose `docs/development/*.md` concluded and moved out | three-states rule | **GREEN (21/09, `D-RELEASE-0.5.0-SCOPE`)** — remaining OWNED loose plans are allowlisted and do not gate the cut (`ffi-abi-structs` [jonas], `db-parity-plan` [gaps-db]); `makealive-plan`/`secrets-plan`/`IMPLEMENTATION-UNIVERSAL-PLATFORM` moved 21/09; `type-system-extensions-plan` moved 22/09 |
| 4 | Total stability | full suite 0F/0E + 5/5 matrix on the candidate; auto-measured by `stability-report.sh` from a **stamped** real log (`SUITE-SHA`==tip, `SUITE-DIRTY=0`) | GREEN (21/09 `29198ea8`: `TOTAL: tests=3479 failures=0 errors=0 skipped=13`, `SUITE-SHA==tip`, `stability-report.sh` GREEN). Earlier stale reds (§422 `96af9b63`, JS-FFI ratchets at `§431`) are resolved; the candidate is re-measured on the final clean tip at cut time |
| 5 | 0 open issues that are a bug | GitHub OPEN issues with a `bug` label = 0; reads the **query's exit code** (API failure = `UNKNOWN`) | GREEN (0 open bug issues; `scripts/fetch-open-issues.sh` supplies `R050_OPEN_ISSUES_TSV` on hosts without `gh`; #580 is documentation/enhancement) |
| 6 | All edges closed | EG-1..EG-7 closed + open `1.0-blocks` = 0; **EG-8 decoupled** (`D-RELEASE-0.5.0-SCOPE`) | **GREEN (21/09, `R050_OPEN_BLOCKS=0` via authenticated `gh`: only 2 dependabot PRs, 0 issues)**; an unreadable EG table is `UNKNOWN`, never GREEN |
| 7 | Nothing pending in bugs-and-gaps | `check_known_bugs_status.sh` live set empty + `specification-gaps.md` 0 open | RED — 4 live at the tip (authority = `scripts/check_known_bugs_status.sh`; entries in `docs/bugs-and-gaps/known-bugs.md`) |
| 8 | **Full platform parity (BLOCKER, `D-FULL-PARITY-050` 24/09)** | `docs/development/parity/PARITY-GAPS.md`(+PT) **0 open rows**; machine-checked `check_release_050_gate.sh` → `full_parity` (missing/unparsable = UNKNOWN) | RED (24/09) — 16 open rows: `PROC001`; ssh; `MEDIA001/003`; `MQ001`; `GPU001`; `OBS003`; `TIME002/004`; `CONF001`; `MATH001`; `NAT-STR01`/`STR003`; `WEB00x`; `NAT006/007`; `SECN001/003/004/005`; `ORM001`; `DB001`. Unmeasured golden = OPEN (Q5) |

Mechanized by `scripts/check_release_050_gate.sh` (GREEN/RED/NEEDS-MEASURE/UNKNOWN per condition; RED-first `scripts/tests/check-release-050-gate-test.sh`). Every data-driven condition **refuses GREEN when its source is unreadable** — stale jar, a log from another commit/dirty tree, a failed GitHub query, an unparsable EG table, an unreadable bug ledger: inconclusive, never falsely green. RED is expected until the queue closes.

### Recovery — clearing the auto-measured conditions

```bash
eval "$(scripts/setup-cross-toolchain.sh --export)"     # cond. 1: cross binutils/qemu/libc (rootless host; once)
scripts/build-kof-jar.sh                                # cond. 1: rebuild + stamp the tree jar (after the last compiler commit)
scripts/target-matrix.sh                                #          -> PARITY: 100% (6 core targets)
scripts/fetch-open-issues.sh > /tmp/open-issues.tsv     # cond. 5: when `gh` is unavailable (public API)
SAFE_SUITE_LOG="$PWD/.suite.log" scripts/safe-suite.sh  # cond. 4: run on a CLEAN tree
R050_OPEN_ISSUES_TSV=/tmp/open-issues.tsv \
KOF_SUITE_LOG="$PWD/.suite.log" scripts/check_release_050_gate.sh
```
