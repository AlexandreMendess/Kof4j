[English](PLAN-MULTIPARADIGMA.md) | [Português](PLAN-MULTIPARADIGMA.pt_BR.md)

# PLAN-MULTIPARADIGMA — Multiparadigma, Pipelines Funcionais e Consultas Declarativas

**Status:** `PLANO FUTURO` · **Data:** 2026-09-16 · **Versão:** 0.5.0-beta · **Tier:** 2.x (core) → 8 (dados, adiado)
**Autor:** investigação no HEAD `beta-0.4.0` · **Lane:** nenhuma ainda — apenas design, zero código neste doc
**Depende de:** `docs/development/roadmap.md` §23 (SYSTEMS fecha antes do Tier 6+; `DECISIONS.md` fila D-NULL-INTENT N1→N4 da lane compiler), `docs/architecture/compiler-architecture.md`, `docs/language-reference/*`

> **Regra desta pasta:** este documento é um **plano sem código**. Nenhum arquivo listado em §8 foi alterado por este documento. Quando o primeiro incremento funcional for entregue, este plano move para `docs/development/` com tabela de estado real (o que está feito vs o que falta), pela regra dos três estados (`docs/development/future/README.md`). O estado atual do Kof permanece 100% intacto.

---

## 0. Pergunta

> Como permitir que o programador escreva a **intenção** de uma transformação de dados (`users.filter{...}.map{...}`) mantendo Kof simples, eficiente, multiparadigma e capaz de executar essa intenção no backend adequado (memória, stream, BD) — sem transformar Kof numa cópia de Scala/Kotlin/LINQ/Haskell?

A resposta não é "adicionar 15 métodos em List". É: **manter a superfície da linguagem como está, estender a camada semântica para que o compilador reconheça uma expressão de transformação, e deixar cada backend decidir a execução — preservando a semântica eager hoje, habilitando lazy/consulta amanhã sem mudança silenciosa de comportamento.**

---

## 1. Diagnóstico — onde o Kof realmente está (HEAD 2026-09-16)

### 1.1 Como funções e lambdas são representadas

- **Tipo:** `Type.FunctionType(List<Type> paramTypes, Type returnType, String className)` — `Type.java:29-33`. `className` é a classe sintética que implementa a chamada (usada no emit, bug 20).
- **Sintaxe:** `(x: Int) -> x*2`, `(a: Int,b: Int)->{return a+b}`, `() -> println("oi")`, `{ println("bg") }` block-lambda. Parse em `parser/LambdaParser.java:106-143`. `looksLikeLambdaParams` / `looksLikeLambdaBlockParams` são `Implementation-defined`.
- **Lowering:** cada lambda vira classe sintética `Lambda<N>` (ou `LambdaTask<N>` para `spawn`) implementando interface sintética `kof/Function<N>_<mangled>` — `CompilerLambdaClass.java:52`, `CompilerDriver.lambdaClass`. Capturas viram campos `private final`; capturas mutadas via `Box<N>` (`BoxClassFactory`). Call site: `new Lambda<N>(capturas)` + `invoke(args)`.
- **Representação de chamada:** `KofCall` com `KofCallKind {INSTANCE,STATIC,CONSTRUCTOR,FUNCTION,INTERFACE,SUPER}` — `IRNodes.java:30-ops` + `KofCallKind.java`. Chamada de lambda é `FUNCTION`; dispatch `INSTANCE→INVOKEVIRTUAL` etc. em `JvmBackend`.

### 1.2 Lambdas são valores de primeira classe? (parcialmente)

**Sim para:** guardar em variável `var f: (Int)->Int = (x:Int)->x*2` — `closures.md:51-54`; passar como argumento `list.map((x:Int)->x*2)`; retornar `(x:Int)->(y:Int)->x+y` — bug 19 corrigido `6dad633`; guardar em coleção e invocar `listOf((x:Int)->x*2).get(0)(4)` — bug 20 corrigido; corpo de `spawn`.

