[English](kofmd.md) | [Português](kofmd.pt_BR.md)

# Kofmd Specification — Typed, Intent-Oriented Markdown and Agent Memory (D-KOFMD)

> **Status: Fase 2 (Specification) — NORMATIVE for 0.5.0.** Fase 2 is the frozen
> surface decided in `docs/kofmd-plan.md` (+PT) and recorded in
> `DECISIONS.md` §`D-KOFMD`. Fase 3 (parser) is unblocked by this document.
> Based on the Fase 1 findings measured against the real tree (27/09/2026).
>
> **Contract:** Kofmd = Markdown for humans + Kof semantics for data + explicit
> intent + compact operational memory for agents. `.md` extension, degradable to
> plain Markdown, never verbose, never XML/YAML-dispatched. Golden rule:
> **intent in the fewest reasonable words, no lost semantics.**
> `Write intent, not narration.`

Conformance keywords **MUST**, **MUST NOT**, **SHOULD**, **MAY** are used as
defined by RFC 2119.

---

## 1. Scope

This document specifies the Kofmd format, its document grammar, its type and
schema rules, its agent-memory vocabulary, its canonical form, its diagnostics,
its tooling surface (`kof md`, LSP) and its interoperability with Markdown.

Everything **not** stated here is out of scope for Kofmd 0.5.0. A new surface
form **MUST** be an explicit maintainer decision (rule 6) and ship with
`spec + example + test` (plan §59).

## 2. What Kofmd is and is not

| Kofmd **is** | Kofmd is **not** |
|---|---|
| Markdown with optional typed constructs | a replacement for Markdown |
| A data and intent surface with a Kof type model | a second type language |
| Degradable to plain Markdown | XML / YAML / JSON in disguise |
| A compact operational memory for agents | a log, a transcript or chain-of-thought |
| A small, closed vocabulary | an open metadata language or a DSL |
| Data and documentation | executable content or a prompt engine |

## 3. Design principles

1. **Intent before presentation.** Represent *what it means* and *what is
   intended*, not only *how it is shown*.
2. **Do not make the AI infer.** Expose intent, state, cause, location and
   constraints explicitly instead of burying them in prose.
3. **Markdown stays Markdown.** A plain Markdown document is a valid Kofmd
   document with zero constructs; Kofmd constructs render as ordinary text.
4. **Type is not intent.** A type answers *what kind of value is this?*; an
   intent answers *why does this information exist?*. Both are representable and
   MUST NOT be conflated.
5. **Data is data; prose is prose.** What can be typed is typed; prose is used
   only where prose is genuinely needed (explanation, context, narrative).
6. **Zero redundancy.** Never repeat in prose information already represented
   structurally.
7. **Smallest adequate form.** If `next: tests` expresses the intent, do not
   write a sentence. Measure by semantics ÷ complexity (plan §56).
8. **No invention.** Unknown keys are inert data, never errors and never
   executed. No surface form is adopted from another language (rule 8/10).

## 4. Document model

A **Kofmd document** is a UTF-8 text file with the `.md` extension. It is
scanned **line by line**, and only lines that begin at **column 0** can carry
Kofmd constructs. Lines inside a fenced code block are always verbatim content.

| Term | Definition |
|---|---|
| **Line** | A sequence of characters terminated by `\n` (a final line without `\n` is valid). |
| **Construct** | A line matching one of the patterns in §6. |
| **Prose** | Any line that is not a construct and is outside a code fence. Preserved byte-for-byte. |
| **Kofmd block** | A maximal run of consecutive construct lines with no blank line, heading, prose or code fence between them. |
| **Field** | A typed key/value pair inside a block (or standing alone). |
| **Schema** | A `record` declaration (§8) that gives field types. |

Constructs and prose coexist in one file. Prose is never rewritten by the
parser; only constructs carry semantics.

## 5. Lexical structure

