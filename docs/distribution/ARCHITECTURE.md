[English](ARCHITECTURE.md) | [Português](ARCHITECTURE.pt_BR.md)

# Kof Distribution Architecture

**Version:** 0.5.0-beta (09/16/2026)

Kof is not just a compiler — it is a distributable platform. Starting with
0.2.x-beta, the project treats installation as an official part of the product:

> **Kof should feel like a language you install, not a Java project you have to assemble.**

---

## 1. Distribution Model

The official package is self-contained. Installation provides:

```text
kof/
├── bin/
│   ├── kof          # launcher (Unix)
│   └── kof.bat      # launcher (Windows)
├── lib/
│   └── kof.jar      # CLI + compiler + tooling (shaded, self-contained)
├── jdk/             # embedded OpenJDK (only in the official package with --jdk)
│   └── bin/java
├── tooling/         # definitions and tools consumed by editors
├── editor/          # official TextMate grammar (source.kof)
├── docs/            # compact documentation that travels with the distribution
└── VERSION          # installation version
```

The user does **not** need to install Java, configure `JAVA_HOME`, use SDKMAN
or adjust `PATH` manually. The `bin/kof` launcher resolves the JDK embedded in
the installation itself and, only in development builds without an embedded
JDK, falls back to a system `java`.

### File structure per artifact

```text
kof-<version>-linux-x86_64.tar.gz      # Linux (Intel/AMD)
kof-<version>-macos-arm64.tar.gz       # macOS (Apple Silicon)
kof-<version>-windows-x86_64.zip       # Windows (Intel/AMD)
```

Each artifact comes with a `SHA256SUMS` for integrity verification.
Full matrix in [PACKAGING.md](PACKAGING.md).

---

## 2. Embedded JDK (OpenJDK)

Kof's JVM backend needs a JVM to run compiled programs.
Instead of depending on the user's environment, the official package **ships a
compatible OpenJDK** (Eclipse Temurin 25; the tooling API level stays 21 — D-BASELINE).

Decisions:

- We do **not** implement our own JVM — we use OpenJDK.
- The official package brings the JDK in `jdk/` and the launcher uses it
  automatically.
- `kof run`, `kof build --target=jvm`, `kof serve` and `kof test` work
  without any external installation.
- In packages built locally without `--jdk`, the launcher uses `java` from
  `PATH` (equivalent to a development build).

The JDK download is done by the release pipeline via
`scripts/package.sh --jdk` (Adoptium binary API). The verification that the
embedded JDK is being used appears in `kof info` (JVM field marked
as *embedded*).

---

## 3. Tooling API Level: 21

The tooling distributed by Kof assumes the **Java 21 API** as its baseline.

- APIs used by the tooling are compatible with Java 21.
- Kof does not require Java earlier than 21 for its tooling.
- The official package carries its own JVM (Temurin 25).
- The repo toolchain requires JDK 25 (D-BASELINE, 14/09): building the repo and
  running the CLI are JDK 25. The tooling **API level** stays 21
  (`KofVersion.TOOLING_API`) and Kof programs still run on JVM 21+.

This decision is documented in [docs/tooling/README.md](../tooling/README.md)
and is reported by `kof info` (`Tooling API: 21`).

---

## 4. Isolation and Portability

The distribution layout guarantees:

| Property | How |
|-------------|------|
| Isolation | Nothing is installed outside the Kof directory |
| Portability | Relative paths between `bin/`, `lib/` and `jdk/` |
| Simple update | Replace the installation directory (or extract over it) |
| Versioning | `VERSION` + packaged version files |
| Reproducibility | Deterministic build via Maven + packaging scripts |

---

## 5. Multi-target preserved

The distribution does not change the compilation architecture:

```text
Kof Source
    │
    ▼
Frontend
    │
    ▼
Kof IR
    ├──────────► JVM
    ├──────────► Native
    ├──────────► Script
    └──────────► KofJS
```

Kof code is not rewritten when the target changes — **the language is the same,
the backend changes**. Especially for the Native target, the memory complexity
(`malloc`, `free`, pointers, manual management) is absorbed by the
compiler/runtime, never exposed to the programmer.

---

## 6. Installation

### Official package (recommended)

Download the package for your system from
[GitHub Releases](https://github.com/KofLang/Kof4j/releases/latest)
(Linux `linux-x86_64` / macOS `macos-arm64` / Windows `windows-x86_64`).
The name changes every release — the `*` glob avoids depending on the version:

```bash
# Linux
tar -xzf kof-*-linux-x86_64.tar.gz
export PATH="$PWD/$(ls -d kof-*-linux-x86_64 | head -1)/bin:$PATH"

# macOS (Apple Silicon)
tar -xzf kof-*-macos-arm64.tar.gz
export PATH="$PWD/$(ls -d kof-*-macos-arm64 | head -1)/bin:$PATH"

# Windows (PowerShell)
Expand-Archive .\kof-*-windows-x86_64.zip
# add <folder>\bin to PATH
```

Complete guide per system: [INSTALL.md](INSTALL.md).

### Development build

```bash
mvn clean package -DskipTests
bin/kof info
```

Verify the integrity of a download:

```bash
sha256sum -c SHA256SUMS
```

---

## 7. Verification

After installing:

```bash
kof version      # kof 0.5.0-beta (the version of your release)
kof info         # full environment (embedded JVM shows up with "(embedded)")
kof run hello.kf
```
