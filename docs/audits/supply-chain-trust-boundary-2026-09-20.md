[English](supply-chain-trust-boundary-2026-09-20.md) | [Português](supply-chain-trust-boundary-2026-09-20.pt_BR.md)

# KOF supply-chain trust boundary v1 — baseline (20/09/2026)

> **Read-only measurement.** No workflow, script or production file was changed to produce this
> document, and **no decision is taken here**: it maps what is true today so the maintainer can
> answer the contract questions in §5. Sources: repository files at the tip, the GitHub API
> (read-only, as `jonasrochasilva-prog`), and the ratified contract (`D-RELEASE-1.0`,
> `D-1.0-EDGES`, `PROPOSAL-1.0-EXIT-GATE` §32.6/§32.7). Facts are re-verifiable with the commands
> named in each row.

## 1. What the contract already says (ratified) — and what it leaves undefined

| Already ratified | Where |
|---|---|
| the tested package must have the **same digest** as the artifact that will be published | `PROPOSAL-1.0-EXIT-GATE` §32.6 `[RATIFIED]` |
| **artifact identity (SHA256/provenance)** is a mandatory gate; **per-target evidence manifest** | `D-1.0-EDGES` Q7 (§35 gates) |
| Registry = GitHub Releases + `SHA256SUMS` verified before install | `D2-A` |

**Undefined:** the trust root, the builder identity, the minimum provenance format, who verifies, where the policy is enforced, and what happens when proof is missing/invalid. Classification of the front: **CONTRACT AMBIGUITY** (the requirement exists; its content does not).

## 2. Measured baseline

