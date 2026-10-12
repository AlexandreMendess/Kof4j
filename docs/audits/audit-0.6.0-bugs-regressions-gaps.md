[English](audit-0.6.0-bugs-regressions-gaps.md) | [Português](audit-0.6.0-bugs-regressions-gaps.pt_BR.md)

# Audit — Kof 0.6.0: bugs, regressions and gaps

> **Investigation only.** This document changes no production code, applies no fix
> and marks nothing as fixed without reproducible evidence. It **does not close by
> itself**: every pending item points to its home (a bug in
> `docs/bugs-and-gaps/known-bugs.md`, a plan in `docs/development/`, a decision in
> `docs/development/DECISIONS.md`).

**Last updated:** 2026-10-11
**Investigated release:** `0.6.0` — tag `kof-0.6.0-linux-x86_64`, commit
`bb90986d065118e4f63978e8e6985256ff714203`
**Current branch:** `lab`
**Method:** every item reproduced with the **published** 0.6.0 artifact
(`kof-0.6.0-linux-x86_64.tar.gz`, extracted to a scratch dir) **and** the `lab` CLI
(`kof-cli/target/kof-cli-0.6.0.jar`). Read-only investigation; all scratch files under
`/tmp/kof-audit/`. No file in this repository was modified except this audit.

---

## 1. Preparation and integrity

| Fact | Value |
|---|---|
| Repository | `KofLang/Kof4j` |
| Released version | `0.6.0` (`VERSION`, `pom.xml` `<revision>`) |
| Release tag | `kof-0.6.0-linux-x86_64` (+ windows/macos, dated `+2026.10.11` pre-releases) |
| Release commit | `bb90986d065118e4f63978e8e6985256ff714203` |
| Current branch | `lab` |
| Work tree | dirty with **peer-lane** edits (`DOING.md`, `known-bugs.md`, untracked `tests/golden/…`) — not touched |
| Build | Maven 3.9.16, JDK 25 (Temurin) |
| Compile | `mvn -o -pl kof-compiler -am compile -q` |
| Focused tests | `mvn -o -pl kof-compiler -am test -Dtest='<Area>' -Dsurefire.failIfNoSpecifiedTests=false` |
| Full suite | **NOT run** — it hangs on the `Av1*` family (open, `known-bugs` §602); excluded for a green run |
| Environment limits | Native targets need the Linux toolchain (present); JS uses the bundled GraalJS (slow startup on this host); no Windows lane |

**Commands used for reproduction** (both CLIs behave identically unless noted):

```
OFFICIAL=/tmp/kof-audit/kof-0.6.0-linux-x86_64/bin/kof
LAB="java -jar /home/mel/Documentos/Kof4j/kof-cli/target/kof-cli-0.6.0.jar"
$OFFICIAL check|run [--target jvm|js|native] Main.kf
```

---

## 2. Executive summary (A)

**20 items investigated.** Result:

| Status | Count | Items |
|---|---|---|
| **CONFIRMED** (defect reproduced) | 11 | `JVM-01/02/03`, `HTTP-01`, `MATH-02`, `DIAG-01`, `DIAG-02`, `MEM-01`, `CLI-01`, `ORM-01`, `VER-01` |
| **FIXED IN THE RELEASE** (validated on the tag) | 5 | `RB-01`..`RB-05` |
| **NOT A BUG** (behavior matches the proven contract) | 4 | `MATH-01`, `PARSE-01`, `HTTP-02`, `UI-01` |
| **NOT REPRODUCED** | 1 | `SCRIPT-02` |
| **SPEC GAP** (needs a product/language decision) | 4 | `MEM-01`, `CLI-01`, `ORM-02`, `SCRIPT-01` (+ `HTTP-02` opt-out) |

**Root causes, grouped:**

1. **Silent `check` green → divergent codegen** — the dominant family. `MATH-02`,
   `HTTP-01`, `JVM-01/02/03` all pass `kof check` with "no errors", then a JVM
   `VerifyError` (class never loads) or a **silently wrong result on Native**.
   The single most dangerous face: `math.min/max/clamp` and the `math` predicates
   have **no argument-type guard**, so a `Double`/`String`/`Bool` argument reaches an
   Int-only runtime symbol — JVM dies, **Native prints `0`**.
