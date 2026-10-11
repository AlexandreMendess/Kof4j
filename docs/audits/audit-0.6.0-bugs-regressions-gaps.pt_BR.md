[English](audit-0.6.0-bugs-regressions-gaps.md) | [Português](audit-0.6.0-bugs-regressions-gaps.pt_BR.md)

# Auditoria — Kof 0.6.0: bugs, regressões e lacunas

> **Somente investigação.** Este documento não altera código de produção, não aplica
> correção alguma e não marca nada como corrigido sem evidência reproduzível. Ele
> **não se encerra sozinho**: cada item pendente aponta para o seu destino (um bug em
> `docs/bugs-and-gaps/known-bugs.md`, um plano em `docs/development/`, uma decisão em
> `docs/development/DECISIONS.md`).

**Última atualização:** 2026-10-11
**Release investigada:** `0.6.0` — tag `kof-0.6.0-linux-x86_64`, commit
`bb90986d065118e4f63978e8e6985256ff714203`
**Branch atual:** `lab`
**Método:** cada item reproduzido com o artefato **publicado** 0.6.0
(`kof-0.6.0-linux-x86_64.tar.gz`, extraído em diretório de trabalho) **e** com o CLI de
`lab` (`kof-cli/target/kof-cli-0.6.0.jar`). Investigação somente leitura; todo arquivo
auxiliar em `/tmp/kof-audit/`. Nenhum arquivo deste repositório foi modificado além
desta auditoria.

---

## 1. Preparação e integridade

| Fato | Valor |
|---|---|
| Repositório | `KofLang/Kof4j` |
| Versão publicada | `0.6.0` (`VERSION`, `pom.xml` `<revision>`) |
| Tag da release | `kof-0.6.0-linux-x86_64` (+ windows/macos, pré-releases datadas `+2026.10.11`) |
| Commit da release | `bb90986d065118e4f63978e8e6985256ff714203` |
| Branch atual | `lab` |
| Árvore de trabalho | suja com edições de **outra lane** (`DOING.md`, `known-bugs.md`, `tests/golden/…` não rastreado) — não tocadas |
| Build | Maven 3.9.16, JDK 25 (Temurin) |
| Compilação | `mvn -o -pl kof-compiler -am compile -q` |
| Testes focados | `mvn -o -pl kof-compiler -am test -Dtest='<Área>' -Dsurefire.failIfNoSpecifiedTests=false` |
| Suíte completa | **NÃO executada** — trava na família `Av1*` (aberto, `known-bugs` §602); excluída para uma execução verde |
| Limites de ambiente | Alvos Native exigem a toolchain Linux (presente); o JS usa o GraalJS embarcado (partida lenta neste host); sem lane Windows |

**Comandos de reprodução** (ambos os CLIs se comportam igual, salvo indicação):

```
OFFICIAL=/tmp/kof-audit/kof-0.6.0-linux-x86_64/bin/kof
LAB="java -jar /home/mel/Documentos/Kof4j/kof-cli/target/kof-cli-0.6.0.jar"
$OFFICIAL check|run [--target jvm|js|native] Main.kf
```

---

## 2. Resumo executivo (A)

**20 itens investigados.** Resultado:

| Estado | Qtde | Itens |
|---|---|---|
| **CONFIRMADO** (defeito reproduzido) | 11 | `JVM-01/02/03`, `HTTP-01`, `MATH-02`, `DIAG-01`, `DIAG-02`, `MEM-01`, `CLI-01`, `ORM-01`, `VER-01` |
| **CORRIGIDO NA RELEASE** (validado na tag) | 5 | `RB-01`..`RB-05` |
| **NÃO É BUG** (comportamento igual ao contrato comprovado) | 4 | `MATH-01`, `PARSE-01`, `HTTP-02`, `UI-01` |
| **NÃO REPRODUZIDO** | 1 | `SCRIPT-02` |
| **LACUNA DE ESPECIFICAÇÃO** (exige decisão de produto/linguagem) | 4 | `MEM-01`, `CLI-01`, `ORM-02`, `SCRIPT-01` (+ opt-out de `HTTP-02`) |

**Causas raiz, agrupadas:**

