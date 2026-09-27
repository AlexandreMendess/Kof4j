[English](kofmd-plan.md) | [Português](kofmd-plan.pt_BR.md)

# Kofmd implementation plan — typed, intent-oriented Markdown (D-KOFMD)

> **State (27/09): IN DEVELOPMENT — Fase 1 (investigation) DONE in this
> doc; Fase 2 (this plan) lands here; Fase 3+ execute as slices below.**
> Explicit maintainer decision 27/09/2026 (`D-KOFMD`): Kofmd is
> INDISPENSABLE for 0.5.0 — doc verbosity is the problem it solves.
> Queue: roadmap §23 (opened in the same commit as `D-KOFMD`).

## 0. Contract (the 37+13 spec, condensed — the full text lives in chat)

Kofmd = **Markdown for humans + Kof semantics for data + explicit intent
+ compact operational memory for agents.** `.md` extension, degradable to
plain Markdown, never verbose, never XML/YAML/JSON-disguised, never a
metadata language. Golden rule: **intent in the fewest reasonable words,
no lost semantics.** `Write intent, not narration.`

Agent-memory core (the genuinely new surface): `last` / `doing` / `next`
/ `location` / `state` / `instructions` / `constraint` / `decision` /
`result` / `question` / `answer` (+ `reason`, `symbol` as modifiers).

## 1. Fase 1 findings — MEASURED against the real tree (27/09)

| Spec need | Real infrastructure | Verdict |
|---|---|---|
| Lexer/parser to reuse | `parser/Lexer.java` (509 ln) + `parser/Parser.java` (403 ln) + `parser/AnnotationParser.java` (168 ln) + `parser/TypeParser.java` (324 ln) | Kof grammar only — there is **no Markdown parser** anywhere in the tree (only a MIME sniff `case "md"` in `JvmMediaWebRuntime`). Markdown-block parsing is new code, but lives in its own class, never touches the Kof grammar |
| `@block` syntax (`@decision`…) | `AnnotationParser`: `@Name(k = v)`, compile-time constants, interop metadata preserved to IR/bytecode | **Real precedent**: `@`-prefix already parses in Kof. Kofmd block intents reuse the `@Name` shape at doc level — zero grammar conflict |
| Type system to reuse | `Type.java`: sealed `PrimitiveType/ClassType/TypeVariable/FunctionType/ArrayType/WildcardType/UnknownType/NullableType`; primitives = `String Int Bool Float Double Long Char Void Object` + `List/Map/Set` builtins; `record` = the data carrier; `json.encode/decode<T>` = the serialization precedent | **No `Option<T>`/`Result<T,E>`/`Date/Time/DateTime/Duration/UUID/URL/Path/Bytes` as surface types** (spec §6 list is NOT adopted wholesale — fake-idiom guard D-KOFMD item 4). Absence = `String?` + narrowing; error = `throw "msg"`. Schema-declared scalar refinements (date/time/uuid/url/path) validate as `String` + format check, never new primitives |
| Schema declaration | `record Point(Int x, Int y)` + `json.decode<T>` compile-time fold | Kofmd schemas ARE Kof records. `type TestResult {...}` (spec §37) is spelled `record TestResult(Int total, Int passed, Int failed)` — no new declaration syntax |
| Data carrier | `record` (immutable, accessors, `json` support JVM+JS) | Agent-memory blocks decode into records; no new runtime value kind |
| CLI home | `kof-cli/Main.java` dispatch (`build/run/check/test/fmt/lsp/...`); `CmdCheck` = the check-shaped precedent | `kof md check/format/convert` follows `CmdCheck`/`Fmt` patterns; subcommand `md` under `Main`, one class per verb (≤500 gate) |
| LSP home | `LspServer.java` + `LspHover/LspSymbols/LspRename/...` | Kofmd diagnostics/hover/completion ride the existing server; `.md` file hook, no second server |
| `kof.file` relation | `future/kof-file-plan.md` (FUTURE, zero code): unified file/format API, R1 boundary (heavy codecs = official packages), R9 interop-first | Kofmd file I/O (read `.md`, write canonical) composes `kof.io` text faces; never duplicates `kof.file` — when `kof.file` promotes, Kofmd rides it |
| Test infra | E2E `*E2ETest` per area + golden files + `ConformanceMatrixTest` | `KofmdE2ETest` + golden corpus `kofmd/*.md` (one idea per file, spec §48) |