2. **Lambda value typing leaks the concrete generated class** (`LambdaN`) instead of
   the shared synthetic SAM interface — `JVM-01` and `JVM-03` are one family;
   `JVM-02` is the sibling `FunctionType`-as-non-reference default bug.
3. **Diagnostics are not deduplicated and are not uniformly formatted** — `DIAG-01`
   (one error emitted twice) and `DIAG-02` (raw `ClassType[...]`/`PrimitiveType[...]`
   leaked in `SEM012`/`SEM021`/constructor `SEM014`).
4. **ORM has no name-mapping layer** — `ORM-01`: a `snake_case` column never reaches a
   `camelCase` field; a reference field is **silently `null`** (exit 0), a primitive
   field fails with `JSN004`. `ORM-02`: `ORM005` does not exist.
5. **Release hygiene** — `VER-01`: the published `0.6.0` binary self-reports
   `kof 0.5.0-beta` because `kof version` reads a **stale** `version.properties`.

**Critical release risks:**

- `VER-01` is a **reproducible defect in the published artifact** (wrong version string).
- `MATH-02` and `ORM-01` produce **silently wrong data** on supported targets.
- `JVM-01/02/03` make a whole class unloadable after a clean `check`.

**Contract blockers (rule 6 — a maintainer decision is required, no fix can be invented):**
`MEM-01` (is `app.listen(...)`/process lifetime a valid resource lifetime?), `CLI-01`
(should `serve` support `--deps`?), `ORM-02` (define `ORM005` or declare it unused),
`SCRIPT-01` (should inline KofScript gain a `say` spelling?), `HTTP-02` (opt-out from
"auth-if-present").

---

## 3. Full inventory (B)

Legend: **Tag** = behavior at `0.6.0`; **Lab** = behavior at current `lab`; **Repro** =
reproducibility (`always` unless noted).