1. **`check` verde em silêncio → codegen divergente** — a família dominante. `MATH-02`,
   `HTTP-01`, `JVM-01/02/03` passam o `kof check` com "no errors" e depois dão
   `VerifyError` na JVM (a classe nunca carrega) ou **resultado silenciosamente errado no
   Native**. A face mais perigosa: `math.min/max/clamp` e os predicados de `math` **não
   têm guarda de tipo de argumento**, então um argumento `Double`/`String`/`Bool` chega a
   um símbolo de runtime só-Int — a JVM morre, o **Native imprime `0`**.
2. **A tipagem de lambda-válor vaza a classe concreta gerada** (`LambdaN`) em vez da
   interface SAM sintética compartilhada — `JVM-01` e `JVM-03` são uma família; `JVM-02`
   é o irmão (`FunctionType` tratado como não-referência no default).
3. **Os diagnósticos não são deduplicados e não são formatados de forma uniforme** —
   `DIAG-01` (um erro emitido duas vezes) e `DIAG-02` (`ClassType[...]`/`PrimitiveType[...]`
   crus vazando em `SEM012`/`SEM021`/`SEM014` de construtor).
4. **O ORM não tem camada de mapeamento de nomes** — `ORM-01`: uma coluna `snake_case`
   nunca alcança um campo `camelCase`; um campo de referência fica **silenciosamente
   `null`** (exit 0), um campo primitivo falha com `JSN004`. `ORM-02`: `ORM005` não existe.
5. **Higiene de release** — `VER-01`: o binário publicado 0.6.0 se auto-reporta
   `kof 0.5.0-beta` porque o `kof version` lê um `version.properties` **obsoleto**.

**Riscos críticos para a release:**

- `VER-01` é um **defeito reproduzível no artefato publicado** (string de versão errada).
- `MATH-02` e `ORM-01` produzem **dados silenciosamente errados** em alvos suportados.
- `JVM-01/02/03` tornam uma classe inteira não-carregável após um `check` limpo.

**Bloqueios de contrato (regra 6 — exige decisão da mantenedora, nenhuma correção pode
ser inventada):** `MEM-01` (o tempo de vida de `app.listen(...)`/processo é um lifetime
válido?), `CLI-01` (o `serve` deve suportar `--deps`?), `ORM-02` (definir `ORM005` ou
declará-lo não usado), `SCRIPT-01` (o KofScript inline deve ganhar uma grafia `say`?),
`HTTP-02` (opt-out de "auth-if-present").

---

## 3. Inventário completo (B)

Legenda: **Tag** = comportamento em `0.6.0`; **Lab** = comportamento no `lab` atual;
**Repro** = reprodutibilidade (`sempre`, salvo indicação).

