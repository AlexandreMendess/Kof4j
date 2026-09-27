[English](type-system-extensions-plan.md) | [Português](type-system-extensions-plan.pt_BR.md)

# Type-system extensions — incremental plan (X5 variance + sealed · X6 interop reflection)

last: x6-parity-docs
doing: closed
next: none
location: type-system-extensions-plan
state: done
decision: D-TYPE-VARIANCE

> **IMPLEMENTADO 22/09 — todas as fatias X5/X6 pousaram com prova** (exec = lane do compilador). A mantenedora votou X5 = opção C e X6 incremental, respondendo às perguntas de superfície X5 (`DECISIONS.md` §D-TYPE-VARIANCE, §D-INTEROP-REFLECT, §D-X5-SURFACE); o texto abaixo é a spec medida. Fila: `roadmap.md` §2.8.4/§2.8.5. Regras: regra 6, regra 11, `D-KOF-FIRST`. As duas frentes tocam o core congelado, então a spec precede o código; cada fatia é aditiva com prova própria. Type-classes permanecem não-objetivo permanente.

## X5 — variance + sealed

Objetivo: `sealed` = classe/record com conjunto de subtipos fechado e conhecido em compile-time, para o typer provar um `switch` exaustivo (sem `default`); variance = `out`/`in` em params genéricos para `List<Dog>` atribuir a `List<Animal>` com segurança provada pelo compilador.
Não-objetivos: sem type-classes/higher-kinds/effect system; variance é apagada, sealed é compile-time (saída idêntica JVM/Native/JS).

Superfície congelada (`D-X5-SURFACE`): (a) keyword `out`/`in`; (b) `sealed` em `class`/`record`/`interface`; (c) projeção use-site (`List<out T>`) na v1, não adiada; (d) diagnósticos `SEM0xx`.

| # | Fatia | Estado + prova |
|---|---|---|
| X5.0 | spec + cells | congelada 21/09 — células de conformidade `sealed`/`variance` + rascunho `training/idioms` |
| X5.1 | declaração `sealed` | feita 21/09 — `sealed` contextual antes de `class`/`record`/`interface`; `SEM080` (subtipo direto fora da unidade de compilação do tipo sealed); `SealedTypeE2ETest` 6 (JVM/Script run, JS/Native compile, red-first `SEM080`, retro-compat) |
| X5.2 | `switch` exaustivo | feita 21/09 — `SEM081` (subtipo direto faltando, sem `default`); `SealedTypeE2ETest` JVM/Script/JS + red-first `SEM081` + controle default |
| X5.3 | variance declaration-site | feita 21/09 — `TypeParser` + `TypeParams.variance` por tipo; `TypeChecker.genericArgsCompatible` aplica `out`/`in`/invariante (§270) sobre args do MESMO raw; solidez `SEM082` (`VarianceChecks`); `SemExpressionTyper` alinhado ao emit; erasure intacta. `TypeVarianceE2ETest` 9 |
| X5.3b | variance em herança | feita 21/09 — `VarianceChecks.checkHeritage` + `SEM083` (variance incompatível passada a param de supertipo); v1 conservadora (arg de type-param simples). `TypeVarianceE2ETest` 13 (4 novas: out→in, in→out, out→invariante, matching OK) |
| X5.4 | projeção use-site | feita 21/09 — `TypeParser.parseTypeRef` preserva `out`/`in` em type-arg; `Type.of` → `Type.WildcardType`; `MemberResolver` valida o bound; `CompilerTypes.qualifyDeep`; projeção por USO (`List<out Animal>` aceita `List<Dog>`; `List<in Dog>` aceita `List<Animal>`); erasure reusa `WildcardType`. `UseSiteVarianceE2ETest` 5 |
| X5.5 | paridade + docs | feita 21/09 — batch de conformidade 4 (`sealedswitch`/`variance`/`useproj`, 4 alvos); linhas EN+PT de `backend-parity`; `training/idioms/classes` + `fake-idioms` (só `permits` fica fake); drift de `learn/10-inheritance`/`15-pattern-matching` corrigido; `lexical-structure` SG-002; roadmap 2.5/2.8.4; docs-lang 100% |