| ID | Subsystem | Description | Tag 0.6.0 | Lab | Severity | Root cause | Files / methods | Tests today | Tests needed | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `RB-01` | ORM JVM read | camelCase field/column round-trip | OK | OK | — | fixed §564 | `jvm/JvmConfigRuntime.java:474-507` | `OrmCamelCaseE2ETest` | collision pin (optional) | none |
| `RB-02` | CLI stdout | UTF-8 output (accents/multibyte) | OK | OK | — | fixed §539 | `kof-cli/…/KofStdio.java` | `StdioEncodingE2ETest`, `KofStdioTest` | Windows lane | none (documented Windows residual) |
| `RB-03` | JSON JVM binder | missing primitive component → `JSN004` | OK (JVM) | OK (JVM) | — | fixed §565 | `jvm/JvmRuntimeJson.java:391-394` | `JsonMissingPrimitiveE2ETest` | **JS/Native parity** (see `JSN-01`) | none on JVM |
| `RB-04` | JSON typing | `json.decode` requires an explicit type-arg | enforced | enforced | — | §583/§588 | `MemberCallNamespaces.java:29-44`, `SemMethodCallTyper.java:48-65` | `GenericJsonDecodeRefusalTest` | count==1 pin | none |
| `RB-05` | `kof.ui` | unknown UI method → `SEM025` | OK | OK | — | fixed #711/§547 | `CompilerUiEmitter.java:274-285` | `UiUnknownMethodDiagnosticE2ETest` | count==1 pin | none |
| `JSN-01` | JSON cross-target | missing primitive component on JS/Native | silent | silent | **High** | decoders have no presence check | `CompilerTypeSupport.java`, `NativeJsonSchema.java`, `js/JsRuntimeOps.java` | none (JVM-only) | JS/Native `JSN004` or declared gap | mirror the JVM refusal |
| `JVM-01` | JVM backend | reassigning a lambda-typed local → `VerifyError` | broken | broken | **High** | concrete `LambdaN` in the local's type | `StatementLowererLocalBoxing.java:70-81`, `ExpressionAssignmentLowerer.java:262-296`, `ExpressionBareCallLowerer.java:156-157` | none | `lambdaLocalReassignJvm` | unify to SAM interface (§156 precedent) — ✅ FIXED 11/10 on `lab` as `known-bugs` §672 (lane compiler/JVM/native `192.168.15.30:9092`; the inferred `var`/`val` lambda local is now typed as the synthetic interface, NOT the concrete `LambdaN`; gated OFF for the lambda-returns-lambda sub-shape, which is `JVM-03`'s family) |
| `JVM-02` | JVM backend | function-type local with no initializer → `VerifyError` | broken | broken | Med-High | no-init default is `0`, not `null` | `StatementLowererLocalBoxing.java:130-133`, `CompilerTypeSupport.java:89-92` | none | `functionTypeLocalWithoutInitializerJvm` | treat `FunctionType` as reference in the default predicate — ✅ FIXED 11/10 on `lab` as `known-bugs` §664 (lane flatt `192.168.100.14:0`; the PLAIN path — the captured twin rides the JVM-01/03 lambda-box unit, recorded in the §664 Boundary) |
| `JVM-03` | JVM backend | if/switch-expr with lambda branches → `VerifyError` (+ phantom `invokestatic`) | broken | broken | **High** | branch join yields `Object` / first-branch `LambdaN` | `ExpressionTyper.java:255-277,350-358`, `HierarchyResolver.java:132-152`, `ExpressionBareCallLowerer.java:252-253` | none | `ifExprLambdaBranchesUnifyToSamInterfaceJvm` + switch twin | unify same-signature lambda branches to the SAM interface — ✅ FIXED 11/10 on `lab` as `known-bugs` §673 (lane compiler/JVM/native `192.168.15.30:9092`; `ExpressionTyper.unifyLambdaBranches`/`unifyAllLambdaBranches` unify same-signature lambda branches to the synthetic interface BEFORE the commonSupertype join, both if-expr and switch-expr; gated OFF for lambda-returns-lambda, mirroring §672) |
| `HTTP-01` | HTTP / web | `http.status(401)` / bare `status(401)` pass `check` silently | broken | broken | **High** | arity-only dispatch, no type check; no diagnostic on mismatch | `KofHttp.java:98-99`, `MethodCallNamespaces.java:72-78`, `ExpressionHttpCallLowerer.java:17`, `ExpressionBareCallLowerer.java:252` | `StdCatalogSignaturesTest` (dispatcher only) | `http.status(401)`/`get(401)`/`timeout("abc")` must fail `check` | add a type check beside the arity check; emit `SEM025`/dedicated `HTTP00x` — ✅ FIXED 11/10 on `lab` as `known-bugs` §665 (lane flatt `192.168.100.14:0`; the guards make the EXISTING SEM025 namespace diagnostic fire; bare `status(401)` + `time.sleep` recorded in the §665 Boundary) |
| `MATH-01` | `kof.math` | `math.abs(Double)` | `SEM025` | `SEM025` | — | honest (Int-only, guarded) | `KofMath.java:48-49`, `StdCatalog.java:207` | `StdCatalogSignaturesTest:184` | — | none (SPEC GAP: Double overloads?) |
| `MATH-02` | `kof.math` | `min`/`max`/`clamp`/`isEven`/`isOdd`/`isPositive`/`isNegative`/`isZero` accept any type | broken | broken | **High** | **no type guard** (arity only) | `KofMath.java:52-59` | `KofMathTest` (Int happy path only) | non-Int arg must be `SEM025` | add the `isInt` guard, mirroring `abs`/`sign` — ✅ FIXED 11/10 on `lab` as `known-bugs` §658 (lane flatt `192.168.100.14:0`, `KofMathTypeGuardTest` RED-first) |
| `DIAG-01` | diagnostics | one error emitted twice on the `VarDeclStmt` path | 2× | 2× | Low-Med | `Unknown` never cached + redundant re-inference | `SemExpressionTyper.java:15-28`, `StatementAnalyzer.java:151,162,166` | none | count==1 pin | cache `Unknown` or dedup at the sink — ✅ FIXED 11/10 on `lab` as `known-bugs` §661 (lane flatt `192.168.100.14:0`, exact-duplicate dedup at `DiagnosticCollector.report`, `DiagnosticSinkDedupTest` RED-first) |
| `DIAG-02` | diagnostics | raw `ClassType[...]`/`PrimitiveType[...]` in `SEM012`/`SEM021`/ctor `SEM014` | leak | leak | Low-Med | raw record `toString()` instead of `Type.display` | `SemAssignmentAnalyzer.java:54,140-142,159`, `SemExpressionTyper.java:184`, `StatementAnalyzer.java:173-175`, `TypeChecker.java:163-167` | `QualifiedTypeDiagE2ETest` (SEM010 only) | assert no `*Type[` in messages | use `Type.display(...)` at those six sites — ✅ FIXED 11/10 on `lab` as `known-bugs` §663 (lane flatt `192.168.100.14:0`, `TypeDisplayDiagnosticTest` RED-first 3/0F, battery 347/0F) |
| `MEM-01` | memory analyzer | `MEM014` on `web.app()` (server lifetime) | warns | warns | Low | per-scope rule ignores `listen`/process lifetime | `memory/ResourceLeakAnalysis.java:52-73,111-171` | `ResourceLeakE2ETest` (no server case) | server-lifetime pin | **SPEC GAP** — treat `listen` as lifetime-establishing (maintainer call) |
| `PARSE-01` | lexer/parser | expression `1..3` | `PARSE039` | `PARSE039` | — | no range operator by design | `parser/Lexer.java:437`, `parser/ExpressionParser.java:182-211` | probe in `lexical-structure.md` | — | none (diagnostic-quality note only) |
| `CLI-01` | CLI `serve` | `kof serve --deps` | rejected | rejected | Low | `serve` has no `--deps` wiring | `kof-cli/…/CmdServe.java:41-86` | none | pin if `--deps` is added | **SPEC GAP** — decide whether `serve` supports deps |
| `HTTP-02` | web security | request with `Authorization` → `401` | by design | by design | — | documented "auth-if-present" | `jvm/JvmRuntimeWebDispatch.java:150-158`, `JvmStringSecurityRuntime.java:320-360` | `KofWebHardeningTest` | pass-through pin if opt-out added | **SPEC GAP** — opt-out from auth-if-present? |
| `UI-01` | `kof.ui` | `Style(token)` → `SEM077` | `SEM077` | `SEM077` | — | correct family classification | `BuiltinUiCallTyper.java:60-70`, `KofStyleParser.java:130-140` | `UiStyleCssE2ETest` | token pin | none (rename mismatched test methods) |
| `ORM-01` | `kof.orm` | `snake_case` column ↔ `camelCase` field | no mapping | no mapping | **High** | exact-name equality only, no mapping layer | `jvm/JvmConfigRuntime.java:474-507`, `jvm/JvmRuntimeJson.java:383` | `OrmCamelCaseE2ETest` (exact only) | snake↔camel RED-first | **SPEC GAP** — decide automatic/opt-in/reject |
| `ORM-02` | ORM diagnostics | `ORM005` | absent | absent | Med | never defined | `KofOrm.java:137-139`, `CompilerOrmSupport.java:44-186` | none | — | **SPEC GAP** — define or declare unused |
| `VER-01` | release / CLI | `kof version` prints `0.5.0-beta` on 0.6.0 | wrong | fixed | **High** | stale `version.properties` (bump skipped it) | `kof-compiler/…/version.properties`, `scripts/bump-version.sh`, `kof-cli/…/Main.java:49-55` | — | a `version == VERSION` test | already fixed on `lab`; **re-publish** is the maintainer's call |
| `SCRIPT-01` | KofScript inline | `say` | `SEM015` | `SEM015` | Low | no `say` symbol exists anywhere | `TopLevelCallTyper.java:141-154` | `KofScriptTest` (no `say`) | pin if a shell `say` is specified | **SPEC GAP** — decide a shell-only `say` |
| `SCRIPT-02` | KofScript inline | snippet without `main` | works | works | — | top-level statements wrap into `main()` | `kof-script/…/KofScript.java:82-174,187-189` | `KofScriptTest`, `KofReplTest` | pin | none |