| ID | Subsistema | Descrição | Tag 0.6.0 | Lab | Severidade | Causa raiz | Arquivos / métodos | Testes hoje | Testes necessários | Recomendação |
|---|---|---|---|---|---|---|---|---|---|---|
| `RB-01` | ORM leitura JVM | round-trip campo/coluna camelCase | OK | OK | — | corrigido §564 | `jvm/JvmConfigRuntime.java:474-507` | `OrmCamelCaseE2ETest` | pin de colisão (opcional) | nenhuma |
| `RB-02` | stdout do CLI | saída UTF-8 (acentos/multibyte) | OK | OK | — | corrigido §539 | `kof-cli/…/KofStdio.java` | `StdioEncodingE2ETest`, `KofStdioTest` | lane Windows | nenhuma (residual Windows documentado) |
| `RB-03` | binder JSON JVM | componente primitivo ausente → `JSN004` | OK (JVM) | OK (JVM) | — | corrigido §565 | `jvm/JvmRuntimeJson.java:391-394` | `JsonMissingPrimitiveE2ETest` | **paridade JS/Native** (ver `JSN-01`) | nenhuma na JVM |
| `RB-04` | tipagem JSON | `json.decode` exige type-arg explícito | aplicado | aplicado | — | §583/§588 | `MemberCallNamespaces.java:29-44`, `SemMethodCallTyper.java:48-65` | `GenericJsonDecodeRefusalTest` | pin count==1 | nenhuma |
| `RB-05` | `kof.ui` | método de UI inexistente → `SEM025` | OK | OK | — | corrigido #711/§547 | `CompilerUiEmitter.java:274-285` | `UiUnknownMethodDiagnosticE2ETest` | pin count==1 | nenhuma |
| `JSN-01` | JSON cross-target | componente primitivo ausente em JS/Native | silencioso | silencioso | **Alta** | decoders sem checagem de presença | `CompilerTypeSupport.java`, `NativeJsonSchema.java`, `js/JsRuntimeOps.java` | nenhum (só JVM) | `JSN004` em JS/Native ou lacuna declarada | espelhar a recusa da JVM |
| `JVM-01` | backend JVM | reatribuir local de tipo lambda → `VerifyError` | quebrado | quebrado | **Alta** | `LambdaN` concreta no tipo do local | `StatementLowererLocalBoxing.java:70-81`, `ExpressionAssignmentLowerer.java:262-296`, `ExpressionBareCallLowerer.java:156-157` | nenhum | `lambdaLocalReassignJvm` | unificar na interface SAM (precedente §156) |
| `JVM-02` | backend JVM | local de tipo função sem inicializador → `VerifyError` | quebrado | quebrado | Média-Alta | default sem-init é `0`, não `null` | `StatementLowererLocalBoxing.java:130-133`, `CompilerTypeSupport.java:89-92` | nenhum | `functionTypeLocalWithoutInitializerJvm` | tratar `FunctionType` como referência no predicado |
| `JVM-03` | backend JVM | if/switch-expr com ramos lambda → `VerifyError` (+ `invokestatic` fantasma) | quebrado | quebrado | **Alta** | junção de ramos dá `Object` / `LambdaN` do 1º ramo | `ExpressionTyper.java:255-277,350-358`, `HierarchyResolver.java:132-152`, `ExpressionBareCallLowerer.java:252-253` | nenhum | `ifExprLambdaBranchesUnifyToSamInterfaceJvm` + gêmeo switch | unificar ramos lambda de mesma assinatura na interface SAM |
| `HTTP-01` | HTTP / web | `http.status(401)` / `status(401)` cru passam o `check` | quebrado | quebrado | **Alta** | dispatch só por aridade, sem checagem de tipo; sem diagnóstico na divergência | `KofHttp.java:98-99`, `MethodCallNamespaces.java:72-78`, `ExpressionHttpCallLowerer.java:17`, `ExpressionBareCallLowerer.java:252` | `StdCatalogSignaturesTest` (só o dispatcher) | `http.status(401)`/`get(401)`/`timeout("abc")` devem falhar no `check` | checagem de tipo ao lado da de aridade; `SEM025`/`HTTP00x` dedicado |
| `MATH-01` | `kof.math` | `math.abs(Double)` | `SEM025` | `SEM025` | — | honesto (só-Int, com guarda) | `KofMath.java:48-49`, `StdCatalog.java:207` | `StdCatalogSignaturesTest:184` | — | nenhuma (LACUNA: sobrecargas Double?) |
| `MATH-02` | `kof.math` | `min`/`max`/`clamp`/`isEven`/`isOdd`/`isPositive`/`isNegative`/`isZero` aceitam qualquer tipo | quebrado | quebrado | **Alta** | **sem guarda de tipo** (só aridade) | `KofMath.java:52-59` | `KofMathTest` (só o caminho Int) | argumento não-Int deve dar `SEM025` | adicionar a guarda `isInt`, espelhando `abs`/`sign` — ✅ CORRIGIDO 11/10 no `lab` como `known-bugs` §658 (lane flatt `192.168.100.14:0`, `KofMathTypeGuardTest` RED-first) |
| `DIAG-01` | diagnósticos | um erro emitido duas vezes no caminho `VarDeclStmt` | 2× | 2× | Baixa-Média | `Unknown` nunca cacheado + re-inferência redundante | `SemExpressionTyper.java:15-28`, `StatementAnalyzer.java:151,162,166` | nenhum | pin count==1 | cachear `Unknown` ou deduplicar no sink — ✅ CORRIGIDO 11/10 no `lab` como `known-bugs` §661 (lane flatt `192.168.100.14:0`, dedup de duplicatas exatas em `DiagnosticCollector.report`, `DiagnosticSinkDedupTest` RED-first) |
| `DIAG-02` | diagnósticos | `ClassType[...]`/`PrimitiveType[...]` crus em `SEM012`/`SEM021`/`SEM014` de construtor | vaza | vaza | Baixa-Média | `toString()` cru do record em vez de `Type.display` | `SemAssignmentAnalyzer.java:54,140-142,159`, `SemExpressionTyper.java:184`, `StatementAnalyzer.java:173-175`, `TypeChecker.java:163-167` | `QualifiedTypeDiagE2ETest` (só SEM010) | assegurar ausência de `*Type[` nas mensagens | usar `Type.display(...)` nesses seis pontos |
| `MEM-01` | analisador de memória | `MEM014` em `web.app()` (lifetime de servidor) | avisa | avisa | Baixa | regra por escopo ignora `listen`/lifetime de processo | `memory/ResourceLeakAnalysis.java:52-73,111-171` | `ResourceLeakE2ETest` (sem caso servidor) | pin de lifetime de servidor | **LACUNA** — tratar `listen` como estabelecedor de lifetime (decisão da mantenedora) |
| `PARSE-01` | lexer/parser | expressão `1..3` | `PARSE039` | `PARSE039` | — | não há operador de range, por projeto | `parser/Lexer.java:437`, `parser/ExpressionParser.java:182-211` | sonda em `lexical-structure.md` | — | nenhuma (só nota de qualidade do diagnóstico) |
| `CLI-01` | CLI `serve` | `kof serve --deps` | rejeitado | rejeitado | Baixa | `serve` não tem fiação de `--deps` | `kof-cli/…/CmdServe.java:41-86` | nenhum | pin se `--deps` for adicionado | **LACUNA** — decidir se `serve` suporta deps |
| `HTTP-02` | segurança web | requisição com `Authorization` → `401` | por projeto | por projeto | — | "auth-if-present" documentado | `jvm/JvmRuntimeWebDispatch.java:150-158`, `JvmStringSecurityRuntime.java:320-360` | `KofWebHardeningTest` | pin de pass-through se opt-out for adicionado | **LACUNA** — opt-out de auth-if-present? |
| `UI-01` | `kof.ui` | `Style(token)` → `SEM077` | `SEM077` | `SEM077` | — | classificação de família correta | `BuiltinUiCallTyper.java:60-70`, `KofStyleParser.java:130-140` | `UiStyleCssE2ETest` | pin de token | nenhuma (renomear métodos de teste divergentes) |
| `ORM-01` | `kof.orm` | coluna `snake_case` ↔ campo `camelCase` | sem mapeamento | sem mapeamento | **Alta** | igualdade exata de nome, sem camada de mapeamento | `jvm/JvmConfigRuntime.java:474-507`, `jvm/JvmRuntimeJson.java:383` | `OrmCamelCaseE2ETest` (só exato) | snake↔camel RED-first | **LACUNA** — decidir automático/opt-in/rejeitar |
| `ORM-02` | diagnósticos ORM | `ORM005` | ausente | ausente | Média | nunca definido | `KofOrm.java:137-139`, `CompilerOrmSupport.java:44-186` | nenhum | — | **LACUNA** — definir ou declarar não usado |
| `VER-01` | release / CLI | `kof version` imprime `0.5.0-beta` na 0.6.0 | errado | corrigido | **Alta** | `version.properties` obsoleto (o bump o pulou) | `kof-compiler/…/version.properties`, `scripts/bump-version.sh`, `kof-cli/…/Main.java:49-55` | — | um teste `version == VERSION` | já corrigido no `lab`; **re-publicar** é decisão da mantenedora |
| `SCRIPT-01` | KofScript inline | `say` | `SEM015` | `SEM015` | Baixa | não existe símbolo `say` em lugar algum | `TopLevelCallTyper.java:141-154` | `KofScriptTest` (sem `say`) | pin se um `say` de shell for especificado | **LACUNA** — decidir um `say` só-do-shell |
| `SCRIPT-02` | KofScript inline | trecho sem `main` | funciona | funciona | — | statements de topo envolvidos em `main()` | `kof-script/…/KofScript.java:82-174,187-189` | `KofScriptTest`, `KofReplTest` | pin | nenhuma |

