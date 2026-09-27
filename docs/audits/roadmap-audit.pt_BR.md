[English](roadmap-audit.md) | [Português](roadmap-audit.pt_BR.md)

# Roadmap Audit — Matriz de Implementação (06/09/2026; resync 12/09, toque 13/09)

> Auditoria de código real (3 explorações, file:line) + execução de suíte. Regra: estado REAL, não o que o roadmap diz. Baseline `0.3.0-beta`. Contagens de teste = gate 12/09 dos surefire-reports (`docs/status.md` §Testes, `e2c03812`); células que mudaram desde 06/09 foram reescritas com prova (SG-009/WEB001/conformance/LSP/GC). 13/09: P4 marcado FECHADO (contradizia a linha 25 — SG-009 ✅ desde 10/09).

## Matriz de estado

| Item | Estado real | Código | Testes | Gap principal |
|---|---|---|---|---|
| 1. Standard Library | **PARTIAL (bom)** | 23 namespaces em `Kof*.java` com gates R6 (`supportedOn`/`gapCode`) | E2E por área (KofCache/Db/Http/Mq/Time/…) | web JS tem base REAL (GraalJS HttpServer `bc577aa` 03/09 — não é mais stub silencioso; ws/sse = residual declarado `WEB001`, `kofWebStub` só fallback); sec sem cross = gap honesto `SECN000`; db/orm JVM-only; `scheduler.at` cron fake |
| 2. GC auto-collect | **PARTIAL** | mark-sweep real x86_64 (`RuntimeGc.java`); **SEM safe-points/root-map**; auto-collect desligado (`RuntimeMemory.java:121-133`); riscv/aarch bump **sem GC** (G-0 `356f33b9`: bloco-header 32B + guard OOM) | `KofGcE2ETest` 3/3 | face (1) decomposta em **G-1..G-5** (`native-multiarch.md` §decomposição): G-1 free-list riscv próximo (sem pré-requisito) → G-2 header flags → G-3 mark (depende do `kof_heap_root_end`, S-5-x86 fila bugfix) → G-4 sweep → G-5 aarch herda |
| 3. Package Manager | **MVP** | `Deps.java` (flat Maven Central, cache `~/.kof/deps`) | `DepsTest` 4/4 | POM/transitivas, lockfile, ranges, publish |
| 4. Async | **PARTIAL** | JVM vthreads + Handle/await/timeout; Promise JS real (CONC003 ✅); Native pthread | `KofConcurrency2Test` 33 | timeout/cancel/selectAny fechados nos 3 targets; resta: select sobre channels |
| 5. Concurrency G8 | **PARTIAL (bom)** | spawn/await/cancel/selectAny/awaitTimeout/channel/scheduler 3 targets | idem | `scheduler.at` cron = 60s fixo (MVP); cancel por TID%256 |
| 6. KofAndroid | **DONE (ressalva)** | `Target.ANDROID`; `--apk` (d8/aapt2/apksigner); `AndroidProjectWriter` (Maven) | depende de ANDROID_HOME | lifecycle/ART na Fase 2; consolidar docs |
| 7. Debugger | **MVP** | DAP stdio JVM (`KofDebug.java`), breakpoints JDWP reais; DWARF line-only | — | **locals = placeholder** (`"line N"`, `KofDebug.java:197`, verificado 12/09); stepping/evaluate; ext VS Code |
| 8. KofJS | **PARTIAL (alpha → funcional)** | ESM + source maps V3 + GraalJS + DOM/UI (9 arquivos); web base real (GraalJS HttpServer `bc577aa`) | `KofJsE2ETest` 40 | ws/sse **residual declarado WEB001** (gap honesto; não é stub silencioso); serve×JS indireto |
| 9. LSP | **PARTIAL (bom)** | diagnostics reais via CompilerDriver; hover/completion/references/rename textuais + **go-to-definition ✅** (`LspServer.java:323`) | `LspServerTest` 4/4 | hover/completion/rename devem usar SymbolTable |
| 10. KofScript | **PARTIAL (bom)** | interpretador de IR compartilhado | `KofScriptTest` 31 + gate paridade | globals por regex multiline-frágil; REPL re-avalia tudo |
| 11. Language Spec | **PARTIAL (bom)** | `docs/language-reference/` 16 arquivos | — | **fila SG COMPLETA** (SG-001–020 + E1–E3 resolvidas 06–12/09; SG-009 subtipagem ✅ SEM021 `StatementAnalyzer.java:154`); gramática não-normativa |
| 12. Conformance Suite | **PARTIAL** | `conformance-matrix.md` (07/09), células travadas por `ConformanceMatrixTest` (11) + `ConformanceMatrixDocTest`; `BackendParityTest` 16 casos | — | próximos lotes por categoria |
| 13. Full Web Platform | **NOT STARTED** | routing parcial no kof.ui; validação existe | — | declarativo/forms/SSR — depende de 8+9 |
| gRPC | **NOT STARTED** | — | — | planejado; não iniciar antes de P0-P2 |
| Auto-hosting | **NOT STARTED** | — | — | documentado como gap |

## Bugs semânticos críticos (P0) — fallbacks silenciosos UNKNOWN

A auditoria achou **12 fallbacks silenciosos** que aceitam programas inválidos. Os 4 maiores (todos em `SemExpressionTyper`/`MemberCallTyper`):

1. **#7 — maior**: método inexistente em namespace builtin → UNKNOWN sem SEM025. Só `process`/List/Map/Set corrigidos (`7ec8b9d`, bugs 31/34).
2. **#3 —** `obj.campoInexistente()` → UNKNOWN (SemExpressionTyper.java:312).
3. **#6 —** `super.metodoInexistente()` → UNKNOWN (MemberCallTyper.java:92) enquanto o caminho normal de classe emite SEM025.
4. **#8 —** receiver UNKNOWN + método inexistente → sem diagnóstico (SEM025 só quando `isKnownReceiver`, MemberCallTyper.java:384-386).

Regra do plano: inferência nunca cria declaração implícita; identificador inexistente deve falhar.

> **STATUS 07/09:** #7, #3, #6 **CORRIGIDOS** — `unknownNamespaceMethod` + gate `isKnownReceiver`; prova `SemanticResolutionTest` 6/6. #8 é error-recovery legítimo (manter UNKNOWN). **P0 FECHADO.**

## Ordem de execução (ajustada pela auditoria)

- **P0**: ~~fallbacks semânticos~~ **FECHADO 07/09**.
- **P1**: GC auto-collect — face cross G-1..G-5; G-0 ✅ `356f33b9`, G-1 free-list riscv próximo; G-3 depende do `kof_heap_root_end` (fila bugfix S-5-x86).
- **P2**: PM lockfile+transitivas; debugger locals via JDWP VariableTable; LSP hover/references via SymbolTable (go-to-definition ✅).
- **P3**: cron real (`scheduler.at`); KofScript globals via frontend.
- **P4**: ~~conformance suite estruturada; spec §subtipagem (SG-009)~~ **FECHADO** (matriz + `ConformanceMatrixTest`/`ConformanceMatrixDocTest` como gate de CI; SG-009 ✅ SEM021 09/10).
- **P5**: web platform, gRPC, auto-hosting (não iniciar antes).

## Evidência bruta

Ver relatório de auditoria 06/09 (3 explorações): stdlib (23 áreas + paridade por target), tooling (CLI 20 comandos, PM, debugger, KofJS, LSP, KofScript), semântica (12 fallbacks, cobertura SEM025, pipeline).