**Limitações (bloqueiam expressividade):**
- Param sem anotação default `Object` (`LambdaParser.parseLambdaParameter:127` = `"Object"`, `closures.md:37` SG-012). Uso em aritmética → `SEM001` com hint. A única **inferência contextual** é para `map/filter/reduce` em `List` (`MemberCallTyper.java:135-141` reescreve param `Object` para `elemType` via `contextualLambda`). Nenhuma inferência para outro higher-order, sem `it`/`$0` implícito (por design — `closures.md:148`).
- Sem lambdas genéricas, sem default params em lambdas, sem overload dentro de lambda, sem alias de tipo `fun`.
- `FunctionType → ClassType` conversão SAM sempre passa em `isAssignable` mas checagem real adiada para o emit — `Unspecified`.

### 1.3 Como closures capturam

- **Varredura:** `CompilerCaptureScanner.java:14-85` + `CompilerCaptures.collectCaptures`. Tratamento de shadowing para params/decls internas.
- **Captura read-only:** snapshot no momento da criação, campo `private final` — `closures.md:70-84`.
- **Captura mutável:** se var capturada é atribuída dentro da lambda **ou** o externo muta var capturada, dispara `Box<N>` (`CapturedVarBox.java:24`). Leituras/escritas viram `KofLoadField/KofStoreField "value"`. Mutação dentro da lambda é visível fora — `Stable`, testado. Bugs históricos: captura mutável após mutação externa dava garbage em Native (bug 9 FIXED), captura de lambda aninhada faltando (bug 19).

### 1.4 Como o compilador representa chamadas

- **Frontend:** `MethodCallExpr(receiver, methodName, arguments, typeArgs)` — receiver nullable (`f()` vs `obj.m()`). Chamada genérica `f<T>(args)` via `< >` postfix - `grammar.md:218`.
- **Tipagem (duas camadas):** `SemanticAnalyzer` fase 3 via `SemExpressionTyper` + `MemberCallTyper` (tabelas hard-coded para `List/Map/Set/String/Channel`, namespaces `Kof*`). `CompilerDriver` re-infere via `ExpressionTyper`/`MethodCallTyper`/`CollectionMethodTyper` — `compiler-architecture.md:8-4` "lowering re-infere tudo" (deliberado).
- **Lowering:** `ExpressionMethodCallLowerer.lower` → `CollectionCallLowerer.lower` para coleções → senão `ExpressionInstanceCallLowerer` / `ExpressionStaticCallLowerer`. `CollectionCallLowerer` baixa `kof_list_map/filter/reduce` como `KofCall(FUNCTION)` para `KofRuntime`.

### 1.5 Como o sistema de tipos trata funções

- `Type.of("(Int)->Int")` parseia tipo função — `Type.java:51-59`. `FunctionType` é variante normal de `Type` com SAM pass-through. Sem variância/bounds. Erasure em `JvmTypeMapper:16`. Sem checagem de subtipo de função (gap SG-009: `ClassType→ClassType` sempre atribuível). Chamada em valor com `FunctionType` checa `ft.parameterTypes` via `TypeChecker.checkArgTypes`.

### 1.6 Expressões vs statements

- **Formas expressão:** literal, identificador, binop, call, array access, lambda, `if (c) a else b` **else obrigatório** (`PARSE044`), `switch (o){case->}` **default obrigatório** (`SEM032`) sem fallthrough, `spawn f()` → `Handle<T>` (expr) vs `spawn f()` statement. `throw/return/break/continue` são **apenas statements** — `expressions.md:16-17`. Atribuição é **statement** não value-expression (`SEM027`).

### 1.7 Onde otimizações ocorrem

- `Optimizer.java:68-74` — **sempre ligado**, 4 passes: `constantFold`, `deadEffects`, `reachability`, `removeJumpToNext`. **Não há** inlining, fusão, pushdown, LICM, etc. `Kof IR` é op stream linear flat com labels, single `IRBasicBlock` por método. Trabalho pesado delegado ao JIT/assembler. Sem fusão de coleções.

### 1.8 Há IR adequado para representar operações semânticas?

- **Hoje: não.** `Kof IR` (30 ops, linear stack) não tem grafo de dependência nem CFG real. `QueryDslExpr.java:4` (`QueryDslExpr(entityType, whereClauses, orderBy, limit)`) existe só para `entity.query(db){...}` ORM DSL — não é IR genérico de transformação.
- **Conclusão:** para distinguir `constant / property / binop / lambda / map / filter / groupBy / distinct / sort / projection / materialização` como **expressões semânticas** otimizáveis por backend, é necessária **nova camada** — um **`TransformationExpr` antes do lowering** anotando a cadeia de chamadas, não um novo conjunto de opcodes.

