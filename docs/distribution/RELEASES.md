[English](RELEASES.md) | [Português](RELEASES.pt_BR.md)

# Release Pipeline

Each commit on `main` represents a publishable state. The pipeline guarantees
that `main` never points to a state that does not compile.

```text
commit on main
      ↓
CI (ci.yml) — gate: main always compiles
      ↓
test-and-bump
   ├─ mvn clean package (gate)
   ├─ tests/run-golden.sh (jvm + native)
   ├─ tests/run-integration.sh (CLI + serve + kof test)
   ├─ version bump (scripts/bump-version.sh) — e.g.: 0.4.7-beta → 0.5.0-beta
   ├─ changelog section → CHANGELOG.md
   └─ commit + push of the bump ([skip ci])
      ↓
package-and-release (matrix — one job per platform)
   ├─ checkout of the BUMP COMMIT (not the trigger one)
   ├─ mvn clean package
   ├─ sanity check: VERSION of the checkout == release version
   ├─ scripts/package.sh --jdk (embeds Temurin 25 — toolchain baseline D-BASELINE)
   ├─ validates the artifact (extracts, bin/kof version + info, embedded JDK)
   └─ GitHub Release kof-<version>-<platform> with artifact + SHA256SUMS
```

---

## Workflows

### `.github/workflows/ci.yml` — Pull Requests and branches

Runs:

1. Check that `VERSION` and `pom.xml` agree
   (`scripts/bump-version.sh` + `git diff --exit-code`);
2. `mvn clean test` (all E2E tests JVM + Native);
3. `mvn clean package`.

### `.github/workflows/release.yml` — push to `main`

Two jobs:

1. **test-and-bump** (Ubuntu):
   - `mvn clean package` — **gate**: no release is published with a
     broken build;
   - `tests/run-golden.sh` (8 cases × jvm+native) and
     `tests/run-integration.sh` (CLI + serve + kof test);
   - reads `VERSION` (e.g.: `0.3.22-beta`), computes the next one
     (`0.5.0-beta`), runs `scripts/bump-version.sh`;
   - inserts the changelog section into `CHANGELOG.md`;
   - commits and pushes the bump (`[skip ci]` so it does not re-trigger);
   - exports the **SHA of the bump commit** (`bump_sha`).

2. **package-and-release** (matrix: `ubuntu-latest`/linux-x86_64,
   `windows-latest`/windows-x86_64, `macos-latest`/macos-arm64):
   - **checkout the bump commit** (via `ref: bump_sha`) — without this the
     checkout would bring the commit that triggered the workflow (pre-bump)
     and the package would come out with the previous version;
   - sanity check: `VERSION` of the checkout must equal the release
     version (fails the job if it diverges);
   - `mvn clean package`;
   - `scripts/package.sh --jdk` (embeds Temurin 25);
   - validates the artifact: extracts, runs `bin/kof version`, `bin/kof info`
     and verifies the embedded JDK;
   - creates the **GitHub Release per platform**
     (`kof-<version>-<platform>`) with the package, `SHA256SUMS` and the
     `kof-cli-<version>.jar`.

---

## Tags and releases

- One release **per platform**: `kof-0.5.0-beta-linux-x86_64`,
  `kof-0.5.0-beta-macos-arm64`, `kof-0.5.0-beta-windows-x86_64`.
- The most recent one for each platform carries the **Latest** badge.
- The user installs from the release for **their** system
  (see [INSTALL.md](INSTALL.md)).

---

## Rules

- The release happens **only** if `mvn clean package`, golden and integration
  pass.
- Never publish a broken release.
- The bump is committed with `[skip ci]` to avoid a release loop.
- The package is built **from the bump commit** — the tag and the
  artifact contents always carry the same version.

---

## Artifacts

```text
kof-<version>-linux-x86_64.tar.gz     # in the release kof-<version>-linux-x86_64
kof-<version>-macos-arm64.tar.gz      # in the release kof-<version>-macos-arm64
kof-<version>-windows-x86_64.zip      # in the release kof-<version>-windows-x86_64
SHA256SUMS                            # in each release
kof-cli-<version>.jar                 # standalone jar (each release)
```

Each package contains: compiler, CLI, runtime, stdlib, tooling, editor
support and embedded JDK.

## Changelog

`CHANGELOG.md` is updated by the pipeline via `scripts/changelog.sh`, which
groups the commits since the last tag by convention:

```text
feat:  fix:  docs:  refactor:  test:  build:  tooling:
```

## Manual execution

```bash
# Local
scripts/bump-version.sh            # syncs VERSION → pom/properties
mvn clean test
scripts/package.sh                 # local package without JDK
scripts/package.sh --jdk           # official package with embedded JDK
scripts/changelog.sh               # changelog section on stdout

# GitHub
# Manual release: GitHub → Actions → Release → Run workflow
```
