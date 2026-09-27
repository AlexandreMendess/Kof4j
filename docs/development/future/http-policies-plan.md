[English](http-policies-plan.md) | [Português](http-policies-plan.pt_BR.md)

# HTTP/Web declarative policies — implementation plan

**Status:** plan only, zero code — `docs/development/future/`
**Requested by:** maintainer (23/09/2026)
**Owner when promoted:** Web lane (JVM first; Native/JS gated)
**Rule 6 gate:** before any line of code, the maintainer locks a `DECISIONS.md`
entry (`D-HTTP-POLICIES`) with the surface chosen here. This document is the
**design proposal**; it is not an authorization to implement.

---

## 1. Objective

Let a Kof web application declare **cross-cutting HTTP policies**
(authorization, rate limits, headers, rejection payloads, common WebService
configuration) **once**, and attach them **globally, to a resource (path
prefix) or to a single endpoint** — without the user writing `if`/`switch`
per endpoint.

The feature is an **extension of what already exists** (`app.security(opts)`),
not a new middleware framework. Kof stays Kof: the user declares the
**intention** ("this resource requires `admin`, 100 req/min"), the runtime
composes and executes the fixed pipeline.

---

## 2. Current state (real code, not assumed)

The web stack already has a **global** policy mechanism. Nothing per-route
exists today.

| Piece | Where | What it does today |
|---|---|---|
| Compile-time table | `kof-compiler/src/main/java/dev/kof/compiler/KofWeb.java` | Maps `web.app()`, `app.get/post/...`, `app.use`, `app.security([opts])`, `app.listen`, `app.configure` to `kof_web_*` runtime calls. `ROUTE_METHODS` at `KofWeb.java:36`; `security` at `KofWeb.java:114`. |
| App/runtime model | `.../jvm/JvmWebCoreRuntime.java` | `WebApp` (`:384`) holds **flat** policy fields; `WebRoute` (`:93`) holds `method/segments/params/handler/kind` **only**; `kof_web_route` (`:468`); `kof_web_use` (`:490`). |
| Policy parser | `.../jvm/JvmWebSecurityRuntime.java` | `kof_web_security_opts` parses a `Map` (`headers`, `cors`/`corsOrigin`, `rateLimit`, `csrf`, `sessionHeader`, `publicPaths`/`permitAll`, `auth`, `roles`) into the `WebApp` fields. |
| Dispatch | `.../jvm/JvmRuntimeWebDispatch.java` | `kof_web_security_pipeline(app, req)` (`:45`) runs the **fixed order** rate-limit → cors → headers → session → csrf → auth → RBAC **before** route matching (`:210` before `:216`). |
| Descriptors | `.../jvm/JvmRuntimeCallDescriptors.java:210-216`, `JvmRuntimeReturnDescriptors.java:204` | JVM signatures of `kof_web_*`. |
| Decision | `docs/development/DECISIONS.md` §D-SEC.3/.4 (`:368`) | The fixed pipeline order and the "authenticate by default" law. |
| Docs | `docs/stdlib/stdlib-web.md` §3 `app.security()`; `training/idioms/web.md` | The user-facing contract of `app.use` and `app.security`. |
| Tests | `KofWebHardeningTest` (6), `KofWebE2ETest` (10), `KofHttpServerTest` (8) | Global security + routing E2E with real sockets. |
| Other targets | `.../nat/NativeWebRuntime.java`, `.../js/JsRuntimeUiWeb.java` | Web base (WEB001); `app.security` is a **compile-time gap `WEB006`** (`KofWeb.gapCode`, `:189`). |

**The gap:** policies are **global to the app**. The only path-scoped control is
the `publicPaths` allow-list inside the global opts. To protect one endpoint,
today the user must write `app.use { ... if (path() == "/admin") ... }` — exactly
the manual `if`/`switch` the improvement wants to remove. `WebRoute` has no field
to carry a per-route policy.

**Useful fact (verified):** the grammar **already parses** a call with arguments
followed by a trailing lambda — `ExpressionParser.java:202-212` appends the
`{ ... }` block as the last argument after `parseArguments`. So
`app.get("/x", opts) { ... }` is parseable today; only the `KofWeb` table and the
typer need to accept the extra argument.

---

## 3. Desired behavior

`app.security(opts)` keeps being the **global default policy**. Two additive
forms scope it:

```kof
main() {
    auth.secret(secrets.get("JWT_SECRET", "dev"))
    var app = web.app()

    // 1. GLOBAL default — unchanged surface, same options.
    var g = mapOf()
    g.put("rateLimit", "200/60")
    g.put("auth", true)
    app.security(g)

    // 2. RESOURCE policy — applies to every route under the prefix.
    var admin = mapOf()
    admin.put("roles", "admin")
    admin.put("rateLimit", "20/60")
    admin.put("responses", mapOf("forbidden", "{\"error\":\"forbidden\"}"))
    app.policy("/admin", admin)

    // 3. ENDPOINT policy — the trailing-lambda form with an opts Map.
    var strict = mapOf("auth", true, "rateLimit", "5/60")
    app.post("/login", strict) {
        return "{\"token\":\"...\"}"
    }

    // Public by intention, not by an `if` inside every handler:
    var pub = mapOf("publicPaths", "/health,/metrics")
    app.policy("*", pub)

    app.get("/admin/users") { return usersJson() }   // inherits /admin policy
    app.get("/health") { return "ok" }               // public (allow-list)
    app.listen(8080)
}
```

Rules the surface must make obvious (declared, not re-derived by the user):

- A **handler never re-checks** what a policy already declares.
- `publicPaths`/`permitAll` and `roles` **accumulate** across scopes
  (allow-lists grow; the deepest scope adds, never removes).
- Every other key **overrides** (the deepest matching scope wins).
- The pipeline **order is fixed by the runtime** (§D-SEC.3); the user never
  composes it.

---

## 4. Semantic model

### 4.1 Policy value

A `Policy` is the parsed form of the existing opts `Map` (same keys, same
defaults as `app.security`). Parsing is exactly today's `kof_web_security_opts`
body, extracted into a reusable parser:

```
Policy.parse(Map opts, Policy base) -> Policy     // base = parent scope (optional)
Policy.merge(child)                               // scalar: child wins; lists: union
Policy.appliesTo(String path)                     // prefix match (see 4.2)
```

### 4.2 Scope matching

- A scope is registered with a **plain path prefix** (`/admin`, `/api/v1`).
- `"*"` (or `""`) means "every request".
- Matching is a **prefix** match on the normalized request path; the
  **longest** matching prefix wins.
- **No globs/regex in v1** — keep it simple and predictable; if a real need
  appears it becomes a rule-6 decision, not a silent glob engine.
- Path params in the matcher are **not** supported in v1 (prefix only).

### 4.3 Precedence (the merge law)

For a request whose path matches `P` and, when a route is matched, whose route
carries policy `E`:

```
effective = global.merge( P1.merge( P2.merge( ... Pn ) ) ).merge( E )
```

where `P1..Pn` are the path scopes ordered from **shortest to longest prefix**
(longest applied last → wins on scalars). Scalar keys: **deepest wins**.
List keys (`publicPaths`, `roles`): **union** of all scopes.

`publicPaths` is evaluated **once** against the request path with the effective
allow-list (a public path is public regardless of which scope declared it).

### 4.4 Endpoint policy vs the global-before-routing invariant

Today the pipeline runs **before** route matching, so an unknown path still gets
security headers / auth. That invariant must survive:

1. Compute `pathPolicy` from the scopes matching the request path.
2. Match the route (without invoking).
3. `effective = pathPolicy.merge(routePolicy)` if a route matched, else
   `pathPolicy`.
4. Run the fixed pipeline with `effective`.
5. On pass: invoke the handler, or answer the 404 with the **effective**
   rejection payload/headers.

### 4.5 Rejection payloads (declarative)

New documented opt key `responses` (a `Map`) lets the app declare the body
returned by the pipeline's synthetic rejections instead of the built-in JSON:

| Key | Fired by |
|---|---|
| `unauthorized` | 401 (auth/session) |
| `forbidden` | 403 (roles/CORS/CSRF) |
| `notFound` | 404 (documented absence — `return null`) |
| `tooManyRequests` | 429 (rate limit) |

Value is the literal response body (`String`); `Content-Type` follows the
existing JSON auto-detection. Missing keys keep today's built-in bodies
(**backward compatible**). Payloads can reference nothing (no templating in
v1) — a static body, which is the common WebService case.

---

## 5. Architecture (internal)

### 5.1 Runtime shape

- A new immutable `Policy` holder inside the generated `KofRuntime`
  (in `JvmWebSecurityRuntime.java`, where the parser already lives).
- `WebApp` gains:
  - `Policy globalPolicy` (replacing the flat `security*` fields — pure
    refactor, **no behavior change**);
  - `List<ScopedPolicy> policies` (`prefix` + `Policy`);
  - `responses` map (part of `Policy`).
