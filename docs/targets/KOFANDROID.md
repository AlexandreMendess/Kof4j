[English](KOFANDROID.md) | [Português](KOFANDROID.pt_BR.md)

# KofAndroid — Kof's Android target

> **Status: Phases 1-4 implemented (Phase 3 = responsiveness, Phase 4 = `--min-sdk`/`--target-sdk`, 17/09).**
> `kof build --target android` generates the
> Maven project + APK pipeline with the host Activity written IN KOF
> (`dev/kof/android-host.kf`) — zero Java, zero Kotlin, zero Gradle in the
> generated project; dependencies resolved by Kof (ExternalClasspath).
> Phase 2: label/permissions derived from the program, standalone `--apk`
> (aapt2/d8/apksigner straight from the CLI) and release signing `--keystore`.
> Phase 3: the WebView renders at the device width (`<meta viewport>` +
> `setUseWideViewPort`) with a narrow-screen CSS block — see below.
> The compiler base this requires is functional: external class inheritance,
> `super(...)`/`super.method()` with correct INVOKESPECIAL, chained calls on
> external receivers, external constructors and fields, annotations emitted in
> the bytecode.

## What it is

`kof-android` is the target that turns a Kof program into an **installable
Android application** — APK/AAB — keeping the language's central promise:

> **The language does not change. The target changes.**

The same `.kf` that opens a `Window` on the desktop opens an app on the phone:

```kof
main() {
    var w = Window("Contador")
    var label = Label("contagem: 0")
    w.bind(label)
    w.bind(Button("+1", () -> {
        label.text = "contagem: " + App.count
    }))
    w.show()
}
```

No `Activity`, `Intent`, `LayoutInflater`, layout XML or `findViewById` in the
user's code. If it is essential to any program, it belongs to the platform
(language + compiler + runtime) — never to the mechanism leaking into the
intent.

## Why it is NOT a new compiler

Android has no language of its own: ART executes **bytecode converted to
dex**. The pipeline reuses everything that exists:

```text
                    Kof Source
                         │
                         ▼
                 ┌──────────────┐
                 │ Kof Frontend │   (single: lexer/parser/types/IR)
                 └──────┬───────┘
                        ▼
                    Kof IR
                        │
                ┌───────┴────────┐
                ▼                ▼
          JvmBackend        AND* validations
          (.class bytecode)
                │
        ┌───────┼──────────────────┐
        ▼       ▼                  ▼
   d8/dex    AndroidManifest   project/package
   (dx)      synthetic         (Gradle phase 1;
   │                            aapt2+d8+apksigner phase 2)
        ▼
     APK/AAB → installation → ART
```

**It is not transpiling to Java.** It is the same bytecode from the JVM
backend, with restrictions and post-processing specific to the target — the
same relationship KofJS has with the single frontend.

## kof.ui execution model

Today `kof.ui` renders widgets as **DOM via KofJS** in the desktop's native
webview (embedded WebKitGTK). Android already ships a mature WebView
(`android.webkit.WebView`). The realization per target, without changing the
code:

```text
Window/Label/Button/Input/Column/Row/View/Style
        │  (same IR, same Int handles)
        ▼
MainActivity (synthesized by the target)
  └── WebView (fullscreen, JS enabled)
        └── embedded engine loads the program's .mjs
              └── widgets → DOM (same render layer as the desktop)
```

The `MainActivity` is written **in Kof** (`dev/kof/android-host.kf`) and
compiled together with the program by the same frontend — the user never
writes an Activity in Java. Those who need **truly native** UI use direct
interop: with `ExternalClasspath` on the classpath, all of this compiles
today (see [learn/10-inheritance.md](../../learn/10-inheritance.md)):

```kof
import android.widget.Button
import android.view.View

class MeuListener implements OnClickListener {
    Void onClick(View v) {
        println("clicado")
    }
}

// SAM conversion: lambda becomes the listener directly
var b = new Button(this)
b.setOnClickListener((v) -> println("clicou"))
b.setOnLongClickListener((v, n) -> println("long " + n))
var i = Button.inflate(this)      // external static method
if (i instanceof View) { ... }
var c = i as Button               // qualified external cast
b.clicks = 5                      // external field (read/write)
```

## Lifecycle and conventions

| Intent | How the user writes it | What the target does |
|----------|------------------------|--------------------|
| UI app | `main()` with `Window(...)` | synthesizes host Activity + WebView |
| Android component | `class MinhaTela extends android.app.Activity` | respects the hierarchy; requires real signatures via classpath |
| framework metadata | `@Override`, `@NonNull`, ... | emits RuntimeVisible/Invisible in the bytecode |
| logical entry point | `main()` | still exists — test the logic with `kof test` on `jvm`/`js`; android is packaging, not a test target (`kof test --target android` refuses with exit 1, R6) |

Convention rules (no mandatory configuration):

1. **Package/application**: derived from the file's `package`; default
   `dev.kof.app`.
2. **Label/icon**: label comes from the title of the first `Window`; default
   Kof icon (future override by declarative metadata, not annotation).
