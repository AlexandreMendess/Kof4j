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
over the frozen `scripts/test-hygiene-baseline.txt`, 174 keys). **Quick-win slice 1
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
**How to finish:** Phase 1/2 discovery done — then **Phase 2 quick-win removals**
(shrink the baseline: sleeps / duplication / oversized) → 3 (modularization) → 4
(harness) → 5 (targets) → 6 (conformance) → 7 (`mvn verify`). **Pure test infrastructure — the compiler is never touched**
(golden rule below). One slice per commit, RED-first + `check_500`.

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
4. propose the modularization (Phase 3 — not started).

**Important:** this refactoring must not interfere with anything in the
compiler. It is purely test infrastructure (golden rule). The front is open
(`D-TEST-ARCHITECTURE-GO`); Phase 1 profiling + Phase 2 discovery/ratchet have
landed — the open work is the quick-win removals.