- Encoding **MUST** be UTF-8.
- A construct line **MUST** start at column 0. An indented line is prose/code.
- Blank lines separate blocks; they carry no semantics.
- A fenced code block (```` ``` ```` … ```` ``` ````) is prose content: its
  lines **MUST NOT** be interpreted as constructs.
- Field keys are lowercase `snake_case`: `[a-z][a-z0-9_]*`.
- Intent names are lowercase `[a-z][a-z0-9_-]*`.
- There is **no** comment syntax, **no** include directive and **no** inline
  escape beyond quoted strings (§7.2).

## 6. Constructs

The 0.5.0 surface is exactly these four constructs. Nothing else is a construct.

### 6.1 Typed field

```text
key: value
key:
```

A line whose key is a valid field key (§5) followed by `:`. The value is a
scalar (§7) or empty. An empty value **MAY** be followed by a list (§6.2). One
space after `:` is canonical; the parser **MUST** accept zero or more.

Field keys are never reserved in a way that makes unknown keys an error: an
unknown key is a valid TypedField and is inert (§17).

### 6.2 List field

```text
instructions:
  - inspect
  - preserve-api
```

A field with an empty value followed by one or more `- item` lines forms a
**list value**. The canonical indentation is two spaces; the parser **MUST**
accept column 0 and any consistent indentation. A list ends at the first line
that is not a list item. Lists are canonical for `instructions` (§10) and are
valid for any field.

### 6.3 Block intent

```text
@decision
```

A line beginning with `@` followed by an intent name annotates the **following
block** (the next non-blank Markdown block: paragraph, list, table or fenced
code). It makes explicit the intention the block would otherwise require
inference to recover. Multiple intents MAY stack; they apply to the same target
block.

The reserved intent vocabulary is listed in Appendix A. An intent name outside
the vocabulary is a diagnostic (§14), never a silent extension.

### 6.4 Schema declaration

```text
record TestResult(Int total, Int passed, Int failed)
```

A schema declaration uses the **real Kof `record` syntax** (type before field
name). This is deliberately the same declaration the Kof compiler already
parses; Kofmd introduces **no new type-declaration syntax**. A schema names the
types of the fields it describes so that typed fields can be validated against
it (§8).

### 6.5 Prose

Headings, paragraphs, lists, blockquotes, tables, links, images, emphasis and
code blocks are ordinary Markdown and are **preserved with zero semantics**.

## 7. Values and types

### 7.1 Scalar inference

Kofmd reuses the real Kof type surface — it does **not** invent primitives. A
scalar value is inferred as follows:

| Lexeme | Inferred type | Example |
|---|---|---|
| `true` / `false` | `Bool` | `stable: false` |
| `-?[0-9]+` fitting in 32-bit signed | `Int` | `retries: 3` |
| decimal / exponent form | `Float` | `ratio: 0.5` |
| `"…"` (quoted) | `String` (explicit) | `version: "0.5.0"` |
| any other non-empty token | `String` (bare) | `location: compiler/parser` |
| empty (with or without a list) | absent / list | `next:` |

Rules:

- There is **no `null` literal**. Absence is expressed by omitting the field.
- `Int` is 32-bit signed. A larger integer literal is not inferred `Int`; it is
  treated as a bare `String` until a schema or explicit quote says otherwise.
- A bare `String` is a short token (path-like or `kebab-case` recommended). It
  **MUST NOT** be used to smuggle a sentence; that is prose (§3.6).
- Quoting a scalar forces `String`; quotes are not preserved in the canonical
  output unless required to preserve a value that would otherwise be re-typed
  (e.g. `"true"`, `"3"`).

### 7.2 Adopted types and refused types

**Adopted** (real Kof surface): `Bool`, `Int`, `Float`, `String`, and `record` as
the data carrier. Nullability is spelled `T?`; errors are `throw "msg"`.

