[English](actual-state.md) | [Português](actual-state.pt_BR.md)

# Kof Project State — HISTORICAL SNAPSHOT (02/09, 0.2.6-beta)

> ⚠️ **HISTORICAL RECORD.** This doc freezes the state on 02/09 (0.2.6-beta);
> the "What is NOT implemented" list is largely resolved in
> 0.3.x (see the real proof). For the **CURRENT** state: `docs/status.md`
> (suite gate), `docs/backend-parity.md` (matrix) and
> `docs/bugs-and-gaps/known-bugs.md` (bug queue). Moved from
> `docs/development/` to `docs/` on 11/09 (classification rule: a snapshot
> with no pending work is not a living backlog).

**Last updated:** September 2, 2026
**Version:** 0.2.6-beta

---

## Executive Summary

Kof is a compiled language for multiple targets (JVM, Native, Web, Script).

The project has a **complete frontend** (lexer + parser + AST + symbol table + semantic + type checking), a **backend-agnostic IR** and **seven targets**: JVM (bytecode via ASM), Native (ELF x86-64, syscalls, no mandatory libc), Native riscv64/aarch64 (toolchain + qemu), KofJS (ES Modules on the embedded GraalJS engine), KofScript (direct execution via the IR — `KofInterpreter`), KofC (C subset → ELF) and Android (Phase 1).

**Phases C, D, E COMPLETED**: Type System, generalized IR, NativeBackend ELF.

**Phase F COMPLETED**: String model, Array model, Inheritance, Virtual Dispatch, Interfaces, Exceptions, Memory (mmap, no GC).

**Pipeline 0.0.5 COMPLETED**: JSON parity JVM/Native, real exceptions on the JVM, function syntax without `fun`, serve/LSP/check/install/info, official distribution.

**Platform 0.0.7-0.1.0 (25/08)**: kof.ui (widgets + native webview via KofJS), kof.db (idiomatic JDBC JVM + native SQLite via .so + MySQL wire protocol with `kof_db_mysql_scramble`), kof.orm (declarative `entity` + CRUD/where/migrate + MongoDB), structured logging (JSON, correlation ID), complete JSON (Float/Double, arrays), String→numeric conversions, ARITH001, UTF-8 BOM, generics `Box<T>` with fixed primitive `T` (`NativeE2ETest` 50/50; `substituteTypeVariable` `CompilerDriver.java:3972`), `SEM025` with no false positive in `hashCode/equals/toString`.

**0.2.6-beta (27/08)**: Separate targets `native`/`native.risc`/`native.arm` (`Target.java:1`); riscv64 toolchain `riscv64-linux-gnu-as` + `.option arch,rv64g` + `li a7 214/64/93`; Native free-list (`kof_free_head`) + `kof_gc_collect`; pattern matching `switch case String s` + record destructuring `Point(x,y)` on JVM/Native/JS; basic `String?` null safety; `KofScript` top-level `let` → `KofScriptGlobals`; `KofCcompiler` (`kof c`) C subset → ELF x86_64; `kof.http` JVM+JS (GraalJS `Java HttpClient`); `List map/filter/reduce`; bugs: large-project `import a.b.C` (`CompilerDriver.java:243`), `List.get`/`listOf`, Windows SIGPIPE; VERSION 0.2.6-beta; `mvn test` 810 (793+8+5+4), golden 16/16, integration 9/9.

