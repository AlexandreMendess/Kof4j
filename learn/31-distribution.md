[English](31-distribution.md) | [Português](31-distribution.pt_BR.md)

# 31 — Distribution

> **Kof 0.5.0-beta — Sep 2026 — targets jvm/native/native.risc/native.arm/js/android + kofc**

## Kof is a platform, not just a JAR

Starting with 0.2.x-beta, Kof behaves like a distributable language:

```text
Kof 0.5.0-beta
        ├── Compiler
        ├── CLI (build/run/serve/check/test/script/repl/c/fmt/config gen/bench/
        │    profile/inspect/debug/info/lsp/install/version)
        ├── Runtime (JVM + Native free-list + JS + KofScript + KofC)
        ├── Standard Library (kof.io, kof.http, kof.db, kof.orm, kof.security,
        │    kof.web, kof.cache, kof.scheduler, kof.config, kof.mq, kof.log...)
        ├── Tooling
        ├── Language Server / editor support
        ├── Embedded OpenJDK
        └── documentation
```

The user installs Kof and gets everything they need — **without installing
Java separately**. The `intention->Kof->frontend->IR->backend->runtime` chain is the same for all targets.

## Package structure

```text
kof/
├── bin/
│   ├── kof              # launcher (Unix)
│   ├── kof.bat          # launcher (Windows)
│   └── kof-webview      # native Linux webview (embedded WebKitGTK) —
│                        #   used by `kof run --target=js` for kof.ui
├── lib/
│   └── kof.jar      # CLI + compiler + tooling (self-contained)
├── jdk/             # embedded OpenJDK (official package)
├── tooling/         # definitions consumed by editors
├── editor/          # official TextMate grammar
├── docs/
└── VERSION          # `revision` (single source)
```

`kof-webview` is compiled by `scripts/build-webview.sh` (Linux, requires
`libwebkit2gtk-4.1`); without it, `kof run --target=js` opens in the system
browser. `kof script` and `kof c` do not need a webview.

## Embedded JDK

Kof distributes its own OpenJDK (Temurin **25** — the repo build baseline since
D-BASELINE 14/09; the CLI classes are `--release 25`, so they need a 25 JVM to
run). The `bin/kof` launcher:

1. locates the embedded JDK in `jdk/`;
2. if it exists, uses it (without depending on `JAVA_HOME`/`PATH`);
3. in development builds, falls back to the system `java`.

Verification:

```bash
kof info
# Kof 0.5.0-beta
# Targets: jvm, native, js (alpha)
# JVM: Eclipse Temurin 25.0.x (embedded)
```

## Tooling API Level

The Java API baseline of the **emitted program** is **21** (`KofVersion.TOOLING_API`,
reported by `kof info` — it is the floor of the Kof bytecode `JvmBackend` emits,
`V21`):

- **do not confuse the layers (D-BASELINE 14/09):** building the repo and
  running the CLI require **JDK 25** (toolchain); a compiled Kof program still
  runs on **JVM 21+** (language contract, frozen — rule 6). Raising the repo
  toolchain does NOT raise the minimum runtime of your programs;
- later OpenJDK versions may be used internally when appropriate (e.g. Virtual
  Threads with Java 25), without becoming a requirement;
- the official package carries its own JVM.

## Multi-target preserved (Target separation 0.2.0)

The distribution does not change the language architecture:

```text
Kof Source → Frontend → Kof IR → JVM | Native (x86-64 / riscv64 / aarch64) | KofJS | KofScript | KofC
```

`Target` enum: `JVM`, `NATIVE`, `NATIVE_RISCV64`, `NATIVE_AARCH64`, `JS`, `ANDROID`. `parseTarget` accepts `native.risc`/`native.riscv64` and `native.arm`/`native.aarch64` as aliases.

The language is the same; the backend changes. For native, the programmer
never writes `malloc`, `free` or manages memory manually — the compiler/
runtime absorb this with a **free-list GC** (`kof_free_head`, reuse via `mmap`; mark-sweep pending, memory returned only in the `munmap` fallback).

## Installation

Download the package for **your** system from
[GitHub Releases](https://github.com/KofLang/Kof4j/releases/latest)
(`linux-x86_64.tar.gz` / `macos-arm64.tar.gz` / `windows-x86_64.zip`).
The name changes with each release — use the `*` glob so you do not depend on the version:

```bash
tar -xzf kof-*-linux-x86_64.tar.gz
export PATH="$PWD/$(ls -d kof-*-linux-x86_64 | head -1)/bin:$PATH"
kof info
kof script --repl   # tests KofScript
kof c --help        # tests KofC
```

Verify integrity: `sha256sum -c SHA256SUMS`.
Full guide per system: [INSTALL.md](../docs/distribution/INSTALL.md).

## References

- [docs/distribution/ARCHITECTURE.md](../docs/distribution/ARCHITECTURE.md)
- [docs/distribution/INSTALL.md](../docs/distribution/INSTALL.md)