---

## 4. Evidence (C)

All outputs below are **executed**, on the published 0.6.0 CLI unless stated.

### `VER-01` — the published binary reports the wrong version

```
$ /tmp/kof-audit/kof-0.6.0-linux-x86_64/bin/kof version
kof 0.5.0-beta

$ unzip -p lib/kof.jar dev/kof/version.properties
kof.version=0.5.0-beta
kof.compiler.version=0.5.0
kof.runtime.version=0.5.0
kof.stdlib.version=0.5.0
kof.tooling.api=21
```

The 0.6.0 preparation commit (`75f4b568d`) bumped `VERSION` and `pom.xml` but **not**
`version.properties`; the auto-cascade commit (`628c60f03`, 11/10) fixed it at the
source. `VERSION` is `0.6.0` in the shipped artifact — only the runtime constant is
stale. The `lab` jar prints `kof 0.6.0`.

### `MATH-02` — silent `check`, wrong result on Native

```
main() { println("" + math.min(1, 2.5))  println("" + math.min(3.5, 2.5)) }

$ kof check Main.kf
checked 1 file(s) — no errors

$ kof run --target jvm Main.kf
java.lang.VerifyError: Bad type on operand stack   (Type double_2nd … not assignable to integer)

$ kof run --target native Main.kf
0
0                                                  # expected 1 / 2.5  — SILENT WRONG RESULT
```

