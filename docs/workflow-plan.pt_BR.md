[English](workflow-plan.md) | [Português](workflow-plan.pt_BR.md)

# `kof.workflow` — jobs, pipelines, retry, checkpoints, dead-letter (plano de design · Estágio 2 · TIER 2.1)

> **Estado: CONCLUÍDO 19/09 — as cinco fatias (2.1.0–2.1.4) landaram com prova
> (`WorkflowE2ETest` 24/24, paridade byte JVM==JS, face real no Native); promovido de
> `docs/development/` pela regra dos 3 estados.** Originalmente APROVADO (19/09, enquete
> da mantenedora) — frente aberta, dono lane `.18`.**
> Q1–Q4 respondidos: **stdlib de composição ✓ / MVP mínimo (job/dag/after/run/Report) ✓ /
> retry como helper ADITIVO, `kof.http` migra depois em slice assinado à parte ✓ /
> dead-letter com AMBAS as faces (in-memory + durável via `kof.orm`) ✓**. Zero código neste
> arquivo; ele segue sendo design. Existe porque a linha **2.1** do
> `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` era uma linha `🔵` solta, dono `—`, até esta
> aprovação; todas as outras frentes greenfield em `future/` (`value-records`,
> `scoped-resources`, `shell-plan`, …) têm plano concreto. Ele **não** implementa nada:
> `kof.workflow` não está no lexer, no parser, em nenhum backend, nem na stdlib hoje
> (medido 18/09). Ancorado nas primitivas **reais, medidas** que ele compõe (§4).

## 1. Objetivo
Uma única idiomática tipada e componível para **trabalho de longa duração, retentável e
inspecionável**: definir um job, declarar suas dependências (DAG), executá-lo (agora ou em
schedule), retentar em falha com backoff, fazer checkpoint dos passos concluídos e estacionar
falhas terminais numa fila de dead-letter — **sem** que cada app reinvente o loop
`while (!ok && tries<N)`. Hoje as peças existem mas estão desconectadas (medido, §4):
`kof.scheduler.every/at` agenda *funções*, `kof.supervisor` reinicia *workers* (pure-Kof,
ratificado DD-OTP-01), `kof.process` roda *comandos*, `kof.orm` persiste *linhas*,
canais/`selectAny` coordenam *concorrência*. Falta a **cola que dá a todas as cinco um
contrato único**: `workflow` é uma camada de composição, **não** uma primitiva de runtime
nova — herda o comportamento das primitivas que conecta, exatamente como `shell` é açúcar
sobre `process` (ver [`shell-plan.pt_BR.md`](shell-plan.pt_BR.md)).

## 2. Proposta — pacote stdlib em nível Kof, NÃO sintaxe nova
Fato medido: o parser não tem produções `workflow`/`task`/`pipeline`, e o `roadmap.md`
§"KofJS — Web" é explícito ao dizer que JS não é superfície de segunda classe (regra 5). Então
`kof.workflow` é proposto como **código Kof comum** na árvore `stdlib` (como `kof.supervisor`
já é, por DD-OTP-01 opção A), **não** uma keyword ou forma gramatical nova. Esboço da idiomática
(forma apenas — toda *forma* abaixo é Kof parseável, verificado no 2.1.0 por
`WorkflowPrimitivesE2ETest`: lambdas são argumento `(x: T) -> expr`, listas são `listOf(...)`,
não existe literal `[]`, nem literal `{}` de mapa, nem sufixo de duração `1s` em Kof; as
*decisões* são os `Q`s respondidos no §6):