---

## 4. Evidências (C)

Todas as saídas abaixo são **executadas**, no CLI publicado 0.6.0 salvo indicação.

### `VER-01` — o binário publicado reporta a versão errada

```
$ /tmp/kof-audit/kof-0.6.0-linux-x86_64/bin/kof version
kof 0.5.0-beta

$ unzip -p lib/kof.jar dev/kof/version.properties
kof.version=0.5.0-beta
kof.compiler.version=0.5.0
kof.runtime.version=0.5.0
kof.stdlib.version=0.5.0
kof.tooling.api=21
```

O commit de preparação da 0.6.0 (`75f4b568d`) subiu `VERSION` e `pom.xml` mas **não** o
`version.properties`; o commit do auto-cascade (`628c60f03`, 11/10) o corrigiu na
origem. O `VERSION` está `0.6.0` no artefato publicado — só a constante de runtime está
obsoleta. O jar do `lab` imprime `kof 0.6.0`.

### `MATH-02` — `check` silencioso, resultado errado no Native

```
main() { println("" + math.min(1, 2.5))  println("" + math.min(3.5, 2.5)) }

$ kof check Main.kf
checked 1 file(s) — no errors

$ kof run --target jvm Main.kf
java.lang.VerifyError: Bad type on operand stack   (Type double_2nd … not assignable to integer)

$ kof run --target native Main.kf
0
0                                                  # esperado 1 / 2.5 — RESULTADO SILENCIOSAMENTE ERRADO
```

