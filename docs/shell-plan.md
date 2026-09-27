[English](shell-plan.md) | [Português](shell-plan.pt_BR.md)

# `kof.shell` — idiomatic shell over `kof.process` (design plan · Stage 2 · TIER 2.2)

last: 2.2.3-runwith
doing: closed
next: pipeline-js-native
location: shell-plan
state: done
intent: idiomatic-shell-over-process
constraint: no-grammar-change
decision: function-form

> **CONCLUDED 19/09 — v1 LANDED (18/09 `34e4344f`) + 2.2.3 `runWith` 19/09 (`ShellE2ETest` 15/15); queue closed.** Residual: JS/Native live-pipe `pipeline` = platform item on `IMPLEMENTATION-UNIVERSAL-PLATFORM` row 2.2, not a slice here. `cmd`/`run`/`ok` real on JVM+JS (byte-parity across the 5 `ShellE2ETest` cases), `pipeline` real on JVM; JS/Native `pipeline` = honest `PROC001` at compile time (never a raw `ReferenceError` — §235). Maintainer poll: function form ✓, builtin `KofShell.java` ✓, glob/`~`/redirection OUT of v1. §2 rewritten to what the parser accepts today (Kof has no named arguments — the old draft used `cwd:`).

## 1. Objective

One typed, composable idiom for driving OS commands — run, capture, pipeline, gate on exit code — without ever stringifying a command into `sh -c`. `kof.process` already gives the primitives (§4); `kof.shell` is sugar over it (adds no new runtime primitive where `process` covers the call, so it inherits `process` behavior).

## 2. Surface (function form, NOT shell-infix)

Measured: Kof's `Lexer`/`Parser` have no backtick/`$()`/`|`/`&&`/`||`/`>` and adding them is a grammar change (rule 6). So `kof.shell` is a plain function API.

```kof
cmd(String program, List<String> args) -> List<String>   // argv builder: [program] + args, zero parsing
run(String, List<String>) -> Result                       // Result reused from kof.process
run(List<String>) -> Result                               // argv overload
pipeline(List<List<String>>) -> Result                    // last stage's Result carries the chain outcome
ok(Result) -> Bool                                         // exitCode == 0
runWith(argv, cwd, env)                                    // 2.2.3 overload (Kof has no named args)

import kof.shell
var r   = shell.run("git", listOf("status", "--short"))
if (shell.ok(r)) println(r.stdout)
var out = shell.pipeline(listOf(shell.cmd("ls", listOf("-1")), shell.cmd("wc", listOf("-l")))).stdout
var x   = shell.runWith(shell.cmd("make", listOf("-j4")), "/src", mapOf("CC", "clang"))
```

Invariants: argv is always a `List<String>` — the command is NEVER concatenated into a shell string (the `concatenated-command-line` injection class; `shell` is the good-path idiom); `Result` IS `kof.process`'s `Result` (`stdout`/`stderr`/`exitCode`) — one type, no second shape; `shell.ok(r)` == `exitCode == 0`; no implicit glob/`~`/redirection unless a later signed-off slice designs them.

## 3. Contract

Pure lowering over `kof.process` where possible → same semantics, same binding, no new `Kof*` runtime surface on targets `process` already covers. Additive language surface only (new `kof.shell` namespace), no change to `process` behavior (rule 6: minimal, reversible). Deterministic and platform-honest: a command `shell` cannot run raises the target's existing `process` gap (`PROC001`), never silently degrades.

## 4. Per-target ABI (R7 honest scope) — measured 18/09

From `KofProcess.java` + `ExpressionProcessCallLowerer.java` (read, not inferred):

| target | `process.run` | `process.spawn` | `process.exit` | source of truth |
|--------|:---:|:---:|:---:|---|
| JVM | ✅ | ✅ | ✅ (`System.exit`) | `KofProcess.RESULT/HANDLE`, ProcessBuilder |
| JS | ✅ (`kof_platform.processRun`, node host) | ❌ `PROC001` (JS binds no `kof_process_spawn`/handle op; measured `ReferenceError`, gated honest 18/09) | ✅ (sentinel) | `js/JsRuntimeOps.java` `isRuntimeOp` lists run/exit only |
| Native | ❌ `PROC001` (compile-time) | ❌ `PROC001` (compile-time) | ✅ (syscall) | `ExpressionProcessCallLowerer` spawn/run gates |

Consequence: `run`/`exit` real on JVM+JS day one; `spawn` is JVM-only, inherited `PROC001` on both Native and JS until the platform lands a live-pipe binding there (separate item, not this plan). `shell` reports the same honest gap. *(Corrected 18/09: an earlier draft trusted the lowerer comment "JVM/JS support it"; the JS backend emits a raw `kof_process_spawn(...)` with no binding — see `DomainGapCodesTest.processSpawnOnJsIsProc001`.)*