### 1.9 Coleções

- **Implementação:** `List<T>` → `kof.List` → `java.util.ArrayList` (JVM) / asm próprio com free-list (`runtime/RuntimeList.java:13-362` + `nat/NativeRiscvAsmMapset1.java:196-353`) / `Array` (JS). `Map/Set` análogo. Conjunto de métodos hard-coded em 3 camadas espelhadas + `SEM025/055/056`.
- **Higher-order atual:** **só 3 em List**: `map`, `filter`, `reduce` (`training/idioms/collections.md:30-33`, `CollectionCallLowerer:17`). Semântica **eager, alocadora** (`kof_list_map` aloca `new ArrayList`). Sem lazy, sem fusão, sem análise de efeitos.

### 1.10 Como nativo e JVM compartilham semântica

- **Um frontend + IR agnóstico + backends plugáveis** (`README.md:70`). Mesmo `Kof IR` consumido pelos 4 backends. Paridade **provada por execução**: golden JVM; Native via `qemu`; JS via GraalJS; `KofInterpreter` direto.

### 1.11 Como a stdlib é resolvida e compilada

- **Dispatch tables em compile-time** (`KofStd`, `KofDb`, etc.). Cada namespace tem `staticMethod/instanceMethod` + `supportedOn/gapCode`. Call typer checa allow-list; lowerer emite `KofCall` para `KofRuntime` gerado (`JvmBackend` gera `KofRuntime.java` no output e compila com `javac`). `Native` emite símbolos `kof_*`; `JS` via `JsCallEmitter`. Nunca silencioso: gap diagnosticado.

### 1.12 Como novas features são documentadas e testadas

- **Docs:** `docs/language-reference/*.md` + `training/idioms/`, `learn/`. Novo idiom → atualizar `training/` obrigatório.
- **Tests:** `FunctionSyntaxTest`, `LambdaE2ETest`, `CoreRegressionE2ETest`, `ConformanceMatrixTest`, `KofInterpreterParityTest`, `CompilerDriverTest` (`SEM0xx`). **Quality gate Q0-Q7** (`AGENTS.md`): reproduce → fix root → prova com teste no mesmo commit → suite verde.

### 1.13 Limitações que impedem implementação funcional mais expressiva

| # | Limitação | Evidência | Impacto |
|---|-----------|-----------|---------|
| L1 | Só 3 higher-orders em List | `CollectionCallLowerer:17` | Precisa estender sem clonar `Stream` |
| L2 | Inferência de param só para esses 3 | `MemberCallTyper.contextualLambda` | Novos ops precisam generalizar inferência |
| L3 | IR sem grafo de transformação | `IRNodes.java` | Impossível fusão/pushdown sem camada semântica |
| L4 | Ops eager alocadores | `RuntimeList.kof_list_map/filter` | Pipeline `filter.map.take(10)` aloca 2 listas intermediárias |
| L5 | Sem Sequence lazy | grep `Sequence` → 0 | `Sequence<T>` é a espinha lazy natural |
| L6 | Sem análise de efeitos | nenhum | Não distingue `filter{it.active}` traduzível vs com efeitos |
| L7 | `TYPEOF` string (`"Object"` fallback) | `LambdaParser:127` | Enfraquece typer; trabalho boxed `D-NULL-INTENT` N1→N4 em voo — não sobrepor |
| L8 | `isAssignable: ClassType→ClassType` sempre true | SG-009 | Segurança delegada a `checkcast` runtime |
| L9 | Gate ≤500 linhas | `check_500` | Novos ops devem manter arquivos no gate ou split por responsabilidade |

**Não existe segunda arquitetura** — só o caminho dispatch-table + `KofRuntime`. O plano reutiliza-o.

---

## 2. Proposta arquitetural — compatível com o Kof de hoje

### 2.1 Princípios (não negociáveis)