`math.min(Int,Int)` no Native está correto (`1`, `2`) — só o caminho não-Int quebra.
Também medido, todos `check` = "no errors": `math.min("a","b")` → Native imprime um
ponteiro lixo; `math.max(1.0, 2.0)`, `math.clamp(1.5,0.0,2.0)`, `math.isEven(2.5)` →
`VerifyError` na JVM.

### `JSN-01` — a recusa de primitivo ausente é só da JVM

```
record Pedido(String nome, Int clienteId, Bool pago)
json.decode<Pedido>("{\"nome\":\"a\",\"pago\":true}")

$ kof run --target jvm    → RuntimeException: JSN004: missing field 'clienteId' for Pedido
$ kof run --target js     → nome=a clienteId=undefined pago=true      # silencioso
$ kof run --target native → nome=a clienteId=0 pago=true              # zero-fill silencioso
```

`D-JSON-MISSING-PRIMITIVE` proíbe o zero-fill silencioso; o Native faz exatamente isso.

### `JVM-01` — reatribuir um local de tipo lambda

```
main() { var f = () -> 1  f = () -> 2  println(f()) }

$ kof check Main.kf
checked 1 file(s) — no errors

$ kof run --target jvm Main.kf
java.lang.VerifyError: Bad type on operand stack
  Location: Default/Main.main([Ljava/lang/String;)V @20: invokevirtual
  Reason:   Type 'Lambda1' … is not assignable to 'Lambda0'
  locals: { '[Ljava/lang/String;', 'Lambda1' }
```

O `javap` mostra o slot tipado `Lambda0`, sendo o segundo lambda `Lambda1`; ambos
implementam `kof.Function0_int`, então a correção é tipar o slot como a interface.

### `JVM-02` — local de tipo função sem inicializador

```
main() { var f: (Int) -> Int  f = (x: Int) -> x + 1  println(f(5)) }

$ kof check Main.kf
checked 1 file(s) — no errors

$ kof run --target jvm Main.kf
java.lang.VerifyError: Bad type on operand stack
  Location: Default/Main.main @1: astore_1
  Reason:   Type integer … is not assignable to reference type
```

O `javap` mostra `iconst_0; astore_1` — o default sem-init é o inteiro `0`, não `null`.

### `JVM-03` — if/switch-expressão com ramos lambda

```
main() { var flag = true
        var g = if (flag) (x: Int) -> x + 1 else (x: Int) -> x + 1
        println(g(5)) }

$ kof run --target jvm Main.kf
java.lang.VerifyError: … @35: invokestatic   (Type 'java/lang/Object' … not assignable to integer)
```

O `javap` mostra um `invokestatic Default/Main.f:(I)Ljava/lang/Object;` **fantasma** — um
método que não existe — enquanto o local está tipado `Object`. O gêmeo de
`switch`-expressão falha com `invokevirtual Lambda0.invoke` sobre um frame `Object`.

### `HTTP-01` — `status(401)` passa o `check`

```
main() { var s = http.status(401)  println(s) }

$ kof check Main.kf
checked 1 file(s) — no errors

$ kof run --target jvm Main.kf
java.lang.VerifyError: Bad type on operand stack   (Type integer … not assignable to 'java/lang/String')
```

As formas pretendidas são `http.status(String url) -> Int` (cliente) e
`status(code, body)` (servidor, aridade 2). `status(401)` não casa com nenhuma; a forma
crua cai num `invokestatic Default/Main.status(I)` fantasma.