**0.2.6-beta (30-31/08)**: Real Native `spawn`/`await` (`pthread_create` + trampoline + `pthread_join` + thread-safe allocator with futex — CONC001 closed); real FP in XMM (`vcvtsi2sd`/`mulsd`, dtoa via `snprintf` — FLT001); JSON objects/records + arrays on Native (Int/Long/Bool/String/Double — JSN001/JSN002/JSN003); native SQLite via direct `.so`; MySQL wire protocol in progress (SHA-1 auth scramble + `user:pass@` parse); JVM `WebSocket` (`app.ws`, handshake RFC 6455 + frame codec with mask) and `SSE` (`sse.send/event/close`) via `kof.web`; `kof.http` retry/circuit breaker JVM+JS (`KOF_HTTP_RETRIES`/`KOF_HTTP_TRIPS`/`KOF_HTTP_FAILURES`/`KOF_HTTP_OPEN_UNTIL`, 30s window, fail-fast); `kof.cache` fixed on Native (register clobber `%rax`/`%rdi`); UI Phase 7 Router (`go/replace/back/forward/param/current/depth` — real on JS, no-op on JVM); `KofRuntime.close` + ws descriptors; `kof fmt` and `kof config gen` implemented; release pipeline with 2 jobs (`test-and-bump` → `package-and-release` with version sanity) × 3 platforms.

---

## Build Status

| Check | Result |
|-------------|-----------|
| `mvn clean package` | ✅ PASSES |
| `mvn test` | ✅ PASSES (810 tests: 793 kof-compiler +8 kof-script +5 kof-c-compiler +4 kof-cli, 0 failures); `NativeE2ETest` 50/50, `JvmE2ETest` 29/29, `KofJsE2ETest` 35/35, `KofCCompilerTest` 5/5, `KofHttpE2ETest` 4/4, `KofCacheE2ETest` 5/5 (x3 targets), `KofWebWsE2ETest` 11/11, `KofWebSseE2ETest` 7/7 |
| `kof build` | ✅ PASSES (`--target jvm|native|native.risc|native.arm|js` [--release]; `android` in Phase 1) |
| `kof run` | ✅ PASSES (jvm|native|native.risc|native.arm|js) [--release] |
| `kof serve` | ✅ PASSES (native web.app() + legacy handle() API) |
| `kof check` | ✅ PASSES |
| `kof test` | ✅ PASSES (`test "name" { }` suite on the 3 targets) |
| `kof bench` | ✅ PASSES (harness: compile, run, validate, metrics, baseline) |
| `kof debug` | ✅ PASSES (DAP MVP on the JVM) |
| `kof info` | ✅ PASSES |
| `kof lsp` | ✅ PASSES (hover/completion + real diagnostics) |
| `kof install` | ✅ PASSES |
| `kof c` | ✅ PASSES (KofCcompiler native-only C subset → ELF x86_64 via `kof_c`) |
| `kof script` | ✅ PASSES (pure Kof: top-level statements → `main()`, `var`/`val` → `KofScriptGlobals`; direct execution by the `KofInterpreter` on the IR — no fork; repl, --watch; Windows SIGPIPE fix) |
| `tests/run-golden.sh` | ✅ 16/16 (8 cases × jvm+native) |
| `tests/run-integration.sh` | ✅ 9/9 (CLI + serve + kof test) |
| `scripts/package.sh` | ✅ PASSES (dist layout + tar.gz/zip + SHA256SUMS + jars) |

---

## What WORKS end to end

### Function syntax (without `fun`)

```kf
main() { ... }                       // entry point, implicit void
String saudacao() { ... }            // return before the name
despedida(): String { ... }          // return after the parameters
void fazIsso() { ... }               // explicit void
Bool positivo(Int x) = x > 0         // expression body
int dobro(int x) { ... }             // primitives in any case
```

### Records

```kf
record Point(Int x, Int y)
main() {
    var p = Point(3, 7)
    println(p)                       // Point[x=3, y=7] (toString on the JVM)
    println(p.x() == q.x())
}
```

Generates a valid `.class` (constructor, accessors, toString/equals/hashCode on the JVM) and an ELF x86-64 binary on Native.

### Classes

```kf
class User(String name, Int age) {
    greeting(): String {
        return "Hello " + name
    }
}
```

The record-style constructor (`class X(...)`) generates a **record** — components,
constructor and accessors (`u.name()`); the read `u.name` also becomes an accessor;
the **write** `u.name = "x"` does NOT (immutable, verified 02/09). For mutable
state, use explicit fields + `constructor(...)`. `User(...)` and
`new User(...)` are equivalent (`new` is backward-compatible). Field
initializers run in all constructors (JVM, Native, KofJS). Inheritance, virtual
dispatch and interfaces work.