1. **Intenção sobre mecanismo** — `spawn`, `setOf().contains()` hoje; `filter`/`map` amanhã.
2. **Core pequeno** — profundidade cresce como dispatch da stdlib, nunca como novos targets ou sintaxe.
3. **Additive** — novos métodos de coleção são aditivos; semântica existente não muda.
4. **Honest multi-target** (R6): capacidade só num target → gap diagnosticado `XXX00x`.
5. **Não é cópia** — sem hierarquia Scala, sem transplant `Sequence` Kotlin, sem LINQ verbatim.
6. **Compile-time > runtime** — type info sobre reflection.
7. **Unidades pequenas** — regra ≤500 faz parte da arquitetura; split por responsabilidade.

### 2.2 O split central: intenção vs execução (§3 do prompt)

Uma transformação escrita é **intenção**; como executa é concern do backend — mas sem virar "abstração genérica vaga".

**Kof já faz isso uma vez:** `db.query<User>(db, "SELECT ... WHERE active = ?", binds)` é a intenção (`OrmCall` → `kof_orm_*`); execução é JDBC na JVM, SQLite `.so` no Native, gap honesto no JS (`DB001`). O lado funcional deve reutilizar a mesma separação:

```
Kof:  users.filter{ it.active }.map{ it.name }
 ↓ (a) continua EAGER em List hoje — 2 alocadores, sem novo tipo, sem surpresa
 ↓ (b) mesma grafia pode baixar para Sequence (lazy) quando receiver for Sequence
 ↓ (c) o compilador reconhece o *shape* como TransformationExpr otimizável
 ↓ (d) só quando pure + conhecida pelo DB, traduz para SQL parametrizado
```

(a) entrega primeiro, totalmente testado. (b)(c)(d) atrás de tipos/diagnósticos explícitos.

### 2.3 Representação — onde colocar

**Keep `Kof IR` flat.** Introduzir **camada semântica middle-end** `TransformationExpr` (nível AST/lowering) que **anota** uma cadeia de chamadas, não um novo universo de opcodes.

```
Source → Lexer/Parser → AST → Semantic (anota elem type) → Transform IR (sealed:
Constant, Var, Property, BinOp, Logical, Call, Lambda, Map, Filter, FlatMap,
Fold, GroupBy, Distinct, Sort, Projection, Materialize, ...) → Optimizer
(fusion só quando pure) → Lowering (eager → kof_list_* hoje; Sequence → iteradores lazy;
DB → SQL binds) → Kof IR (op stream flat inalterado)
```

`TransformExpr` vive **antes** do lowering e baixa para os mesmos `KofCall`. Nada carrega em runtime.

### 2.4 Sintaxe — mínima, Kof-native (§5)

**Não inventar sintaxe.** A atual já resolve; estender só por **métodos**, não por gramática.

```kof
var doubled = values.map((value: Int) -> value * 2)
var big = values.filter((x: Int) -> x > 10).map((x: Int) -> x * 2)
```

- Mantém `(x: Int) -> expr`. `it` implícito rejeitado (seria mudança de contrato, regra 6).
- Mantém `listOf(1,2,3)`. Sem literal `[1,2,3]` (fake idiom).
- Pipeline `|>` / `..` / `in` não existe (`grammar.md:5.3`) — não entra agora.

### 2.5 Semântica — explícita, testável (§6)

| Propriedade | Decisão Fase 1 (eager `List`) |
|-------------|-------------------------------|
| **Ordem** | Preservada (inserção). `sorted` estável quando comparator puro. |
| **Avaliação** | **Eager** por operação. Documentado: `filter.map.take(10)` aloca 2 intermediárias hoje; `Sequence` será lazy depois. |
| **Materialização** | Explícita — cada `map/filter` materializa `List`. `Sequence` só em terminal (`toList`, `forEach`, `reduce`...). |
| **Mutabilidade** | Source nunca mutado por `map/filter` (nova lista). |
| **Igualdade** | Congelado `expressions.md:4`: `String`/`record` conteúdo, `enum` identidade, primitivo valor. |
| **Retornos** | `map: List<A>→(A->B)→List<B>`; `filter: →List<A>`; `flatMap: (A->List<B>)→List<B>`; `forEach: (A->Void)`; `find: →T?`; `any/all/none: →Bool`; `count: →Int`; `take/drop: (Int)→List<A>`; `distinct: →List<A>`; `sorted: →List<A>`; `groupBy: (A->K)→Map<K,List<A>>`; `zip: List<B>→List<(T,U)>`. |
| **Composição** | `xs.map(f).map(g) == xs.map(a->g(f(a)))` quando pure (travar com teste). |
| **Falha** | `take(-1)`/`drop(-1)` → `0`/`size` ou `SEM055` — definir em docs antes do código. |
| **Cross-target** | Mesmo output em JVM/Native/JS (golden por target). |