`math.min(Int,Int)` on Native is correct (`1`, `2`) — only the non-Int path breaks.
Also measured, all `check` = "no errors": `math.min("a","b")` → Native prints a garbage
pointer; `math.max(1.0, 2.0)`, `math.clamp(1.5,0.0,2.0)`, `math.isEven(2.5)` → JVM
`VerifyError`.

### `JSN-01` — the missing-primitive refusal is JVM-only

```
record Pedido(String nome, Int clienteId, Bool pago)
json.decode<Pedido>("{\"nome\":\"a\",\"pago\":true}")

$ kof run --target jvm    → RuntimeException: JSN004: missing field 'clienteId' for Pedido
$ kof run --target js     → nome=a clienteId=undefined pago=true      # silent
$ kof run --target native → nome=a clienteId=0 pago=true              # silent zero-fill
```

`D-JSON-MISSING-PRIMITIVE` forbids a silent zero-fill; Native does exactly that.

### `JVM-01` — reassigning a lambda-typed local

```
main() { var f = () -> 1  f = () -> 2  println(f()) }

$ kof check Main.kf
checked 1 file(s) — no errors

$ kof run --target jvm Main.kf
java.lang.VerifyError: Bad type on operand stack
  Location: Default/Main.main([Ljava/lang/String;)V @20: invokevirtual
  Reason:   Type 'Lambda1' … is not assignable to 'Lambda0'
  locals: { '[Ljava/lang/String;', 'Lambda1' }
```

`javap` shows the slot typed `Lambda0`, the second lambda being `Lambda1`; both
implement `kof.Function0_int`, so the fix is to type the slot as the interface.

### `JVM-02` — function-type local without an initializer

```
main() { var f: (Int) -> Int  f = (x: Int) -> x + 1  println(f(5)) }

$ kof check Main.kf
checked 1 file(s) — no errors

$ kof run --target jvm Main.kf
java.lang.VerifyError: Bad type on operand stack
  Location: Default/Main.main @1: astore_1
  Reason:   Type integer … is not assignable to reference type
```

`javap` shows `iconst_0; astore_1` — the no-init default is the integer `0`, not `null`.

### `JVM-03` — if/switch-expression with lambda branches

```
main() { var flag = true
        var g = if (flag) (x: Int) -> x + 1 else (x: Int) -> x + 1
        println(g(5)) }

$ kof run --target jvm Main.kf
java.lang.VerifyError: … @35: invokestatic   (Type 'java/lang/Object' … not assignable to integer)
```

`javap` shows a **phantom** `invokestatic Default/Main.f:(I)Ljava/lang/Object;` — a
method that does not exist — while the local is typed `Object`. The `switch`-expression
twin fails with `invokevirtual Lambda0.invoke` on an `Object` frame.

### `HTTP-01` — `status(401)` passes `check`

```
main() { var s = http.status(401)  println(s) }

$ kof check Main.kf
checked 1 file(s) — no errors

$ kof run --target jvm Main.kf
java.lang.VerifyError: Bad type on operand stack   (Type integer … not assignable to 'java/lang/String')
```

The intended forms are `http.status(String url) -> Int` (client) and
`status(code, body)` (server, arity 2). `status(401)` matches neither; the bare form
falls through to a phantom `invokestatic Default/Main.status(I)`.

### `DIAG-01` — duplicate diagnostic

