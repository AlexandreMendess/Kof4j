[English](PACKAGING.md) | [Português](PACKAGING.pt_BR.md)

# Packaging

How the official Kof artifacts are produced, named and verified.

---

## 1. Script

```bash
scripts/package.sh [--jdk] [--output <dir>] [--skip-build]
```

| Option | Effect |
|-------|--------|
| `--jdk` | Downloads and embeds OpenJDK (Temurin 25) in the package |
| `--output <dir>` | Output directory (default: `dist/`) |
| `--skip-build` | Uses the already compiled jar without rebuilding |

The script:

1. reads the version from `VERSION`;
2. compiles `kof-cli-<version>.jar` if needed (`mvn package -DskipTests`);
3. assembles the distribution layout;
4. optionally embeds the JDK;
5. generates the file (`tar.gz` or `zip`) and the `SHA256SUMS`.

## 2. Naming

```text
kof-<version>-<os>-<arch>.tar.gz   # Linux / macOS
kof-<version>-<os>-<arch>.zip      # Windows
```

The `<os>-<arch>` comes from the release workflow matrix (one package per
platform). Real examples:

```text
kof-0.5.0-beta-linux-x86_64.tar.gz
kof-0.5.0-beta-macos-arm64.tar.gz
kof-0.5.0-beta-windows-x86_64.zip
```

> The name carries the **release version** (e.g.: `0.5.0-beta`). The user does
> not need to memorize the version: the installation guide uses the
> `kof-*-<os>-<arch>.tar.gz` glob.

## 3. Platform matrix (workflow `release.yml`)

| Runner | Target | Artifact |
|--------|--------|----------|
| `ubuntu-latest` | `linux-x86_64` | `kof-<v>-linux-x86_64.tar.gz` |
| `windows-latest` | `windows-x86_64` | `kof-<v>-windows-x86_64.zip` |
| `macos-latest` | `macos-arm64` | `kof-<v>-macos-arm64.tar.gz` |

> **macOS is published for Apple Silicon (`arm64`).** There is no
> `macos-x86_64` package. The script's `os`/`arch` mapping (`linux`/`macos`/
> `windows` × `x86_64`/`arm64`) supports any future combination — to
> publish a new platform you only need to add a line to the workflow
> matrix.

## 4. Package layout

```text
kof-<version>-<os>-<arch>/
├── bin/
│   ├── kof            # Unix launcher
│   ├── kof.bat        # Windows launcher
│   └── kof-webview    # kof.ui shell (when available)
├── lib/
│   └── kof.jar        # CLI + compiler + tooling (self-contained)
├── jdk/               # embedded OpenJDK (only with --jdk)
├── tooling/
├── editor/
│   └── kof.tmLanguage.json
├── docs/              # README, LICENSE, architecture, tooling, distribution
└── VERSION
```

`lib/kof.jar` is the CLI's *shaded* jar — it contains the compiler and
tooling. When the embedded JDK is present, the launcher uses it automatically;
without an embedded JDK, the launcher uses `java` from the PATH (development
builds only).

## 5. Checksums

Each package build generates:

```text
SHA256SUMS
```

User verification:

```bash
sha256sum -c SHA256SUMS
```

## 6. Embedded JDK

`--jdk` downloads OpenJDK Eclipse Temurin 25 (tooling API level 21) from the
Adoptium binary API and places it in `jdk/`. No download is done in local
builds without `--jdk` to keep the cycle fast; the release pipeline always
packages with `--jdk`.

## 7. Artifact verification (CI)

Before the release, CI:

1. extracts the package into a clean directory;
2. runs `bin/kof version` and `bin/kof info`;
3. verifies the presence of the embedded JDK (`jdk/bin/java`).

No artifact is published without passing this validation.

## 8. References

- [ARCHITECTURE.md](ARCHITECTURE.md) — conceptual structure of the distribution
- [INSTALL.md](INSTALL.md) — user installation
- [RELEASES.md](RELEASES.md) — release pipeline