Riscos: solidez da variance com coleções mutáveis (`List<T>.add`) — o typer deve rejeitar a atribuição insegura; exaustividade × `when`/`else`/subjects nullable precisa de regras explícitas; erasure deve manter a ABI byte-idêntica (sem boxing acidental).

## X6 — interop reflection

Objetivo: uma visão somente-leitura da estrutura de um tipo (nomes/tipos de campo) disponível apenas na fronteira de interop, para dados externos (schemas Arrow/Parquet/ML) ligarem a records Kof sem mappers manuais.
Não-objetivos: NUNCA uma fundação da linguagem — sem metaprogramação de runtime, dispatch dinâmico, annotations-como-framework, reflexão no fluxo de controle do usuário; sem caminho de escrita; sem carregamento dinâmico estilo `Class.forName`.

Superfície congelada (`D-INTEROP-REFLECT`): `interop.schema(R)` — intrínseco de compile-time no namespace `interop`, `R` um `record`, resolvendo a um `List<Field>` imutável onde `record Field(String name, String type)` é fornecido pelo compilador na ordem dos componentes. Zero reflexão de runtime → mesma saída em todo alvo, então nenhum gap `REF001`. Somente fronteira.

| # | Fatia | Estado + prova |
|---|---|---|
| X6.0 | spec | feita 21/09 — superfície congelada em `D-INTEROP-REFLECT` |
| X6.1 | intrínseco + fold | feita 22/09 — `import kof.interop` injeta o `record Field` hospedeiro; typer → `List<Field>`; lowerer dobra para as ops `listOf(Field("n","t"),…)` (sem reflexão de runtime, sem código por backend). `InteropSchemaE2ETest` 7/7 |
| X6.2 | diagnósticos | feita 22/09 — entrada única `CompilerInterop.lowerNamespaceCall` a partir de `ExpressionStaticCallLowerer` com guards de shadowing; membro desconhecido `INTEROP002`; aridade≠1 `INTEROP001`; arg value/class/enum/interface `INTEROP001` (mensagem precisa); nome indefinido só `SEM011`; `recordComponents` cobre `entity`. `InteropSchemaE2ETest` 17/17 |
| X6.3 | paridade + docs | feita 22/09 — E2E de binding `InteropSchemaE2ETest#bindingE2eDrivesAnArrowShapedMapper`; célula de conformidade `interopschema` (4 alvos); `training/idioms/interop.md` + `learn/21-java-interoperability.md` + `docs/backend-parity.md`. `InteropSchemaE2ETest` 18/18 |

Notas de implementação: o compilador conhece os componentes de `R`, então `interop.schema(R)` dobra no lowerer para exatamente as ops que `listOf(Field("n","t"),…)` emite (`kof_list_new`+`kof_list_add`; ctor do record pelo caminho normal) — sem código por backend. `record Field` é record hospedeiro injetado (como `kof.supervisor`/`kof.workflow`), então acesso a membro (`f.name`/`f.type`) e codegen inalterados. `import kof.interop` explícito no import (mantenedora 21/09).
Riscos: tentação de crescer para reflexão geral — a cerca "somente fronteira" deve ser imposta/documentada; a reflexão não deve vazar para hot paths nem mudar o layout do record.

## Sequenciamento / dependências

`X5.0 e X6.0 (specs) → revisão da mantenedora → X5.1–X5.5 e X6.1–X6.3`. Independente do caminho crítico; uma fila, não trabalho atual.

## Evidências

- Decisões: `DECISIONS.md` §D-TYPE-VARIANCE, §D-INTEROP-REFLECT, §D-X5-SURFACE.
- Fila: `roadmap.md` §2.8.4/§2.8.5; `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` linhas X5/X6.
- Não-objetivos: `docs/philosophy.md`, `training/anti-patterns/fake-idioms.md`.