```
main() { var x = nope }

$ kof check Main.kf
Main.kf:1:18: error: Undefined variable or type: 'nope' [SEM011]
Main.kf:1:18: error: Undefined variable or type: 'nope' [SEM011]     # ×2 (errorCount=2 in --json)
```

### `DIAG-02` — internal type representation leaked

```
class A { }  class B { }
main() { var a: A = A()  var b: B = B()  a = b }

$ kof check Main.kf
Main.kf:7:1: error: Type mismatch: cannot assign ClassType[packageName=, name=B, typeArguments=[]] to ClassType[packageName=, name=A, typeArguments=[]] [SEM012]
```

Contrast (the good path): `Return type mismatch: expected 'A' but got 'B' [SEM010]`.

### `MEM-01` — `MEM014` on the canonical `web.app()` idiom

```
main() { var app = web.app()  println("created") }

$ kof check Main.kf
Main.kf:2:5: warning: L-05: resource 'app' from 'web.app()' is never closed in this scope and never handed to anyone — call 'app.close()' or return/transfer the handle (MEM014) [MEM014]
checked 1 file(s) — no errors
```

The spec lists "Process" as a valid lifetime for a web server; the canonical idiom in
`training/idioms/web.md` is marked GOOD and never closes `app`.

### `ORM-01` — `snake_case` column never reaches a `camelCase` field

```
entity Cliente { id: String unique  clienteId: Int }
create table "cliente"("id" varchar(64), "cliente_id" int); insert … ("1", 42)
orm.find<Cliente>(db, "1")

$ kof run Main.kf
java.lang.RuntimeException: JSN004: missing field 'clienteId' for Cliente
```

With a **reference** field the failure is silent:

```
entity Cliente { id: String unique  nomeCompleto: String }   # column "nome_completo" = "Ana"
$ kof run Main.kf
[null]                                                        # exit 0 — silent wrong data
```

### `PARSE-01` / `CLI-01` / `UI-01` / `SCRIPT-01`

```
$ kof check range.kf      → Main.kf:2:15: error: Expected field name [PARSE039]   (×2)
$ kof serve --deps        → file not found: --deps
$ kof serve Main.kf --deps→ serve: unknown or incomplete flag: --deps (accepts: --port <n> --host <h> --backend <t> --frontend <t>)
$ kof check style.kf      → Main.kf:3:5: error: style: the declaration string must be a literal … [SEM077]
$ kof script 'say("hi")'  → Inline.kf:3:1: error: Undefined function: 'say' [SEM015]
$ kof script 'println("hi")' → hi   (no `main` needed; SCRIPT-02 does not reproduce)
```

---

## 5. Architectural analysis (D)

**Silent-overload family (biggest risk).** `MATH-02` and `HTTP-01` share one shape: a
namespace dispatch keyed on **arity only**, returning `null`/`UnknownType` on a
mismatch and emitting **no diagnostic**; the lowerer then either emits a call to an
Int-typed symbol or falls through to a **phantom** top-level call
(`ExpressionBareCallLowerer.java:252`). This is the same family already tracked as
`known-bugs` §554/§556/§557 and §629. **One fix shape** (a type check beside the arity
check, plus a diagnostic on the fall-through) can close the static-analysis half of
`MATH-02`, `HTTP-01` and the phantom-call half of `JVM-03`.

**Lambda value typing (one family).** `JVM-01` and `JVM-03` both stem from a lambda
value's type carrying the concrete `LambdaN` class; nothing outside the narrow `listOf`
case (§156, `CompilerTypeSupport.listOfElementType`) unifies same-signature lambdas to
the shared SAM interface. `JVM-02` is independent (the `erasesToReference` predicate
excluding `FunctionType`). **One fix shape** for `JVM-01`+`JVM-03` (drop `className` for
value-position lambdas / unify branch joins to the interface); a one-line predicate
change for `JVM-02`.

**Diagnostics pipeline.** `DIAG-01` (no dedup anywhere; `Unknown` re-inferred) and
`DIAG-02` (raw record `toString()` instead of the existing `Type.display`) are
independent but both live in the semantic-analysis/diagnostic layer. **Isolated
changes**: a sink-level dedup by `(position, code, message)`; six call sites switching to
`Type.display`.

**ORM.** `ORM-01` (no mapping layer) and `ORM-02` (`ORM005` undefined) share the
metadata/hydration mechanism (`JvmConfigRuntime.kof_db_query_n` → `kof_json_bind`).
The read chokepoint is a single place; the write side and the JS bridge would mirror it.
**Requires a decision first.**

