[English](registry-live-roundtrip-2026-09-20.md) | [Português](registry-live-roundtrip-2026-09-20.pt_BR.md)

# Registry 1.5.3 — live GitHub round-trip smoke (20/09/2026) — **RED → GREEN after #564**

> **UPDATE — GREEN after the #564 fix** (see "Re-run after the #564 fix" below): publish, pull by
> version, `latest` pin, idempotent 2nd resolve and honest error all pass against real GitHub.
> The one step that looked open is **not** a Registry defect either: KOF code **can** consume the
> pulled package on the documented path (`kof deps` + `--deps` + `new Class()`); see "Canonical
> consumption" at the end (an earlier claim here was wrong). The text below is the original RED record.
>
> **Result of the first run: RED.** Publish works against real GitHub. Pull does **not**: two real bugs
> found, both filed (#564, #565). The tracker note `live GitHub round-trip = smoke
> manual pendente` (`IMPLEMENTATION-UNIVERSAL-PLATFORM`, item 1.5.3) **stays pending**.
> No production code was changed by this smoke. Only sanitized data below: no token
> was ever printed, logged or committed (presence is recorded, never the value).

## Setup (measured)

| Item | Value |
|---|---|
| Repo tip under test | `5d8a2b98` (`beta-0.5.0`), jar built from the tree (`kof-cli-0.4.7-beta.jar`) |
| Official CLI used to cross-check | `kof 0.4.9-beta` (released jar) |
| Host | WSL Ubuntu-24.04, JDK 25 |
| Smoke repo | public `jonasrochasilva-prog/kof-registry-smoke` (README only; **not** `KofLang/Kof4j`) |
| Release / tag published | `kof-registry-smoke-0.1.0-smoke.5d8a2b98` |
| Asset | `kof-registry-smoke-0.1.0-smoke.5d8a2b98.tar.gz`, 1920 bytes (jar + `RELEASE.md` + `SHA256SUMS`, tar uid/gid 0) |
| Cache isolation | `-Duser.home=<fresh dir>` per phase; consumer directory fresh; `GH_TOKEN`/`GITHUB_TOKEN` **unset** in every pull phase |
| Token handling | producer only, via environment (never on a command line); output redacted; a **post-publish scan of the uploaded asset** found no token, user path or host name |

The release and tag are kept as evidence (no automatic cleanup).

## Phases

| Phase | What | Result |
|---|---|---|
| A | build the package from a clean tree (`kof deploy src --target jvm ...`), pre-publish scan of the artifact | OK — no sensitive data |
| B | `kof deploy --publish` to the smoke repo → real GitHub Release + `.tar.gz` asset | **OK** — release and asset visible, asset downloadable **without a token** (HTTP 200), inner `SHA256SUMS` verifies |
| C | consumer, fresh HOME/cache, `kof deps add owner/repo@<ver>` + `kof deps resolve` | **RED** — `REG002: release <tag> has no .tar.gz asset` (exit 1); no lock, no cache written |
| D | same with `latest` (no `@version`) | **RED** — identical `REG002` (exit 1) |
| E | idempotence (2nd `resolve`) | **BLOCKED** by C (nothing installed to compare) |
| F | nonexistent reference `@9.9.9-inexistente` | **OK** — `REG001: release not found on registry ...`, exit 1, 0 files in cache, valid release untouched |

C/D were reproduced with the **official** `kof 0.4.9-beta` jar as well, so it is not a local-build
artifact. Phase F proves the failure path is honest (R6): only the asset parsing of an *existing* release is broken.

## Findings

| # | Issue | Class | Summary |
|---|---|---|---|
| A | [#564](https://github.com/KofLang/Kof4j/issues/564) | BUG REAL | `DepsRegistry.pickTarball` reads `"download_url"` (real key: `"browser_download_url"`) and cuts each asset object at the first `}` (real asset has a nested `"uploader": {…}` before the URL). Pull can never succeed on real GitHub; `DepsRegistryTest` passed only against a minimal fake server. |
| B | [#565](https://github.com/KofLang/Kof4j/issues/565) | BUG REAL (low) | `CmdBuild.buildFatJar` writes `classesDir/kof-app.jar` inside the directory it walks, so every fat jar embeds a truncated, invalid `kof-app.jar` entry (561 bytes). Not a runtime failure. |

> **Update 20/09 — #565 FIXED** (delivered in `d1a12dd9`): self-inclusion removed from `kof build --fat` and `kof deploy --target jvm` (staging jar outside `classesDir` + exact exclusion of the final path + replace only after close; a failed rebuild keeps the previous jar and leaves no `.kof-app-*` file). Pinned by `CmdBuildFatTest` (build 1, rebuild in the same `classesDir`, failed rebuild) and by the structural inspection of the distributed jar in `CmdDeployTest`. (At that point the smoke stayed RED because of #564 — superseded by the re-run below.)

Both were triaged KOF-first (D-KOF-FIRST): tooling bugs, no KOF syntax involved; contract = `DECISIONS.md` D2-A;
duplicate search (open+closed) found none.

## Observations (no issue filed)

- The tree-built jar reports `compiler: unknown` in `RELEASE.md` because the local build lacks `dev/kof/version.properties`; the official jar prints `kof 0.4.9-beta`. Environment artifact, not a bug.
- `kof deploy` packages only classes reachable from `main`. A library-only package therefore ships nothing unless the entry point uses it — a contract question for *library* publishing (rule 6: maintainer's), not a defect claimed here.
- My own script called `kof run .` (COMP001: needs a `.kf` file); that was harness misuse, not a bug, and is why the `run` step of C/D/E is not evidence.

## Re-run after the #564 fix — **GREEN** (20/09/2026)

Same public release, same clean conditions: jar built from the tree (`kof-cli-0.5.0-beta.jar`, verified to carry the fix), fresh `-Duser.home` per phase, fresh consumer directory, `GH_TOKEN`/`GITHUB_TOKEN` **unset**, real `api.github.com`. Nothing sensitive is recorded (paths shown as `$HOME`).

| Phase | Result (measured) |
|---|---|
| C — by explicit version | **GREEN** — `resolve` exit 0; jar installed at `$HOME/.kof/deps/kof/<owner>/<repo>/<ver>/<repo>-<ver>.jar`; installed jar sha256 == published jar sha256 (`6099e28f…`); the published package's own `SHA256SUMS` verifies (`OK`) |
| E — idempotence | **GREEN** — 2nd `resolve` exit 0, **no download**; jar mtime/size, `kofdeps` and lock state byte-identical |
| D — `latest` (no `@version`), separate HOME and workspace | **GREEN** — `kofdeps` `owner/repo` becomes `owner/repo@0.1.0-smoke.5d8a2b98`, jar installed |
| F — nonexistent reference | **GREEN** — `REG001` (exit 1), 0 files in the cache |
| run the installed package | `java -cp <jar> Default.Main` → `hello, producer` |

Proof in code: `DepsRegistryTest` now serves the **real GitHub shape** (nested `uploader{…}` with its own `url` before `browser_download_url`, no `download_url`, `author{…}` before `tag_name`, delimiters and escaped quotes inside strings, reversed field order) and asserts the HTTP contract (asset downloaded from the asset API `url` with `Accept: application/octet-stream`; `User-Agent: kof-cli`; `X-GitHub-Api-Version: 2022-11-28`; a 302 to another host is followed and the `Authorization` token is **not** sent to it). Before the fix 11 of its 13 tests failed with `REG002: … has no .tar.gz asset`; after: 13/13 (+ `DepsTest` 4, `DepsTransitiveTest` 10).

Fix decision (KOF-first): the release JSON is read with the CLI's own structural `Json.parse` (no new dependency, unlike the `jackson-core` proposed in the plan); the `SHA256SUMS` requirement and the exact → `-jvm` → first `.tar.gz` selection are unchanged.

## Observations from the re-run

- `kofdeps.lock` is not written for registry deps: it is the Maven transitive closure (roadmap 1.5.2). Registry deps are pinned **in `kofdeps`** itself (`latest` → concrete version) — existing behavior, asserted by `latestResolvesAndPinsConcreteVersion`.
- Consuming the pulled package from KOF code: see the correction section below (the earlier `PKG006` reading came from measuring without `--deps`).

## Canonical consumption of the pulled package — measured 20/09/2026 (correction of an earlier claim)

An earlier version of this audit (and the tracker line) said a KOF program **cannot** `import` the pulled package (`PKG006`, even with `--classpath`). That premise was wrong: it was measured **without `--deps`** (the documented way to put the resolved dependencies on the classpath) and with `run --classpath`, which is not a `kof run` flag (`kof run <file.kf> … [--deps] [args...]`: anything else after the file is passed to the program). Re-measured on the documented surface, same public release, no token, clean HOME, jar built from the tip:

| Step | Result (measured) |
|---|---|
| `kof deps init/add/resolve` | jar installed; classes `Default/Main`, `regsmoke/Greeter` |
| `kof run Main.kf --deps` with `import regsmoke.Greeter` + `new Greeter()` | **GREEN** — `hello, consumer`, rc 0 |
| same, **without** `--deps` (what was measured before) | `PKG006`, rc 1 — expected: the dependency is not on the classpath |
| `kof build src --target jvm --deps`, then `java -cp dist:<jar> Default.Main` | **GREEN** — `hello, consumer`, rc 0 |
| `Greeter()` **without** `new`, `run`/`build` `--deps` (or `--classpath`) | `SEM015` ×3, rc 1, **0 classes emitted** — see below |
| `kof run Main.kf --deps --unknown-flag` | runs, rc 0: extra arguments after the file are program arguments (`[args...]`), by design |

Consequences, all measured (nothing implemented):

- Basic JVM consumption of a published package **exists** on the documented path (`kof deps` + `--deps` + `new Class()`). What is still open is contract, not transport: whether a published package is a *library* with a public surface (today `kof deploy` packages only what `main` reaches), whether KOF→KOF consumption is JVM-only or cross-target, and whether the optional `new` also applies to classes coming from an external classpath (`Greeter()` gives `SEM015`; `new Greeter()` works). Those are maintainer decisions (rule 6).
- "Build reports an error but emits a valid artifact" **does not reproduce**: with any error (`SEM015` by `--deps` or `--classpath`, genuine `SEM011`) the build exits 1 and emits **0 classes**. The artifact seen in the report is a **leftover of a previous successful build in the same output directory** (build 1 valid into `cout`; build 2 fails into the same `cout` without cleaning → `rc=1`, `classes=1`, `java -cp cout:<jar> Default.Main` prints the *previous* build's output; with a clean directory: `rc=1`, `classes=0`).
- `kof run --classpath` is not a documented flag of `run`; adding it would be a new CLI surface (a design decision), not a bugfix.

## Source-module consumption (#566, maintainer decision (b)) — implemented and measured on the real GitHub (20/09/2026)

The maintainer decided (`D-RELEASE-0.5.0-GATE` addendum) that a package published by `kof deploy --publish` is consumed as a **source module**, not through the compiled jar. This supersedes the "canonical consumption" reading above for *new* packages (the jar-on-the-classpath route stays only for packages published before this change).

**Implementation** (no syntax/semantics change): the compiler resolves `import` also in the source roots of installed dependencies (after the local module and the official libraries — a dependency never shadows the standard library), for every target; `kof deps resolve` installs the package sources, each verified against `SHA256SUMS`; `kof run|build --deps` hand those roots to the compiler; `kof deploy` ships `src/…` and accepts a **library** (only a package tree, no top-level source), which is compiled to validate it and published as sources only.

**Real round-trip** (jar built from the tip; producer publishes a library to the public smoke repo; consumer with fresh HOME and **no token** against the real API). Pre-publish scan of the tarball and post-publish scan of the **real downloaded asset**: 0 findings (user paths, host, token, secrets); asset = 3 entries (`src/regsmoke/Greeter.kf`, `RELEASE.md`, `SHA256SUMS`), `SHA256SUMS` verifies.

| Step | Result (measured) |
|---|---|
| `kof deps add/resolve` by version (library, sources only) | **GREEN** — sources installed under `$HOME/.kof/deps/kof/<owner>/<repo>/<ver>/src/`; installed source sha256 == published |
| `kof run Main.kf --deps` with `import regsmoke.Greeter` + `Greeter()` (**no `new`**) | **GREEN** — `hello, consumer` |
| same, without `--deps` | `PKG006`, rc 1 (honest) |
| `kof build --target jvm --deps` then `java -cp dist` (no dependency jar) | **GREEN** — `hello, consumer` |
| `kof build --target js --deps` (the same sources, another target) | **GREEN** — rc 0 |
| 2nd `resolve` | **GREEN** — no download, cache byte-identical |
| `latest` (fresh HOME/workspace) | **GREEN** — `kofdeps` pins the concrete version |
| **legacy jar-only package** (`0.1.0-smoke…`) with `run --deps` + `new Greeter()` | **GREEN** — regression check on a real pre-change package |
| missing tag | `REG001`, rc 1 |

**Tests** (WSL Ubuntu-24.04, JDK 25; each slice RED before the fix): `DependencySourceRootE2ETest` 7/7 (RED 4/7), `DepsSourceModuleTest` 8/8 (RED 8/8), `CmdDeploySourcesTest` 5/5 (RED 3/5, includes the full cycle deploy → registry → `kof run --deps`). Full local suite of the 4 modules: kof-compiler 2792 tests — 7 failures, **all the known §337** (`qemu-aarch64` SIGSEGV on WSL2: Dtoa + 5 GC classes; identical on the base; hosted CI is 0F), 0 errors; kof-script 50/0; kof-c 7/0; kof-cli 432/0F/0E/3 skips. The deploy tar contract changed on purpose (it was 3 entries; sources now travel between the artifact and `RELEASE.md`).

The residual questions listed earlier (public surface of a library, cross-target consumption) are answered by this model; `Class()` without `new` works because the dependency is a KOF source class (the `SEM015` of #568 only concerned classes coming from an external jar).