```
import kof.workflow

// Superfície MVP 2.1.2 ENTREGUE — flat, sem prefixo `workflow.` (o idioma do
// supervisor; o prefixo era nível-sketch, corrigido na entrega):
var build = job("build", () -> process.run("make", listOf("-j")).exitCode == 0)
var image = job("image", () -> process.run("docker", listOf("build", "."))
                                     .exitCode == 0).after(build)

var flow   = dag(listOf(build, image))  // dag(...) recebe listOf — Kof não tem variádicos
var report = flow.run()                 // Report: succeeded/failed/skipped/errors,
                                        // allOk(), summary()

// Bundle 2.1.3 (ENTREGUE 19/09 — a cadeia desenhada aqui landou; ver §5. Esboço
// histórico mantido por regra 8 — o desenho mudou no landing: `schedule` delega a
// `scheduler.at`, checkpoint/supervisão viraram faces separadas 3a/3b):
//   flow.retry(...).checkpoint(...).deadLetter(...); flow.schedule("0 3 * * *")
```

> **Nota da aprovação (19/09):** a superfície do §2 é o idiomático completo, mas pela Q2 o
> **v1/MVP entrega só `job`/`dag`/`after`/`run`/`Report`**; `retry` (Q3: helper **aditivo**),
> `checkpoint` e `deadLetter` (Q4: **ambas** as faces, in-memory e durável) chegam juntos no
> 2.1.3. A cadeia-builder do sketch, os argumentos posicionais e a unidade `1000`-ms do
> backoff são nível de exibição: **resolvido no 2.1.0** — a assinatura stdlib concreta
> precisa analisar como Kof real (sem argumentos nomeados, sem literais de duração daquela
> forma); `WorkflowPrimitivesE2ETest` trava as rejeições por negativo e o §2 foi reescrito
> nas formas parseáveis.

Inventárias de design (herdadas de precedente existente, não inventadas aqui):
- **DAG, não lista linear.** Ciclos são rejeitados **em runtime** por padrão (mesma classe de
  honestidade dos diagnósticos `SEM` — mensagem acionável, nunca um skip silencioso). Detecção
  de ciclo em tempo de compilação, se desejada, é uma linha à parte (**3.7** é essa decisão
  para o grafo infra; workflow a espelha).
- **Jobs são funções Kof `(Ctx) -> R`.** `Ctx` traz um logger (sobre `kof.log`), um store de
  `checkpoint` (sobre `kof.db`/`kof.orm`) e um `state: Map[String,Any]` para a entrada do
  próximo job. Sem IR de job próprio — reusa o tipo função (§155/§157 são a cautela que isto
  não toca).
- **Retry/backoff embarca como helper ADITIVO dentro de workflow primeiro** (Q3, assinado
  19/09). Hoje o padrão vive só dentro de `kof.http` (`NativeHttpCore.java`,
  `JsRuntimeUiLayout.java`, `RuntimeConcurrency.java` — medido); refatorá-lo em helper
  compartilhado editaria arquivos em curso de outras lanes (regra 8), então `http` migra para
  o helper do workflow numa **fatia posterior, assinada à parte**. Mesmo vocabulário
  (`retry(times, backoff, when)`), nenhuma divergência de semântica, um teste de migração depois.
- **Checkpoint = registro em formato `Result` gravado via o `kof.orm` existente** — mesma ABI
  que `shell` reusa o `process.Result` (um tipo só, sem segunda forma). Nenhum backend de
  persistência novo.
- **Dead-letter = uma view `Iterable` das falhas** — a implementação in-memory é um
  `List[Failed]`; a durável é uma tabela `kof.orm`. Nenhum subsistema de fila novo.
- **Scheduling reusa `kof.scheduler.at(cron)`.** Workflow **não** implementa parsing de cron.
  Se `at` levanta `CRON001` num alvo (native), `flow.schedule` levanta `CRON001` também — mesmo
  gap honesto, sem disfarce.
- **Supervision reusa `kof.supervisor` (OTP, DD-OTP-01 opção A).** Um run de workflow é uma
  árvore supervisionada; política de restart = a política do supervisor. `workflow` **não**
  reimplementa semântica de restart.

