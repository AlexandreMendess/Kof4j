[English](shell-plan.md) | [Português](shell-plan.pt_BR.md)

# `kof.shell` — idiomatic shell over `kof.process` (design plan · Stage 2 · TIER 2.2)

last: 2.2.3-runwith
doing: closed
next: pipeline-js-native
location: shell-plan
state: done
intent: idiomatic-shell-over-process
constraint: no-grammar-change
decision: function-form

> **CONCLUÍDO 19/09 — v1 POUSADA (18/09 `34e4344f`) + 2.2.3 `runWith` 19/09 (`ShellE2ETest` 15/15); fila fechada.** Residual: `pipeline` live-pipe em JS/Native = item de plataforma na linha 2.2 de `IMPLEMENTATION-UNIVERSAL-PLATFORM`, não uma fatia deste plano. `cmd`/`run`/`ok` reais em JVM+JS (byte-parity nos 5 casos do `ShellE2ETest`), `pipeline` real no JVM; `pipeline` em JS/Native = `PROC001` honesto em compile-time (nunca `ReferenceError` cru — §235). Enquete da mantenedora: forma-função ✓, builtin `KofShell.java` ✓, glob/`~`/redireção FORA do v1. §2 reescrito para o que o parser aceita hoje (Kof não tem argumentos nomeados — o rascunho antigo usava `cwd:`).

## 1. Objetivo

Um idioma tipado e composável para dirigir comandos do SO — rodar, capturar, pipeline, gate por exit code — sem NUNCA stringificar um comando para `sh -c`. O `kof.process` já dá as primitivas (§4); `kof.shell` é açúcar sobre ele (não adiciona primitiva de runtime onde `process` cobre a chamada, então herda o comportamento de `process`).

## 2. Superfície (forma-função, NÃO shell-infix)

Medido: `Lexer`/`Parser` do Kof não têm backtick/`$()`/`|`/`&&`/`||`/`>` e adicioná-los é mudança de gramática (regra 6). Então `kof.shell` é uma API de funções simples.

```kof
cmd(String program, List<String> args) -> List<String>   // builder de argv: [program] + args, zero parsing
run(String, List<String>) -> Result                       // Result reusado de kof.process
run(List<String>) -> Result                               // overload de argv
pipeline(List<List<String>>) -> Result                    // Result do último estágio carrega o desfecho da cadeia
ok(Result) -> Bool                                         // exitCode == 0
runWith(argv, cwd, env)                                    // overload 2.2.3 (Kof não tem args nomeados)

import kof.shell
var r   = shell.run("git", listOf("status", "--short"))
if (shell.ok(r)) println(r.stdout)
var out = shell.pipeline(listOf(shell.cmd("ls", listOf("-1")), shell.cmd("wc", listOf("-l")))).stdout
var x   = shell.runWith(shell.cmd("make", listOf("-j4")), "/src", mapOf("CC", "clang"))
```

Invariantes: argv é sempre `List<String>` — o comando NUNCA é concatenado numa string de shell (a classe de injeção `concatenated-command-line`; `shell` é o idioma do caminho bom); `Result` É o `Result` do `kof.process` (`stdout`/`stderr`/`exitCode`) — um tipo, sem segunda forma; `shell.ok(r)` == `exitCode == 0`; sem glob/`~`/redireção implícitos a menos que uma fatia posterior assinada os desenhe.

## 3. Contrato

Lowering puro sobre `kof.process` onde possível → mesma semântica, mesmo binding, sem nova superfície de runtime `Kof*` nos alvos que `process` já cobre. Só superfície de linguagem aditiva (novo namespace `kof.shell`), sem mudar o comportamento de `process` (regra 6: mínimo, reversível). Determinístico e platform-honest: um comando que `shell` não roda num alvo levanta o gap `process` existente daquele alvo (`PROC001`), nunca degrada em silêncio.

## 4. ABI por alvo (escopo honesto R7) — medido 18/09

De `KofProcess.java` + `ExpressionProcessCallLowerer.java` (lido, não inferido):

| alvo | `process.run` | `process.spawn` | `process.exit` | fonte da verdade |
|--------|:---:|:---:|:---:|---|
| JVM | ✅ | ✅ | ✅ (`System.exit`) | `KofProcess.RESULT/HANDLE`, ProcessBuilder |
| JS | ✅ (`kof_platform.processRun`, node host) | ❌ `PROC001` (JS não liga op `kof_process_spawn`/handle; `ReferenceError` medido, gated honest 18/09) | ✅ (sentinela) | `js/JsRuntimeOps.java` `isRuntimeOp` lista só run/exit |
| Native | ❌ `PROC001` (compile-time) | ❌ `PROC001` (compile-time) | ✅ (syscall) | `ExpressionProcessCallLowerer` gates de spawn/run |

Consequência: `run`/`exit` reais em JVM+JS desde o dia um; `spawn` é JVM-only, `PROC001` herdado em Native e JS até a plataforma pousar um binding live-pipe lá (item separado, não deste plano). `shell` reporta o mesmo gap honesto. *(Corrigido 18/09: um rascunho anterior confiou no comentário do lowerer "JVM/JS support it"; o backend JS emite uma chamada crua `kof_process_spawn(...)` sem binding — ver `DomainGapCodesTest.processSpawnOnJsIsProc001`.)*