| # | Measurement | Result | How to re-check |
|---|---|---|---|
| M1 | **Tested SHA vs published SHA** (real release `0.4.9-beta`) | tested = `e790137ee1` (merge of #558); published = `22a186b9bf` (bump commit by `kof-release-bot`, **unsigned**). The trees differ in exactly `CHANGELOG.md`, `VERSION`, `version.properties`, `pom.xml`. | `git log --grep 'bump version to 0.4.9-beta'`; `git diff <parent> <bump> --name-only` |
| M2 | Release workflow shape | `release.yml`: full tests (`mvn clean package` + golden + integration) run in job 1 on the **trigger SHA**; job 1 then commits the version bump and pushes; job 2 checks out the **bump SHA** and rebuilds with `-DskipTests` in a 3-OS matrix (linux/windows/macos), then publishes. Inside the release workflow the windows/macos archives are built once and only sanity-checked (`kof version`/`kof info`); the full suite runs there on Linux only (the separate `CI` workflow has a multi-OS `kof.io` job, which is a different run). | `.github/workflows/release.yml` |
| M3 | Token scope of the release workflow | `permissions: contents: write` at **workflow level** (both jobs), and job 1 does a direct `git push` to `main`. | `release.yml` lines 14–15, 94 |
| M4 | Third-party action references | **56** `uses:` references, **0** pinned by full commit SHA (all by tag/branch, e.g. `actions/checkout@v7`, `softprops/action-gh-release@v3`, `docker://…gitleaks:v8.28.0`). Repository setting `sha_pinning_required=false`, `allowed_actions=all`. | `grep -h 'uses:' .github/workflows/*.yml`; `gh api repos/KofLang/Kof4j/actions/permissions` |
| M5 | Default token / approvals | default workflow permissions = `read`; `can_approve_pull_request_reviews=false`. 3 workflows declare no top-level `permissions:` (they inherit the `read` default). | `gh api repos/KofLang/Kof4j/actions/permissions/workflow` |
| M6 | Source governance | `main` and `beta-0.5.0`: `protected=false`, **0 rulesets**, no required status checks visible. (This is what the API shows for the visible repository settings; collaborator permissions and org policy are separate dimensions and were not measured.) | `gh api repos/KofLang/Kof4j/branches/<b>/protection`, `.../rulesets` |
| M7 | Provenance / attestations | none found: the attestations API returns 404 for the digest of a real release asset. Releases are published by `github-actions[bot]`; assets = archive + `kof-cli-*.jar` + `SHA256SUMS` (the checksum file is produced and published by the same job as the artifact). GitHub itself records a server-side `digest` per asset. | `gh api repos/KofLang/Kof4j/attestations/sha256:<digest>` |
| M8 | Consumer verification (Registry) | `kof deps resolve` verifies `SHA256SUMS` **before installing**, and now each source file (`REG002/REG004`); the checksum file travels **inside the same tarball**. | `DepsRegistry`, `DepsSources` |
| M9 | Already in place (do not redesign) | CodeQL, Gitleaks, Dependabot (`maven` + `github-actions`, weekly), `SECURITY.md` (private vulnerability reporting), path-traversal guard on extraction, honest `REG00x` failures, #564/#565/#563 closed with hosted-CI evidence. | repo files |
| M10 | Published assets vs `SHA256SUMS` | `SHA256SUMS` (1 line) covers **only the archive**; the standalone `kof-cli-0.4.9-beta.jar` published next to it is covered by no checksum, and the same-named jar has **different bytes per OS** (42,090,580 B linux × 42,090,623 B windows — the builds are not reproducible). | `gh api repos/KofLang/Kof4j/releases/tags/<tag>`; download the `SHA256SUMS` asset |
| M11 | Verdict on the exact published commit | tested commit `e790137ee1`: CodeQL, Benchmark, Release and Code Quality runs succeeded. Published commit `22a186b9bf` (`[skip ci]`): only `Code Quality: Push on main` (plus issue-triggered bot runs) — **no CodeQL, Release or Benchmark run**; the `CI` workflow excludes `main` from `push` (`'!main'`). | `gh api 'repos/KofLang/Kof4j/actions/runs?head_sha=<sha>'`; `ci.yml` lines 3–8 |

## 3. Classification of the findings

| Finding | Class | Note |
|---|---|---|
| M1/M2 same-candidate discontinuity | **release-readiness gap already covered by the ratified §32.6** | belongs to the release/EXIT-GATE lane; not a claim that a past release was tampered — only that, unchanged, this design would not satisfy the 1.0 evidence contract |
| M3, M4 | **security hardening finding / release-trust gap** | not a `BUG REAL`; needs the workflow owner's decision (Q7) |
| M6 | **source-governance ambiguity** | build provenance proves "commit X, workflow Y", not "commit X was authorized" (Q8) |
| M7/M8 | **contract ambiguity** | SHA256 gives integrity, not authenticity: if artifact **and** checksum are replaced by the same actor, the hash still matches (T3) |
| M10, M11 | **hardening / same-candidate gap** | a checksum that covers only part of the published assets, non-reproducible per-OS jars, and no CodeQL/Release verdict on the exact published commit — evidence for the §32.6 gap, not a bug against a KOF contract |

## 4. Threat model (what each layer can and cannot say)

| ID | Threat | SHA256SUMS today | Build provenance | Workflow/source hardening |
|---|---|---|---|---|
| T1/T2 | corruption / package changed without its hash | detects | detects | — |
| T3 | package **and** checksum replaced together | **insufficient alone** | helps if the proof comes from an independent root | helps |
| T4/T5 | artifact from another commit / rebuilt after the tests | no | **strong** if the attested subject digest is the tested one | pipeline shape (M2) is essential |
| T6 | compromised workflow/action | no | can attest a compromised build | **essential** (M3/M4) |
| T7 | leaked publish credential | no | helps detect divergent origin | least privilege / short-lived credentials |
| T8 | unauthorized commit | no | still valid provenance | **source governance** (M6) |
| T9/T10 | rollback/replay; signing-key compromise | no | not alone | TUF-class designs address these (post-1.0 unless decided otherwise) |
| T11/T12 | malicious legitimate dependency; false-green CI | no | proves origin, not safety | trustworthy gates (`EG-2`) |

There is no single tool that covers all of it; the chain is **source trust → trusted build policy → test the exact artifact → digest → provenance → publish the same bytes → verify by policy**.

## 5. Questions that only the maintainer can decide (none is decided here)

1. **Mandatory property for 1.0:** integrity, authenticity, build provenance, source/change-control provenance, or a combination?
2. **Trusted identity** that may attest an official release (the official workflow, a reusable trusted workflow, a maintainer identity, a Sigstore identity, other).
3. **Source policy:** is provenance of *any* commit enough, or must the revision also have passed a policy (review/required checks)?
4. **Exact-artifact invariant** (build once → test → attest → publish the same bytes): formal rule? (§32.6 already ratifies the digest equality.)
5. **Where verification is mandatory:** release gate only, plus download docs, also in `kof deps resolve`, only for official packages?
6. **Failure policy:** missing/invalid evidence blocks the release, blocks consumption, warns, or differs official vs community?
7. **Workflow trust:** SHA-pinned actions, job-level least privilege, a trusted/reusable build workflow, all, or hardening outside the contract?
8. **Source protection:** branch protection/rulesets, required checks, review, signed commits, none?
9. **SBOM:** 1.0 gate, complementary evidence, or post-1.0?
10. **Rollback/freshness:** are GitHub Releases + provenance enough for 1.0, or is explicit rollback/replay protection required?
11. **Vendor neutrality:** name "GitHub Artifact Attestation" in the contract, or state neutral properties and allow equivalent implementations? (Research favours the second.)
12. **Consumer object (follows #566(b)):** since packages are now consumed as source modules, the artifact that receives the digest/attestation for a *library* is the sources tarball — confirm.

## 6. Lab results — GitHub Artifact Attestations (Onda 2, 20/09/2026)

Run in a **personal public smoke repository** (nothing in Kof4j was touched). Two workflows publish the same kind of artifact: **`lab-broad`** mirrors `release.yml` (workflow-level `contents: write`, actions by tag) and **`lab-pinned`** follows the ideal flow (actions pinned by full SHA resolved through the API, job-level permissions, **build once → attest → upload → publish the same bytes**). Verification is done by a consumer with `gh` 2.98.

| Experiment | Result (measured) |
|---|---|
| both pipelines | success in 18 s / 22 s; the asset digest GitHub records server-side equals the local sha256 |
| online `gh attestation verify` (both) | **GREEN**; verification took ≈6.2 s (includes fetching the trust root) |
| N1 artifact with 1 byte changed | **RED** — no attestation exists for the new digest |
| N2 attestation bundle of artifact A used for artifact B | **RED** |
| N3 wrong repository / N4 wrong owner | **RED** |
| N5 signer workflow pinned to the *other* workflow | **RED** |
| N6 wrong `--source-ref` / N7 wrong predicate type | **RED** |
| P1/P2 policy: signer = `lab-pinned.yml`, ref = `refs/heads/main` | **GREEN** |
| **offline** (bundle + local trusted root, network forced to fail via a dead proxy) | **GREEN**; the same command without the bundle is **RED** (needs the network); tampered file and wrong-digest bundle are **RED** offline too |
| what the attestation states | predicate `slsa.dev/provenance/v1`; builder = `<repo>/.github/workflows/<file>@<ref>`; issuer = GitHub OIDC; source repo/ref/commit; `github-hosted` runner; 1 verified timestamp (transparency log); bundle ≈11.7 KB, trusted root ≈34.6 KB |
| least-privilege probe (build job has `contents: read`; tries `gh release create`) | **`HTTP 403 Resource not accessible by integration`**, no release created — scoping per job really prevents the action |
| repository setting `sha_pinning_required=true` | the tag-based workflow **fails at "Set up job"** ("all actions must be pinned to a full-length commit SHA"); the SHA-pinned one passes. The setting was restored to `false`. |

**What the lab teaches (measured, not decided):**

- Verification is only as strong as the **policy** given to it: `--repo`/`--owner` alone accept any workflow of that repository; pinning `--signer-workflow` (and `--source-ref`) is what makes the builder identity a real property (N5/P1).
- **A flaw in my first lab run**, kept here on purpose: the two workflows produced **byte-identical** tarballs (same commit, same run number, deterministic `tar`), so one digest carried two attestations and the "wrong digest" and "wrong workflow" negatives came out GREEN legitimately. It was fixed by making the content differ per workflow. Lesson: negative tests must use genuinely distinct subjects.
- Offline verification works, but only with a **locally stored trusted root**; the trust root has to be distributed/refreshed by someone.
- The `sha_pinning_required` repository setting is a **mechanical** gate (no workflow change needed to enforce it) — but for Kof4j it would block all 56 tag references at once.
- Cost/lock-in: free for a public repository; the certificate/log infrastructure is GitHub-OIDC + Sigstore public good, so the *evidence format* (SLSA provenance in-toto statement) is portable while the *issuer* is GitHub-specific — the basis of the vendor-neutrality question (Q11).

## 7. Not done here / next steps

- **Lab:** done — see §6 (results, including the flaw found in my first run).
- **Design issue filed: #571** (`[Design/Contract][Security]`, the §5 questions + the lab evidence); duplicate check found none. Awaiting the maintainer; it carries no `1.0-*` category yet (the gate/lane classifies, not the author).
- **Hard stop:** no production change (workflow, gate, `kof deps`) until the maintainer records a decision; and no file `IN PROGRESS` of another lane is touched.
- **Side effect of this research:** while listing the workflow runs of the published commit, a real defect in an unrelated bot workflow was found and filed as **#570**; the maintainer's lane fixed it first (`known-bugs.md` §394) and this work added the regression test and a real-GitHub proof. It is not part of the trust contract.

## 8. Done without waiting for the maintainer's decision on #571

Everything below stays inside what is **already ratified** (`PROPOSAL-1.0-EXIT-GATE` §32.6 `[RATIFIED]` "tested digest == published digest", §32.7/§35 "evidence manifest per target", `D-1.0-EDGES` Q7) or is a measurement. None of it picks a trust root, a provenance format or a verification policy, and none touches `release.yml`, the gates or `kof deps`.

**Tools (read-only / offline, each with a RED-first test registered in the agent suite that the CI already runs):**

- `scripts/verify-release-identity.sh` — checks a published release (`--release <tag>`) or a local directory (`--dir`): `SHA256SUMS` exists; every line points to a real asset and matches the digest GitHub records server-side (or the recomputed one); the archive (`*.tar.gz`/`*.zip`) is covered (FAIL if not; other uncovered assets = WARN); and, with `--tested <sha256>`, **tested == published**. Without `--tested` the equality is reported as **`NOT_RUN`, never PASS**; `--require-tested` turns that into a failure. Test: 12 offline scenarios (tamper, uncovered archive, listed-but-missing, no `SHA256SUMS`, `*` binary marker, tested equal/different/absent, server digest mismatch).
  **Run on the real releases (`0.4.9-beta` linux and windows):** archive covered — `PASS`; the standalone `kof-cli-0.4.9-beta.jar` — `WARN` (no checksum, finding M10); tested == published — **`NOT_RUN`**, and `--tested` with a wrong digest — `FAIL`. In other words, today the ratified equality is literally **unverifiable**, and the tool makes that visible instead of silent.
- `scripts/release-evidence.sh` — the per-target evidence manifest (a TSV: timestamp, target, candidate SHA, result, **tested-package digest**, proof, verifier). `check` approves only if the 8 Stable targets (`jvm x86-64 riscv64 aarch64 js script kofc android`) have **GREEN** evidence on the **same candidate SHA**: RED/SKIP/NOT_RUN never become green, evidence for an older SHA is refused, the tested-package digest is mandatory (except Script), a RED followed by GREEN on the same SHA passes **with a WARN** (a rerun is never silent), invalid entries are rejected and nothing is written. `digest <manifest> <target>` prints the digest to chain into `verify-release-identity.sh --tested`. Test: 9 offline groups.
- Not wired into any gate: hooking them into EG-8 / the release gate belongs to the EG/release lane.

**Additional measurements (feed the decision, decide nothing):**

| Question | Measurement | Reading |
|---|---|---|
| Does a valid attestation protect against **rollback/replay** (T9, Q10)? | the attestation of an **old** lab artifact (run 1) still verifies GREEN with newer versions published (runs 3–4); it has no notion of "latest" | provenance alone does not give freshness; a separate mechanism would be needed if Q10 requires it |
| Is the KOF build **byte-reproducible**? (same commit, same OS, native filesystem, 3 clean builds) | all 3 succeeded; same size (42,156,948 B) and **0 files with different content**, but **3 different sha256** (same path repeated, and another path) | reproducible in *content*, not in *bytes* (jar metadata/timestamps): "rebuild and get the same digest" is impossible today, which is why the exact-artifact rule must publish the *tested bytes* rather than rebuild — see the next row for whether one standard Maven property fixes it |
| Would `project.build.outputTimestamp` make the jar byte-reproducible? | **yes** — 3 clean builds (same path repeated, and another path) gave the **same sha256** (`30e2bca1…`) | one standard Maven property makes the `kof-cli` jar byte-reproducible; applied to the root `pom.xml` and codified in `scripts/check-reproducible-build.sh` (RED before: different digests; GREEN after). The distribution archives are measured in the next row (Linux `tar.gz` without JDK: **not** reproducible); the archives with the embedded JDK and the `windows-x86_64.zip` are still **not** measured |
| Is the Linux `tar.gz` of `scripts/package.sh --skip-build` **byte-reproducible**? (21/09; same commit `e7a2e876`, same jar, same user, WSL on ext4, no `--jdk`, two different output directories, runs 2 s apart) | **no (RED)** — `d069d29e…` ≠ `313a255d…` (38,657,033 B × 38,657,025 B). Of the 134 bytes that differ in the decompressed tar, 58 are in the `mtime` header field and 76 in `chksum` (derived from `mtime`): **`mtime` differs in 58 of 58 entries** (Δ = 2 s = when `cp` copied each file). Names, order, mode, uid/gid, size, type, the content of all 58 files and the gzip header (its mtime is 0) are **identical** | the only cause measured is the entry timestamps (`cp` without `-p`, `tar -czf` without `--mtime`). **Not measured:** entry order on another filesystem/host (`package.sh` never sorts), owner/group across different accounts (uid/uname/gname are stored in the tar), gzip implementation differences, the archives with `--jdk`, the `windows-x86_64.zip`. A valid GREEN needs the two runs in different seconds (inside the same second they could match by chance). No fix in this measurement — `package.sh` was not touched |
| (my first attempt at the build measurement was **invalid**: `mvn clean` failed on the Windows mount and both "builds" were the same stale jar; it was caught by reading the printed `rc=1`, the script then got an `rc` guard, and it was redone on a native filesystem) | | |

**What still needs her decision (nothing was done on these):** pin actions by SHA and least-privilege tokens (Q7); branch protection/rulesets and required checks (Q8); generating/attesting provenance in `release.yml` and the same-candidate rewrite of the pipeline (Q1–Q4); verifying in `kof deps resolve` and the failure policy (Q5/Q6); SBOM (Q9); freshness (Q10); the contract wording (Q11); and the consumer object for libraries (Q12).
