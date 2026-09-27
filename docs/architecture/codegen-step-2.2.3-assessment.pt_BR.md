[English](codegen-step-2.2.3-assessment.md) | [Português](codegen-step-2.2.3-assessment.pt_BR.md)

# Avaliação 2.2.3 — "Migrar DDL/runner ao hook `CodegenStep`" (RESOLVIDA · opção B)

> **✅ RESOLVIDA 21/09/2026 — opção B decidida (`DECISIONS.md` §D-DESUGAR-STEP) e
> IMPLEMENTADA (`85779f20`).** Aberta 21/09/2026 pela lane docs/plataforma após a
> mantenedora pedir **investigação + proposta** (não uma edição); fechou a lacuna
> de medição da linha **2.2.3** do roadmap §23. Movida para `docs/architecture/`
> pela regra dos 3 estados (ver Resolução abaixo).

**Dono (registro):** lane docs/plataforma · **Decisão:** mantenedora (`D-DESUGAR-STEP`)

---

## O que o 2.2.3 diz

`roadmap.md` §23 linha 2.2.3 ("Migrar DDL/runner ao hook formal") foi aberta
depois de **2.2.2 / R4 pousar** (`CodegenStep`/`CodegenStepPipeline`,
`D-CODEGEN-STEP` 21/09). Ela assume que as duas peças nomeadas — o **DDL** do ORM
(entity→record+schema) e o **runner** de testes (síntese da declaração `test`) —
são candidatas a migrar para esse hook.

## O que foi medido (21/09, esta lane)

1. **O hook roda na IR OTIMIZADA**, depois da tipagem e do lowering, logo antes do
   emit/interpret (`CompilerPipeline.java:332`). Seu registry
   (`driver.codegenSteps`, `CompilerDriverState.java:100`) está **vazio**, **sem API
   de registro** (só a lista package-private) e **sem consumidor de produção** — o
   único caller é `CodegenStepPipelineTest`. A costura é a **identidade** hoje.
2. **Os quatro pontos implícitos de codegen vivem em outras fases:**
   - o **runner** de testes (`desugarTests`), `desugarApplication`, `desugarInfra`
     (3.2) e `desugarNestedFunctions` são **desugars de AST**, rodam **antes da
     análise** (`CompilerPipeline.java:301-304` → `CompilerDesugar`);
   - o **DDL** do ORM (entity→record+schema) está dentro do **lowering** —
     `ExpressionOrmCallLowerer.java:70` → `KofOrm.schemaString` — não é passe separável.
3. **O 3.2 é a prova:** o açúcar declarativo que o hook deveria hospedar
   (`infra "prod" {}`) pousou como `desugarInfra` (AST, commit `966c86a4`), **não**
   pelo hook — porque o frontend precisa ver a forma rebaixada **antes da tipagem**.
   Um hook pós-IR é estruturalmente **tarde demais** para desugar de código-fonte.

## Conclusão

O 2.2.3 como escrito é um **descompasso de fase**: o DDL é lowering, o runner é
desugar de AST; nenhum pode ir ao hook pós-IR sem mudar o comportamento observável
(e a fase de AST é onde o frontend *precisa* deles). Duas opções honestas:

- **A (recomendada, mínima):** **reclassificar o 2.2.3 como obsoleto/fechado** — a
  costura de desugar de AST (`CompilerDesugar`) **é** a costura formal para desugars
  de fonte; o hook de IR fica como costura-identidade documentada e testada para
  passos de *IR* futuros (nenhum hoje). Sem código; atualizar roadmap + tracker.
- **B (se uma costura de desugar de primeira classe for desejada):** criar um
  registry **`DesugarStep` na fase de AST**, espelhando `CodegenStep`, e migrar os
  quatro desugars para passos registrados — **livre de comportamento**, provado
  pela mesma suíte + golden E2E por alvo. Isto é **código**, unidade separada
  **após** a decisão.
- O DDL (entity→schema) **não** é candidato a nenhum dos dois registries — é parte
  do lowering.

## Desfecho (21/09)

A opção **B** foi escolhida e landada: `DesugarStep` + `DesugarStepPipeline` +
`DesugarSteps.defaults()` (os quatro desugars como passos ordenados) conduzem a
fase de AST, e `CompilerPipeline:303` os executa no lugar das quatro chamadas
diretas. Prova: `DesugarStepPipelineTest` 7/7 + o lote dos quatro desugars
331/0F/0E, com `mvn -o -pl kof-compiler -am compile` verde (commit `85779f20`).
O hook pós-IR `CodegenStep` permanece a costura-identidade documentada para
passos de IR futuros (nenhum hoje).

## Impacto se opção A

- `roadmap.md` linha **2.2.3** → reclassificada (fechada/obsoleta, com esta razão).
- Wording do R4 em `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` perde "and DDL/runner migration".
- Sem código; `CodegenStep`/`CodegenStepPipeline` permanecem (com o teste).

## Impacto se opção B

- Nova interface `DesugarStep` + `DesugarStepPipeline` na fase de AST + registry.
- Os quatro pontos de entrada do `CompilerDesugar` viram passos registrados (ou
  delegados finos).
- Prova: suíte completa + golden E2E por alvo, saída inalterada (regra 3 do freeze).

---

## Resolução (opção B — IMPLEMENTADA)

- **Decidida:** `DECISIONS.md` §D-DESUGAR-STEP (mantenedora 21/09) escolheu a **opção B**.
- **Implementada:** `85779f20` — `DesugarStep` + `DesugarStepPipeline` na fase de
  AST; `DesugarSteps.defaults()` registra os quatro desugars (`tests`,
  `application`, `infra`, `nested-functions`) e `CompilerPipeline:303` os roda
  pelo registry. O `CompilerDesugar` agora é só o delegado que cada passo chama
  (não restam chamadas diretas).
- **Prova:** `DesugarStepPipelineTest` 7/7; livre de comportamento (regra 3 do freeze).
- **DDL:** permanece no lowering (`ExpressionOrmCallLowerer:70`) — não é candidato
  a registry (regra 11).
- **Movida** de `docs/development/` (regra dos 3 estados; a condição 3 do gate de
  release `loose_docs` a rastreava).