## 2. Frozen surface (Fase 2 decision — what ships, nothing more)

**Doc-level grammar** (a `.md` file is scanned line-block-wise; anything
that is not a Kofmd construct is preserved prose — Markdown stays valid):

```text
# Heading / paragraph / list / code / table  → preserved, zero semantics
key: value                                    → TypedField (scalar infer: Bool/Int/Float/String)
@intent                                       → block intent for the following block
record Name(Field: Type, ...)                 → schema declaration (real Kof record syntax)
```

**Agent-memory vocabulary (closed initial set, spec §§12–26):** `last`,
`doing`, `next`, `location`, `state`, `instructions` (list), `constraint`,
`decision`, `result`, `question`, `answer`, `reason`, `symbol`. No other
key is special — unknown keys are plain `TypedField`s (never an error;
security §54: unknown ≠ execute).

**Canonical form:** `last/doing/next/location/state` first (this order),
then alphabetical; one space after `:`; lists as `- item`; no trailing
prose echo of a field (zero-redundancy rule).

## 3. Slices (each = complete vertical cut + proof, Q0–Q7)

| # | Slice | Proof |
|---|---|---|
| 3.1 | `KofmdTool`: block scanner + `TypedField`/`@intent` parse → `record KofmdDoc` (JVM-first; others `MD001` honest) | `KofmdE2ETest` parse goldens |
| 3.2 | Scalar type inference + validation vs `record` schema (Bool/Int/Float/String; mismatch = `MD002`) | mismatch RED→GREEN |
| 3.3 | Agent-memory vocabulary validation (closed set; `instructions` list shape) | vocab goldens |
| 3.4 | Markdown round-trip (`Kofmd→Markdown→Kofmd` preserves semantics; prose untouched) | round-trip goldens |
| 3.5 | Canonical formatter (deterministic emit) | idempotence `fmt(fmt(x))==fmt(x)` |
| 3.6 | CLI `kof md check/format` (`CmdMd`, `CmdCheck` pattern) | CLI E2E |
| 3.7 | LSP hook (diagnostics for `MD002` + hover) | `LspServerTest`-style |
| 3.8 | Golden corpus `kofmd/*.md` + AI read/write tests (spec §§48/51) | corpus green |
| 3.9 | Gradual migration of HOT agent-working docs only (see §5) | consistency, not volume |

Gap codes: `MD001` (target without backing) / `MD002` (type/schema
mismatch). Both named, never silent (R6).

## 4. Non-goals for 0.5.0

Full-LSP completion, `kof.file` promotion, doc-wide migration, new
primitive types. Anything beyond the table above = honest refusal.

## 5. Migration scope — maintainer order 27/09: hot working docs ONLY

Kofmd migration covers **only the hot working documents agents read and
write every turn**: `DOING.md`(+PT), `docs/status.md`(+PT),
`docs/development/*-plan.md`(+PT) queue headers, `CHANGELOG.md`(+PT)
entries, `docs/bugs-and-gaps/known-bugs.md`(+PT) ledger rows,
`docs/development/roadmap.md`(+PT) §23 queue lines — i.e. the state files
named by the autonomous loop (§"The loop": READ the state → CHOOSE →
CLAIM → EXECUTE → GO BACK).

**Explicitly OUT of scope: `learn/` and `training/`.** They are the
human/AI teaching corpus (tutorials + idioms/anti-patterns) — prose-first
by design, consumed as narrative, versioned as doctrine. Converting them
would destroy the very readability they exist to provide, for zero
operational gain: no agent loop reads `learn/` per turn. If a future
maintainer decision ever re-opens this, it re-opens as its own rule-6 vote,
never as drift inside slice 3.9.