### JSON

```kf
json.encode(42)                      // "42"
json.encode(user)                    // {"name":"Mel","age":30} (JVM: objects/records)
json.encode(listOf(1, 2, 3))         // [1,2,3]
var u = json.decode<User>("{\"name\": \"Ana\", \"age\": 25}")
var l = json.decode<List<Int>>("[1, 2, 3]")
```

JVM + Native parity for int/long/bool/string/list/array. Objects/records: JVM
(reflection) + Native (compile-time composition, 31/08 — JSN002). Arrays of
`Float`/`Double` on Native: 31/08 (JSN001).

### Exceptions (JVM — real)

```kf
try {
    throw "boom"
} catch (String e) {
    println("caught: " + e)
} finally {
    println("finally")
}
```

Real exception table + StackMapTable. `throw "msg"` wraps in RuntimeException; `catch (String e)` unwraps. `finally` runs on all paths (normal, caught, propagated). Native: real unwinding through the frame chain (`kof_throw_string`) — try/catch/finally on the 3 targets.

### HTTP (`kof serve`)

```kf
handle(String method, String path, String body): String {
    if (path == "/hello") {
        return "{\"msg\": \"hi\"}"
    }
    return "{\"msg\": \"not found\"}"
}
```

Top-level (static) handlers, automatic Content-Type, `--port`/`--host`, graceful shutdown.

### Modern HTTP (`web.app()` — native web stack)

```kf
var app = web.app()
app.get("/users/:id") {
    var user = User(param("id"))
    json.encode(user)
}
app.listen(8080)
```

Routes with path params (`:id`), query, headers, middleware `app.use { }`,
automatic Content-Type (JSON), 404/500, concurrency with virtual threads,
`status(201, body)`/`headerSet("X","y")` (27/08), **WebSocket** `app.ws("/chat") { }`
(handshake RFC 6455 + frame codec with mask) and **SSE** `sse.send/event/close`
(30/08, JVM). `kof serve <file.kf>` detects `main()` and runs `web.app()` apps.
E2E with real sockets: `KofWebE2ETest` (9), `KofWebWsE2ETest` (11),
`KofWebSseE2ETest` (7), `KofWsFrameTest` (7). See `docs/stdlib/stdlib-web.md`.

### kof.config and kof.log

```kf
config.str("database.url", "jdbc:h2:mem:test")   // file > env > profile > default
log.info("servindo na porta 8080")               // debug/info/warn/error, levels
```

`kof.config` (typed: str/int/long/bool, env/has; Native in its own asm with
full precedence — `KOF_CONFIG` > env `KOF_<KEY>` > profile > `kof.config`)
and `kof.log` (levels, off, warn→stderr; Native in its own asm, UTC; JS
console.* with `KOF_LOG_LEVEL` — LOG001 closed 01/09) — JVM/Native/JS.
Structured logging in JSON with correlation ID on the
JVM. Tests: `KofConfigE2ETest` (8), `NativeConfigE2ETest` (8), `KofLogE2ETest`
(11, incl. JS), `NativeLogE2ETest` (7).

### kof.db and kof.orm — native persistence

```kf
entity User {
    id: Long generated
    name: String
    email: String unique
}

main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    orm.create<User>(db)
    orm.save(db, User(0, "Mel", "mel@kof.dev"))
    var u = orm.find<User>(db, 1)
}
```

- `kof.db`: idiomatic JDBC (connect/execute/query/query<T>/transaction) on the
  JVM; **native SQLite** via direct `.so` link; MySQL/MariaDB over wire
  protocol on native sockets (WIP — SHA-1 auth scramble + `user:pass@` parse,
  31/08); JS reports DB001.
- `kof.orm`: schema in the language (`entity`, compile-time), full CRUD
  (`create/save/find/all/where/delete/count`), versioned migrations
  (`orm.migrate`), constraints (`generated`, `unique`), non-numeric PK.
  **MongoDB** supported (official driver via compatible reflection). Native/JS
  report ORM001.
