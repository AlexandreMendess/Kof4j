[English](37-kofjs.md) | [Português](37-kofjs.pt_BR.md)

# 37 — KofJS: the Web path

> **Kof 0.5.0-beta — targets jvm/native/native.risc/native.arm/js/kofc — `intention->Kof->frontend->IR->backend->runtime`**

KofJS is Kof's `js` target: the same language, the same frontend and the
same Kof IR generating **ES Modules (ECMAScript 2022+)** — without Node.js,
without an external runtime. With it comes the UI platform (`kof.ui`) that
renders in a native webview (WebKitGTK) or in the browser.

## The idea in one sentence

One language, six targets: `kof build --target=jvm|native|native.risc|native.arm|js` + `kof c`/`kof script`. What changes is
the backend; the Kof code is the same (`intention->Kof->frontend->IR->backend->runtime`).

```text
                    Kof Source
                         │
                         ▼
                 ┌──────────────┐
                 │ Kof Frontend │
                 └──────┬───────┘
                        │
                        ▼
                    Kof IR
                        │
          ┌─────────────┼─────────────┐
          │             │             │
          ▼             ▼             ▼
        JVM           Native        KofJS
          │             │             │
       .class          ELF           .mjs
```

## Roadmap

1. **[Language fundamentals](03-language-basics.md)** — any Kof program
   compiles to JS; start here.
2. **[Functions](06-functions.md) and [Lambdas](16-lambdas.md)** — lambdas
   compile to synthetic classes with `invoke()`; **captures** (read-only
   snapshot of the value) work on all three targets.
3. **[Classes](07-classes-and-objects.md)** — static fields are the
   global state of a KofJS application (the pattern for UI counters).
4. **Running JS**:
   ```bash
   kof build src --target=js            # generates Default.mjs + kof-runtime.mjs
   kof run src --target=js              # runs on the embedded engine (GraalJS)
   ```
   The JS target does not need Node: Kof itself runs the module. Programs
   with a `kof.ui` window open the **native webview** (`bin/kof-webview`).
5. **[kof.ui — the UI platform](35-kof-ui.md)** — `Window`, `Label`,
   `Button` (with actions), `Input`, `Column`/`Row`, `View`+`Style`.
6. **Deploy** — `kof build --target=js` generates `index.html` + modules: serve the
   folder as a static web application (any HTTP server).

## What works today (actual state)

Backend **alpha**. KofJS generates ES Modules run on Kof's embedded GraalJS
— without Node.js; `kof.http` comes through interop with the `Java HttpClient`.

| Area | State |
|------|--------|
| Complete language (classes, inheritance, generics, exceptions, List, JSON) | ✅ |
| Lambdas with captures | ✅ (3 targets) |
| `spawn`/`await`/`channel<T>()` (concurrency) | ✅ real (async/await/Promise, CONC003 closed 03/09) |
| `kof http` client (get/post/put/delete/patch/options + timeout/retry/circuit) | ✅ (interop Java HttpClient) |
| `kof.ui`: colors, themes, widgets, layout, styling, events | ✅ (JS render) |
| Router (`Router.route/go/replace/back/forward/...`) | ✅ (real JS; 31/08) |
| Native webview `bin/kof-webview` (embedded WebKitGTK) | ✅ Linux |
| `kof run --target=js` (embedded GraalJS) | ✅ |
| `kof build --target=js` + `index.html` (static deploy) | ✅ |
| `kof.db` | ✅ untyped (16/09, GraalJS-host bridge); `query<T>` typed = `DB002` |
| file io in the browser | ~ (real io only in the embedded runner; the browser falls back to a clear error) |

## Example application: counter

```kof
class App {
    static Int count = 0
}

main() {
    var w = Window("Contador")
    var label = Label("contagem: 0")
    w.bind(label)
    w.bind(Button("+1", () -> {
        App.count = App.count + 1
        label.text = "contagem: " + App.count
    }))
    w.show()
}
```

```bash
kof run contador.kf --target=js
```

The window opens with real WebKit; each click updates the label live;
**closing the window terminates the program**.

## Limitations and gaps

- **JVM/Native**: the `kof.ui` handles are no-ops (rendering is KofJS).
- **Browser**: file io throws a clear error (`kof_platform` only exists in the
  embedded runner); `print` goes to the browser console.
- **Captures** are snapshots: for mutable state use static fields.
- **Statics on Native**: not supported (no-op).

## References

- `docs/targets/KOFJS.md` — JS backend architecture
- `docs/status.md` — project state (kof.ui section)
- `native/webview/kof-webview.c` — headerless WebKitGTK shell
- Tests: `UiE2ETest`, `WindowE2ETest`, `KofJsE2ETest`, `BackendParityTest`