### `DIAG-01` — diagnóstico duplicado

```
main() { var x = nope }

$ kof check Main.kf
Main.kf:1:18: error: Undefined variable or type: 'nope' [SEM011]
Main.kf:1:18: error: Undefined variable or type: 'nope' [SEM011]     # ×2 (errorCount=2 no --json)
```

### `DIAG-02` — representação interna de tipo vazando

```
class A { }  class B { }
main() { var a: A = A()  var b: B = B()  a = b }

$ kof check Main.kf
Main.kf:7:1: error: Type mismatch: cannot assign ClassType[packageName=, name=B, typeArguments=[]] to ClassType[packageName=, name=A, typeArguments=[]] [SEM012]
```

Contraste (o caminho bom): `Return type mismatch: expected 'A' but got 'B' [SEM010]`.

### `MEM-01` — `MEM014` no idioma canônico `web.app()`

```
main() { var app = web.app()  println("created") }

$ kof check Main.kf
Main.kf:2:5: warning: L-05: resource 'app' from 'web.app()' is never closed in this scope and never handed to anyone — call 'app.close()' or return/transfer the handle (MEM014) [MEM014]
checked 1 file(s) — no errors
```

A spec lista "Process" como lifetime válido para um servidor web; o idioma canônico em
`training/idioms/web.md` está marcado GOOD e nunca fecha `app`.

### `ORM-01` — coluna `snake_case` nunca alcança um campo `camelCase`

```
entity Cliente { id: String unique  clienteId: Int }
create table "cliente"("id" varchar(64), "cliente_id" int); insert … ("1", 42)
orm.find<Cliente>(db, "1")

$ kof run Main.kf
java.lang.RuntimeException: JSN004: missing field 'clienteId' for Cliente
```

Com um campo de **referência** a falha é silenciosa:

```
entity Cliente { id: String unique  nomeCompleto: String }   # coluna "nome_completo" = "Ana"
$ kof run Main.kf
[null]                                                        # exit 0 — dado silenciosamente errado
```

### `PARSE-01` / `CLI-01` / `UI-01` / `SCRIPT-01`

```
$ kof check range.kf      → Main.kf:2:15: error: Expected field name [PARSE039]   (×2)
$ kof serve --deps        → file not found: --deps
$ kof serve Main.kf --deps→ serve: unknown or incomplete flag: --deps (accepts: --port <n> --host <h> --backend <t> --frontend <t>)
$ kof check style.kf      → Main.kf:3:5: error: style: the declaration string must be a literal … [SEM077]
$ kof script 'say("hi")'  → Inline.kf:3:1: error: Undefined function: 'say' [SEM015]
$ kof script 'println("hi")' → hi   (sem `main`; SCRIPT-02 não se reproduz)
```

---

## 5. Análise arquitetural (D)

**Família de sobrecarga silenciosa (maior risco).** `MATH-02` e `HTTP-01` partilham uma
forma: um dispatch de namespace chaveado **só por aridade**, que devolve `null`/`UnknownType`
na divergência e **não emite diagnóstico**; o lowerer então emite uma chamada a um símbolo
tipado Int ou cai numa chamada de topo **fantasma** (`ExpressionBareCallLowerer.java:252`).
É a mesma família já registrada como `known-bugs` §554/§556/§557 e §629. **Uma forma de
correção** (checagem de tipo ao lado da de aridade, mais um diagnóstico no fall-through)
fecha a metade estática de `MATH-02`, `HTTP-01` e a metade de chamada fantasma de `JVM-03`.

**Tipagem de lambda-válor (uma família).** `JVM-01` e `JVM-03` derivam de o tipo de um
lambda-válor carregar a classe concreta `LambdaN`; nada fora do caso estreito de `listOf`
(§156, `CompilerTypeSupport.listOfElementType`) unifica lambdas de mesma assinatura na
interface SAM compartilhada. `JVM-02` é independente (o predicado `erasesToReference`
excluindo `FunctionType`). **Uma forma de correção** para `JVM-01`+`JVM-03` (remover o
`className` de lambdas em posição de valor / unificar a junção de ramos na interface); uma
mudança de uma linha de predicado para `JVM-02`.

