[English](TEST-PERFORMANCE.md) | [Português](TEST-PERFORMANCE.pt_BR.md)

# Performance da suíte de testes — registro medido

last: phase-1-profiling-tooling
doing: test-architecture-phase-1
next: phase-2-quick-wins
location: docs/testing
state: active
intent: measured-suite-performance
constraint:
  - read-only-profiler
  - no-compiler-change
  - measured-not-remembered
decision: D-TEST-ARCHITECTURE-GO

Fase 1 (profiling) de [`docs/development/test-architecture-plan.md`](../development/test-architecture-plan.md).
Os números abaixo são **medidos**, nunca lembrados: vêm dos relatórios Surefire
já presentes na árvore, parseados por `scripts/test-suite-profile.sh` (read-only
— nunca re-executa a suíte).

## Como regerar

```
scripts/test-suite-profile.sh --top 20
scripts/test-suite-profile.sh --top 20 --md /tmp/suite-tables.md
```

O profiler totaliza tests/failures/errors/skipped/time e ranqueia os casos de
teste, classes e módulos mais lentos. Sem relatório = `rc 3` (nunca inventa
número).

## Snapshot

**Medido:** `tests=4368 failures=2 errors=0 skipped=125 time=2451.201s` em
4 módulo(s) / 502 classe(s) / 502 relatório(s) Surefire.

> **Base honesta:** um agregado dos arquivos `TEST-*.xml` presentes na árvore no
> momento da coleta (timestamps de execuções mistos — não é uma corrida limpa
> única). As 2 falhas pertencem a registros de corridas em voo de outras lanes;
> esta frente é infraestrutura pura e não roda suíte. Um snapshot limpo de
> corrida única é produzido sempre que a suíte completa é executada.

### 20 casos de teste mais lentos

| # | Tempo (s) | Teste | Módulo |
|---|---------:|-------|--------|
| 1 | 243.181 | `dev.kof.compiler.ArrayBoundsDeepStressTest#stress020_reducedSoakKofJsOnly` | `kof-compiler` |
| 2 | 90.62 | `dev.kof.compiler.ArrayBoundsDeepStressTest#deepStress003_oneMillionMixedIndexSeveralSeeds` | `kof-compiler` |
| 3 | 66.099 | `dev.kof.compiler.RingPrivilegeE2ETest#ring1BuiltinRunsKofFunctionAtCpl1` | `kof-compiler` |
| 4 | 56.989 | `dev.kof.compiler.ConformanceMatrixTest#conformanceCoreArithmetic` | `kof-compiler` |
| 5 | 23.584 | `dev.kof.cli.KofDebugJvmStepTest#stepThenImmediateStackTraceNeverLosesTheStoppedThread` | `kof-cli` |
| 6 | 21.75 | `dev.kof.cli.CmdWorkflowTest#realCiPipelineExampleRunsEndToEnd` | `kof-cli` |
| 7 | 17.728 | `dev.kof.cli.DepsRegistryTest#tarballSelectionKeepsExactThenJvmThenFirstTarGz` | `kof-cli` |
| 8 | 17.34 | `dev.kof.cli.CmdMakealiveTest#jsTargetFacesJvmBytes` | `kof-cli` |
| 9 | 17.194 | `dev.kof.cli.CmdMakealiveTest#destroyRemovesAllReverseTopoAndStateRebirthsOnNextPlan` | `kof-cli` |
| 10 | 17.143 | `dev.kof.cli.CmdWorkflowTest#runJsDryRunAndJobFacesJvm` | `kof-cli` |
| 11 | 15.112 | `dev.kof.compiler.ConformanceMatrixTest#conformanceJson` | `kof-compiler` |
| 12 | 14.808 | `dev.kof.compiler.ArrayBoundsDeepStressTest#deepStress011_millionElementArrayHeavyRejectionLoop` | `kof-compiler` |
| 13 | 13.42 | `dev.kof.compiler.InteropTimeoutE2ETest#cancelIdleIsNoopOnCross` | `kof-compiler` |
| 14 | 12.544 | `dev.kof.cli.CmdMakealiveTest#applyPersistsGenerationAndPlanBecomesEmpty` | `kof-cli` |
| 15 | 11.314 | `dev.kof.cli.CmdWorkflowTest#listShowsJobsAndDeps` | `kof-cli` |
| 16 | 10.97 | `dev.kof.script.KofScriptTest#interpreterParitySweep` | `kof-script` |
| 17 | 10.625 | `dev.kof.cli.CmdWorkflowTest#runFailureExitsOne` | `kof-cli` |
| 18 | 10.548 | `dev.kof.cli.DepsSourceModuleTest#libraryPackageWithoutJarInstallsVerifiedSourcesAndIsIdempotent` | `kof-cli` |
| 19 | 9.821 | `dev.kof.cli.DepsRegistryTest#pullResolvesKofReleaseClasspathSeesTheJarAndIsIdempotent` | `kof-cli` |
| 20 | 9.616 | `dev.kof.compiler.PdfLibraryReachProbeTest#probeReach` | `kof-compiler` |

