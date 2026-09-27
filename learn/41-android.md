[English](41-android.md) | [Português](41-android.pt_BR.md)

# 41 — Android

> **Status: implemented — `--target android` emits JVM bytecode compiled for
> ART (no FFM at runtime) with a host Activity in Kof (`android-host.kf`),
> packaged by the CLI's own pipeline (aapt2/d8/zipalign/apksigner — build-tools
> ≥ 35 pinned). Without an SDK the failure is an honest `DEP001` (R6).**

## Build

```bash
kof build app/ --target android                 # Maven project + APK (SDK present)
kof build app/ --target android --apk           # standalone APK straight from the CLI
kof build app/ --target android --aab           # Android App Bundle
```

- Same Kof source, same semantics as the JVM target — `kof.db`, `kof.orm`,
  `kof.security` and `kof.gpu` compile on Android like on the JVM (§278
  closed; `kof.gpu` runs on the FFM-free stub runtime on ART).
- `detectAppLabel` derives the app label from the program; permissions come
  from `@Permissions` on your code — no hand-written manifest.

## Sign a release

```bash
kof build app/ --target android --apk \
  --keystore release.jks --storepass *** --keypass *** --alias kof
```

- Release signing is part of `kof build` (not a post-step) — `keytool`/`apksigner`
  invoked by the CLI, CWD pinned (§299).

## Deploy a release

```bash
kof deploy app/ --target android --name meu-app --version 1.0
```

- Produces the signed APK + `RELEASE.md` + `SHA256SUMS` + `.tar.gz`
  (see [32 — CLI and Tooling](32-cli-tooling.md)); a missing SDK or
  unsupported target refuses with `DEP001` — never a fake artifact.

## Next step

[Glossary →](glossary.md)