**Pipeline de diagnósticos.** `DIAG-01` (sem dedup em lugar algum; `Unknown`
re-inferido) e `DIAG-02` (`toString()` cru do record em vez do `Type.display` existente)
são independentes mas ambos vivem na camada de análise semântica/diagnóstico. **Mudanças
isoladas**: dedup no sink por `(position, code, message)`; seis pontos de chamada
trocando para `Type.display`.

**ORM.** `ORM-01` (sem camada de mapeamento) e `ORM-02` (`ORM005` indefinido) partilham o
mecanismo de metadados/hidratação (`JvmConfigRuntime.kof_db_query_n` → `kof_json_bind`).
O chokepoint de leitura é um único lugar; o lado de escrita e a ponte JS espelhariam.
**Exige decisão primeiro.**

**JSON cross-target.** `JSN-01` é o decoder JS/Native sem a checagem de presença que o
binder JVM ganhou — mudança aditiva nos caminhos de decode compartilhados.

**Independentes:** `MEM-01` (analisador), `VER-01` (build/release), `SCRIPT-01/02`
(shell↔KofScript).

---

## 6. Plano de correção (E)

Ordenado por prioridade e dependência. **Aceitação** = um teste RED-first que falha no
código atual e passa depois; **encerramento** = mergeado no `lab` com o teste verde e a
bateria relevante não regredida. Nada abaixo é implementado nesta auditoria.

**P0 — dados silenciosamente errados / artefato publicado quebrado**

1. `MATH-02` — adicionar a guarda `isInt` em `KofMath.java:52-59`. *Teste:*
   `math.min(Double,Double)`, `math.clamp(Double,…)`, `math.isEven(Double)` → `SEM025`.
   *Risco:* baixo. — **FEITO 11/10:** pousado como `known-bugs` §658 (lane flatt
   `192.168.100.14:0`): `KofMathTypeGuardTest` mediu VERMELHO no repro exato da auditoria
   pré-correção, 2/0F pós; `*Math*`+`StdCatalogSignaturesTest` 53/0F; golden 236/236.
2. `VER-01` — já corrigido na origem no `lab`; adicionar um teste de que
   `KofVersion.version()` é igual a `VERSION`, e a mantenedora decide sobre um
   **re-publish** (tags são imutáveis). *Risco:* nenhum no código.
3. `ORM-01` — **bloqueado por decisão** (`ORM-02`/spec). Uma vez decidido: normalizar
   chaves em `kof_db_query_n` (leitura) + o lado de escrita simétrico. *Risco:* médio
   (caminho de decode compartilhado).

**P1 — `check` limpo, classe não-carregável**

4. `JVM-02` — tratar `FunctionType` como referência no default sem-init. *Teste:*
   `functionTypeLocalWithoutInitializerJvm`. *Risco:* baixo.
5. `JVM-01` + `JVM-03` — tipar lambdas em posição de valor como a interface SAM;
   unificar junções de ramos if/switch. *Testes:* `lambdaLocalReassignJvm` (#284
   verbatim), `ifExprLambdaBranchesUnifyToSamInterfaceJvm` + gêmeo switch. *Risco:* médio
   (bateria compartilhada de lambda/inferência — `LambdaE2ETest` 36, `ComponentCoreE2ETest`,
   higher-order).
6. `HTTP-01` — checagem de tipo ao lado da de aridade; diagnosticar o fall-through de
   função de contexto. *Testes:* `http.status(401)`, `http.get(401)`, `http.timeout("abc")`,
   `status(401)` cru falham; `status(401,"body")` ainda compila. *Risco:* médio (o
   fall-through em `ExpressionBareCallLowerer.java:252` é compartilhado com chamadas de
   topo legítimas — restringir só a membros de namespace/contexto conhecidos).

**P2 — paridade cross-target**

7. `JSN-01` — espelhar a recusa de presença de primitivo nos decoders JS e Native, ou
   registrar uma lacuna explícita em `known-bugs`. *Risco:* médio (caminhos de decode
   compartilhados; rodar a bateria JSON).

**P3 — qualidade de diagnósticos**

8. `DIAG-01` — dedup no sink de diagnóstico por `(position, code, message)`. *Teste:*
   `errorCount() == 1` para `var x = nope`. *Risco:* baixo-médio. — **FEITO 11/10:** pousado como
   `known-bugs` §661 (lane flatt `192.168.100.14:0`): dedup de duplicatas exatas (a tupla completa do
   record, args inclusos) em `DiagnosticCollector.report`; `DiagnosticSinkDedupTest` mediu VERMELHO
   3/3 pré-correção (o repro da auditoria ×2, dois defeitos ×4), 3/0F pós; bateria de diagnóstico/
   analisador 82/0F.