### 4.1 Mapa de wiring de um novo namespace builtin (medido 18/09)

Toda célula acima já é travada por testes, então nenhum teste de recon é adicionado (`DomainGapCodesTest` fixa native-run/native-spawn/js-spawn `PROC001` + JVM-spawn sem gap; `CoreRegressionE2ETest.processRun` (F4) `runBoth` roda `process.run` em JVM e JS). `KofShell.java` deve registrar:

| ponto de toque | file:line | o que entra |
|---|---|---|
| member-call parser/typer | `MemberCallNamespaces.java:90` | receiver `shell` → tipar a chamada (como `process`) |
| method-call typer (sem member) | `MethodCallNamespaces.java:145` | mesmos tipos de retorno no caminho sem receiver |
| whitelist de identificador nu | `SemExpressionTyper.java:90,152` | adicionar `"shell"` para `shell` não ser "unknown identifier" |
| dispatch de lowering | `ExpressionMethodCallLowerer.java:242` | `shell.*` → novo `ExpressionShellCallLowerer` |
| binding de runtime JVM | `jvm/JvmRuntimeCallDescriptors.java`, `JvmRuntimeReturnDescriptors.java`, `JvmRuntime.java` (lista de nomes) | `kof_shell_pipeline` (o único binding novo; `run`/`cmd`/`ok` baixam para `kof_process_run` + helpers de list/bool já existentes) |
| catálogo LSP | `StdCatalog.java:45` + `StdCatalogTest.java:89,194,276` | `m.put("shell", KofShell.functions())` + guarda do catálogo |

`pipeline` não consegue reusar handles `kof_process_spawn` do IR (precisaria de loops read/write/exit por estágio); baixa para um novo helper JVM `kof_shell_pipeline(List<List<String>>) -> Result` (cadeia ProcessBuilder, stdout→stdin no runtime, desfecho do último estágio). JS/Native batem no gap herdado de spawn em compile-time — o lowerer de shell faz gate de `pipeline` para `PROC001` lá exatamente como `process.spawn`, nunca emitindo uma chamada que daria `ReferenceError` (a lição §235).

## 5. Fila de passos (dono: lane `.18`)

- **2.2.0 recon [0 código]** ✅ 18/09 — tabela de paridade §4 medida; toda célula já travada por testes existentes (§4.1), então nenhum pin duplicado.
- **2.2.1 sign-off de design [regra 6]** ✅ 18/09 — enquete da mantenedora: Q1 forma-função ✓, Q2 builtin `KofShell.java` ✓, Q3 glob/`~`/redir out ✓. Superfície §2 adotada (forma posicional/overload; `shell.ok(r)` como fn de namespace porque o `Result` de process não carrega métodos — exemplos antigos `r.ok()`/`cwd:` não parseiam).
- **2.2.2 MVP [JVM+JS `run`, `pipeline` só JVM]** ✅ — `cmd`+`run` (ambos overloads via `kof_process_run`)+`ok`+`pipeline` (novo `kof_shell_pipeline`); `ShellE2ETest` golden contra `wc`/`tr` sem dependências, afirmando argv-as-list (sem `sh -c`); pins `PROC001` de js/native-pipeline; paridade de run JS via `runBoth`. `cwd/env` (`runWith`) movido para 2.2.3 (precisa de bindings de runtime novos).
- **2.2.3 paridade + add-ons** ✅ 19/09 — `runWith(argv, cwd, env)` em JVM + binding do host JS (env aditivo, cwd `""` herda, Results `-1` honestos para erros de spawn e argv vazio; `ShellE2ETest` 15/15 com byte-parity + pin `PROC001` Native). `pipeline` JS só se um binding live-pipe `process.spawn` JS pousar (item de plataforma separado); Native fica `PROC001` até o `process.run` da lane native pousar.
- **2.2.4 docs** — doc de idioma `docs/stdlib/shell.md` (+PT), linha em `backend-parity`, virar `IMPLEMENTATION-UNIVERSAL-PLATFORM` 2.2 `🟡 → ✅`; promover este arquivo conforme a regra de pasta.

## 6. Questões abertas

Todas as três RESPONDIDAS 18/09 pela enquete da mantenedora (recriar via o mesmo multi-choice se revisitado — regra 6): Q1 forma-função (não shell-infix backtick/`|`, mudança de gramática regra 6); Q2 builtin do compilador `KofShell.java` como `KofProcess` (não pacote stdlib a nível Kof); Q3 glob/`~`/redireção FORA do v1 (fatia posterior assinada pode revisitar).

## 7. O que NÃO fazer

Sem execução por string `sh -c` / concatenação de string de comando (classe de injeção). Sem mudança de gramática (backtick/pipe/redir) sem decisão regra 6 assinada. Sem runtime de processo paralelo — reuse `kof.process` e seus códigos de gap verbatim. Sem código antes do sign-off 2.2.1; sem alegar suporte Native que `process` não tem.