- Tests: `KofDbE2ETest` (9), `KofOrmE2ETest` (10, including MongoDB E2E).
- See `docs/stdlib/DATABASE_VISION.md`.

### Real usage feedback (kof-calculator-lab)

- UTF-8 BOM at the start tolerated by the Lexer (Windows editors).
- `String.toInt()/toLong()/toDouble()/toFloat()` as runtime functions.
- ARITH001: division/remainder by **constant** zero rejected at compile-time
  (integers; float/double produce Infinity/NaN and are not diagnosed).
- `--help` on the `kof run/build/serve/check` subcommands.

### kof.ui (UI platform)

`Color` (32-bit RGBA), `Theme` (light/dark), `Palette.*`, widgets
`Window`/`Label`/`Button`/`Input`/`Column`/`Row`/`View`/`Style` — **KofJS**
rendering: `kof run --target=js` opens the interactive app in the native webview
(`bin/kof-webview`, embedded WebKitGTK; ES modules over `file://` enabled
via `webkit_settings_set_allow_file_access_from_file_urls`). Button actions
via lambdas with captures; closing the window ends the program. JVM/Native:
no-op handles.

---

## What is implemented

### Type System

| Feature | Status |
|---------|--------|
| `Type.java` | ✅ PrimitiveType, ClassType, TypeVariable, ArrayType, WildcardType |
| `SymbolTable.java` | ✅ Chained scopes, resolution in hierarchy |
| `SemanticAnalyzer.java` | ✅ Methods, constructors, fields, locals, generics by erasure; 25/08 `SEM025` ignores `hashCode/equals/toString` |
| Type checking | ✅ Assignability, primitive widths, arg types; 25/08 `Box<T>` `T→Int` via `substituteTypeVariable` |

### IR Lowering

| Feature | Status |
|---------|--------|
| Records, classes, interfaces, inheritance | ✅ |
| Top-level functions (all forms) | ✅ |
| Methods, constructors, `super` | ✅ |
| `var`/`val`, `return` | ✅ |
| `if`/`else`, `while`, `for`, `do-while`, `switch`, `break`/`continue` | ✅ |
| `try`/`catch`/`finally` + `throw` | ✅ (JVM real; Native panic) |
| Binary, unary, bitwise expressions | ✅ |
| Arrays, List\<T\>, generics | ✅ (25/08 `Box<T>` primitive/Boxed `T` + native `println` `kof_int_to_string`) |
| JSON, strings (full API), `instanceof`/`as` | ✅ |

### Security (kof.security — docs/stdlib/security.md)

| Feature | Status |
|---------|--------|
| `passwords.hash/verify/needsRehash` | ✅ JVM/JS (PBKDF2-HMAC-SHA256 600k); Native ✅ PBKDF2/SHA-512/JWT/AES-GCM asm (G10 closed 25/08) |
| `crypto.sha256/sha512/hmacSha256` | ✅ JVM/Native (asm)/JS — identical values |
| `crypto.aesGcm` | ✅ JVM/Native (asm) |
| `crypto.randomHex/randomInt` | ✅ JVM (SecureRandom)/Native (getrandom)/JS |
| `jwt.create/verify` (HS256, exp/iss/aud) | ✅ JVM/Native (asm)/JS |
| `secrets.get/redact` | ✅ JVM/Native (/proc/self/environ)/JS |
| `security.constantTimeEquals` | ✅ 3 targets |
| `security.csrf*/corsAllowed/headers` | ✅ JVM |
| `auth.*` (web context Bearer JWT) | ✅ JVM |
| `security.rateLimit/session/apiKey` | ✅ 3 targets (G9 `KofSecurityG9Test` 3/3) |
| Gaps per target | ✅ Clear diagnostics SECN001/002/003/004 |
| `KofSecurityTest` | ✅ 22 tests (unit + E2E + adversarial) + `KofSecurityG9Test` 3/3 |
| `benchmarks/security/` | ✅ password-hash, jwt, hash-speed, aes-gcm |