9. `DIAG-02` — trocar as interpolações cruas por `Type.display(...)` nos seis pontos.
   *Teste:* SEM012/SEM021/ctor-SEM014 nunca contêm `ClassType[`/`PrimitiveType[`/`NullableType[`.
   *Risco:* baixo (só texto de mensagem).

**P4 — exige decisão da mantenedora antes de qualquer código**

10. `MEM-01`, `CLI-01`, `ORM-02`, `SCRIPT-01`, `HTTP-02` — registrar a decisão, depois
    implementar. `PARSE-01`, `UI-01`, `MATH-01` — nenhum código necessário.

---

## 7. Lacunas de especificação (F)

Comportamentos que **não** podem ser definidos sem decisão de produto/linguagem. Nenhum
contrato é inventado aqui.

| Lacuna | Pergunta | Onde o comportamento atual é honesto |
|---|---|---|
| `MEM-01` | O lifetime de `app.listen(...)`/processo é um lifetime de recurso válido para L-05, ou um servidor deve `close()` no escopo? | a spec `docs/spec/memory-safety.md` lista "Process" como válido; o analisador exige `close()` no mesmo escopo |
| `CLI-01` | O `kof serve` deve suportar `--deps` (como `run`/`build`)? | a superfície documentada do `serve` não tem `--deps`; a rejeição é consistente com ela |
| `ORM-02` | Definir `ORM005` (quais falhas de ORM, que contexto, em que fase) ou declará-lo não usado? | `ORM005` não existe; `ORM003` cobre coluna desconhecida |
| `ORM-01` | O mapeamento `snake_case`↔`camelCase` deve ser automático, opt-in, ou rejeitado com diagnóstico? | o contrato do ORM é igualdade exata de nome |
| `SCRIPT-01` | O KofScript inline/REPL deve ganhar uma grafia `say`, e só nesse contexto? | não existe símbolo `say`; o `SEM015` é correto pelo contrato |
| `HTTP-02` | Um opt-out de "auth-if-present" é desejado (gateways de pass-through)? | o 401 é documentado e intencional |
| `MATH-01` | `abs`/`min`/`max`/`clamp` devem ganhar sobrecargas `Double`? | `math.abs(Double)` é honestamente recusado hoje |

---

## 8. Veredito (G)

A versão investigada apresenta problemas em **três categorias distintas**:

- **Regressões / defeitos funcionais** (`CONFIRMADO`): `MATH-02`, `JVM-01/02/03`,
  `HTTP-01`, `ORM-01`, `JSN-01`, `DIAG-01`, `DIAG-02`, `MEM-01`, `CLI-01`. Os severos
  partilham uma propriedade — **o `kof check` é verde e a falha só aparece no load ou
  produz dados errados**, o que viola as restrições `no-silent-fallback` /
  `honest-cross-target`.
- **Lacunas de implementação que exigem decisão**: `ORM-01`, `ORM-02`, `MEM-01`,
  `CLI-01`, `SCRIPT-01`, `HTTP-02`, `MATH-01` (lacunas de spec, não defeitos).
- **Defeito de processo de release**: `VER-01` — o binário publicado 0.6.0 se
  auto-reporta `0.5.0-beta`; a fonte já está corrigida no `lab`, mas o artefato publicado
  não.

Os cinco itens antes reportados como corrigidos (`RB-01`..`RB-05`) estão **confirmados
corrigidos na tag publicada** (cada um por um commit ancestral de `bb90986d0` mais
comportamento executado). Duas dessas correções, porém, estão **incompletas em outros
alvos** (`JSN-01`; a duplicação de `SEM025` de namespace em `DIAG-01`), e o contrato de
`RB-03` é **só da JVM**.

---

## Artefatos da auditoria

Arquivos auxiliares (fora do repositório, nenhum código de produção tocado):
`/tmp/kof-audit/` — a distribuição 0.6.0 extraída, os diretórios de reprodução `verify*`
e os diretórios de investigação por subsistema (`agent-2-1`, `agent-verifyerror`,
`agent-http`, `agent-diag`, `agent-misc`, `agent-orm`, `agent-shell`).