**Not adopted as Kofmd surface types** (fake-idiom guard, `D-KOFMD` item 4):
`Option<T>`, `Result<T,E>`, `Date`, `Time`, `DateTime`, `Duration`, `UUID`,
`URL`, `Path`, `Bytes`. Schema-declared scalar refinements
(`date`/`time`/`uuid`/`url`/`path`) validate as `String` plus a documented format
check, never as a new primitive. Requests for the refused list are answered by
the Kof idiom (`String?` + narrowing; `throw`) and closed as "Kof is not Java"
(rule 8).

## 8. Schema binding and validation

- A schema is declared with §6.4. Field names in a schema are the field keys
  they validate.
- A typed field whose key matches a declared schema field **MUST** be validated
  against that field's type. A mismatch is `MD002`.
- A field with no schema is validated by scalar inference only.
- A schema field that is missing from the document is **not** an error unless
  the schema marks it required; required-field marking is a **non-goal** for
  0.5.0 (declared, not silently omitted).
- Unknown fields are never an error. Unknown fields **MUST NOT** be coerced into
  a schema.

## 9. Agent-memory vocabulary

The genuinely new surface. The following keys are special. No other key is
special; any other key is a plain TypedField.

| Key | Kind | Meaning | Canonical value |
|---|---|---|---|
| `last` | continuity | last relevant state/context | short token |
| `doing` | continuity | current intent/activity | short token |
| `next` | continuity | next known intent | short token |
| `location` | context | where the intent applies | path-like token |
| `state` | status | current state | token (Appendix A) |
| `instructions` | action | explicit operational intent | list of tokens |
| `constraint` | rule | restriction that must hold | token |
| `decision` | fact | option that was chosen | token |
| `result` | outcome | outcome of work | token (Appendix A) |
| `question` | dialogue | open question | token |
| `answer` | dialogue | answer to a question | token |
| `reason` | modifier | why (qualifies the preceding fact/state/result) | token |
| `symbol` | modifier | symbol (qualifies `location`) | token |

Semantics:

- `last` is the **immediately previous** context, never a full history.
- `doing` is the current intent, not a sentence describing it.
- `next` is the next known intent, never a backlog.
- `location` is short and contextual: file, directory, module, symbol, issue,
  PR, branch, endpoint, environment or identifier — it is **not** necessarily a
  filesystem path.
- `state` and `result` are typed to a recommended closed set (Appendix A);
  values outside it are permitted in 0.5.0 but are flagged by `kof md check`
  as `MD002` only when a schema or the future enum rule requires it.
- `reason` **MAY** follow `state`, `decision` or `result`; `symbol` **MAY**
  follow `location`. A modifier without a target is allowed but discouraged.

## 10. Instructions

`instructions` carries **explicit operational intent for the consuming agent**.
Canonical shape:

```text
instructions:
  - inspect
  - preserve-api
  - test
```

A single instruction MAY be written inline: `instructions: inspect`.

Rules:

- Instructions are **data**, never authorization. A document does not gain the
  right to be executed because it contains `instructions:` (§17).
- The instruction vocabulary is the recommended closed set in Appendix A. An
  instruction outside it is not a syntax error, but the consumer **MUST NOT**
  act on it unless it is recognized and authorized.
- Instructions **MUST NOT** be free prose. A sentence where a token suffices is
  a spec violation (§3.6), not a stylistic choice.

## 11. Canonical form

The canonical form is deterministic. A formatter output **MUST** satisfy:

1. Fields `last`, `doing`, `next`, `location`, `state` come **first, in that
   order**, when present.
2. Remaining fields follow in ascending key order (byte order).
3. Exactly one space after `:`; no trailing whitespace.
4. Lists are one `- item` per line, indented two spaces, in declared order.
5. A block is a single contiguous run; blocks are separated by one blank line.
6. No prose echo of a field. No duplicate fields in a block.
7. Quoting is added only when needed to preserve the inferred type of a value
   that would otherwise be re-typed.

## 12. Formatter

- The formatter is **idempotent**: `format(format(x)) == format(x)`.
- The formatter **MUST NOT** alter prose bytes, code fences, headings, tables or
  any line that is not a construct.
