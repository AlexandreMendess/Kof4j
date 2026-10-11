[English](qrcode-wasm-plan.md) | [Português](qrcode-wasm-plan.pt_BR.md)

# Kof — incremental implementation of `kofqrcode` + `KofWasm`

**Owner:** `192.168.15.117:0` (lane nativa/GC — ONE plan, ONE owner per `D-PLAN-ONE-OWNER`).
> **Landed 11/10 — Phase 1 slice 1: the pure-Kof QR writer (lane nativa/GC `192.168.15.117:0`).** `libs/qrcode/Qrcode.kf` (matrix: ISO/IEC 18004 byte mode over UTF-8, versions 1-10, ECC level M, automatic version selection and automatic mask by the spec's penalty scoring) + `libs/qrcode/QrPng.kf` (grayscale PNG with the 4-module quiet zone, stored-deflate zlib stream, CRC-32 and Adler-32 written through `File.writeBytes`). **Proof (executed, red-first):** the module matrices are byte-identical to the Python `qrcode` module — an independent implementation of the same spec — across an 80-case battery spanning versions 1-10 (ASCII/URL/Unicode/empty/full-capacity; measured 20 + 60 randomized, 0 fails); every PNG decodes back to its text under the zbar reader via ctypes (ASCII payloads byte-exact; the payload bytes of the non-ASCII ones are proven by the parity battery, the local zbar build applies its own charset guess there); `QrCodeWriterE2ETest` **8/8** — JVM parity + zbar round-trip + Script + native x86-64 + JS + cross riscv64/aarch64 (all matrix outputs byte-identical) + oversized-input refusal (`QRCODE:`); `FileLibraryE2ETest` 13/13 (the `File.writeBytes` neighbor). First measured red: the import failed before the library existed (`PKG006`); three real bugs were caught and fixed against the reference during the port (the RS data-interleave loop stopped one byte short on the two-group versions v8/v9/v10; the data bits were read LSB-first instead of MSB-first; the N4 penalty used a pre-floored percentage instead of the exact one). **How to finish Phase 1:** next slices = the file reader (decode QR symbols from an image) → the camera reader (`kof.process`/FFI). **Phase 2 (frontend abstractions) and Phase 3 (`KofWasm` target) remain NOT STARTED — Phase 3 is a rule-6 maintainer decision (new `Target`), never this unit's scope.** The original plan text follows.

> **State (11/10): UNDER DEVELOPMENT — promoted out of `future/` by `D-FUTURE-PROMOTION` (resumed per `D-FUTURE-UNFREEZE`, maintainer 11/10; lane nativa/GC `192.168.15.117:0`).** Phase 1 slice 1 = the pure-Kof QR writer: `libs/qrcode/Qrcode.kf` (ISO/IEC 18004 byte mode, versions 1–10, ECC M, auto version, spec mask penalty scoring) + PNG output over the image writers' stored-deflate path; proven by an independent decoder (zbar via ctypes, `libz.so` measured on the lane host) round-tripping the generated PNG and by matrix parity against the python `qrcode` module. **How to finish Phase 1:** next slices = file reader (ZXing-shape decode in pure Kof or an honest JVM-only interop face) → camera reader (`kof.process`/FFI) → the writer/reader test battery of the §"kofqrcode tests" section. **Phase 2 (frontend abstractions) and Phase 3 (`KofWasm` target) remain NOT STARTED — Phase 3 is a rule-6 maintainer decision (new `Target`), never this unit's scope.** The former wording follows for history: Not an execution queue
> (three-states rule + R12). Reality-check notes before promotion: (a)
> `kofqrcode` is a heavy domain → **official package** (R1 + gate
> `scripts/check_stdlib_boundary.sh`), decoder via interop (ZXing on JVM —
> R9), camera via `kof.process`/FFI; (b) `KofWasm` = **new target** →
> touches the `Target` enum, the CLI and the dispatch = a design decision
> by the maintainer (rule 6), and the `app { route ... }` in the example is
> pseudocode (current `kof.ui` declares intent, not HTML — rule 9); (c) the
> examples use `let`/`print` (fake idioms —
> `training/anti-patterns/fake-idioms.md`); real syntax is `var`/`val`/`println`.

I want to evolve the Kof project with **two independent features**,
strictly following the existing architecture and breaking absolutely
nothing that already works:

1. `kofqrcode` — QR Code reader + writer
2. `KofWasm` — new frontend target based on WebAssembly, coexisting with `KofJS`

## ABSOLUTE RULE

### DO NOT BREAK THE EXISTING BASE

The implementation must be **incremental, isolated and compatible with
the project's current state**.

Before writing code:

* analyze the whole relevant architecture;
* understand the compiler;
* understand the target system;
* understand the current KofJS;
* understand Kof4J/KofNative/KofScript;
* understand the module system;
* understand frontend APIs;
* understand the tests;
* understand the CLI;
* understand the LSP;
* understand the documentation;
* run the existing tests.

No broad architectural refactor without need.

Do not change existing APIs without reason.

Do not remove features.

Do not replace existing implementations just because a "better" approach
exists.

Do not create gigantic abstractions in advance.

**Preserve the existing base and evolve it only where necessary.**

---

# PART 1 — `kofqrcode`

Create QR Code support in the Kof ecosystem.

The feature must have two sides:

```text
kofqrcode
├── reader
│   ├── file/image
│   └── live camera
└── writer
    └── text → QR Code
```

## Reader — file

Allow something conceptually like:

```kof
import kof.qrcode.*

let result = QRCode.read("qrcode.png")

print(result.text)
```

The final API must follow the REAL Kof patterns, so **do not copy this
example blindly**.

The reader must:

* receive a file path;
* load the image;
* detect the QR Code;
* decode the content;
* return a strongly-typed result;
* handle a missing file;
* handle an invalid image;
* handle a missing QR Code;
* handle an invalid/corrupted QR Code;
* provide clear errors.

Consider a shape like:

```text
QRCodeResult
├── text
├── format
└── rawBytes
```

Only add fields that are actually needed.

---

# Reader — live camera

Add reading through the camera when the target/platform allows it.

Conceptually:

```kof
let reader = QRCode.camera()

reader.start(frame -> {
    if frame.hasCode() {
        print(frame.text)
    }
})
```

Again, this is only an ergonomics reference.

The real implementation must respect the Kof architecture.

Consider:

* camera opening;
* permissions;
* frame capture;
* frame processing;
* detection;
* callbacks/events;
* start/stop;
* resource release;
* unavailable camera;
* concurrency;
* avoid excessive processing;
* do not block the main thread unnecessarily.

If Kof has async/events/threads/streams infrastructure, reuse it.

**Do not implement a naive infinite loop.**

If a given target lacks adequate camera support, document the limitation
instead of creating a fake implementation (R6 — gap `XXX00x`, never
silence).

---

# Writer

Allow generating a QR Code from text.

Conceptually:

```kof
QRCode.write(
    "https://koflang.dev",
    "qrcode.png"
)
```

The writer must:

* receive text;
* generate the QR Code;
* allow saving as an image;
* handle write errors;
* support Unicode;
* work consistently with the reader.

Do not create a huge configuration API initially.

Leave room for future evolution such as:

* size;
* margin;
* format;
* error correction level.

---

# QR Code dependencies

Do not implement a QR encoder/decoder from scratch if a mature, adequate
library exists.

Evaluate existing libraries.

For the JVM, consider consolidated libraries like ZXing if they are
compatible with:

* architecture;
* license;
* size;
* performance;
* targets.

For Native/JS/WASM, evaluate adequate solutions separately.

Do not add huge dependencies without need.

Every new dependency needs a justification.

---

# `kofqrcode` tests

Create tests for:

### Writer

* simple text;
* URL;
* Unicode;
* output file;
* write errors.

### Reader

* valid QR Code;
* QR Code generated by the writer;
* missing file;
* invalid image;
* image without QR Code;
* Unicode;
* URL.

Main integration test:

```text
text
 ↓
QRCode writer
 ↓
file
 ↓
QRCode reader
 ↓
original text
```

The result must be identical to the original text.

---

# PART 2 — `KofWasm`

Add a new target:

```text
wasm
```

as an alternative to:

```text
js
```

The goal is to let Kof be used in the frontend through **JavaScript or
WebAssembly**, keeping the same application source code.

Desired architecture:

```text
                         Kof Source
                             │
                             ▼
                    ┌─────────────────┐
                    │ Kof Frontend API│
                    └────────┬────────┘
                             │
                  ┌──────────┴──────────┐
                  ▼                     ▼
              KofJS                 KofWasm
                  │                     │
                  ▼                     ▼
             JavaScript               WASM
                  │                     │
                  └──────────┬──────────┘
                             ▼
                         Browser
```

## FUNDAMENTAL PRINCIPLE

### The application code must NOT change between JS and WASM.

Example:

```kof
import kof.web.*

app {
    route("/") {
        page {
            title("Kof")
            text("Hello World")
        }
    }
}
```

The same code must be compilable with:

```bash
kof build --target js
```

and:

```bash
kof build --target wasm
```

Without creating:

```kof
if target == js
```

Without duplicating the application.

Without creating:

```text
MyAppJS
MyAppWasm
```

The difference must live in the backend/runtime/interop, not in the
application logic.

---

# Frontend architecture

Before implementing `KofWasm`, carefully study the existing `KofJS`.

Identify which APIs are:

* language;
* frontend;
* DOM;
* browser;
* runtime;
* JavaScript interop;
* compiler;
* backend.

If needed, extract ONLY the abstractions that really need to be shared.

The goal is to progress towards something like:

```text
kof.web
│
├── common API
│   ├── DOM
│   ├── Events
│   ├── HTTP
│   ├── Storage
│   └── Browser APIs
│
├── KofJS implementation
│
└── KofWasm implementation
```

The common API must be backend-independent.

---

# KofJS cannot be broken

`KofJS` already exists.

Do not replace it.

Do not rewrite it.

Do not migrate everything to WASM.

Do not remove JS support.

`KofWasm` must be added next to it.

The final result must be:

```text
Kof Frontend
├── JS
└── WASM
```

---

# WASM ↔ JavaScript interoperability

WebAssembly has no magic access to the DOM.

Therefore, design the communication correctly:

```text
Kof/WASM
      ↕
JavaScript bridge
      ↕
Browser APIs
```

Evaluate carefully:

* DOM;
* events;
* fetch;
* Web APIs;
* console;
* storage;
* timers;
* callbacks;
* strings;
* arrays;
* objects;
* lifecycle;
* memory;
* JS ↔ WASM communication.

Do not hide real platform limitations.

If a given API depends on JavaScript, encapsulate it in an interop layer.

---

# Target system

The compiler must recognize:

```bash
kof build --target js
kof build --target wasm
```

And, if it makes sense within the current CLI:

```bash
kof run --target wasm
```

or another equivalent mechanism.

Do not invent commands incompatible with the existing CLI.

Follow the current target pattern exactly.

---

# Compatibility

Document clearly:

```text
Target     Status
-------------------------
JVM        existing
Native     existing
JS         existing
WASM       new
Script     existing
```

For each frontend API, state whether it has:

```text
JS
WASM
JS + WASM
```

Do not declare support that does not exist.

---

# Same code, different targets

Create a real example:

```text
examples/frontend/
```

or follow the existing structure.

Example:

```kof
import kof.web.*

app {
    route("/") {
        page {
            title("Kof WASM")
            text("Hello from Kof")
        }
    }
}
```

Test the same file with:

```bash
kof build --target js
kof build --target wasm
```

The source code must remain exactly the same.

This requirement is fundamental.

---

# KofWasm tests

Add tests for:

* compilation to WASM;
* correct artifact generation;
* loading in the browser;
* execution;
* interop with JavaScript;
* DOM;
* events;
* HTTP when supported;
* compilation errors;
* unsupported APIs;
* the existing JS build.

Guarantee that:

```text
KofJS before
        ↓
KofWasm implementation
        ↓
KofJS after
```

keeps working.

---

# Compiler integration

Do not create a parallel compiler.

The ideal is:

```text
Kof Parser
    ↓
Kof AST / IR
    ↓
    ├── JVM Backend
    ├── Native Backend
    ├── JS Backend
    └── WASM Backend
```

If the current architecture is not exactly like this, adapt to the
existing architecture.

The principle is to avoid duplicating the language.

`KofWasm` must be another backend/target, not another language.

---

# LSP

Check whether the new target requires LSP changes.

Kof code must keep:

* syntax highlighting;
* diagnostics;
* completion;
* hover;
* symbol resolution.

Do not create a different language for WASM.

---

# Documentation

Update the existing documentation.

Document:

## `kofqrcode`

* reader;
* writer;
* file;
* camera;
* generation;
* error handling;
* targets;
* limitations.

## `KofWasm`

* what it is;
* why it exists next to KofJS;
* how to select the target;
* compatibility;
* JavaScript interop;
* available APIs;
* limitations;
* examples.

Show explicitly:

```bash
kof build --target js
```

versus:

```bash
kof build --target wasm
```

and explain that **the application source code remains the same**.

---

# Implementation order

Do not try to implement everything at once.

Do it in phases.

## Phase 0 — analysis

* study the architecture;
* run the tests;
* study the targets;
* study KofJS;
* identify extension points.

## Phase 1 — `kofqrcode`

First:

```text
writer
 ↓
file reader
 ↓
tests
 ↓
integration
```

Then:

```text
camera reader
```

## Phase 2 — frontend abstractions

Before WASM:

* identify shareable APIs;
* reduce the frontend's coupling to JS;
* extract only the necessary abstractions.

Do not rewrite all of KofJS.

## Phase 3 — `KofWasm`

Implement:

```text
target
 ↓
backend
 ↓
runtime/bridge
 ↓
browser
```

Start with the smallest functional set possible.

## Phase 4 — common frontend

Validate that the same Kof code works with:

```text
--target js
--target wasm
```

## Phase 5 — stabilization

* tests;
* integration;
* documentation;
* performance;
* errors;
* regressions.

---

# Acceptance criteria

The implementation may only be considered complete when:

### `kofqrcode`

* [ ] writer functional
* [ ] file reader functional
* [ ] camera reader functional where supported
* [ ] writer → reader working
* [ ] errors handled
* [ ] tests added
* [ ] documentation updated

### `KofWasm`

* [ ] `wasm` target recognized
* [ ] backend functional
* [ ] WASM artifact generated
* [ ] execution in the browser
* [ ] JavaScript interop functional when needed
* [ ] common frontend APIs working
* [ ] same code working on JS
* [ ] same code working on WASM
* [ ] KofJS preserved
* [ ] tests added
* [ ] documentation updated

### Whole project

* [ ] existing build keeps working
* [ ] existing tests keep passing
* [ ] golden tests keep passing
* [ ] CLI keeps working
* [ ] LSP keeps working
* [ ] no unnecessary breaking change
* [ ] no unnecessary dependency
* [ ] no out-of-scope refactor
* [ ] no existing feature removed

---

# Final rule

Before each important change, ask:

> "Is this necessary to implement the feature, or am I taking the chance
> to refactor the project?"

If it is not necessary, **do not do it**.

The priority is:

```text
stability
    ↓
compatibility
    ↓
consistent architecture
    ↓
tests
    ↓
functionality
    ↓
optimization
```

Do not sacrifice the existing base to speed up the implementation.

The expected result is not just "it works".

It is:

**Kof remains Kof, only now it has QR Code and a real WebAssembly
backend.**