**Cross-target JSON.** `JSN-01` is the JS/Native decoder missing the presence check the
JVM binder gained — an additive change to the shared decode paths.

**Independent:** `MEM-01` (analyzer), `VER-01` (build/release), `SCRIPT-01/02` (shell↔KofScript).

---

## 6. Fix plan (E)

Ordered by priority and dependency. **Acceptance** = a RED-first test that fails on the
current code and passes after; **closure** = merged on `lab` with the test green and the
relevant battery non-regressed. Nothing below is implemented in this audit.

**P0 — silently wrong data / broken published artifact**

1. `MATH-02` — add the `isInt` guard to `KofMath.java:52-59`. *Test:* `math.min(Double,Double)`,
   `math.clamp(Double,…)`, `math.isEven(Double)` → `SEM025`. *Risk:* low. — **DONE 11/10:** landed
   as `known-bugs` §658 (lane flatt `192.168.100.14:0`): `KofMathTypeGuardTest` measured RED at the
   audit's exact repro pre-fix, 2/0F post-fix; `*Math*`+`StdCatalogSignaturesTest` 53/0F; golden 236/236.
2. `VER-01` — already fixed at the source on `lab`; add a test that
   `KofVersion.version()` equals `VERSION`, and have the maintainer decide on a
   **re-publish** (tags are immutable). *Risk:* none to code.
3. `ORM-01` — **blocked on a decision** (`ORM-02`/spec). Once decided: normalize keys at
   `kof_db_query_n` (read) + the symmetric write. *Risk:* medium (shared decode path).

**P1 — clean `check`, unloadable class**

4. `JVM-02` — treat `FunctionType` as reference in the no-init default. *Test:*
   `functionTypeLocalWithoutInitializerJvm`. *Risk:* low. — **DONE 11/10:** landed as `known-bugs`
   §664 (lane flatt `192.168.100.14:0`): the no-init default loads null for a `FunctionType` slot
   (plain path); `FunctionTypeNoInitDefaultE2ETest` measured RED (VerifyError at load, the audit's
   verbatim face) pre-fix, prints `6` post; lambda battery 98/0F. The CAPTURED twin stays open with
   the JVM-01/03 unit (box + dispatch, §664 Boundary).
