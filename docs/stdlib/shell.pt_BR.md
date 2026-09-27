[English](shell.md) | [Português](shell.pt_BR.md)

# `kof.shell` — shell idiomático sobre `kof.process`

**Status:** v1 implementado (18/09, `34e4344f`, plano universal Stage 2 linha 2.2) ·
**Fonte:** `KofShell.java` (dispatch) + `ExpressionShellCallLowerer` (gates/lowering) ·
**Testes:** `ShellE2ETest` (16) · **Registro de design:** `docs/shell-plan.pt_BR.md`

## O que é

Uma idiomática única, tipada e componível para comandos do SO — rodar, capturar,
decidir por exit code, encadear pipelines — **sem jamais stringuificar um comando em
`sh -c`**. É açúcar sobre `kof.process`: o tipo `Result` É o `Result` de
`kof.process` (uma forma, nunca um fork) e `run` abaixa literalmente para
`kof_process_run`. Kof não tem backtick, `$()`, `|`/`&&` infixo de shell (adicioná-los
é mudança de gramática — regra 6); a forma-função é a resposta assinada (Q1–Q3,
enquete da mantenedora 18/09).

## API real (medida no compilador — 0.5.0-beta)

```kof
import kof.shell

var r = shell.run("git", listOf("status", "--short"))   // kof.process.Result
if (shell.ok(r)) {
    println(r.stdout)
}

var argv = shell.cmd("wc", listOf("-l"))
var n = shell.run(argv.get(0), listOf("-l")).stdout.trim()

var out = shell.pipeline(listOf(listOf("echo", "um dois três"),
                                               listOf("wc", "-w"))).stdout  // JVM

var build = shell.runWith(shell.cmd("make", listOf("-j4")), "/src",
                          mapOf("CC", "clang"))                              // JVM + JS
```

| Chamada | O que faz |
|---|---|
| `shell.cmd(program, args)` | monta a `List<String>` argv `[program] + args` — sempre **lista**, nunca string; alimenta `run` via `argv.get(0)` + o resto |
| `shell.run(program)` / `shell.run(program, args)` | roda o comando, devolve `kof.process.Result` (`exitCode`/`stdout`/`stderr`) |
| `shell.ok(result)` | `exitCode == 0` como `Bool` (IR puro de campo/comparação — `Result` não tem métodos) |
| `shell.pipeline(listOf(argv, ...))` | encadeia stdout→stdin entre estágios, devolve o `Result` do último (JVM: `kof_shell_pipeline`; JS: cadeia + threads de pump em `KofJsProcessBridge` — mesmo contrato, paridade byte) |
| `shell.runWith(argv, cwd, env)` | roda o argv **em `cwd`** com ambiente **aditivo** (`cwd` `""` herda o diretório do processo; as chaves do map sobrescrevem as herdadas — nunca uma limpeza silenciosa do ambiente). Erro de spawn e argv vazio devolvem `Result` **honesto** (`stderr` preenchido, `exitCode == -1`) no JVM e no JS; no Native é o mesmo `PROC001` de compilação do `run` |

## A propriedade de segurança (pinada por golden)

`argvIsNeverConcatenatedIntoShellString`: um argumento com metacaracteres de shell
(`"a b|c && d"`) sobrevive como **UM elemento de argv** — a classe de injeção de
"montar uma string de comando e entregá-la ao `sh -c`" é estruturalmente impossível
nesta API. Scripts do próprio repo que concatenam strings de comando re-limpam à toa;
`shell` é o caminho bom.

## Escopo honesto por alvo (R6 — nunca silencioso)

| Face | JVM | JS | Native |
|---|---|---|---|
| `cmd` / `run` / `runWith` / `ok` | ✅ real (`kof_process_run`; `runWith` via `kof_shell_runwith` — cwd + ambiente aditivo, `Result` honesto com `-1`) | ✅ real (paridade byte-a-byte com JVM — 5 casos pinados + `runWith` cwd/env/falhas) | ❌ `PROC001` honesto em tempo de compilação (herda a face `process.run` do Native) |
| `pipeline` | ✅ real (cadeia com pump-threads, golden `echo|wc`) | ✅ real (cadeia no host + pump-threads, 20/09 — paridade byte pinada) | ❌ `PROC001` honesto (sem fork/exec em asm) |
| membro desconhecido (`shell.foo`) | ✅ `SEM025` | — | — |

Exit code diferente de zero **não** é exceção: `failingCommandPropagatesExitCodeNotException`
pina `Result.exitCode` como dado.

## Faces residuais (não são dívida do v1 — o escopo assinado acaba aqui)

- `pipeline` no Native — destrava quando a lane nativa landar `process.run`/spawn
  em asm; o pino `pipelineOnNativeIsHonestProc001` vira execução real então. (JS
  fechado 20/09: `pipelineChainsStdoutToStdinOnJvmAndJs` + pin de 3 saltos são
  execuções reais.)
- Addons v2 excluídos pela enquete Q3: glob, expansão de `~`, redirecionamento `>` —
  **não** no v1, apenas design no plano.

## BAD → GOOD

| ❌ RUIM | ✅ BOM | Por quê |
|---|---|---|
| `process.run("sh -c \"echo a | wc\"")` | `shell.pipeline([["echo","a"],["wc"]])` | argv-como-lista mata a classe de injeção |
| `if (r.exitCode == 0) ...` espalhado | `if (shell.ok(r)) ...` | intenção, não mecanismo |
| re-plumbing manual de `Result` no JS | `kof.process.Result` compartilhado | uma forma, nunca um fork |

## Ver também

- `docs/shell-plan.pt_BR.md` (decisões de design Q1–Q3, mapa de fiação, fatias 2.2.0–2.2.4)
- `docs/backend-parity.pt_BR.md` — linhas `kof.shell` na tabela de namespaces + tabela de gaps
- Face `kof.process` no Native: `PROC001` (backend-parity, Known Gaps)