3. **minSdk/targetSdk**: conservative defaults fixed by the target
   (e.g.: minSdk 24); override by an explicit CLI flag, not a magic file.

## Phases

### Phase 1 — implemented: Maven pipeline, 100% Kof code

`kof build app.kf --target android` produces:

```text
<output>/
├── pom.xml                          ← SDK pipeline glue; NO <dependencies>
├── src/main/AndroidManifest.xml     ← platform data (label, launcher)
├── src/main/assets/kof/
│   ├── index.html, Default.mjs      ← KofJS output of the SAME program
│   └── kof-runtime*.mjs
├── libs/kof-app.jar                 ← bytecode: program + host Activity IN KOF
└── README.txt
```

Key points:

- **Zero Java. Zero Kotlin. Zero Gradle.** The host `MainActivity` is written
  IN KOF (`kof-compiler/src/main/resources/dev/kof/android-host.kf`),
  compiled together by the same frontend and goes in the jar. The user who
  wants their own host declares `class MainActivity extends Activity` in Kof —
  the embedded version gives way.
- **Dependencies managed by Kof**: `android.*` signatures come from
  ExternalClasspath (the android.jar that the project flow provides). The
  `pom.xml` declares no dependency at all — it only orchestrates the SDK's
  official binaries in the Maven phases (pure antrun):
  `d8 → aapt2 link -A assets → zipalign → apksigner`.
- Usage:

```bash
# only the Maven project:
kof build app.kf --target android --output app-android \
    --classpath $ANDROID_HOME/platforms/android-34/android.jar

# or straight to the APK (standalone, no Maven; needs build-tools >= 35 for Java-21 classes):
kof build app.kf --target android --output app-android --apk \
    --classpath $ANDROID_HOME/platforms/android-34/android.jar
```

Permissions live IN THE Kof CODE — metadata consumed by the target:

```kof
@Permissions(["android.permission.INTERNET", "android.permission.CAMERA"])
class MainActivity extends Activity { ... }
```

The app label is the program's first `Window("...")`. The icon is
vectorial (`res/drawable/ic_launcher_kof.xml`) — no generated binary.

### Phase 2 — implemented (31/08): refinements

- ✅ **label derived from the program**: title of the first `Window("...")` becomes
  the manifest's `android:label` (`AndroidProjectWriter.detectAppLabel`);
- ✅ **declarative permissions**: `@Permissions([...])` on a Kof class becomes
  `<uses-permission>` in the manifest (`detectPermissions`);
- ✅ **standalone mode without Maven**: `kof build --target android --apk` calls
  `aapt2 → d8 → zip → zipalign → apksigner` straight from the CLI (build-tools >= 35 +
  `ANDROID_HOME`). If the SDK is missing (`ANDROID_HOME` unset, no `aapt2`), the
  flag fails with **exit 1** and an honest message — never exit 0 without an APK
  (R6); the project is still generated, so `mvn verify` remains an option;
- ✅ **parametrizable release signing**: `--keystore <ks> [--storepass <p>]
  [--keypass <p>] [--alias <a>]` — without `--keystore`, it keeps the local
  debug keystore generated the first time. The signing/artifact flags
  (`--apk`, `--keystore`, `--storepass`, `--keypass`, `--alias`) are
  **android-only**: on any other target, or signing without `--apk`, the CLI
  refuses with exit 1 (R6) instead of silently ignoring them;
- icon: Kof's vectorial default (`res/drawable/ic_launcher_kof.xml`);
  declarative override by metadata remains planned (no generated binary).

### Phase 3 — implemented (17/09): responsiveness

The WebView host now renders the UI at the **device width** instead of the
desktop 980px layout viewport:

- ✅ **`<meta viewport>` in the generated `index.html`** — `width=device-width,
  initial-scale=1, viewport-fit=cover` (`JsArtifactWriter.writeHtmlEntry`);
  `AndroidProjectWriter.patchIndexForPlatform` injects it defensively into a
  custom `index.html` too;
- ✅ **host enables the wide viewport** — `setUseWideViewPort(true)` +
  `setLoadWithOverviewMode(true)` in `dev/kof/android-host.kf`; without both
  the WebView **ignores** the meta tag and the UI shows up zoomed out;
- ✅ **narrow-screen CSS** — a `@media (max-width: 600px)` block wraps
  `.kof-row` and shrinks the titlebar/root padding, so the same
  `Window`/`Column`/`Row` intent adapts with no change in the `.kf` code;
- `kofUiSerializeHtml` (HTML-export path) emits the same viewport meta.

Proof: `AndroidInteropE2ETest.androidResponsiveViewportAndWebViewWideViewport`.

### Phase 4 — implemented (17/09): SDK versioning by flag

- ✅ **`--min-sdk <n>` / `--target-sdk <n>`** on `kof build --target android`
  (explicit flag, never a magic file). The values reach the generated
  `AndroidManifest.xml` (`<uses-sdk>`), the `pom.xml` (platform jar
  `android-<targetSdk>` + `d8 --min-api <minSdk>`) and the standalone
  `--apk` pipeline. Defaults stay 24/34; `min > target` and non-android
  targets are refused with an honest diagnostic (R6).
  Proof: `AndroidInteropE2ETest.androidSdkOverrideThreadsToManifestPomAndReadme`
  + `CmdBuildAndroidSdkTest`.