### JVM Backend (ASM)

| Feature | Status |
|---------|--------|
| Direct V21 bytecode, COMPUTE_FRAMES | ✅ |
| Exception table + StackMapTable | ✅ |
| Records with Record attribute + toString/equals/hashCode | ✅ |
| Virtual dispatch, interfaces | ✅ |
| Erasure boxing (`kof_box`/`kof_unbox`) | ✅ |
| JSON helper `dev.kof.runtime.KofJson` (generated via javac) | ✅ |
| List = java.util.ArrayList | ✅ |

### Native Backend (x86-64)

| Feature | Status |
|---------|--------|
| Real stack machine over the IR | ✅ |
| System V AMD64 ABI, ELF via `as`+`ld` | ✅ |
| Heap via mmap (`kof_alloc`) | ✅ |
| Vtables, virtual and interface dispatch | ✅ |
| Strings, arrays, lists, JSON in assembly | ✅ |
| Network syscalls (`kof_net_*`) emitted (future API) | ✅ |

### CLI

| Feature | Status |
|---------|--------|
| `kof build` (jvm/native/js + risc/arm), `kof run` | ✅ |
| `kof serve`, `kof check`, `kof info [--json]` | ✅ |
| `kof lsp` (minimal LSP with real frontend) | ✅ |
| `kof install`, `kof version` | ✅ |
| `kof bench` (37 benchmarks, median+RSS+baseline), `kof profile` | ✅ |
| `kof inspect` (IR) | ✅ |
| `kof debug <file.kf>` (DAP + raw JDWP; breakpoints by Kof line, stack) | ✅ |
| `kof fmt` (real parser, idempotent), `kof config gen` | ✅ (31/08) |

---

## What is NOT implemented (residual 0.2.6-beta)

### Language Features
- Full null safety (`String?` basic ✅ 27/08, advanced checks planned)
- Advanced pattern matching (nested destructuring, guards — basic `case String s` + `Point(x,y)` ✅ 27/08)
- Generic annotations, Reflection

### Type System
- Full overload resolution
- Variance / advanced bounds
- Sealed types

### Backends
- KofJS — alpha (embedded GraalJS): `while(true)`, `try/finally`, `switch` pattern, `listOf map/filter/reduce`, `kof.http` via `Java HttpClient` (+ retry/circuit JVM parity, 30/08), object decode — JVM/Native/JS parity; UI via native webview; `spawn`/`await`/`channel<T>()` with real concurrency (async/await/Promise, CONC003 closed 03/09)
- KofScript — ✅ pure Kof + REPL + `--watch`; **0.3.0: direct execution by the `KofInterpreter` (IR, no fork)**; parity with JVM proven in test
- KofC — ✅ `KofCcompiler` native-only C subset (`kof c`) → ELF x86_64 (while/if/deref `&`/`*(int*)`)
- Native riscv64 — **real codegen (02/09)** — riscv64 stack machine + runtime in pure asm (raw syscalls, no C), `NativeRiscv64E2ETest 4/4` via qemu (`NATIVE002` partial); aarch64 placeholder (target separation done)

### Runtime
- Native automatic GC — free-list `kof_free_head` (`mmap` reuse, 27/08); mark-sweep GC pending; auto-GC disabled after hang (memory returned only on the `munmap` fallback)
- ~~JSON Float/Double: JSN001~~ — ✅ closed 31/08 (encode/decode/FP arrays XMM + fractional/exponent parser)
- ~~Native floating point: no real SSE (FLT001)~~ — ✅ closed: FP arithmetic is XMM; JSON FP closed in JSN001

### Security (kof.security — docs/stdlib/security.md)
- v1 + G9 implemented (3 targets).
- Pending: OAuth2/OIDC client, audit logging, JWT/passwords outside the JVM complete (SECN001/004 in progress).

