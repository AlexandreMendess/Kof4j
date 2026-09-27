# Debt taxonomy — three axes

Normative reference for `scripts/debt-scout/`. See `DEBT_SCOUT_CONTRACT.md`
§4. A candidate always sets exactly one `debt_type`, exactly one
`primary_domain` (plus any number of secondary `domains`), and exactly one
`mechanism`.

## Axis A — Debt type (TDM category)

```text
REQUIREMENTS_CONTRACT
ARCHITECTURE
DESIGN
CODE
TEST
BUILD
DOCUMENTATION
INFRASTRUCTURE
VERSIONING_COMPATIBILITY
```

`REQUIREMENTS_CONTRACT` includes KOF normative/language decisions.

## Axis B — KOF domain

```text
PARSER_GRAMMAR   SEMANTICS        TYPE_SYSTEM      NULLABILITY
GENERICS_ERASURE JVM              JS               SCRIPT
NATIVE_X86       NATIVE_RISCV     NATIVE_AARCH64   FFI_ABI
RUNTIME_MEMORY_GC CONCURRENCY     STDLIB           DIAGNOSTICS
CLI_TOOLING      BUILD_RELEASE    CI_GOVERNANCE    SECURITY
DOCS_TRAINING    ECOSYSTEM
```

## Axis C — Debt mechanism

```text
WORKAROUND               DUPLICATED_CONTRACT_LOGIC  TARGET_DIVERGENCE
ACCIDENTAL_CONTRACT       PARTIAL_MIGRATION          DISABLED_GATE
OBSOLETE_ASSUMPTION       RECURRING_REGRESSION       COMPATIBILITY_LOCK_IN
ARCHITECTURE_EROSION      DEPENDENCY_LOCK_IN         MISSING_TEST_ORACLE
DOC_CODE_DRIFT            MANUAL_PROCESS             MIGRATION_GAP
```

## Example

```yaml
debt_type: VERSIONING_COMPATIBILITY
primary_domain: NULLABILITY
domains: [NULLABILITY, JVM, JS, NATIVE_X86]
mechanism: COMPATIBILITY_LOCK_IN
```

## Lock-in scale (used alongside the taxonomy, contract §"Principal,
Interest and Lock-in")

```text
NONE  INTERNAL  TEST_ENCODED  DOCUMENTED  NORMATIVE  ECOSYSTEM_OBSERVED
```