- `WebRoute` gains one field: `Policy policy` (`null` for today's routes).
- The pipeline signature becomes
  `kof_web_security_pipeline(WebApp app, WebRequest req, Policy effective)`.

### 5.2 New `kof_web_*` functions

| Kof surface | Runtime function | Signature (JVM) |
|---|---|---|
| `app.policy(prefix, opts)` | `kof_web_policy` | `(String appId, String prefix, Map opts)V` |
| `app.get/post/... (path, opts) { }` | `kof_web_route` **overload** | `(String appId, String method, String path, Map opts, Object handler)V` |

`kof_web_route` keeps its 4-arg form unchanged; the 5-arg form is additive.

### 5.3 Dispatch changes (`JvmRuntimeWebDispatch.java`)

- `kof_web_security_pipeline` reads the passed `effective` `Policy` instead of
  `app.security*` fields.
- `kof_web_dispatch` computes `pathPolicy` + `routePolicy` and passes the
  effective policy to the pipeline (restructure described in 4.4).
- `kof_web_build`/rejection helpers read `responses` from the effective policy.

---

## 6. Exact code touchpoints

| # | File | Change |
|---|---|---|
| 1 | `KofWeb.java` | Add `policy` to `instanceMethod` (`:105` switch); accept the 3-arg route form (`:99`); extend `gapCode` so `kof_web_policy` and the 5-arg route are `WEB006` on non-JVM. |
| 2 | `JvmWebCoreRuntime.java` | `WebRoute` (`:93`) + `policy`; `WebApp` (`:384`) → `Policy globalPolicy` + `policies` list; `kof_web_route` (`:468`) overload; new `kof_web_policy`. |
| 3 | `JvmWebSecurityRuntime.java` | Extract `Policy` + `parse`/`merge`/`appliesTo`; `kof_web_security_opts` builds `globalPolicy`; parse `responses`. |
| 4 | `JvmRuntimeWebDispatch.java` | `kof_web_security_pipeline(..., Policy)`; effective-policy resolution in `kof_web_dispatch` (`:173`); rejection payloads. |
| 5 | `JvmRuntimeCallDescriptors.java:210` | Descriptors for `kof_web_policy` and the 5-arg `kof_web_route`. |
| 6 | `BuiltinCallTyper.java` / `ExpressionBuiltinInstanceCalls.java` (`:70`) | Route methods with `(STR, MAP, handler)`; `policy(STR, MAP)`. |
| 7 | `CompilerComparisons.java:514` | Value/void classification for the new calls. |
| 8 | `JsRuntimeUiWeb.java` + `NativeWebRuntime.java` | Compile-time gate `WEB006` for `app.policy` / route-opts (no silent ignore — R6). |

> The trailing-lambda grammar (`ExpressionParser.java:202-212`) needs **no**
> change — confirmed by reading the parser.

---

## 7. Backend impact

| Target | v1 | Rule |
|---|---|---|
| **JVM** | Full implementation. | First target (same precedent as `app.security`, `serveDir`, `ws`). |
| **Native** | `WEB006` at compile time (honest gap). | R7/R6: declared, never silent. |
| **JS** | `WEB006` at compile time. | Same. |

Parity debt is **explicit**: the plan does not promise Native/JS policies;
they remain a documented gap until separately promoted (rule 6).

---

## 8. Errors and invalid behavior (R6 — never silent)

- Unknown opt key → **ignored** (today's behavior, kept for compatibility).
  A future decision may turn this into a warning; not in v1.
- Invalid `rateLimit` (no `/`) → `IllegalArgumentException` (existing).
- Invalid `policy` prefix (empty/blank) → `IllegalArgumentException` at startup.
- `app.policy` with a non-Map opts → compile-time rejection (typer returns
  `null` → the existing "unsupported instance call" path), never a silent no-op.
- On non-JVM targets → `WEB006` compile-time (no silent policy drop).
- Errors happen when the **app is built**, before `listen`, so a misconfigured
  policy fails fast.

---

## 9. Compatibility impact

- **Additive.** Existing `app.security(opts)`, `app.use`, `app.get(path, handler)`
  and single-arg trailing lambdas are unchanged.
- The flat `WebApp.security*` fields become a `Policy` object — an **internal**
  refactor, invisible in bytecode behavior (proved by the existing hardening
  test suite staying green, slice F1).
- No grammar change, no new keyword, no new type exposed to users beyond the
  `Map` opts already used by `app.security`.

---

## 10. Risks

1. **Dispatch order regression** — moving route matching before the pipeline can
   change behavior for unknown paths. Mitigation: F2 keeps the current global
   pipeline result for all existing tests; new behavior is only reached when
   scopes exist.
2. **Policy thread-safety** — policies are read on every request; make `Policy`
   immutable and build the scope list before `listen`.
3. **Rate-limit keying** — a per-route limit needs a composite key
   `ip + effective-route-pattern`; today the counter is per IP with the global
   window. F5 must add the route dimension without breaking the global limit.
4. **Type/typer surface** — supporting `(STR, MAP, handler)` in the typer must
   not weaken the existing value/void inference (`CompilerComparisons:514`).
5. **Two targets behind** — Native/JS gating must be added with the first slice,
   or the feature leaks a silent no-op (R6 violation).

---

## 11. Alternatives considered

| Alternative | Why rejected |
|---|---|
| **Generic middleware framework** (register/reduce/next) | Contradicts the philosophy: it is a framework, adds accidental complexity, and the user must reconstruct ordering. |
| **Annotations** (`@Auth`, `@RateLimit`) | Kof has no annotations as a foundation (permanent non-goal). |
| **A `route` block syntax** (`route GET "/x" with auth { }`) | Grammar change; heavier than the existing trailing-lambda + opts form which already parses; rule 6. |
| **`kof.toml` policy config** | Cannot express runtime values (secrets, roles from code); less intention-revealing; harder to attach per endpoint. |
| **Status quo (global `app.security` only + manual `app.use`)** | Fails the core need: forces `if path() == ...` per endpoint — the exact thing to remove. |
| **Full glob/regex matchers in v1** | Accidental complexity; a prefix covers the real cases; globs would be a later rule-6 decision. |

---

## 12. Ordered implementation slices (each independently provable)

> Each slice: compile + test + `check_500`; commit per slice. No slice ships
> without a green proof and updated docs.

- **F1 — Refactor to `Policy` (no behavior change).** Extract
  `Policy.parse/merge/appliesTo`; `WebApp.globalPolicy` replaces the flat
  fields; pipeline reads the policy object. **Proof:** existing
  `KofWebHardeningTest` 6 + `KofWebE2ETest` 10 stay green (pure refactor, rule 3
  of the freeze).
- **F2 — `app.policy(prefix, opts)` (resource scopes).** Scope list + effective
  policy resolution + longest-prefix merge. **Proof:** new
  `KofHttpPoliciesE2ETest` cases — a `/admin` role applies under the prefix and
  not outside; scalar override; global default preserved for unmatched routes.
- **F3 — Endpoint opts (`app.get(path, opts) { }`)**, 5-arg `kof_web_route`,
  descriptor + typer. **Proof:** endpoint policy overrides the resource scope;
  endpoint-less route still inherits.
- **F4 — Declarative `responses` payloads.** Rejection bodies from the effective
  policy. **Proof:** 401/403/429 bodies match the declared payload; absent keys
  keep built-in bodies (compat case).
- **F5 — Per-route rate-limit keying** (`ip + route pattern`). **Proof:** two
  routes with different limits do not share the counter; the global limit still
  works.
- **F6 — Docs + decision + gaps.** `docs/stdlib/stdlib-web.md` §3,
  `training/idioms/web.md`, `DECISIONS.md` `D-HTTP-POLICIES`, roadmap Phase 4;
  confirm `WEB006` on Native/JS with a test.

---

## 13. Tests required (summary)

| Slice | Test | Focus |
|---|---|---|
| F1 | `KofWebHardeningTest`, `KofWebE2ETest` (unchanged) | zero regression |
| F2 | `KofHttpPoliciesE2ETest` (resource scope, precedence, unmatched) | the merge law |
| F3 | endpoint override + inheritance | deepest-wins |
| F4 | rejection payloads + defaults | declarative bodies |
| F5 | per-route vs global rate limit | composite key |
| F6 | `WEB006` on Native/JS + `kof check` | honest gap |

All tests compile a `.kf` program and drive it over **real sockets** (the
existing web-test pattern: `KofWebE2ETest`), so the proof is the running server,
not memory.

---

## 14. Documentation required when promoted

- `docs/stdlib/stdlib-web.md` §3 — new `app.policy` + endpoint opts + `responses`,
  with the merge law and the precedence table.
- `training/idioms/web.md` — one BAD (`if path() == ...` per endpoint) → GOOD
  (`app.policy`) idiom pair.
- `docs/development/DECISIONS.md` — `D-HTTP-POLICIES` (rule 6): surface + merge
  law + JVM-first scope.
- `docs/development/roadmap.md` Phase 4 (Security) — link the front.
- This document moves from `docs/development/future/` to `docs/development/`
  with `UNDER DEVELOPMENT` at F1 (three-states rule).

---

## 15. Promotion conditions

- Maintainer locks `D-HTTP-POLICIES` in `DECISIONS.md` (rule 6).
- The current web lane has no in-flight change to
  `JvmWebCoreRuntime`/`JvmRuntimeWebDispatch` (avoid a same-file collision).
- R12: opens only with explicit slice authorization (this is a new front on the
  language/stdlib surface — Simplicity Law gate applies to the surface before
  landing).