- The formatter **MUST** be deterministic: same semantics ⇒ same bytes.
- The formatter is exposed as `kof md format` (§15) and in the LSP (§16).

## 13. Interoperability and round-trip

| Direction | Guarantee |
|---|---|
| Plain Markdown → Kofmd | Always valid; zero constructs. |
| Kofmd → Markdown | Valid Markdown: construct lines render as ordinary text. Semantic extraction is separate from rendering. |
| Kofmd → parse → canonical → parse | **MUST** preserve semantics (idempotent canonical form). |
| Kofmd → Markdown → Kofmd | **MUST** preserve semantics when constructs are kept as literal lines; if a renderer drops them, the loss is documented, never silent. |

Markdown features that Kofmd **MUST** preserve: headings, paragraphs, lists,
links, images, code blocks, tables, blockquotes and emphasis.

## 14. Diagnostics

Every diagnostic is **named and never silent** (R6). The namespace is `MDxxx`.

| Code | Name | Trigger | 0.5.0 |
|---|---|---|---|
| `MD001` | target without backing | a Kofmd capability is requested on a target that does not implement it (JVM-first) | shipped |
| `MD002` | type/schema mismatch | an inferred/explicit type disagrees with a schema type, or an invalid value for a typed slot | shipped |
| `MD003` | malformed construct | a construct-shaped line fails to parse (reserved) | reserved |

Diagnostics **MUST** carry a line number and a short message. They **MUST NOT**
be downgraded to a warning to make a gate pass. Unknown keys are **not** a
diagnostic.

## 15. CLI

The CLI surface follows the existing `kof-cli` dispatch and the `CmdCheck`
pattern. One class per verb, subject to the ≤500-line gate.

```text
kof md check <file>     # parse + validate; non-zero exit on MDxxx
kof md format <file>    # rewrite in canonical form (idempotent)
```

`kof md convert` is a **non-goal** for 0.5.0 (§21) and is refused honestly.

## 16. LSP

Kofmd rides the existing LSP server (`LspServer`) — there is **no** second
server. The `.md` file hook provides:

- **diagnostics** for `MDxxx`;
- **hover** for field keys and inferred types.

Completion, go-to-definition and full schema completion are **non-goals** for
0.5.0 (declared).

## 17. Security

Kofmd is **data and documentation**; it never executes code implicitly.

- The parser and validator **MUST NOT** evaluate, fetch or execute any content.
- There are no includes, macros, imports, remote references or expression
  evaluation.
- Links, images and URLs are inert data; nothing is dereferenced.
- Unknown fields are inert and **MUST NOT** trigger behavior.
- `instructions` is **not** an authorization channel: the consumer decides which
  instructions are valid and authorized. `unknown ≠ execute`.
- A Kofmd document **MUST NOT** be treated as a trusted prompt. Prompt-injection
  content in prose has no special status.

## 18. Testing requirements

Implementation **MUST** ship an E2E test per capability (`KofmdE2ETest`) and a
golden corpus, per the quality gate (Q0–Q7). The suite covers:

```text
kofmd syntax · parser · types · semantics · intent · agent-memory
instructions · markdown compatibility · roundtrip · formatter · schema
ai patterns · diagnostics · lsp
```

Tests prove both directions (`Markdown→Kofmd→Markdown` and
`Kofmd→Markdown→Kofmd`) where semantically possible. A test that only covers
the happy path is insufficient (Q3); numeric, empty, limit, error,
cross-target, idempotency and malformed-input edges **MUST** be covered where
applicable.

## 19. Golden corpus

A corpus of small files under `kofmd/`, one idea per file:

```text
basic · typed-data · schema · mixed-markdown
agent-memory · agent-task · agent-context · agent-instructions
agent-handoff · agent-blocked · agent-result · agent-decision
```

Each file is simultaneously documentation, test, example and AI-evaluation
material. Consistency matters more than volume.

## 20. AI rules (normative)