### CI

`.github/workflows/android.yml` (manual `workflow_dispatch`) has two jobs:
`interop` (runs `AndroidInteropE2ETest` against the SDK) and `emulator-smoke`
(builds the CLI, generates the project, `mvn verify` assembles the APK, then
installs and launches it with `android-emulator-runner`). It is not wired to
run on every push.

### Pending (Phases 5+, no owner yet)

- `--aab` output (App Bundle for Play) — needs `bundletool` (not in build-tools).
  The flag is recognized and refused with an honest diagnostic (R6) instead of
  being silently ignored; the project is still generated.
- declarative icon override by metadata — **decision-pending** (the mechanism
  `kof.toml [app] icon` vs `--icon` flag is not locked).

## Restrictions and gaps (diagnosed at compile-time)

The contract is the same as the other targets: **the intent compiles on all
targets; the target that cannot realize it says so right away, with a code.**

| Code | Situation | Reason |
|--------|----------|--------|
| ~~`AND001`~~ | ~~`spawn { ... }`~~ | ✅ **closed 31/08**: ART has no virtual threads (Java 21), but the runtime falls back to **platform threads** when `Thread.startVirtualThread` does not exist — `spawn`/`await`/`cancel`/`cancelled`/`selectAny`/`awaitTimeout`/`channel`/`scheduler` compile and run (bytecode: `CompletableFuture` + `new Thread` + `LinkedBlockingQueue`; WebView's KofJS: sequential). `KofConcurrency2Test`/`AndroidInteropE2ETest` |
| `AND002` | `web.app()` / `kof.web` (embedded server) | ✅ **enforced at compile-time (17/09)** in both `kof build` and `kof check --target android` (18/09): a mobile app does not listen on a port — the target refuses with `AND002` and points to interop, never emits server code that cannot run (R6) |
| `AND003` | reflection on Kof classes via interop | *caveat, not a compile-time gate*: desugaring/R8 may strip symbols; the language has no reflection surface of its own, so there is nothing for the compiler to detect |
| `AND004` | android.jar missing from ExternalClasspath | host Activity not included (warning) |
| `SECN00x` / `GPU001` | `kof.security` / `kof.gpu` | `kof.security` **fixed 23/09** (`D-TECHDEBT-23/09` "port the stacks"): the JVM shims are JCA/`java.util` only, so `KofSecurity.supportedOn` now treats `ANDROID` like `JVM` — `crypto.sha256/sha512`, `crypto.hmacSha256`, `passwords.*`, `secrets.*`, cookies, JWT all compile clean, `Main.class` byte-identical to JVM (`KofSecurityTest.androidSecurityCompilesByteIdenticalToJvm`). `kof.gpu` **fixed 24/09** (§278): Android compiles like the JVM (`Main.class` byte-identical) and the runtime is the FFM-free `JvmVkStubRuntime` (`available()=false` + CPU fallback), front unchanged. `kof.db`/`kof.orm` were over-gated and were **fixed 20/09** (D-DB-GAPS DB-2) — `known-bugs.md` §278 FIXED |
| `SAM001` | lambda arity ≠ SAM method | external interface requires N args |
| `SUP001` | `super.method()` in Native | already covered; ANDROID reuses the JVM path |

Supported in the compiler: **constructor** overloading (dispatch by arity),
`super.method()` **inside lambdas** via `$outer` capture + bridge method
`kof_super$*` on the owning class, `Class.class` values and enum
(`@Anno(Pkg.Enum.CONST)`) resolved by the classpath. Still missing:
overloading of common methods, checking of external generics.

Bytecode: JvmBackend emits a modern level; `d8` does the desugaring for old
devices. If Phase 2 needs a lower level, the flag becomes a backend
parameter — not a second backend.

## Integration with ongoing work

- **ExternalClasspath** (Gradle → `.jar`/`.aar`): source of the signatures
  for exact INVOKESPECIAL in `super.method()` against `android.*`;
  `CP002` warnings for unreadable entries.
- **Annotations**: `@Override`/`@NonNull`/androidx already emitted with
  correct retention; Android frameworks that read metadata at runtime keep
  working.
- **kof.ui**: no new widget; the WebView host replaces the desktop
  WebKitGTK. `Palette`/`Theme` remain identical.

## Summary roadmap

1. `Target.ANDROID` in the enum + CLI dispatch (`--target android`) with the
   `AND*` validations before emission (reuse of JvmBackend).
2. Maven project generator (pom.xml, zero Gradle) + manifest + assets
   (`AndroidProjectWriter`).
3. Host Activity + WebView ↔ `kof.ui` handles bridge (reuse runtime.mjs).
4. E2E: build → assembleDebug in CI with emulator smoke test.
5. Phase 2: standalone aapt2/d8/apksigner.

## Next step

Backend philosophy comparison:
[KOFJS.md](KOFJS.md) · Java/Android interop:
[../../learn/21-java-interoperability.md](../../learn/21-java-interoperability.md)