### 4.1 Wiring map for a new builtin namespace (measured 18/09)

Every cell above is already locked by tests, so no recon test is added (`DomainGapCodesTest` pins native-run/native-spawn/js-spawn `PROC001` + JVM-spawn gap-free; `CoreRegressionE2ETest.processRun` (F4) `runBoth` runs `process.run` on JVM and JS). `KofShell.java` must register:

| touchpoint | file:line | what goes in |
|---|---|---|
| parser/typer member-call | `MemberCallNamespaces.java:90` | `shell` receiver → type the call (like `process`) |
| typer method-call (no member) | `MethodCallNamespaces.java:145` | same result types on the non-receiver path |
| bare-identifier whitelist | `SemExpressionTyper.java:90,152` | add `"shell"` so `shell` is not "unknown identifier" |
| lowering dispatch | `ExpressionMethodCallLowerer.java:242` | `shell.*` → new `ExpressionShellCallLowerer` |
| JVM runtime binding | `jvm/JvmRuntimeCallDescriptors.java`, `JvmRuntimeReturnDescriptors.java`, `JvmRuntime.java` (name list) | `kof_shell_pipeline` (the only new binding; `run`/`cmd`/`ok` lower onto existing `kof_process_run` + list/bool helpers) |
| LSP catalog | `StdCatalog.java:45` + `StdCatalogTest.java:89,194,276` | `m.put("shell", KofShell.functions())` + catalog guard |

`pipeline` cannot reuse `kof_process_spawn` handles from the IR (would need read/write/exit loops per stage); it lowers to one new JVM helper `kof_shell_pipeline(List<List<String>>) -> Result` (ProcessBuilder chain, stdout→stdin in the runtime, last stage's outcome). JS/Native hit the inherited spawn gap at compile time — the shell lowerer gates `pipeline` to `PROC001` there exactly like `process.spawn`, never emitting a call that would `ReferenceError` (the §235 lesson).

## 5. Step queue (owner: lane `.18`)

- **2.2.0 recon [0 code]** ✅ 18/09 — §4 parity table measured; every cell already locked by existing tests (§4.1), so no duplicate pin.
- **2.2.1 design sign-off [rule 6]** ✅ 18/09 — maintainer poll: Q1 function form ✓, Q2 builtin `KofShell.java` ✓, Q3 glob/`~`/redir out ✓. §2 surface adopted (positional/overload; `shell.ok(r)` namespace fn because process `Result` carries no methods — earlier `r.ok()`/`cwd:` examples do not parse).
- **2.2.2 MVP [JVM+JS `run`, JVM-only `pipeline`]** ✅ — `cmd`+`run` (both overloads through `kof_process_run`)+`ok`+`pipeline` (new `kof_shell_pipeline`); `ShellE2ETest` golden against dependency-free `wc`/`tr`, asserting argv-as-list (no `sh -c`); js/native-pipeline `PROC001` pins; JS run parity via `runBoth`. `cwd/env` (`runWith`) moved to 2.2.3 (needs new runtime bindings).
- **2.2.3 parity + add-ons** ✅ 19/09 — `runWith(argv, cwd, env)` on JVM + JS host binding (additive env, `""` cwd inherits, honest `-1` Results for spawn errors and empty argv; `ShellE2ETest` 15/15 with byte-parity + Native `PROC001` pin). JS `pipeline` only if a JS live-pipe `process.spawn` binding lands (separate platform item); Native stays `PROC001` until the native-lane `process.run` ships.
- **2.2.4 docs** — idiom doc `docs/stdlib/shell.md` (+PT), `backend-parity` row, flip `IMPLEMENTATION-UNIVERSAL-PLATFORM` 2.2 `🟡 → ✅`; promote this file per the folder rule.

## 6. Open questions

All three ANSWERED 18/09 by the maintainer poll (recreate via the same multi-choice if revisited — rule 6): Q1 function form (not shell-infix backtick/`|`, a rule-6 grammar change); Q2 compiler builtin `KofShell.java` like `KofProcess` (not a Kof-level stdlib package); Q3 glob/`~`/redirection OUT of v1 (a later signed-off slice may revisit).

## 7. What NOT to do

No `sh -c` string execution / command-string concatenation (injection class). No grammar change (backtick/pipe/redir) without a signed-off rule-6 decision. No parallel process runtime — reuse `kof.process` and its gap codes verbatim. No code before 2.2.1 sign-off; no claiming Native support `process` does not have.