---

## 3. Plano incremental — não fazer tudo de uma vez (§4)

| Fase | Escopo | Gate | O que entrega | O que fica futuro |
|------|--------|------|---------------|-------------------|
| **0** | Diagnóstico + proposta (este doc) | este commit | este `PLAN-MULTIPARADIGMA.md` par | — |
| **1** | Fundações funcionais (§5) — fechar L1-L2 sem novo IR | `beta-0.4.0` aditivo | `flatMap`, `forEach`, `find`/`firstOrNull`, `any`/`all`/`none`, `count` (+ `take`/`drop`), `distinct`, mínimo `groupBy`/`zip` depois — tudo eager em `List`; generalizar `contextualLambda`; diagnósticos `SEM05x` reuse | Sem Sequence, sem IR, sem pure analysis |
| **2** | Avaliação & benchmarks | após Fase 1 | harness `kof bench` compara eager vs lazy (map/filter/combinado/with-take, pequenos/grandes, com capture) — sem promessa de perf antes de números | Sem código Sequence ainda, só números |
| **3** | Sequence / pipelines (§7) — `Sequence<T>` lazy | tier `experimental` | `Sequence<T>` (`sequenceOf`, `asSequence`, `toList`, terminais) com iteradores lazy (JVM `Iterator`, Native state machine, JS `generator`), compat `List.asSequence()` / `Sequence.toList()` | Sem fusão ainda — lazy via iteradores, não reescrita do op stream |
| **4** | Transformation Expression IR (§8) | middle-end, não-runtime | `transform/TransformExpr` sealed + `TransformOptimizer` (fusão filter-filter, map-map só quando pure) | Sem reescrita de backend, sem DB |
| **5** | Análise de efeitos (§9) | checker estático | `isPure`, `isTranslatable`, `hasObservableEffect` — progressiva (pure Bool/Int/String property/binop/logic/call puro conhecido vs unknown/external/log) | Sem promessa "metade traduz, metade local" |
| **6** | Abstração de consulta declarativa (§10) | representação neutra | `queryOf(lista)` / `q.filter{}.map{}` sobre `TransformExpr` source-agnostic; backend coleção/memória primeiro | Sem SQL, sem JDBC |
| **7** | Backend SQL (§11) | só parametrizado | `users.filter{it.active && it.age>=18}.map{it.name}` → `SELECT name FROM users WHERE active=? AND age>=?` quando pure & mapeável; nunca concatenação | Sem ORM disfarçado, sem query-builder que só renomeia SQL |

**Ordem é normativa:** sem `Sequence` antes dos novos ops sólidos; sem `TransformExpr` antes de benchmarks provarem custo eager; sem SQL antes de pure testável. Cada fase leva seus testes + paridade + docs, e o doc anterior move `future/`→`development/`→`docs/` ao fechar.

**Guarda R12:** Fases 0-4 são **hardening do core** (permitidas antes de SYSTEMS fechar). Fases 5-7 são **DATA/INFRA** — gated por fechamento do Tier 1; este doc não as abre como trabalho, só como design.

---

## 4. Novas operações — spec Fase 1 (§6)