### 20 classes mais lentas

| # | Tempo (s) | Classe | Módulo |
|---|---------:|--------|--------|
| 1 | 366.279 | `dev.kof.compiler.ArrayBoundsDeepStressTest` | `kof-compiler` |
| 2 | 113.055 | `dev.kof.compiler.ConformanceMatrixTest` | `kof-compiler` |
| 3 | 102.585 | `dev.kof.cli.CmdWorkflowTest` | `kof-cli` |
| 4 | 76.124 | `dev.kof.compiler.RingPrivilegeE2ETest` | `kof-compiler` |
| 5 | 65.871 | `dev.kof.cli.CmdMakealiveTest` | `kof-cli` |
| 6 | 64.733 | `dev.kof.cli.DepsRegistryTest` | `kof-cli` |
| 7 | 51.059 | `dev.kof.compiler.KofWebE2ETest` | `kof-compiler` |
| 8 | 48.789 | `dev.kof.cli.DecompileTest` | `kof-cli` |
| 9 | 43.956 | `dev.kof.compiler.InteropTimeoutE2ETest` | `kof-compiler` |
| 10 | 41.824 | `dev.kof.compiler.KofSecurityTest` | `kof-compiler` |
| 11 | 40.225 | `dev.kof.compiler.KofOrmE2ETest` | `kof-compiler` |
| 12 | 38.348 | `dev.kof.cli.DepsSourceModuleTest` | `kof-cli` |
| 13 | 30.869 | `dev.kof.compiler.NullablePrimitiveContractE2ETest` | `kof-compiler` |
| 14 | 28.883 | `dev.kof.cli.KofDebugJvmStepTest` | `kof-cli` |
| 15 | 27.271 | `dev.kof.compiler.KofTimeE2ETest` | `kof-compiler` |
| 16 | 26.684 | `dev.kof.compiler.KofConcurrency2Test` | `kof-compiler` |
| 17 | 26.53 | `dev.kof.compiler.IoE2ETest` | `kof-compiler` |
| 18 | 25.504 | `dev.kof.compiler.ComponentCoreE2ETest` | `kof-compiler` |
| 19 | 24.99 | `dev.kof.compiler.UiE2ETest` | `kof-compiler` |
| 20 | 24.268 | `dev.kof.script.KofScriptTest` | `kof-script` |

### Tempo por módulo

| Tempo (s) | Módulo |
|----------:|--------|
| 1883.836 | `kof-compiler` |
| 512.715 | `kof-cli` |
| 53.301 | `kof-script` |
| 1.349 | `kof-c-compiler` |

## Descoberta da Fase 2 (auditoria de fontes)

`scripts/test-suite-audit.sh` varre as **fontes** de teste (read-only; nunca
executa a suíte) atrás dos alvos de quick win nomeados pelo plano.

**Medido:** `sleeps=56 oversized(>=500)=43 duplicate-across-classes=117` em
502 fontes de teste.

- **`Thread.sleep`** — 56 sites (um é menção em prosa no javadoc do
  `AsyncSleepJsE2ETest`), concentrados em esperas de servidor/debug/boot
  (`ServePortTest`, `BiosBootE2ETest`, `KofDebugJvmExceptionTest`). Leads para a
  Fase 2 (esperas determinísticas), não remoções automáticas: vários guardam
  processos externos reais.
- **Classes de teste grandes** — 43 classes ≥ 500 linhas; a cauda é
  `CompilerDriverTest` (5298), `KofOrmE2ETest` (3932), `NativeRiscvDbWireTest`
  (2871). Alvos primários da modularização da Fase 3.
- **Nomes de método de teste repetidos** — 117 nomes em ≥ 2 classes. Isto é um
  **lead, não uma contagem de defeito**: clusters de paridade cross-target (mesma
  face em JVM/Native/JS) e helpers compartilhados (`main`, `assumeToolchain`,
  `jvmOracle`) são esperados e intencionais.

A auditoria não modifica nada; agir sobre um lead é uma unidade separada e
escopada.

## Leitura

- A cauda lenta é dominada por **stress tests** (`ArrayBoundsDeepStressTest`,
  ≈366s) e **E2E entre processos** (`ConformanceMatrixTest`, `CmdWorkflowTest`,
  `CmdMakealiveTest`, `DepsRegistryTest`) — os alvos naturais da Fase 2.
- A Fase 2 (quick wins: repetição/sleeps/setup redundante) e a Fase 3
  (modularização em camadas) agem sobre esta base medida, nunca sobre memória.