1. Data → typed fields. 2. Intent → semantic fields. 3. State → explicit state.
4. Context → `location`. 5. Action → `instructions`. 6. Continuity →
`last`/`doing`/`next`. 7. Explanation → Markdown prose. 8. Do not repeat
information. 9. Do not invent fields. 10. Do not invent types. 11. Prefer flat
structures. 12. Prefer short answers. 13. Preserve schema. 14. Preserve intent.

An AI writing Kofmd **MUST NOT**: invent syntax, invent types, produce huge
structures, repeat information, mix data and prose, break a schema, or turn
memory into narrative.

## 21. Non-goals for 0.5.0

- Aggregate/record-valued typed fields (only scalars and lists ship).
- Required-field marking, enum enforcement and value refinement validation.
- `kof md convert`, full LSP completion, go-to-definition.
- New primitive types or the refused type list (§7.2).
- `kof.file` promotion; doc-wide migration.
- Any syntax outside §6. Everything beyond the frozen surface is an honest
  refusal (R6), not a stub (Q7).

## 22. Phases and acceptance

Implementation follows the plan slices 3.1→3.9 (complete vertical cuts with
proof). Acceptance for 0.5.0:

1. Parser green on the golden corpus; prose byte-preserved.
2. Scalar inference and schema validation with `MD002` RED→GREEN.
3. Agent-memory vocabulary validated; `instructions` list shape enforced.
4. Markdown round-trip preserves semantics.
5. `kof md format` idempotent; `kof md check` exits non-zero on `MDxxx`.
6. LSP reports `MDxxx` and hovers inferred types.
7. Full suite green; no regression (quality gate).

---

## Appendix A — Reserved vocabularies (closed by addition only)

**Block intents** (`@name`):

```text
information · decision · requirement · task · question · answer · result
warning · configuration · example · api · message · explanation · release
```

**Instructions** (recommended closed set):

```text
inspect · preserve-api · no-breaking-change · run-tests · update-docs
do-not-refactor · verify-backends · add-tests · implement · test
```

**Advisory value sets** (enforcement deferred, §9):

```text
state:  active · blocked · done · failed
result: pass · fail · blocked
```

## Appendix B — Grammar (EBNF)

```ebnf
document     = { line } ;
line         = block-intent | schema-decl | typed-field | list-item | other ;

block-intent = "@" intent-name , newline ;
schema-decl  = "record" , ws , type-name , "(" , [ schema-field , { "," , ws , schema-field } ] , ")" , newline ;
schema-field = type-name , ws , field-key ;

typed-field  = field-key , ws , ":" , [ ws , scalar ] , newline , [ list-value ] ;
list-value   = { ws , "-" , ws , scalar , newline } ;
scalar       = bool | int | float | quoted-string | bare-string ;

bool         = "true" | "false" ;
int          = [ "-" ] , digit , { digit } ;
float        = [ "-" ] , digit , { digit } , "." , digit , { digit } , [ exponent ] ;
quoted-string= '"' , { char | escape } , '"' ;
bare-string  = non-space , { non-space } ;
intent-name  = lower , { lower | digit | "_" | "-" } ;
field-key    = lower , { lower | digit | "_" } ;
type-name    = upper , { letter | digit | "_" } ;
ws           = { " " | tab } ;
```

`other` is any prose line, heading, code fence, table or blank line; it is
preserved verbatim.

## Appendix C — Worked example

```text
# Handoff — type-system front

@decision
Adopt flow-sensitive narrowing on `x != null`.

location: compiler/type-system
last: null-safety
doing: ownership
next: lifetime
state: active
instructions:
  - inspect
  - preserve-api
  - add-tests
constraint: no-breaking-change
reason: smaller-diff

Ownership prevents invalid mutable aliasing.
```

- `@decision` applies to the following paragraph.
- The block validates: `state` ∈ advisory set; `instructions` is a list.
- `reason` qualifies the `constraint`; `location` + `symbol` would refine a
  target.
- The prose paragraph is preserved untouched.