### Database (docs/stdlib/DATABASE_VISION.md)
- Level 0 (connection + SQL), 2 (basic ORM) and 4 (migrations) implemented; native SQLite + MySQL handshake `kof_db_mysql_scramble` 27/08.
- Pending: level 3 typed query DSL `User.query { where ... }`, connection pooling, complete MySQL (query/prepared), kof.db/kof.orm outside the JVM (DB001/ORM001 — JS).

### Platform (gaps — docs/bugs-and-gaps/ecosystem-coverage.md §4)
- All original P0 closed in 0.1.0; 0.2.0 closes pattern matching, basic null safety, kof.http JS, free-list GC, target separation, KofScriptGlobals, KofCcompiler; 0.2.6-beta (30-31/08) closes WebSocket/SSE (JVM), `kof.cache` (3 targets), `kof.http` retry/circuit (JVM+JS) and Native `spawn` (CONC001).
- Residual: Native scheduler (SCHED001), Native `kof.http` (HTTP002), tracing, native MySQL (prepared), RISC/ARM codegen, mark-sweep GC.

### Tooling
- `kof fmt` ✅ (real parser, idempotent — 31/08); `kof init` still planned
- LSP hover/completion/rename + Native Debugger DWARF/JS source maps (P5)

---

## Architecture (0.2.6-beta — 7 targets: jvm, native x86_64, native.risc, native.arm, js, kofc, android)

```text
Source (.kf)
  ↓ Lexer
  ↓ Parser
  ↓ AST
  ↓ Symbol Resolution
  ↓ Semantic Analysis
  ↓ Type Checking
  ↓ Kof IR (backend-agnostic + KofDebugInfo)
  ↓ Optimizer
  ├── JVM Backend (ASM) → .class V21 (virtual threads, KofRuntime with web/ws/sse/cache)
  ├── Native Backend (x86-64, free-list + kof_gc_collect, pthread spawn, FP XMM)
  ├── Native riscv64 (native.risc — real codegen 02/09, pure asm + qemu)
  ├── Native aarch64 (native.arm — toolchain + x86_64 placeholder via qemu)
  ├── JS Backend (GraalJS, kof.http via HttpClient, retry/circuit)
  ├── KofC Backend (C subset → native)
  ├── KofScript (pure Kof → KofInterpreter on the IR)
  └── Android (JVM bytecode → Maven project + APK; host Activity in Kof, Phase 1)
```

| Module | State |
|--------|--------|
| kof-compiler | Functional (~10k LOC) |
| kof-cli | Functional (18 commands: build, run, serve, check, test, script, repl, c, fmt, config, bench, profile, inspect, debug, info, lsp, install, version) |
| kof-runtime | Structure created (native runtime embedded in NativeBackend; KofJson on the JVM) |

| Metric | Value (0.2.6-beta 31/08) |
|---------|--------------------------|
| JUnit Tests | 810 (793 kof-compiler +8 kof-script +5 kof-c-compiler +4 kof-cli, 0 failures) |
| E2E JVM | 29 |
| E2E Native (x86_64) | 50 |
| E2E JS (KofJS) | 35 (+ kof.http JS) |
| E2E KofScript | 8 |
| E2E KofCcompiler | 5 |
| E2E JSON | 14 + 7 (complete) |
| E2E Exceptions | 9 |
| E2E HTTP/Web | 8 + 9 (TLS 5) + http 4 (JVM+JS) + ws 11 + sse 7 + frame 7 + resilience 3 (30/08) + cache 5 (x3 targets) |
| E2E kof.io | 15 |
| E2E UI | 14 + 3 (Window) |
| E2E kof.db / kof.orm | 8 + 16 (native SQLite + MySQL scramble) |
| E2E kof.security + G9 | 22 + 3 |
| Golden | 16/16 (8 cases × jvm+native) |
| Integration | 9/9 |
| Benchmarks | 37 in 17 categories |
| Targets | jvm stable, native x86_64 stable (free-list + pthread spawn), native.risc/native.arm (toolchain + placeholder via qemu), js alpha, kofc native-only, android Phase 1 |