## 3. Contrato
- Pura composição sobre namespaces existentes (`process`, `scheduler`, `supervisor`, `orm`,
  canais `concurrency`, `Result`, `retry`/`backoff`) — nenhuma chamada de runtime `Kof*` nova,
  nenhum hook de backend novo, nenhum token de lexer novo, nenhum `Target` novo.
- **Superfície de linguagem aditiva apenas** (um namespace `kof.workflow` novo); nenhuma mudança
  no comportamento de qualquer primitiva existente (regra 6: mínima, reversível).
- Determinístico por alvo: um workflow que o alvo **não consegue rodar por completo** (ex.: um
  que precisa de `at(cron)` no native, ou `db.checkpoint` no native) falha **na definição** com
  o gap **existente** daquela primitiva (`CRON001`/`PROC001`/`ORM001`), nunca numa surpresa
  depois em `run()`. Se o workflow só usa `process` com `run`, funciona onde `process.run`
  funciona.
- Paridade cross-target (regra 5): a **mesma fonte** deve produzir o mesmo `Report` em todo alvo
  onde as peças de baixo existem. Onde não existem, o **mesmo** código honesto
  (`ORM001`/`CRON001`/`PROC001`) aparece naquele alvo.

## 4. ABI por alvo (escopo honesto R7) — MEDIDO em 18/09, não assumido
Tudo que `kof.workflow` compõe já existe; `workflow` em si não adiciona nada a backend
nenhum. Lido dos arquivos-fonte (não de memória), cada coluna cita o seu arquivo:

| primitiva | arquivo-fonte | JVM | ANDROID | JS | NATIVE |
|-----------|---------------|:---:|:-------:|:--:|:------:|
| `process.run` → `Result{stdout,stderr,exitCode}` | `KofProcess.java`, `ExpressionProcessCallLowerer.java` | ✅ | ✅ | ✅ (`kof_platform.processRun`) | ❌ `PROC001` |
| `process.spawn` (handle) | idem, `spawnCall` | ✅ | ✅ | ❌ `PROC001` (gate 18/09) | ❌ `PROC001` |
| `scheduler.every(fn, ms)` / `.cancel(h)` | `KofScheduler.java` `supportedOn` | ✅ | ✅ | ✅ | ✅ (`Rt B` cross) |
| `scheduler.at(cron5f, fn)` | idem, `gapCode` | ✅ | ✅ | ✅ (parser landado 17/09) | ❌ `CRON001` (honesto, R6) |
| `kof.orm.{create,save,find,all,where,page,migrate,delete,count,deleteAll,saveAll}` | `KofOrm.java`, JS `KofJsOrmBridge` | ✅ | ✅ | ✅ (JS fechado 18/09) | ❌ `ORM001` |
| canais + `spawn` + `selectAny` | `KofInterpreterConcurrency.java`, conc. JS/`native` (`CONC003` fechado 03/09, §132 resolvido 18/09) | ✅ | ✅ | ✅ | ✅ (workers = clone 220 + spinlock) |
| tipo `Result` (já reusado pelo `shell-plan`) | `KofProcess.RESULT` | ✅ | ✅ | ✅ | ✅ (como tipo) |
| `retry(times, backoff)` | hoje só dentro de `kof.http` (`NativeHttpCore.java`, `RuntimeConcurrency.java`, `JsRuntimeUiLayout.java`) | ⚠️ não geral | ⚠️ | ⚠️ | ⚠️ |
| `supervisor.one_for_one` (OTP) | `docs/planning-otp-supervision.md` §Decision status (ratificado 13/09, DD-OTP-01 opção A pure-Kof) | ✅ | ✅ | ✅ | ✅ (×3 ✓ 19/09 — §129 port cross) |

**Consequências para `kof.workflow`:** em JVM + ANDROID + JS (com host Graal), um workflow
completo (jobs + DAG + retry + checkpoint + cron-schedule + dead-letter) está disponível desde o
dia um — cada primitiva é verde lá. No **Native**, `at(cron)` (`CRON001`) e `orm.checkpoint`
(`ORM001`) apresentam o gap honesto existente; **um `flow.run()` sem checkpoint e sem cron ainda
funciona lá** (jobs + DAG + retry + dead-letter in-memory, todos verdes no native). O plano **não**
disfarça os gaps `PROC001`/`ORM001`/`CRON001` do native — ele os herda verbatim.