| Op | Assinatura (Kof) | Comportamento | Esboço Native |
|----|------------------|---------------|---------------|
| `forEach` | `List<T>.forEach((T)->Void): Void` | Itera sem alocar; receiver não mutado | loop chamando `invoke` por elemento |
| `flatMap` | `List<T>.flatMap((T)->List<R>): List<R>` | `T→List<R>` por elemento, concatenado em ordem | aloca `out`, itera src, `invoke` → `List<R>` tmp, splice via `get`+`add` |
| `find` | `List<T>.find((T)->Bool): T?` | Primeiro match ou `null`; alias `firstOrNull` | loop `invoke`, `test` → return elem, senão `null` + `NullableType` |
| `any`/`all`/`none` | `List<T>.any((T)->Bool): Bool` | Quantificadores short-circuit (vacuous: `all` true em vazio) | loop com `return` antecipado |
| `count` | `List<T>.count(): Int` e `count((T)->Bool): Int` | Sem predicado = `size`; com predicado conta matches | `size` ou `if(pred) cnt++` |
| `take`/`drop` | `List<T>.take(Int): List<T>` | `take(n)` → prefix `min(n,size)`; `drop(n)` → suffix | `copy` de fatias |
| `distinct` | `List<T>.distinct(): List<T>` | Dedup preservando primeira ocorrência; String conteúdo | loop com `contains` check antes de `add` |
| `sorted` | `List<T>.sorted(): List<T>` | Copia e ordena; natural via `compareTo` | `copy` + `qsort` |
| `groupBy` | `List<T>.groupBy((T)->K): Map<K,List<T>>` | Mapa de grupos em ordem de inserção | `HashMap<K,ArrayList<T>>` |
| `zip` | `List<T>.zip(List<U>): List<(T,U)>` | Até `min(sizeA,sizeB)` | pairwise `add` |

**Inferência para `find/any/...` deve generalizar `contextualLambda`** — estender de `map/filter/reduce` para o novo set; senão usuário escreve `(x:Int)` para sempre.

**Diagnósticos reuse:** `SEM025` método desconhecido, `SEM055` tipo índice errado, `SEM056` heterogêneo.

---

## 5. Efeitos e traduzibilidade (§9) — design antes de código

```kof
users.filter{ it.active && it.age >= 18 }          // pure → traduzível
users.filter{ external.isValid(it.email) }         // unknown → não traduzível
users.filter{ log(it); it.active }                 // efeito → não traduzível
```

Futuro checker classifica `TransformExpr` como:

- **Pure** — só `constant | var | property | compareTo | binop | logic | call puro conhecido`;
- **Known** — predicado usa só propriedades com mapping conhecido pelo backend;
- **Translatable** — pure ∧ known ∧ shape `where|orderBy|limit|groupBy...`.
- **Sem sistema de efeitos na Fase 1** — documentar evolução como tabela progressiva.

---

## 6. Abstração de consulta (§10) & SQL (§11)

```kof
users.filter{ it.active }.map{ it.name }
db.query<User>(db).filter{ it.active }             // quando traduzível
```

**Invariantes para SQL (quando chegar):**
- Modelo via `entity User { name:String; age:Int }` (record+schema já existe, `DATABASE_VISION.md` level 2).
- Propriedade → coluna por nome.
- Sempre preparado (`?` + binds, nunca concat).
- `join`/agg/`page`/transação → gaps `DB001/ORM001` até implementação.

---

## 7. Otimização por intenção (§12)

| Otimização | Quando segura |
|------------|---------------|
| `map(f).map(g)` fusion → `map(a->g(f(a)))` | só quando `f`/`g` pure |
| `filter` sucessivos fusion | sempre (conjunção) |
| filter pushdown | só quando provar independência |
| projection pruning | só quando tradução pure |
| eliminação de intermediárias em `Sequence` | via iterador |

**Nada** que elimine efeito; nada paralelo sem claim de safety explícito.

---

## 8. Inventário de arquivos — o que muda e o que é novo

### Serão editados (reuse)

| Arquivo (`kof-compiler/src/main/java/dev/kof/compiler/...`) | Papel |
|---|---|
| `parser/LambdaParser.java` | manter, anotar gate de inferência para novos ops |
| `SemanticAnalyzer.java` / `SemExpressionTyper.java` / `MemberCallTyper.java:127-163` | alargar `contextualLambda` de `map/filter/reduce` → set completo Fase 1 |
| `ExpressionTyper.java` + `MethodCallTyper.java:240-254` + `CollectionMethodTyper.java:14-43` | espelhar mudança |
| `CollectionCallLowerer.java:14-56,101-227,334-398` | **core edit** — ramificações `flatMap/forEach/find/any/all/none/count/take/drop/distinct/sorted/groupBy/zip` (eager `kof_list_*`) |
| `jvm/JvmStringMiscRuntime.java:81-103` | bodies JVM `kof_list_*` novos |
| `jvm/JvmRuntimeCallDescriptors.java:429` + `jvm/JvmRuntimeReturnDescriptors.java:191` | descriptors novos |
| `runtime/RuntimeList.java:245-361` + `nat/NativeRiscvAsmMapset1.java:196-353` | asm x86 & riscv novos; aarch64 via translator |
| `js/JsRuntimeOps.java:48` + `js/JsBackend` + `js/JsCallEmitter` | dispatch novos `kof_list_*` |
| `KofInterpreterConcurrency.java:34-56` | cases interpreter |

