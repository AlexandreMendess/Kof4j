[English](test-architecture-plan.md) | [Português](test-architecture-plan.pt_BR.md)

# 🧪 Refactoring Plan — Kof Test Architecture and Modularization

**Status:** `UNDER DEVELOPMENT` — promoted from `future/` 28/09/2026 (`D-TEST-ARCHITECTURE-GO`, `D-FUTURE-BATCH-2809`, `D-FUTURE-PROMOTION`)
**Owner:** issues/tooling lane (this session)
**Decision:** `D-TEST-ARCHITECTURE-GO` (`DECISIONS.md`) — promotion authorized "profiling → integration".
**Real state (updated 28/09/2026, post-ratchet):** the suite is thousands of
`*Test.java` files with no layers/harness; the plan is now under way. **Landed:**
Phase 1 profiling (`scripts/test-suite-profile.sh` + permanent
`docs/testing/TEST-PERFORMANCE.md`), Phase 2 discovery audit
(`scripts/test-suite-audit.sh`) and Phase 2 **ratchet** (`scripts/check_test_hygiene.sh`
over the frozen `scripts/test-hygiene-baseline.txt`, 157 keys). **Quick-win slice 1
(28/09):** removed the false-positive `Thread.sleep` key (comment-only mention in
`AsyncSleepJsE2ETest`) and the redundant post-`startServer` settle in
`KofWebHardeningTest` (the port-readiness probe already guarantees the bind).
**Quick-win slice 2 (28/09):** the duplicated JVM web readiness probe
(`while (attempt < 40)` + `Thread.sleep(100)`, copy-pasted in `KofWebE2ETest`,
`KofHttpE2ETest`, `KofHttpPoliciesE2ETest`, `KofWebStreamE2ETest`) now lives once
in `TestServerFixture.awaitListening(Process, int)` → baseline 185→182 keys (4
class keys removed, 1 helper key added). **Quick-win slice 3 (28/09):** the same
fixture absorbed the readiness loops of `KofMediaE2ETest`,
`KofOAuthResourceServerTest`, `KofWebHardeningTest`, `KofWebSseE2ETest` and
`KofWebWsE2ETest`; the redundant SSE reconnect settle was dropped; and the
`maxConnections` 503 race test was freed from its fixed sleep in favour of a
bounded poll (it was flaky: 1/3 green) → baseline 182→179 keys. **Honest
correction:** slice 1's "redundant" settle in `KofWebHardeningTest` was part of
that race's timing — the test now waits for the 503 instead of guessing. The visible cost is
feedback latency, not correctness (the reactor suite is green).
**Quick-win slice 4 (28/09):** `TestServerFixture` gained a TCP-only
`awaitPort(port, attempts, interval)` and an explicit-budget
`awaitListening(process, port, attempts, interval)`; the remaining pure readiness
loops in `KofWebNativeE2ETest` (4), `KofWebJsE2ETest` (3) and `KofBlogE2ETest` (1)
now call them instead of hand-rolled probes → baseline 179→176 keys. Fail-fast on
child death and kill-on-timeout stay inside the fixture.
**Quick-win slice 5 (29/09):** `TestServerFixture` gained
`awaitTrue(attempts, interval, condition)` — a bounded poll for counters/response
codes that treats a throwing probe as "not ready yet". `KofWebHardeningTest`
replaced its four fixed settles (`awaitStats` 20 ms, the 503 poll 50 ms, and the
SSE/WS counter decrements 1600/100 ms) with bounded polls; `KofWebWsE2ETest`
replaced its 300 ms "socket stays open" settle with a `setSoTimeout(300)` read
that must time out → baseline 176→174 keys. Both de-flake: the old settles were
guessing the app's `time.sleep(1500)` margin.
**Quick-win slice 6 (29/09):** `TestServerFixture` gained
`await(process, port, attempts, interval, probe)` — a custom readiness probe where
an `IOException` means "not ready yet" and any other exception aborts, so an
`AssertionError` inside the probe still fails the test; `awaitListening` now
delegates to it. `KofWebTlsTest` replaced its two SSL-handshake readiness loops
with `await`; `KofLogE2ETest` replaced its two readiness loops with `awaitListening`
and its two fixed `Thread.sleep` settles (300/400 ms) with bounded `awaitTrue` polls
over a now thread-safe `StringBuffer` stdout — zeroing the last two `sleep` keys of
the web/log E2E. The baseline count stays 174: the 2 removed `sleep` keys are offset
by 2 `dupname` **leads** (`assertManagedTargets`, `runCross`) that entered the frozen
set with the kof-file/multiparadigma lanes in the same window — recorded, not hidden.
**Quick-win slice 7 (29/09):** the CLI E2E readiness/teardown sleeps moved into a new
`CliAwaitFixture` (`awaitTrue`, `awaitExit`, `pause`, `kof-cli` test infra):
`ServePortTest` (2 readiness loops + the §390 orphan wait), `ServeManifestPortE2ETest`
and `FullStackE2ETest` (readiness) and `CliDebugProcessLeakTest` (§438 orphan wait) now
poll a deadline or block on `ProcessHandle.onExit()` instead of a fixed `Thread.sleep`
→ baseline 174→171 keys (4 test keys removed, 1 fixture key added).
`KofDebugJvmExceptionTest` keeps its 500 ms — an intentional "let the loop run before
pause" in the DAP flow, not a readiness settle.
**Quick-win slice 8 (29/09):** `KofTimeE2ETest#durationSchedulerAtFiresJvm` was re-measured and
reclassified — its two `Thread.sleep(150/80)` were NOT load-bearing boot timing but a scheduler
poll: both became bounded `TestServerFixture.awaitTrue` polls (wait for ≥3 fires on a 20 ms
interval; then assert no fire in the 80 ms after `cancel`), and `TickCounter.n` is now `volatile`
(read across the scheduler thread). Baseline 171→170 keys (1 file leaves the sleep set);
`KofTimeE2ETest` 44/44 (0 skip), focused test 4/4 runs.
**Phase 3 cost found (29/09):** the giant-test split is NOT a cheap increment — test class names
are cited as proof across `docs/` (e.g. `TranslateTest` in `known-bugs`, `audits/`,
`future/TRANSLATOR`), so splitting or renaming a class requires a reference sweep and risks doc
drift. This is now **measured, not guessed**: `scripts/test-suite-audit.sh --citations` counts, per
oversized class, how many files under `docs/` mention its name (`0` = split with no citation
sweep). Measured cheapest-first head: `ArrayBoundsStressTest` (2), `KofSetEqualityTest` (2),
`SemanticResolutionTest` (4), `CmdDeployTest`/`BiosBootE2ETest`/`KofInterpreterParityTest`/
`NullablePrimitiveContractE2ETest` (8) … `ConformanceMatrixTest` (42). Two `docs/bugs-and-gaps`
citations of `KofSetEqualityTest`/`ArrayBoundsStressTest` are class-level counts ("whole
`KofSetEqualityTest` 21/21"), which drift even when the cited method stays in place — so the
cheap Phase 3 rule is: **move only uncited tests out, keep cited methods and class name in the
original file, update the counts**. **First split landed (29/09):** the reusable support of
`KofSetEqualitySupport` (the four Kof sources + the JVM/JS runners) was extracted out of
`KofSetEqualityTest` — all 21 cases and the cited method stayed, so **zero citation drift** — with
oversized 43→42 and baseline 170→169. **Second split landed (29/09):** `KofMathSupport` extracted
the JVM/Native/JS runners + cross-arch qemu golden + toolchain guard out of `KofMathTest` (all 29
cases and the `conformance-matrix`/parity-cited class name stayed) → oversized 42→41, baseline
169→168. **Third split landed (29/09):** `ArrayBoundsStressSupport` extracted the JVM/JS/Native
runners, the Kof program generators and the invariant oracles out of `ArrayBoundsStressTest` (all
15 cases and the cited class name stayed) → oversized 41→40, baseline 168→167. **Fourth split
landed (29/09):** `KofMediaSupport` extracted the pure WAV/MP4 byte builders (`makeWav`/`mp4Box`/
`makeMp4`/`mp4Box64`/`makeMp4WithExtendedSizeBoxBeforeMoov`) out of `KofMediaE2ETest` (all 17 cases
and the cited class name stayed) → oversized 40→39, baseline 167→166. **Fifth split landed
(29/09):** `NullablePrimitiveContractSupport` extracted the JVM/SCRIPT/JS runners + target oracle
out of `NullablePrimitiveContractE2ETest` (all 26 cases and the cited class name stayed) →
oversized 39→38, baseline 166→165. **Sixth split landed (29/09):** `LambdaSupport` extracted the
JVM/Native/SCRIPT/JS runners out of `LambdaE2ETest` (all 36 cases and the cited class name
stayed) → oversized 38→37, baseline 165→164. **Seventh split landed (29/09):** `BiosBootSupport`
extracted the qemu/serial/build helpers out of `BiosBootE2ETest` (all 10 cases and the cited class
name stayed) → oversized 37→36, baseline 164→163. **Eighth split landed (29/09):**
`FfiStructSupport` extracted the C shim source, the host-`.so` compiler, the toolchain lookup
and the JVM/Native/JS runners out of `FfiStructE2ETest` (all 12 cases and the cited class name
stayed) → oversized 36→35, baseline 163→162. **Ninth split landed (29/09):** `ShellSupport`
extracted the JVM/JS capture harness and the parity/refusal oracles (with the inherited `@TempDir`
field) out of `ShellE2ETest` (all 21 cases and the cited class name stayed) → oversized 35→34,
baseline 162→161. **Tenth split landed (29/09):** `KofStringsSupport` extracted the JVM/Native/JS/
qemu runners, the toolchain guard and the two largest inline Kof programs (as `ALL_JVM`/
`ALL_NATIVE` constants) out of `KofStringsTest` (all 18 cases and the cited class name stayed) →
oversized 34→33, baseline 161→160. **Eleventh split landed (29/09):** `KofSwitchExprSupport`
extracted the JVM/Native/JS runners and hoisted all 32 inline Kof programs out of
`KofSwitchExprE2ETest` into named constants (all 32 cases and the cited class name stayed) →
oversized 33→32, baseline 160→159. **Twelfth split landed (29/09):** `KofInterpreterParitySupport`
(harness) + `KofInterpreterParityPrograms` (the 8 largest inline Kof programs, hoisted) out of
`KofInterpreterParityTest` (all 26 cases and the cited class name stayed) → oversized 32→31,
baseline 159→158. **Thirteenth split landed (29/09):** `UiSupport` (runners) + `UiPrograms` (11
largest inline Kof programs, hoisted) out of `UiE2ETest` (all 29 cases and the cited class name
stayed) → oversized 31→30, baseline 158→157. The metric is a
guide, not an oracle: naming candidates in this queue (and in `README`)
itself adds citations to a class, so **re-measure `--citations` before choosing the next split**.
That rule + ordering is the traced Phase 3 todo.
**How to finish:** Phase 1/2 discovery done — then **Phase 3 modularization** (re-measure
`--citations`; extract support and move only uncited tests, keeping cited methods and class names)
and remaining **Phase 2 quick-win removals** interleaved (shrink the baseline: sleeps / duplication
/ oversized) → 4 (harness) → 5 (targets) → 6 (conformance) → 7 (`mvn verify`). **Pure test
infrastructure — the compiler is never touched** (golden rule below). One slice per commit,
RED-first + `check_500`.

## 📌 Overview

The repository currently has **thousands of tests**, but they are not
organized as a test architecture. They grew along with the compiler.

The problem is not the quantity.

The problem is that, over time, these emerged:

- repeated tests;
- equivalent scenarios written in different ways;
- giant tests trying to validate many things;
- test classes heavily coupled to the implementation;
- syntax tests mixed with lowering tests;
- lowering tests mixed with execution;
- E2E execution mixed with conformance;
- stress tests living next to fast tests;
- suites whose feedback is slow.

This compromises three things:

1. development speed;
2. compiler reliability;
3. the project's engineering quality.

## 🎯 Objective

Turn the tests into an organized, modular, fast and deterministic system.

The suite must stop being just a large volume of `*Test.java` files and
acquire clear validation layers.

## 🧠 Kof's Testing Philosophy

Proposed as the project's official philosophy:

> A test does not exist to prove the code works.
>
> A test exists to prevent an engineering decision from being lost in the
> future.

Consequently:

- every fixed bug stays protected;
- every design decision stays documented;
- every observable behavior stays validated;
- no test exists merely to inflate a number.

## 🏗️ Layered Architecture

Proposed formal architecture:

```
L0 - Unit Tests
    Parser
    Lexer
    AST
    Typer
    Semantic Analysis

L1 - Component Tests
    Lowering
    Codegen
    IR
    Optimizer
    Backend
    ABI

L2 - Target Execution
    JVM
    Native
    JavaScript
    Script
    Android

L3 - E2E
    Compile
    Run
    Compare stdout
    Check exit code

L4 - Conformance
    syntax
    semantics
    stdlib
    operators
    runtime

L5 - Stress
    concurrency
    memory
    fuzzing
    load
    stability
```

## 🔥 Main Identified Problems

### 1. Massive structural repetition

Currently many tests:

- create a compiler;
- load code;
- compile;
- execute;
- check a string.

This repeats across practically the whole suite.

Proposed: create an official **Kof Test Harness**, centralizing:

```
compile()
run()
expect()
expectOutput()
expectDiagnostic()
```

## 2. Lack of isolation between targets

Today the tests:

```
JVM
Native
JS
Script
```

end up coexisting in the same test repository without explicit boundaries.

Proposed formal separation:

```
compiler/
native/
jvm/
js/
script/
shared/
conformance/
```

## 3. Giant tests

There are files with hundreds of scenarios.

This makes it hard to:

- debug;
- run in isolation;
- measure time;
- discover regressions.

Proposed: split by responsibility.

## 4. Absence of execution profiles

Currently the developer practically runs everything.

There should be profiles:

### Fast

```
mvn test -Pfast
```

Goal: feedback under ~30 seconds.

Contains:

- Parser
- Lexer
- Typer
- Lowering
- Unit
- Component

### Integration

```
mvn test -Pintegration
```

Contains:

- targets
- execution
- golden
- ABI

### Full

```
mvn test
```

Everything.

### Stress

```
mvn test -Pstress
```

Contains:

- concurrency
- memory
- fuzzing
- stability

This separation prevents 10-minute tests from dictating the daily pace.

## 5. Lack of traceability

Today there is no easy map between:

- feature
- test
- bug
- decision

Proposed: each test family declares:

```
Feature:
Records
Pattern Matching
Generics
FFI
```

and:

```
Coverage:
Parser
Typer
Lowering
JVM
Native
JS
```

## 6. Golden tests

Proposed: an official **Golden Suite**.

It should contain:

- real examples;
- compiled code;
- expected output;
- exit code;
- hash.

Goal:

```
same code
↓
same output
↓
on every target
```

## 7. Regression tests

Today many bugs become a single testcase.

Proposed official policy:

> Every fixed bug must generate:
>
> 1. Minimal reproduction
> 2. Regression test
> 3. Permanent reference

The test must never be removed.

## 8. Compiler-crash tests

Today many tests try to reproduce errors.

What's missing is a dedicated Stability suite.

It should validate:

- the parser never hangs;
- lowering never throws an unexpected exception;
- the typer never loops;
- invalid code always produces a diagnostic;
- the AST never ends up inconsistent.

## 9. Deterministic tests

No test may depend on:

- the current time;
- the network;
- a specific operating system;
- an external tool's availability without an explicit guard.

Every external dependency must be protected by honest environment guards
(`assumeTrue` + documented gap — R6, never a silent skip).

## 10. Feedback cost

Proposed: continuous measurement.

Generate a report:

```
test
time
failures
stability
```

The slowest tests must be permanently monitored.

## 🧪 Refactoring Strategy

The refactoring must NOT touch the compiler.

It changes only the test infrastructure.

### Phase 1 — Profiling

Instrument the whole suite.

Discover:

- time per class
- time per target
- duplicated tests
- redundant tests
- unstable tests

### Phase 2 — Quick Wins

**State (28/09):** discovery + guard LANDED — `scripts/test-suite-audit.sh`
measures the leads (sleeps / oversized / duplicate names); `scripts/check_test_hygiene.sh`
is the ratchet over the frozen `scripts/test-hygiene-baseline.txt`
(`--write-baseline` only after improving). The removals below are the open work
(shrink the baseline, then re-freeze).

Remove:

- repetition;
- sleeps;
- unnecessary loops;
- redundant setup.

### Phase 3 — Modularization

Separate layers:

```
compiler tests
backend tests
target tests
conformance tests
stress tests
```

### Phase 4 — Harness

Build the official infrastructure.

### Phase 5 — Targets

Separate execution:

```
JVM
Native
JS
Script
```

### Phase 6 — Conformance

Build the official equivalence suite.

### Phase 7 — Integration

Deploy:

```
mvn verify
```

or equivalent.

## 📊 Goal

After the refactoring:

- fast feedback;
- less redundancy;
- a testable architecture;
- greater release confidence;
- regressions easier to investigate;
- tests that explain decisions;
- a suite that keeps up with Kof's growth.

## Architecture Diagram

```
Kof Test Suite
        │
        ├── Unit
        │
        ├── Component
        │
        ├── Backend
        │
        ├── Target
        │      │
        │      ├── JVM
        │      ├── Native
        │      ├── JavaScript
        │      ├── Script
        │      └── Android
        │
        ├── E2E
        │
        ├── Conformance
        │
        ├── Golden
        │
        ├── Fuzzing
        │
        └── Stress
```

## Golden rule

> "The compiler may change architecture.
>
> The test suite does not."

That phrase exactly captures the direction we are following.

## Conclusion

Kof's test suite must not be treated as secondary code.

It is one of the project's main engineering assets.

After consolidating the compiler, this is one of the biggest opportunities to
evolve Kof's quality.

Additional proposal: at the end of the refactoring, generate a permanent
document:

```
docs/testing/TEST-PERFORMANCE.md
```

tracking metrics such as:

- total time;
- time per layer;
- slowest tests;
- most unstable tests;
- suite runtime evolution.

## Next Step

Before any deep refactoring, the path is:

1. measure the whole suite (`scripts/test-suite-profile.sh`, Phase 1 — LANDED
   tooling; results in `docs/testing/TEST-PERFORMANCE.md`);
2. identify the 20 slowest tests (the profiler ranks them);
3. look for duplication (Phase 2 — discovery + ratchet LANDED:
   `scripts/test-suite-audit.sh` + `scripts/check_test_hygiene.sh`; open work =
   shrink `scripts/test-hygiene-baseline.txt` and re-freeze);
4. propose the modularization (Phase 3 — started: `--citations` measures the split cost per
   oversized class and the drift rule is fixed; four splits landed = `KofSetEqualitySupport`
   out of `KofSetEqualityTest` (21/21 kept), `KofMathSupport` out of `KofMathTest` (29/29 kept),
   `ArrayBoundsStressSupport` out of `ArrayBoundsStressTest` (15/15 kept), `KofMediaSupport`
   out of `KofMediaE2ETest` (17/17 kept), `NullablePrimitiveContractSupport` out of
   `NullablePrimitiveContractE2ETest` (26/26 kept), `LambdaSupport` out of `LambdaE2ETest`
   (36/36 kept), `BiosBootSupport` out of `BiosBootE2ETest` (10/10 kept) and `FfiStructSupport`
   out of `FfiStructE2ETest` (12/12 kept), `ShellSupport` out of `ShellE2ETest` (21/21 kept)
   `KofStringsSupport` out of `KofStringsTest` (18/18 kept) and `KofSwitchExprSupport` out of
   `KofSwitchExprE2ETest` (32/32 kept) and `KofInterpreterParitySupport`/`...Programs` out of
   `KofInterpreterParityTest` (26/26 kept) and `UiSupport`/`UiPrograms` out of `UiE2ETest` (29/29
   kept) → oversized 43→30, baseline 170→157; next split picks by a fresh `--citations`
   measurement).

**Important:** this refactoring must not interfere with anything in the
compiler. It is purely test infrastructure (golden rule). The front is open
(`D-TEST-ARCHITECTURE-GO`); Phase 1 profiling + Phase 2 discovery/ratchet have
landed — the open work is the quick-win removals.