## 5. Fila de passos (o todo executável que este doc existe para produzir)
Dono: **lane `.18`** (atribuído pelo greenlight da mantenedora em 19/09).

- **2.1.0 [recon — 0 código]** ✅ FEITO 19/09 — `WorkflowPrimitivesE2ETest` (6/6): os pins
  de honestidade por alvo já existem travados por teste (`DomainGapCodesTest` PROC001,
  `KofTimeE2ETest` CRON001, `KofOrmE2ETest` ORM001, `CoreRegressionE2ETest` process JVM+JS)
  — citados, não duplicados. Parser resolvido: listas colchete `["x"]`, mapas chave `{"k":v}`
  e o sufixo `1s` **não** são sintaxe Kof (travados por negativo); listas são `listOf(...)`,
  mapas `mapOf(...)`, lambdas são em posição de argumento `(x: T) -> expr`, encadeamento de
  métodos funciona. O recon achou e consertou um bug real na própria unidade: lambda
  retornando `process.run(...)` vazava um descriptor `LResult;` pelado (o round-trip por
  string de `CompilerLambdaClass` perde o pacote; `JvmTypeMapper` não tinha entrada para
  `kof.process/Result` — agora mapeado como `Handle` #31). Nenhuma superfície entregue.
- **2.1.1 [aprovação de design — ⛔ regra 6]** ✅ FEITA 19/09 — enquete da mantenedora: Q1 stdlib
  ✓, Q2 MVP mínimo ✓, Q3 retry aditivo ✓, Q4 ambas as faces de dead-letter ✓. Frente aberta.
- **2.1.2 [MVP — stdlib pure-Kof, mínimo, JVM+JS]** ✅ FEITO 19/09 — host pure-Kof
  `dev/kof/workflow-host.kf` injetado FLAT pelo `CompilerWorkflow` no `import kof.workflow`
  (mecanismo do supervisor, DD-OTP-01 opção A — por isso sem prefixo `workflow.`; §2
  corrigido na entrega). Superfície exatamente Q2: `job`/`dag`/`after`/`run`/`Report`
  (succeeded/failed/skipped/errors, `allOk()`, `summary()`). Sem gate de target (fixpoint
  sequencial, sem fronteira de runtime). Golden `WorkflowE2ETest` 7/7 stdout exato JVM==JS +
  pin de compilação Native. Três bordas de parser/typer achadas e contornadas (documentadas
  em `docs/stdlib/workflow.pt_BR.md` §5).
- **2.1.3 [add-ons — um bundle]** — **LANÇADO 19/09, as cinco faces.** retry ✅ (Q3
  honrada: o helper é PRÓPRIO do workflow — `dag.retry(job, times, exponential(base,
  factor))` + `retryFixed` + `Report.retries`; `kof.http` intocado, a migração dele
  segue fatia assinada à parte); deadLetter ✅ AMBAS as faces (in-memory `Report.dead`
  sempre + sink durável opt-in por job — Q4); schedule ✅ (delega a `scheduler.at`,
  stub `CRON001` alto no Native); checkpoint ✅ 3a (store sobre `kof.db`/`kof.orm`,
  stub `ORM001` alto no Native); supervisão ✅ 3b — `runSupervised(dag, nome,
  maxReinicios)`: todo job vira um child `transient` de um `kof.supervisor` cujo laço
  por filho É o one_for_one (só o filho que falha reinicia); a política de reinício
  segue sendo do supervisor (§3), e a pergunta rule-6 que sobrou ("como expressar um
  job one-shot como worker?") foi RESPONDIDA pela mantenedora 19/09 ("pode ir pra
  fatia 3") — a resposta é exatamente o idioma de parada limpa do núcleo (`!falhou &&
  politica != permanent`), sem superfície nova além da função de composição. Prova:
  `WorkflowE2ETest` 24/24 (paridade byte JVM==JS + pin Native).
- **2.1.4 [docs]** ✅ FEITO 19/09 (mesma sessão do 2.1.2) — doc de idiomática
  `docs/stdlib/workflow.pt_BR.md` (+EN), linha na matriz `backend-parity` + delta 19/09(2)
  (EN+PT), tracker 2.1 `🔵→🟡` e 2.5/2.6 `🔵→⏳` (EN+PT), este arquivo promovido para fora
  de `future/` (`a71a4f51`). Fila do plano **ENCERRADA 19/09**: 2.1.3 landou por completo (as cinco
faces, `WorkflowE2ETest` 24/24) — sobram só os gaps honestos catalogados (Native `CRON001`/`ORM001`)
e a decisão de superfície do `kof workflow run` (§2.6, mantenedora).

## 6. Questões abertas (decisões da mantenedora — NÃO resolver em código)
**As quatro RESPONDIDAS em 19/09 pela enquete da mantenedora** (recriar via a mesma decisão
multi-escolha se revisitadas — regra 6):

- **Q1 — RESPONDIDA: pacote stdlib em nível Kof** (`stdlib/workflow.kf`, como `kof.supervisor`
  por DD-OTP-01 opção A) — não namespace builtin. A superfície inteira compõe primitivas já
  tipadas; semântica de scheduling é reusada (`kof.scheduler.at`), não mudada.
- **Q2 — RESPONDIDA: v1 mínimo** — `job`/`dag`/`after`/`run`/`Report`; `retry`, `checkpoint` e
  `deadLetter` movidos para o bundle 2.1.3 (ver §5).
- **Q3 — RESPONDIDA: camada aditiva primeiro** — workflow embarca seu próprio helper
  `retry(times, backoff, when)`; `kof.http` migra para ele numa **fatia posterior, assinada à
  parte** (refatorar agora editaria `NativeHttpCore.java`/`RuntimeConcurrency.java`/
  `JsRuntimeUiLayout.java` — território de outras lanes, regra 8).
- **Q4 — RESPONDIDA: ambas as faces juntas** — in-memory `Iterable` E durável (tabela
  `kof.orm`) no mesmo release; o gap honesto `ORM001` no Native já está catalogado e segue
  honesto.

## 7. O que NÃO fazer
- Nenhum token de lexer/parser novo nem produção gramatical (isto **não** é uma keyword `pipeline`).
- Nenhuma primitiva de runtime nova em nenhum backend — toda ação roteia para um namespace
  existente (`process`/`scheduler`/`orm`/`supervisor`/`concurrency`) e herda seus gap codes.
- Nenhum fork de semântica de `retry`/`backoff` — pela Q3 o helper aditivo é primeiro do
  workflow; `kof.http` migra para ele depois, em slice assinado à parte (sem duplicata
  silenciosa que drifta).
- Nenhuma camada nova de persistência para checkpoints — reusa `kof.db`/`kof.orm` (`ORM001` no
  native continua honesto, **não** escreve um `checkpoint` em asm no native para contorná-lo).
- Nenhuma reimplementação de restart/supervision — `kof.supervisor` (DD-OTP-01) é esse lar;
  `workflow` o compõe, nunca o duplica.
- Nenhum disfarce de `PROC001`/`CRON001`/`ORM001` no native — o gap honesto é o contrato.
- Nenhum `.kf` de stdlib antes do recon **2.1.0** confirmar que as formas do §2 analisam como
  Kof real — FEITO 19/09 (a aprovação 2.1.1 também está FEITA 19/09); nenhum alvo declarado verde antes de sua primitiva
  de baixo ser verde lá (medido no §4, re-verificado por `WorkflowPrimitivesE2ETest` no 2.1.0).