5. `JVM-01` + `JVM-03` — type value-position lambdas as the SAM interface; unify
   if/switch branch joins. *Tests:* `lambdaLocalReassignJvm` (#284 verbatim),
   `ifExprLambdaBranchesUnifyToSamInterfaceJvm` + switch twin. *Risk:* medium (shared
   lambda/inference battery — `LambdaE2ETest` 36, `ComponentCoreE2ETest`, higher-order).
6. `HTTP-01` — type check beside the arity check; diagnose the context-function
   fall-through. *Tests:* `http.status(401)`, `http.get(401)`, `http.timeout("abc")`,
   bare `status(401)` fail; `status(401,"body")` still compiles. *Risk:* medium (the
   fall-through at `ExpressionBareCallLowerer.java:252` is shared with legitimate
   top-level calls — gate only on known namespace/context members). — **DONE 11/10 (the
   qualified faces):** landed as `known-bugs` §665 (lane flatt `192.168.100.14:0`): measured
   root = `KofHttp.staticCall` bound by ARITY ALONE (no phantom call needed); type guards on
   every overload make the existing SEM025 namespace diagnostic fire;
   `HttpNamespaceOverloadDiagnosticTest` 3/3 RED pre-fix, 4/0F post; Http/Web batteries
   127/0F; the server `status(401,"body")` face is kof.web and stays green. The BARE face
   and `time.sleep("abc")` are the §665 Boundary (separate units).

**P2 — cross-target parity**

7. `JSN-01` — mirror the primitive-presence refusal in the JS and Native decoders, or
   record an explicit gap in `known-bugs`. *Risk:* medium (shared decode paths; run the
   JSON battery).

**P3 — diagnostics quality**

8. `DIAG-01` — dedup at the diagnostic sink by `(position, code, message)`. *Test:*
   `errorCount() == 1` for `var x = nope`. *Risk:* low-medium. — **DONE 11/10:** landed as
   `known-bugs` §661 (lane flatt `192.168.100.14:0`): exact-duplicate dedup (the full record tuple,
   args included) at `DiagnosticCollector.report`; `DiagnosticSinkDedupTest` measured RED 3/3
   pre-fix (the audit repro ×2, two defects ×4), 3/0F post-fix; diagnostic/analyzer battery 82/0F.
9. `DIAG-02` — replace the raw interpolations with `Type.display(...)` at the six sites.
   *Test:* SEM012/SEM021/ctor-SEM014 never contain `ClassType[`/`PrimitiveType[`/`NullableType[`.
   *Risk:* low (message text only). — **DONE 11/10:** landed as `known-bugs` §663 (lane flatt
   `192.168.100.14:0`): all six sites display; `TypeDisplayDiagnosticTest` measured RED 3/3 pre-fix
   (the audit's verbatim ClassType leak), 3/0F post; #324 battery + diagnostic sweep 347/0F.

**P4 — needs a maintainer decision before any code**

10. `MEM-01`, `CLI-01`, `ORM-02`, `SCRIPT-01`, `HTTP-02` — record the decision, then
    implement. `PARSE-01`, `UI-01`, `MATH-01` — no code required.

---

## 7. Specification gaps (F)

Behaviors that **cannot** be defined without a product/language decision. No contract is
invented here.

| Gap | Question | Where the current behavior is honest |
|---|---|---|
| `MEM-01` | Is `app.listen(...)`/process lifetime a valid resource lifetime for L-05, or must a server `close()` in scope? | spec `docs/spec/memory-safety.md` lists "Process" as valid; the analyzer requires a same-scope `close()` |
| `CLI-01` | Should `kof serve` support `--deps` (as `run`/`build` do)? | `serve`'s documented surface has no `--deps`; rejection is consistent with it |
| `ORM-02` | Define `ORM005` (which ORM failures, what context, which phase) or declare it unused? | `ORM005` does not exist; `ORM003` covers unknown columns |
| `ORM-01` | Should `snake_case`↔`camelCase` mapping be automatic, opt-in, or rejected with a diagnostic? | the ORM contract is exact-name equality only |
| `SCRIPT-01` | Should inline/REPL KofScript gain a `say` spelling, and only in that context? | no `say` symbol exists; `SEM015` is contract-correct |
| `HTTP-02` | Is an opt-out from "auth-if-present" wanted (pass-through gateways)? | the 401 is documented and intended |
| `MATH-01` | Should `abs`/`min`/`max`/`clamp` grow `Double` overloads? | `math.abs(Double)` is honestly refused today |

---

## 8. Verdict (G)

The investigated version presents problems in **three distinct categories**:

- **Regressions / functional defects** (`CONFIRMED`): `MATH-02`, `JVM-01/02/03`,
  `HTTP-01`, `ORM-01`, `JSN-01`, `DIAG-01`, `DIAG-02`, `MEM-01`, `CLI-01`. The severe
  ones share one property — **`kof check` is green and the failure only appears at load
  or produces wrong data**, which violates the `no-silent-fallback` /
  `honest-cross-target` constraints.
- **Implementation gaps needing a decision**: `ORM-01`, `ORM-02`, `MEM-01`, `CLI-01`,
  `SCRIPT-01`, `HTTP-02`, `MATH-01` (spec gaps, not defects).
- **Release-process defect**: `VER-01` — the published `0.6.0` binary self-reports
  `0.5.0-beta`; the source is already fixed on `lab`, but the released artifact is not.

The five items previously reported as fixed (`RB-01`..`RB-05`) are **confirmed fixed in
the released tag** (each by an ancestor commit of `bb90986d0` plus executed behavior).
Two of those fixes are, however, **incomplete on other targets** (`JSN-01`; the
namespace `SEM025` duplication in `DIAG-01`), and `RB-03`'s contract is **JVM-only**.

---

## Audit artifacts

Scratch files (outside the repository, no production code touched):
`/tmp/kof-audit/` — the extracted 0.6.0 distribution, the `verify*` repro dirs, and the
per-subsystem investigation dirs (`agent-2-1`, `agent-verifyerror`, `agent-http`,
`agent-diag`, `agent-misc`, `agent-orm`, `agent-shell`).
