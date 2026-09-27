[English](install.md) | [Português](install.pt_BR.md)

# Installation and Distribution

Facts about the official Kof installation. Use them to answer questions
about "how to install", "do I need Java?", "which package do I download", "how
does distribution work".

**Version:** 0.5.0-beta (Sep 2026)

## Facts

- Kof is distributed as a self-contained platform, not just a JAR.
- The official package contains: compiler, CLI, runtime, stdlib, tooling, editor
  support, embedded OpenJDK and documentation.
- The user does NOT need to install Java, configure JAVA_HOME or use SDKMAN.
- The embedded OpenJDK is Temurin 25 (tooling API level 21).
- The launcher is `bin/kof` (Unix) or `bin/kof.bat` (Windows); it locates the
  embedded JDK in `jdk/` and, in development builds without an embedded JDK,
  uses `java` from the PATH.
- Artifacts: `kof-<version>-<system>.tar.gz` (Linux/macOS) or `.zip`
  (Windows), accompanied by `SHA256SUMS`.
- **Published platforms (release workflow matrix):**
  | System | Package |
  |---------|--------|
  | Linux x86_64 (Intel/AMD) | `kof-<v>-linux-x86_64.tar.gz` |
  | macOS arm64 (Apple Silicon) | `kof-<v>-macos-arm64.tar.gz` |
  | Windows x86_64 (Intel/AMD) | `kof-<v>-windows-x86_64.zip` |
- Each release publishes **one package per platform** (3 releases:
  `kof-<v>-linux-x86_64`, `kof-<v>-macos-arm64`, `kof-<v>-windows-x86_64`).
- The file name changes every release; the installation guide uses a glob
  (`kof-*-linux-x86_64.tar.gz`) so as not to depend on the version.
- `native.risc`/`native.arm` are **compilation targets** (riscv64/aarch64
  via qemu, placeholder), not separate download packages.
- The layout is stable across releases: `bin/`, `lib/`, `jdk/`, `tooling/`,
  `editor/`, `docs/`, `VERSION`.
- The release uses a `test-and-bump` job (bump + push of the version) and a
  `package-and-release` job (matrix of 3 platforms) that **checks out the bump
  commit** (not the trigger one) — it guarantees the package ships with the new version.

## How the user chooses the package

1. Go to <https://github.com/KofLang/Kof4j/releases/latest>.
2. Identify the system:
   - Linux → section `(... linux-x86_64)` → `kof-<v>-linux-x86_64.tar.gz`
   - macOS (Apple Silicon) → section `(... macos-arm64)` → `kof-<v>-macos-arm64.tar.gz`
   - Windows → section `(... windows-x86_64)` → `kof-<v>-windows-x86_64.zip`
3. Download **one** package (~230 MB) + `SHA256SUMS`.

## Package structure

```text
kof-<v>-<system>/
├── bin/kof            launcher (Unix)
├── bin/kof.bat        launcher (Windows)
├── bin/kof-webview    shell of kof.ui (when available)
├── lib/kof.jar        CLI + compiler + tooling (shaded)
├── jdk/               embedded OpenJDK (official package)
├── tooling/           consumption conventions per editor
├── editor/            official TextMate grammar
├── docs/              compact documentation
└── VERSION            installation version (e.g.: 0.5.0-beta)
```

## Installation (step by step — Linux, example)

1. Download `kof-<v>-linux-x86_64.tar.gz` from GitHub Releases.
2. `sha256sum -c SHA256SUMS` (integrity verification).
3. `tar -xzf kof-*-linux-x86_64.tar.gz`.
4. `export PATH="$PWD/$(ls -d kof-*-linux-x86_64 | head -1)/bin:$PATH"`.
5. `kof version` and `kof info` to verify.

Windows: `Expand-Archive .\kof-*-windows-x86_64.zip` and add
`...\bin` to the PATH. Details per system in
`docs/distribution/INSTALL.md`.

## Development build

```bash
git clone https://github.com/KofLang/Kof4j.git
cd Kof4j
mvn clean package -DskipTests
bin/kof info
bin/kof version                 # version from VERSION
```

## `kof info` (reference output — 0.5.0-beta)

```text
Kof 0.5.0-beta
Release channel: beta
Tooling API: 21
OS: linux
Arch: x86_64
Target: linux-x86_64
JVM: Eclipse Adoptium 21.0.x (embedded)
Compiler: 0.2.6
Runtime: 0.2.6
Stdlib: 0.2.6
Targets: jvm, native, js (alpha)
LSP: available
Editor support: available
Install: /opt/kof
```

The JVM field appears marked as "(embedded)" when the official package's
embedded JDK is in use. `kof info --json` produces the same report in
JSON.

Compilation targets available via `--target`: `jvm`, `native`
(x86_64 free-list GC), `native.risc`/`native.riscv64`, `native.arm`/
`native.aarch64` (placeholder via qemu), `js` (KofJS/GraalJS), `android`.
`kofc` (native-only C subset) runs via `kof c`, not via `--target`.

## Important rules

- The distribution never depends on Java installed by the user.
- The official package carries its own JVM.
- We do not implement our own JVM — we use OpenJDK.
- The installation guide never hardcodes the version (it uses the glob `kof-*`).