### Novos (só quando a fase precisar)

| Arquivo | Fase | Tamanho alvo |
|---------|------|--------------|
| `transform/TransformExpr.java` (sealed) | 4 | ≤200 |
| `transform/TransformAnalyzer.java` | 5 | ≤200 |
| `transform/TransformOptimizer.java` (fusion) | 4 | ≤200 |
| `collection/Sequence.java` + `collection/SequenceRuntime.java` | 3 | ≤400 cada |
| `collection/SequenceOpsLowerer.java` | 3 | ≤250 |
| `bench/ListPipelineBenchTest.java` | 2 | harness |
| `E2ET: KofFunctionalOpsE2ETTest`, `SequenceE2ETest` | 1 / 3 | por fase |

**Não tocados:** `IRNodes.java` (flat permanece flat), `Parser.java` (gramática inalterada), `Lexer.java` (sem novo token).

---

## 9. Riscos e mitigações

| Risco | Prob | Impacto | Mitigação |
|-------|------|---------|-----------|
| **M1** Crescer `CollectionCallLowerer` além de 500L | Alta | Gate falha | Split por responsabilidade quando perto de 500; `check_500 --update-baseline` |
| **M2** Reflection JVM `kof_ho_invoke` vira gargalo | Média | Perf | Native já usa ponteiro; JVM cacheia `Method` por lambda class se necessário |
| **M3** Reintroduzir atrito `SEM001` por param sem anotação | Alta | UX | Alargar `contextualLambda` junto com cada novo op |
| **M4** Divergência `contains` String tag para `distinct`/`groupBy` | Média | Bug paridade | Reuse `CollectionWrites.stringTag` conjuntivo |
| **M5** Ambiguidade `Sequence` vs `List` lazy/eager | Média | Quebra semântica | `Sequence` `experimental`, explícito `list.asSequence()` opt-in, nunca implícito |
| **M6** Promessa SQL demais | Média | Injection / semântica | Fase 5 diz **não inventar**: só parametrizado, gate pure |
| **M7** Sobreposição `D-NULL-INTENT` boxed `T?` (`find`→`T?`) | Média | Conflito rebase | Coordenar `NullableType` reuse `SG-008` |
| **M8** Fragilidade translator aarch64 | Baixa | Bug Native | Emitir riscv primeiro e translator segue; coberto por `NativeRiscv64E2ETest`/`Aarch64`+qemu |
| **M9** Abertura prematura de stage universal (R12) | Média | Violação processo | Fases 5-7 marcadas **adiadas** até Tier 1 fechar; promoção precisa decisão em `DECISIONS.md` |

---

## 10. Critérios de aceitação (antes do merge)

### Linguagem

- Kof segue multiparadigma: POO, procedural e funcional coexistem.
- Lambdas consistentes; sintaxe sem boilerplate novo.

### Compilador

- 3 camadas espelhadas (`MemberCallTyper` + `MethodCallTyper`/`CollectionMethodTyper` + `CollectionCallLowerer`) — adicionar método sem as demais é bug.
- `TransformationExpr` no middle-end, não no `Kof IR` nem runtime.
- Diagnósticos reuse `SEM025/055/056`; novo código só com gap honesto `XXX00x`.

### Runtime

- Cada novo op `List` com comportamento definido: ordem, avaliação (eager Fase 1, lazy só em `Sequence`), materialização explícita, mutabilidade, igualdade, composição, falha, paridade 5 targets.
- Nenhuma alocação intermediária evitada silenciosamente na Fase 1 — documentado.

### Performance

- Entradas `kof bench` para `map`, `filter`, combinados, `withTake`, pequenos/grandes, com capture — medido, não prometido.

### Documentação

