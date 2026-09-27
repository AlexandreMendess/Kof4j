[English](README.md) | [Português](README.pt_BR.md)

# KOF Technical Debt Scout

**Status:** IN DEVELOPMENT — Wave 1 (deterministic, shadow-only). See
`DOING.md` for the live claim/owner and next step.

This is tooling that finds and documents *historical* technical debt
before a temporary implementation choice, a target divergence, or an
obsolete assumption silently becomes compatibility that cannot be
changed anymore. It does not fix anything and it does not decide
language contract — it produces evidence for a human (today: for
`docs/development/tech-debt.md`, the maintainer-owned ledger opened
2026-09-23).

## Read in this order

1. `DEBT_SCOUT_CONTRACT.md` — the operating contract: definitions, hard
   stops, the KOF-first gate, confidence scale, fingerprints, publication
   routing/trust rollout, privilege rules. This is what the code under
   `scripts/debt-scout/` implements.
2. `TAXONOMY.md` — the three-axis classification every candidate uses.
3. `docs/development/DECISIONS.md` §`D-DEBT-SCOUT` — the decision record
   that authorized this front and its current scope limit (no Issue
   publication capability exists yet).

## Where the design came from

Two research documents were supplied by the user in the session that
opened this front (2026-09-22/23): an initial proposal and a
research-revised V2 that supersedes it structurally (candidate ≠
confirmed debt; `C2` never auto-opens an Issue; priority is a vector,
never a single score; reuse `scripts/agent-*.sh` instead of a parallel
governance system). Those source documents are 100+ sections each and
are **not** copied into this repo — `DEBT_SCOUT_CONTRACT.md` is the
condensed, code-synchronized distillation the scripts actually follow.
When the two disagree, the contract in this folder wins.

## What exists today (Wave 1)

```text
scripts/debt-scout/
├── config.py            — loads/validates .debt-scout.yml (stdlib only)
├── schema.py             — Candidate schema v2 (validation)
├── fingerprint.py        — finding/debt fingerprints (stable, sha256)
├── branch_discovery.py   — resolves default/active branch; flags
│                            contract drift instead of hardcoding a ref
├── detectors/
│   └── satd.py            — SATD marker detector (TODO/FIXME/HACK/…)
└── scan.py               — orchestrator CLI (--phase state|deterministic)
```

Every module has a `--selftest` and/or a `scripts/tests/debt-scout-*.sh`
test. **No script calls the GitHub Issues write API.** No workflow in
`.github/workflows/` grants this system `issues: write`.

## What does NOT exist yet (do not assume it runs)

SARIF upload, the Debt Inbox, the C3 publisher, any LLM-backed
qualification step, the ecosystem/Crater-style corpus experiment, and
`kof debt`/`kof fix` CLI surfaces. Each is a separate, explicitly scoped
future unit — see `DEBT_SCOUT_CONTRACT.md` §11 and `DOING.md`.