- `docs/language-reference/*`, `training/idioms/collections.md`, `learn/39-stdlib.md` atualizados no mesmo commit que o código.

### Testes

- Um commit = um op + paridade + edges (Q1-Q3). Matriz: happy + vazio/null + limite + erro esperado + cross-target + idempotência + concorrência + recurso.

---

## 11. Estimativa de complexidade por fase (calibrada em trabalho fechado)

| Fase | Forma de trabalho | Referência (fechado, similar) | Estimativa |
|------|-------------------|-------------------------------|------------|
| 1 — List ops eager (`flatMap`..`zip`) | ~10 métodos × 3 camadas × 4 backends | `§179` builtin UI/media `69bc0f73` | **M** (~1 commit por 1-2 ops; total 6-8 commits) |
| 2 — Benchmarks | wiring harness | `kof bench/profile` harness | **E-M** |
| 3 — Sequence lazy | nova kind | `Channel<T>` | **M-H** |
| 4 — Transform IR + fusion | pacote middle-end | `LambdaCapture Scanner` | **M** |
| 5 — Effect analysis | só checker | `§205` branch-by-branch | **M** |
| 6 — Query abstraction | API neutra | `kof.mq` / `kof.validation` | **M** |
| 7 — SQL backend | codegen sobre TransformExpr | `KofOrm` wire | **H** |

**Primeiro incremento viável (Fase 1a, fazível agora):** `flatMap` + `forEach` + `find`/`any`/`all`/`none`/`count` (6 ops) — sem novo IR, sem Sequence, eager, reuse do padrão `kof_list_*`. Um commit por par de ops com E2E parity. Depois, `take`/`drop`/`distinct` (1 commit), depois `sorted`/`groupBy`/`zip`.

---

## 12. O que NÃO será feito (§15) — guardas explícitos

Sem cópia de Scala/Kotlin/Haskell/Rust/LINQ; sem API funcional gigante sem semântica clara; sem ORM disfarçado de linguagem; sem query builder que só renomeia SQL; sem sistema de macros complexo; sem sistema de efeitos completo antes da Fase 5; sem refatoração total do compilador; sem abstração só-JVM; sem reflection obrigatória; sem tradução ingênua de lambda para SQL; sem promessa de otimização antes de números; sem quebra de código existente; sem mudança sem teste + doc.

---

## 13. Próximo passo após este plano (para o re-trigger)

**Não mover este doc ainda.** Para iniciar Fase 1a, o próximo agente:

1. `git log --oneline -5 -- kof-compiler/src/main/java/dev/kof/compiler/CollectionCallLowerer.java` — confirmar arquivo livre (regra de colisão).
2. Adicionar **um** método `flatMap` end-to-end (3 layers + 4 backends + E2E) — prova padrão e harness; commita com claim `IN PROGRESS` no `DOING.md` (dono IPv4, branch, arquivos) e checklist Q0-Q7 na msg.
3. Repetir por par de ops até fechar Fase 1, quando move este doc `future/PLAN-MULTIPARADIGMA.md` → `docs/development/PLAN-MULTIPARADIGMA.md` com estado `IN_PROGRESS`, e finalmente para `docs/` quando fundação funcional `IMPLEMENTED`.

`PRÓXIMO PASSO: Fase 1a — CollectionCallLowerer flatMap+forEach (kof_list_flatMap/forEach) com alargamento contextualLambda, E2E 4-targets, edges Q3, sem novo IR — arquivo kof-compiler/src/main/java/dev/kof/compiler/CollectionCallLowerer.java prova CompilerDriverTest+KofInterpreterParity`

---

## Referências

- `AGENTS.md` (Quality gate Q0-Q7, gate `≤500`, `DOING.md`, regra `future/`)
- `docs/architecture/compiler-architecture.md` §§4.1-5.3
- `docs/language-reference/{grammar,functions,closures,expressions,type-system}.md`
- `docs/bugs-and-gaps/specification-gaps.md` (SG-009, SG-012, SG-002)
- `docs/bugs-and-gaps/known-bugs.md` (bugs 19/20, §170, §181)
- `docs/development/roadmap.md` §23 Tiers 0–12, `docs/development/DECISIONS.md` (D-NULL-INTENT N1→N4)
- `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (invariants R1–R12)
- `training/idioms/collections.md`
