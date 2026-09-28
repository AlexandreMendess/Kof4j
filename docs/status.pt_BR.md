[English](status.md) | [Português](status.pt_BR.md)

# Status do Projeto Kof

last: 659-mem-4.2-travada + F3-pousado
doing: memory-safety / http-policies (duas lanes vivas)
next: fase-4.3-faces-callback (medir-antes, lane paridade) / http-policies-f5 (lane pipeline)
location: status
state: active
constraint: pr619-maintainer-only
decision: D-KOF-FIRST-IMPL

**Versão:** 0.5.0-beta (pom `revision`) · **Última atualização:** 28 de setembro de 2026.

**28/09 — frente pipeline + biblioteca (branch ativa = `lab`, `D-BRANCH-PIPELINE`).**
- **Esteira de qualidade pousada:** cutover `lab → testing → prerelease → stable → release/x.y.z → tag` (`D-QUALITY-PIPELINE-2609`); máquina de estados (`scripts/pipeline/pipeline_state.py` 21/21), promotion gate (`promotion_gate.py`, 100%), `promote.yml`, rulesets (`pipeline-stages`, `release-tags`); `beta-*` congeladas.
- **`http-policies` PROMOVIDO** `future/` → [`docs/development/http-policies-plan.md`](development/http-policies-plan.md) (`D-HTTP-POLICIES`, `D-FUTURE-PROMOTION`): políticas HTTP/Web aditivas (global + `app.policy` prefixo + opts de endpoint + `responses`), JVM-first, Native/JS `WEB006`.
- **`scoped-resources` PROMOVIDO** `future/` → [`docs/development/scoped-resources-plan.md`](development/scoped-resources-plan.md) (`D-SCOPED-RESOURCES-GO`, lote `D-FUTURE-BATCH-2809`, `D-FUTURE-PROMOTION`): fatias 1–5 landed — `using (x = init, closer) { }` → `desugarUsing` primeiro em `DesugarSteps.defaults()` → `try/finally` (`UsingDesugarE2ETest` 14/14 nas fatias 1–5: reverse-close aninhado + `db`-H2 JVM/Script/JS happy/exceção com silêncio MEM014 e isolation-by-release; closer explícito porque `x.close()` é falso p/ `db`).
- **memory-safety fase 4.2 (#659):** matriz spawn-captura travada nos 4 alvos incluindo captura MUTADA (`44`) e visibilidade filho→pai (`44/22`) — lacunas pegas pelas duas primeiras passadas do verifier independente; escopo da corrida escalar escalado a mantenedora; vereditos registrados: `MEM023` sem face de compilacao, geradores inexistentes (ausencia, nao gap)
- **memory-safety fase 4.1 (#658):** paridade B-06 de captura mutavel de lambda travada nos 4 alvos — `LambdaE2ETest` 36/36 com faces Script (`KofInterpreter`) e JS (`KofJsRunner`) golden-identicas; zero mudanca de comportamento (a paridade ja era verdadeira — medida, nao assumida).
- **`test-architecture` PROMOVIDO** `future/` → [`docs/development/test-architecture-plan.md`](development/test-architecture-plan.md) (`D-TEST-ARCHITECTURE-GO`, `D-FUTURE-BATCH-2809`, `D-FUTURE-PROMOTION`): infraestrutura pura de testes (não toca o compilador); primeira fatia = profiling da Fase 1 (`scripts/test-suite-profile.sh` → `docs/testing/TEST-PERFORMANCE.md`).
- **`pagination` PROMOVIDO** `future/` → [`docs/development/pagination-plan.md`](development/pagination-plan.md) (`D-PAGINATION`, `D-FUTURE-BATCH-2809B`, `D-FUTURE-PROMOTION`): janela de primeira classe — `Window<T>`, `slice`/`take`/`drop` em memória (P1) → `Window<T>` (P2) → `LIMIT/OFFSET` SQL (P3/P4) → `pageRequest` HTTP (P5); superfície travada, primeira fatia RED-first.
- **`D-FUTURE-BATCH-2809B`:** as questões de design abertas dos planos restantes de `future/` estão resolvidas (pagination, value-records, entity-history, multiparadigma, graphics, testing-platform, connectors, kof-file, image-vision, bootstrap, wasm D-WASM-01..09).
- **Parity linha 11 FECHADA** (`compareToIgnoreCase` em todos os backends) → `full_parity` GREEN.

**24/09 — janela 21/09→24/09 (conferida contra `git log`/tips; reator completo 3818 / 0F / 0E). A PR `#619` (`beta-0.5.0 → main`) está ABERTA e o merge é da MANTENEDORA — regra 10 do AGENTS.md: nenhum agente mergeia/aprova/fecha sob hipótese alguma.**
- **Igualdade de record no Native COMPLETA** (família §104b/§114 fechada): `List/Set.contains` + dedup do `set.add` comparam record por CONTEÚDO (tag 2 → `kof_obj_equals` + `kof_equals_table`, `4cce594e7`); chaves de `Map` por conteúdo (`f31ac11f4`); `containsValue(record)` lado-valor tag 7 da **PR #616 do Publio Santos** (RED 3/3 → GREEN 3/3 em x86-64+riscv64+aarch64, `b7c13ba1f`, #615 fechada pela via oficial de evidência); `equals` sintetizado cobre campo record aninhado (`523dfabb5`); faces do `hashCode` String/Double/record-aninhado por conteúdo (`67acf5a77`/`e2f0629f6`/`2e90aa7c2`). Oráculos `NativeRecordCollectionEqualityE2ETest` + `NativeRecordHashCodeE2ETest` byte-idêntico JVM≡Native em 3 arquiteturas.
- **Paridade do harness de teste no Windows fechada** (issues do Jonas Rocha, lane issues): #612 `-cp` de JVM filho usa `java.io.File.pathSeparator` (18 arquivos) + `ClasspathSeparatorGuardTest` nomeando `arquivo:linha` (`cdbf45145`); #603 18 vermelhos Windows do `KofOrmE2ETest` fixados — `kofPath()` põe barra normal (o `\` cru colapsava como escape pela regra lexical; a linguagem estava certa, o harness não), `/tmp` → `tempDir`, stderr do filho DENTRO da falha, Postgres por credencial real (`970c847a`); #617 `File.mkdir()` deixa de ser no-op silencioso; membro desconhecido em builtin kof.io → **SEM102** limpo com hint `Directory.createDirectories()` (`aed5fe7b4`); #618 NÃO-bug (o argumento arquivo do `kof run` tem o diretório como módulo, por design; documentado em `learn/32-cli-tooling`). **Zero issues abertas.**
- **Endurecimento Native**: §485 o `receive` do canal drenava a fila sem zerar `tail` → SIGSEGV NULL determinístico em x86_64+riscv64+aarch64 (`7f5a2e054`); §486 bridges de retorno covariante, duas faces; **B-3b baremetal: o payload Kof real RODA pelo caminho BIOS sob SeaBIOS** (`KO-BIOS PAYLOAD`, `e3644d596`, 5 bugs achados+corrigidos).
- **Ciclo do tech-debt aberto+fechado em 48h**: `tech-debt.md` aberto 23/09 com 6 §NNN vivos — todos medidos ✅ até 24/09 (§205 print boxed, §248 default methods em 4 alvos, §271 bridges de interface genérica, §278 face `kof.security` Android via JCA + stub `kof.gpu`, §283 exit do scheduler aarch64, §423 canais cross) + `check_500` verde → a mantenedora MATOU o ledger, a ferramenta `debt-scout` (38 arquivos) e `technical-debt/` (`f4a987166`); DECISIONS anotam KILLED.
- **Docs três estados**: `PROPOSAL-1.0-EXIT-GATE` → `docs/` (contrato normativo do 1.0); `PROPOSAL-VERSIONING-RELEASE` → `docs/distribution/` (PR #582, 22/09); X5/X6 variância IMPLEMENTED (cond.2 do release gate GREEN); allowlist do `check_release_050_gate` enxugada → `loose_docs GREEN`.
- **Contribuidores (fechado com prova, creditados)**: #608/#614/#609 (face JVM de record+interface genérica do Publio pousada como `081202e0c` após rebase, autoria preservada), #610/#611 (diamante de `default` conflitante → SEM101 + overload por aridade, `374b2b4bb`), #604/#605 (gate CodeQL conta só CodeQL), #613 (face nativa da família de bridges, verifier independente p/ risk HIGH).
- **Prep da release**: `release-beta-0.5.0-prep` acompanha o corte; bots de CI do tip verdes; estado restante por `scripts/check_release_050_gate.sh` (parity/bugs_gaps = ambiente/lane medida; decisions/edges GREEN).

**20/09 — noite 19/09→20/09 (tip `1080238f`; CI `Build+Tests` verde; reator ~3034 testes 0F/0E).**
- **D-TROOL**: `Bool` permanece 2 valores; `Troolean` (`true/false/null`) é o tipo trivalente de Kleene (mantenedora 19/09, roadmap 2.6.5–2.6.7 ✅; `916b9fb7`/`d61836eb`/`5f0757e8`): `Bool?` do usuário → SEM095; `&&`/`||`/`!` Kleene estrito na máquina boxed; `if (t)` ≡ `if (t == true)`; `TrooleanLawE2ETest` 13/13; corpus sincronizado.
- **Rio da erasure JVM** (§355/§356/§357, `16f16081`/`c8d55a10`/`0aa6a307`) — 8 issues fechadas (#399/#363/#375/#385/#366/#365/#295/#368): variáveis de tipo carregam o bound a todo emit site; interfaces genéricas baixam com descritores apagados e pontes covariantes varrem pais E interfaces; campo `T[]`/`as T[]` apaga honestamente; `SEM098` só-JVM. §358 FIXED 21/09 com `NAT004` honesto (gap nativo pré-existente no tip limpo, `NativeGenericDispatchGapE2ETest` 2/2).
- **20/09**: gate do §362 pousado (`57a0d5f0` — ctor fantasma → SEM023/SEM014, 7/7); varredura fatia-2 achou §371/#550 (loader da CLI distribuída nunca prune → força `usesDb`+link dinâmico, quebra `kof build --target native.risc|arm` com COMP001); §370/#549 fechado (`35bfaec1`, coercão numérica de `extern` byte-idêntica, SEM014); §373/#443 pousou (`d969bc3a`, `List/Set/Map` bare em posição declarada → builtin em `qualifyDeep` 2b, `BareCollectionFieldE2ETest` 8/8); X7-5 JDWP cru reconstruído vs o wire do JDK 25.0.4.1 (§376, `81401629`; falso-verde §377). Bugs de processo: §372/#551 (regressão do gate §368 sobre o rio), #552 (`BuiltinCallTyper` 612≥600), §374/#553, §378/#554. §360 FIXED (`4ed9bb2f`, handle ops do process.spawn mortos no JVM); face JS `081a48f8`+`2d20e5d4` (`ProcessSpawnE2ETest` 4/4, `ShellE2ETest` 15/15). X7 DWARF cross `.file/.loc` (`NativeDwarfCrossTest`, `5d9c855c`/`23b7ecf7`) + DIEs `890b58bf`. X10 FECHADA (`27826838`, LSP 32/32 namespaces, 265 membros/282 formas). Bugs novos: §361 escrita nullable-primitivo (`e293c4a5`, PARCIAL — face `Char?` ainda morre, §365 aberto), §362/#545, §366/#547, §367/#548. Disciplina do ledger: §NNN do tip REMOTO; `check_known_bugs_status.sh` token 🔓. DECISIONS cresceu (`7dfc6441`): `D-MAKEALIVE`, `D-KOF-AS-CLOUD`, `D-BOOTSTRAP`, `D-DB-GAPS`, Lei da Simplicidade = regra 11.
- **18/09 — R3 FFI (JVM) generalizada**: `extern` casa a ABI escalar completa, qualquer aridade sobre {Int,Long,Float,Double,Boolean,String}, um downcall `kof_ffi(lib,name,sig,Object[])` (`FfiE2ETest` 8→9, `FfiSignatureTest` 4/4); paridade JS via `KofJsFfiBridge` (7 casos `assertJvmJsParity`); callbacks na JVM e no host JS (`JvmFfiCallbackE2ETest` 42/42/6.0/7.5). Não-escalares → `FFI002`; Native `FFI001` (§61, gap honesto por target).
- **18/09 — §132 FECHADO (#83-JS)**: supervisor OTP roda no KofJS (`time.sleep` é await-point via `computeAsyncColoring`; `OTP002` levantado; `AsyncSleepJsE2ETest` 3/3); riscv/aarch seguem `OTP001`.
- **19/09 — §129 FECHADO em riscv64/aarch64; `OTP001` REMOVIDO** (tabela por-TID `kof_exc_slots`, `gettid`=a7 178; "TLS real via clone" provado ABI-inseguro e abandonado; `KofConcurrency2Test` 48/0, `KofSupervisorE2ETest` 16/0, E2E cross 45/0). `planning-otp-supervision.md` promovido a `docs/`.
- **15/09 — §129 FECHADO Native x86** (`kof_exc_chain` TLS por thread + `kof_spawn_trampoline` handler por worker; `KofSupervisorE2ETest` 15/15, `KofConcurrency2Test` 40/0, `ExceptionsE2ETest` 11/0, `NativeE2ETest` 65/0; o 1 vermelho era §205, fechado no mesmo dia fatia 1 `97d08e54`).
- **14/09 — linha de base da estabilização de release** (`192.168.100.17`): run limpo 4-módulos `1819 testes, 3F, 0E, 7 skip` — os 3 cross-arch são de outras lanes: residual §181 (`riscv64/aarch64CastSaturation`, só `(-inf) as Int`) e §192 (`KofMathTest.parseOrDefaultCrossArch` hang, aliasing de slot). §176 fechado (`a5eedbe2`). Decompiler despriorizado.
- **12/09 — §107 face escalar FECHADA nos 3 alvos nativos** (`kof_{list,set,map}_to_string` x86 `f3b3821c` + cross `411e9ce5`); §138 destravou o build cross. Issue #97 tree-shaking S-1/T0 FEITA (`a3996600`, `ArtifactSize` + `ArtifactSizeTest` gate 5% + `kof build --print-sizes`). Re-auditorias doc-vs-realidade `42c716ed`.
- **11/09 — OTP núcleo (#83) JVM+Script** (`kof.supervisor`, `KofSupervisorE2ETest` 6/6; §130 corrigido); §133 fetch assíncrono KofJS (`KofHttpE2ETest` 8/8); MATH001, TIME002, SG-011B (sobrecarga top-level; SEM047/SEM057; `TopLevelOverloadE2ETest` 5/5; §136) fechados. **§131 sobrecarga de MÉTODO de classe FECHADA 13/09** (`18a64d45`, 4 backends; `CoreRegressionE2ETest.methodOverloadByArity`).

---

## Build

```
mvn clean package    → PASSA
mvn test             → 3225 testes (2762 kof-compiler + 50 kof-script + 7 kof-c-compiler + 406 kof-cli), 0 falhas / 0 erros, 221 skip — job CI Build+Tests do tip `404d8be6` em 20/09 ~18:14 (1º verde na `beta-0.5.0`); cross no job qemu dedicado, guardas de toolchain/DB externo + guard de sysroot §255; `node` presente
kof build            → PASS (--target jvm|native|js|native.risc|native.arm) [--release]
kof run              → PASS (jvm|native|js|native.risc|native.arm) [--release]
kof serve            → PASS (web.app() nativo + API legada handle())
kof check            → PASS
kof test             → PASS (suíte estruturada `test "nome" { }` nos 3 targets)
kof bench            → PASS (harness: compile, run, validate, métricas, baseline)
kof debug            → PASS (DAP MVP no target JVM)
kof info             → PASS
kof lsp              → PASS (hover/completion/references/rename + diagnostics reais)
kof install          → PASS
kof c                → PASS (KofCcompiler nativo-only C subset → ELF x86_64 via kof_c)
kof script           → PASS (KofScript top-level `var`/`val` → KofScriptGlobals, repl, --watch)
tests/run-golden.sh  → 16/16 (8 casos × jvm+native)
tests/run-integration.sh → 9/9 (CLI + serve + kof test)
scripts/package.sh   → PASS (layout dist + tar.gz/zip + SHA256SUMS + jars)
```

---

## 02/09 — Revisão da filosofia idiomática

- **`Set<T>` como tipo declarado no JVM**: descriptor `kof.Set` → `java/util/HashSet`; parser de membro de classe com retorno genérico (`Set<Int> foo()`).
- **Null-safety narrowing no JVM corrigido**: `if (s != null) { s.length }` não emite mais `getfield "?".length`; `if (x != null)` não usa mais `if_icmp*` em referência; `String? s = null` parseia.
- **stdlib honesta**: `File.readText`/`readFile` → `String?`; `File.size()` lança em vez do sentinela `-1`; `Map.get` → `V?`; `readLine()` → `String?`.
- Ver `CHANGELOG.md` [0.2.6-beta] 02/09 e `docs/backend-parity.md`.

---

## Performance & Benchmarks (docs/architecture/performance.md)

- **Otimizador de IR** (`Optimizer.java`, sempre ativo): constant folding, branch simplification, dead stack effects, eliminação de código inalcançável (CFG, try/catch preservado), jump-to-next, identidades aritméticas; debug positions preservadas.
- **debug/release**: `kof build|run --release` remove metadata de debug (SourceFile/LineNumberTable no JVM, source map no JS).
- **`kof bench`**: `[paths...] [--target jvm|native|js] [--iterations N] [--quick] [--baseline <file>] [--update-baseline <file>] [--threshold <ratio>] [--json] [--fail-on-regression]` — compila → executa → valida stdout vs `expected.txt` → mediana + RSS → baseline → `PERFORMANCE REGRESSION`.
- **`benchmarks/`**: 37 benchmarks em 17 categorias; baselines `benchmarks/baselines/<target>-<version>.json` (33 jvm, 29 native, 32 js).
- **CI**: `.github/workflows/benchmark.yml` (`--fail-on-regression --threshold 1.20`); `scripts/run-benchmarks.sh`.
- Regra de features novas: performance.md §40-§41 (DoD inclui benchmark, stress, memory, resource, debug metadata).

### Correções de backend descobertas pelos benchmarks e E2E

| Bug | Correção |
|-----|----------|
| Chamada via interface com retorno primitivo gerava descritor `Object` (`iadd` inválido) | `analyzeInterface` define os symbols em `members()` |
| `l.get/remove/size/contains` como statement não emitia `KofPop` (Frame.merge crash) | `hasReturnValue` cobre métodos de List que deixam valor |
| `if (long>long)` / `float` / `double` gerava `IF_ICMP` (underflow) | `KofConditionalJump.operandType` → `LCMP`/`FCMPL`/`DCMPL`; `emitComparisonShortcut` faz widening |
| JS: call com efeito descartado em statement Pop não executava | handler de `KofPop` preserva `JsCall`/`JsSequence` |
| `Box<Int>`/`Box<T>` `b.get()` imprimia `T` no Native → segfault | `ExpressionTyper.inferExprType` (`ExpressionTyper.java:14`) substitui `T` via `CompilerTypes.substituteTypeVariableIn` (`CompilerTypes.java:423`) |
| `record Ponto` `hashCode()` falso-positivo `SEM025` | `MemberResolver.java:65` ignora `isObjectMethod(hashCode/equals/toString)` |
| **Regressão `dc849f6`**: `kof_list_add` sem `POP` → 15 frame crashes | POP restaurado + `hasReturnValue` blinda `add/push/append/set/clear/put` (`c7b23a1`…`7c6aca9`) |
| Surefire nunca rodava `NativeDebugTest2/3/4/5` | `<includes>*Test*.java</includes>` no surefire do kof-compiler |
| `spawn { lambda c/ captura }` → `VerifyError`/valor errado | `SpawnStmt` coleta via `collectCaptures` (`SpawnE2ETest.spawnLambdaCapturesOuterLocal`) |
| `&&`/`||` no JS emitia bitwise (avaliava os dois lados) | `&&`/`||` booleanos → `&&`/`||` JS (`KofJsE2ETest.logicalAndOrShortCircuit`) |
| `Channel<T>` como parâmetro de função → codegen inválido | `Type.of`/`toType` tratam `Channel` como builtin; `JvmTypeMapper` → `LinkedBlockingQueue` (`KofConcurrency2Test.channelAsFunctionParameter{Jvm,Native,Js}`) |
| `println` antes de `spawn` → SIGSEGV (`pthread_create` desalinhado) | `andq $-16, %rsp` antes do C call em `kof_spawn_handle_new` (`SpawnE2ETest.nativePrintBeforeSpawnDoesNotSegfault`) |
| AES-GCM no JS ignorava tamper no ciphertext (`SECN002`) | `kofSecB64Decode(s, strict)`; `decryptAesGcm` passa `strict=true` (`KofSecurityTest.aesGcmJsRoundTrip`) |
| `OBS002: histogram/metrics no Native` (asm: sem `jmp`, cmp invertido, clobber `%rsi`, desalinhamento) | store 32B + `kof_string_concat`; scratch em `%r10` (`KofObservabilityTest.observabilityNative`) |
| `transaction {}` no Native link error + rollback não desfazia | `kf_db_transaction` asm: `%rdi`=this, recarrega handle do BSS, re-throw; `.asciz` (`KofDbE2ETest.nativeTransaction{Commits,RollsBackOnFailure}`) |
| `MQ001: kof.mq no Native` | asm pub/sub + filas in-process; comparação por identidade no `unsubscribe` (`KofMqE2ETest` 4/4) |
| `Set<T>`/`Map<K,V>` como campo/retorno de classe → `NoClassDefFoundError: kof/Set` | `JvmTypeMapper` mapeia `Set`→`HashSet`, `Map`→`HashMap`; ramo genérico no `ClassMemberParser` (`KofMapSetTest.setMapAsFieldAndReturn`) |

---

## Segurança (kof.security, docs/stdlib/security.md)

- **`kof.security` v1**: `passwords`, `crypto`, `jwt`, `secrets`, `security`, `auth` — secure by default, gaps de target com diagnóstico claro em compile-time (SECN001/002/003).
- **JVM**: PBKDF2-HMAC-SHA256 (600k), SHA-256/512, HMAC, AES-GCM, SecureRandom, JWT HS256, env secrets, constant-time, redaction, web `auth.*`.
- **Native**: SHA-256/512 + HMAC em assembly puro (FIPS 180-4/RFC 2104, idêntico ao JVM), PBKDF2, AES-GCM, JWT HS256, `getrandom`, `/proc/self/environ`, constant-time.
- **JS**: SHA-256/512 + HMAC em JS puro, PBKDF2 delegado ao runner da plataforma, JWT, AES-GCM (SECN002), constant-time.
- **Testes**: `KofSecurityTest` 27 (3 targets + adversariais: tamper, expiração, confusão de algoritmo, malformado, chave errada, issuer/audience). **Benchmarks**: `benchmarks/security/`. **Docs**: `docs/stdlib/security.md`, `learn/36-security.md`, `training/language/security.md`.

---

## Database + ORM (kof.db / kof.orm)

### kof.db — persistência como parte da linguagem

```kof
main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    db.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR)")
    var rows = db.query("SELECT * FROM users WHERE id = ?", 1)
    transaction {
        db.execute("INSERT INTO users VALUES (1, 'Mel')")
        db.execute("UPDATE users SET name = 'Melissa' WHERE id = 1")
    }
    db.close(db)
}
```

- **JVM**: JDBC idiomático (`db.connect/execute/query`, `query<T>` tipado, credenciais opcionais, `transaction {}` commit/rollback real).
- **Native**: SQLite via link direto da `.so` (sem JDBC) — E2E real; `transaction {}` real (01/09, `kof_db_transaction` asm, EH `kof_exc_chain`/`kof_throw_string`); MySQL/MariaDB wire protocol (handshake + scramble SHA-1 + auth-switch + COM_QUERY + resultset + binds `?`, 31/08) + **prepared statements binários COM_STMT_PREPARE/EXECUTE** (03/09) + **SQLite riscv64/aarch64 fechado 15/09** (link-by-use `libsqlite3`, `RtB46/RtB47`; residual: transações concorrentes compartilham o slot global `.Ldb_tx_handle`).
- **JS** (16/09, DB001 fechado): `connect/connect2/close/execute/query/transaction` delegam a `kof_platform.db*` no host GraalJS (`KofJsDbBridge`, mesma JVM/classpath); **`db.query<T>` tipado = DB002 fechado 18/09** (bind `__kof_decode_<T>`).
- DSNs: `jdbc:*` (JVM), `sqlite:` (JVM/Native), `mongodb://` (ORM).

### kof.orm — o ORM da própria linguagem

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}

main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    orm.create<User>(db)                                  // DDL do schema
    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))       // insert/update
    var u = orm.find<User>(db, 1)                         // PK
    var adultos = orm.where<User>(db, "age", 30)          // query por campo
    var veteranos = orm.where<User>(db, "age", ">", 30)   // operadores: > < >= <= != LIKE
    orm.saveAll<User>(db, l)                              // batch (upsert por PK)
    var pg = orm.page<User>(db, 20, 40)                   // paginação
    println(orm.count<User>(db))
    orm.delete<User>(db, 1)
    orm.migrate(db, "add-phone", "ALTER TABLE user ADD phone VARCHAR")
}
```

- Schema declarado na linguagem (`entity`) — o compilador conhece campos/tipos/constraints em compile-time (nunca reflection); `generated`, `unique`, PK não-numérica.
- Backends SQL: H2/SQLite/MySQL/MariaDB/PostgreSQL via JDBC (JVM). CRUD completo + `saveAll`, `where` com operadores, `count` com filtro, `page`, `deleteAll`.
- **Tipagem de coluna (P3-10)**: coluna literal não-campo → `ORM003` em compile-time (JVM); coluna dinâmica liberada.
- **MongoDB**: `save/find/all/where/delete/count` sobre o driver oficial (E2E com skip condicional, serviço Mongo no CI). Migrations versionadas (`kof_migrations`).
- Native FECHADO 24/09 (cross riscv/aarch sobre SQLite + MySQL wire, D-DB-GAPS S5.5 `RtB76`–`RtB81`; x86-64 desde 22/09); JS FECHADO 18/09 (`KofJsOrmBridge`, E2E byte-paridade).
- Testes: `KofDbE2ETest`, `KofOrmE2ETest` (31, skips condicionais). Docs: `docs/stdlib/DATABASE_VISION.md` (níveis 0-4).

---

## Infraestrutura de distribuição

- `VERSION` fonte única; `<revision>` no Maven; `KofVersion` + `version.properties`; `scripts/bump-version.sh`.
- CLI (26 commands): `build, run, serve, check, test, script, repl, c, fmt, config gen, bench, profile, inspect, decompile, translate, compare, migrate, debug, info, lsp, install, deps, editor, new, init, version`.
- `kof lsp` via stdio (diagnostics do frontend real, hover, completion, references + rename; `LspServerTest` 4/4).
- `bin/kof` / `bin/kof.bat` com JDK embutido (Temurin 25; Kof emite bytecode V21, piso JVM 21+). `scripts/package.sh` (layout dist, `--jdk`, SHA256SUMS).
- CI: `ci.yml` (PR — testes/golden/integração/multiplatform), `release.yml` (main → bump → package 3 plataformas → changelog → GitHub Release).
- Editor: `editor/kof.tmLanguage.json` (TextMate).

---

## Targets

| Target | Backend | Execução | Status |
|--------|---------|----------|--------|
| `jvm` | `JvmBackend` (ASM) | bytecode V21, exception table, virtual threads | estável |
| `native` | `NativeBackend` (x86_64) | ELF x86_64, syscalls, free-list alloc + GC mark-sweep (auto-coleta ✅ 19/09, §260 FECHADO) | estável |
| `native.risc` | `NativeBackend` (riscv64) | ELF riscv64 via `riscv64-linux-gnu-as/ld` + qemu (26/26) | estável (core) |
| `native.arm` | `NativeBackend` (aarch64) | ELF aarch64 via `aarch64-linux-gnu-as/ld` + qemu (26/26 via tradução) | estável (core) |
| `js` | `JsBackend` + `KofJsRunner` | ES Modules via GraalJS, `kof.http` via `Java HttpClient` interop | alpha |
| `kofc` | `KofCcompiler` | C subset (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`) → nativo x86_64 | nativo-only |

---

## Estado da Linguagem

### Sintaxe de funções (sem `fun`)

```kof
main() { ... }                       // entry point, void implícito
String saudacao() { ... }            // retorno antes do nome
despedida(): String { ... }          // retorno após os parâmetros
void fazIsso() { ... }               // void explícito
Bool positivo(Int x) = x > 0         // expression body
```

### Features implementadas

| Feature | JVM | Native | KofJS |
|---------|-----|--------|-------|
| println/print; variáveis, aritmética, bitwise, hex | ✅ | ✅ | ✅ |
| if/else, if-expr; while/for/do-while/for-in, break/continue; switch | ✅ | ✅ | ✅ |
| funções (todas as formas); classes/campos/métodos; `constructor(...)` + primary class | ✅ | ✅ | ✅ |
| records (toString/equals/hashCode) | ✅ | ✅ | ✅ |
| herança/`super`/override/virtual dispatch | ✅ | ✅* | ✅ | Native: `super.metodo()` = SUP001 |
| interfaces; generics por erasure; lambdas + capturas | ✅ | ✅ | ✅ |
| exceptions reais (try/catch/finally + unwinding); `assert` | ✅ | ✅ | ✅ |
| `spawn` (join implícito) | ✅ | ✅ (pthread) | ✅ |
| strings (concat `+`, `==`, indexOf, trim, split...); arrays | ✅ | ✅ | ✅ |
| `List<T>`, `listOf`, `map/filter/reduce` | ✅ | ✅ | ✅ |
| `Box<T>` primitivo/boxed `T` | ✅ | ✅ | ✅ |
| JSON encode/decode + arrays nativos; `List<User>` aninhado | ✅ | ✅/— | ✅ |
| kof.io (File/Path/Directory, readFile, writeFile) | ✅ | ✅ | ✅ |
| kof.time (now/sleep/interval) | ✅ | ✅ | ✅ |
| kof.web (`web.app()`, rotas, middleware, WebSocket/SSE, configure/stats) | ✅ | — | — |
| kof.http (+timeout/retry/circuit) | ✅ | ✅ HTTP002 | ✅ |
| kof.config; kof.mq; kof.log; kof.security | ✅ | ✅ | ✅ |
| kof.db (JDBC, query<T>, transaction) + SQLite | ✅ | ✅ (cross ✅ 15/09) | ✅ (DB001/DB002) |
| kof.orm (entity, CRUD, where, migrate, MongoDB) | ✅ | ORM001 | ✅ 18/09 |
| String.toInt/toLong/toDouble/toFloat; kof.ui; default params; `readLine()` | ✅ | ✅ | ✅ |
| `KofCcompiler` C subset → native; `KofScript` top-level `var`/`val` | ✅ | ✅ | ✅ |

### Concorrência (`spawn`)

- JVM: virtual threads (join implícito). Native: pthread_create + trampoline + `await`/pthread_join + allocator futex + `done`/`poll`/`cancel`/`cancelled`/`selectAny` (CONC001 fechado 31/08). JS: `async`/`await`/`Promise` reais (CONC003 fechado 03/09, `KofJsRunner` drena `kofActiveTasks`).
- Zero API de plataforma exposta. **Modelo de memória (SG-020)**: `docs/language-reference/concurrency-memory-model.md` — SC em todos os targets, 6 bordas HB; provas `staticsAreSequentiallyConsistent`/`noWordTearingOnLong` em `KofConcurrency2Test`.
- Ver `docs/language-reference/concurrency.md`.

### HTTP (`kof serve`)

API legada top-level `handle(method,path,body,query,headers): String` (null → 404) segue funcionando. Stack web nativa (Fase 1, sem Spring):

```kof
main() {
    var app = web.app()
    app.use { if (header("x-auth") == "secret") { return null } return "{\"error\":\"unauthorized\"}" }
    app.get("/hello") { return "Hello from Kof" }
    app.get("/users/:id") { return "user " + param("id") + " q=" + query("name") }
    app.post("/user") { return json.encode(json.decode<User>(body())) }
    app.listen(8080)
}
```

- Rotas com lambda trailing; path params, query, headers, body, `method()`/`path()`; middleware `app.use`; engine HTTP gerado no runtime do programa (cada conexão uma virtual thread); `configure`/`stats` (JVM). `kof serve <file.kf>` detecta `main()`.
- Ver `docs/stdlib/stdlib-web.md`, `KofWebE2ETest`.

### Media (`kof.media`) — arquivos, não strings

A linguagem NÃO transporta imagem/áudio como `String` gigante (sem base64 literal/data-URI). O app trata o ARQUIVO:

```kof
main() {
    var app = web.app()
    app.serveDir("/img", "assets")                 // bytes do disco, image/png
    app.get("/thumb") { var img = Image.open("assets/logo.png"); img.saveAs("assets/thumb.jpg", "jpeg"); return "w=" + img.width() }
    app.get("/rec") { var m = Mic.record(2); m.saveWav("assets/gravacao.wav"); return "ms=" + m.durationMs() }
    app.get("/clip") { var v = Video.open("assets/clip.mp4"); return "ms=" + v.durationMs() + " " + v.format() }
    app.listen(8080)
}
```

- **`Image`**: `open` (PNG/JPEG/GIF/BMP), `width/height/format`, `save`/`saveAs`, `bytes`/`bytesAs`, `dataUri` (runtime), `close`. **`Audio`**: `openWav`/`saveWav` (WAV RIFF PCM 16-bit), `sampleRate`, `durationMs`, `pcmBytes`. **`Mic`**: `record(seconds)`, `list()`. **`Video`**: `open` + metadados do container (MP4/MOV `mvhd`), sem decodificar frames (gap honesto).
- **`app.serveDir(prefix, dir)`**: serving de ARQUIVO binário, content-type por extensão, `Cache-Control`, proteção de path-traversal + **Range requests** (206/416) p/ `<video>`. Caminhos relativos contra `-Dkof.root`.
- **Targets**: JVM. Gaps honestos: frames de vídeo, câmera (MEDIA002), mic (MEDIA003), paridade Native/JS (MEDIA001).
- Ver `KofMediaE2ETest` (16).

### Configuração nativa (`kof.config`)

Precedência `KOF_CONFIG` > env `KOF_<KEY>` > profile `kof.<KOF_PROFILE>.config` > `kof.config`; tipagem em compile-time, default em ausente/inválido. Native: implementação asm própria (`NativeConfigE2ETest` 8, CONF001 fechado). Prova: `KofConfigE2ETest` 11/11.

### Logging nativo (`kof.log`)

Formato `timestamp LEVEL mensagem`; info/debug → stdout, warn/error → stderr; `KOF_LOG_LEVEL` (debug<info<warn<error<off). Funciona em handlers web. Native: asm próprio (UTC; `KOF_LOG_JSON` no-op); JS `console.*` (LOG001). Docs `docs/stdlib/stdlib-logging.md` (`KofLogE2ETest`, `NativeLogE2ETest` 7).

### Testes da linguagem (G6 — suíte estruturada)

`test "nome" { }` desugar para `kof_test_N` (runner sintetizado, zero reflection); `kof test <file.kf|dir> [--target ...]` reporta `PASS`/`FAIL` + exit code; cada teste isolado. `process.exit(code)` primitivo nos 3 targets. G7: `jwt.*` Native `SECN004` em compile-time. Ver `learn/23-testing.md`, `StructuredTestE2ETest`.

---

## Testes (3225 = 2762 kof-compiler + 50 kof-script + 7 kof-c-compiler + 406 kof-cli — suíte completa verde, 0F/0E, 221 skip; medido 20/09 ~18:14 pelo job CI Build+Tests do tip `404d8be6`; flake §252 nativo calado de novo; host sem qemu: cross → skip honesto)

| Suíte | Quantidade | Cobertura |
|-------|-----------|-----------|
| CompilerDriverTest | 252 | compilação, semântica, fases, isolamento |
| NativeE2ETest | 65 | execução real de binários nativos |
| KofJsE2ETest | 40 | JS real (GraalJS) + `&&`/`||` short-circuit vs bitwise |
| JvmE2ETest | 31 | execução real de bytecode JVM |
| KofSecurityTest | 28 | kof.security + adversariais |
| OptimizerTest | 22 | passes de otimização da IR |
| KofOrmE2ETest | 32 | kof.orm: entity/CRUD/where (+ORM003), Query DSL, migrate, unique, MongoDB |
| KofConcurrency2Test | 33 | spawn, selectAny, cancel, done/poll, awaitTimeout, channel |
| IoE2ETest | 16 | kof.io multiplatform |
| ComponentCoreE2ETest | 14 | kof.ui Component: view/onMount/onDispose |
| CoreRegressionE2ETest | 50 | regressões de uso real (BOM, toInt, ARITH001...) |
| JsonE2ETest | 15 | JSON JVM + Native |
| UiE2ETest | 29 | kof.ui widgets, estilo, bindings, Table/Ul/Ol/Form (link JVM+Native) |
| AndroidInteropE2ETest | 12 | android: interop Java (external classpath) |
| KofConfigE2ETest | 11 | kof.config env/arquivo/profiles/precedência, CONF001 |
| KofWebWsE2ETest | 11 | WebSocket RFC 6455 handshake/frame/lifecycle |
| StructuredTestE2ETest | 11 | `test "nome"` nos 3 targets + process.exit |
| BackendParityTest | 16 | paridade JVM/Native/JS |
| KofLogE2ETest | 11 | kof.log JVM: níveis, stderr, off, JSON, correlation |
| KofPatternMatchingTest | 12 | switch `case String s` / `Point(x,y)` 3 targets |
| KofWebE2ETest | 12 | stack web nativa (rotas, JSON, middleware, `app.health` bypass) |
| ExceptionsE2ETest | 9 | try/catch/finally JVM + Native |
| KofDbE2ETest | 24 | kof.db JDBC/query<T>/transaction/rollback/SQLite nativo, DB001/DB002 JS, SQLite cross riscv+aarch |
| KofHttpServerTest | 8 | serve engine (sockets reais) |
| KofMediaE2ETest | 16 | kof.media + serveDir, Range 206/416, conteúdo binário |
| NativeConfigE2ETest | 8 | kof.config Native asm |
| SpawnE2ETest | 10 | spawn + join implícito + capturas + println-antes-de-spawn + spawn→await→spawn |
| IdiomaticE2ETest | 7 | idiomas consolidados (chaining, primary ctor) |
| JsonCompleteE2ETest | 7 | JSON Float/Double + arrays decode (JVM) |
| KofAwaitTest | 8 | spawn/await Handle<T> tipado (JVM) |
| KofWebSseE2ETest | 7 | SSE: sse.send/event/close |
| KofWsFrameTest | 7 | frame codec RFC 6455 |
| NativeLogE2ETest | 7 | kof.log Native asm |
| IdiomaticCoreE2ETest | 6 | field initializers, `\u810810`, listOf<T>() |
| PackagesE2ETest | 12 | pacotes/módulos multi-arquivo (import a.b.C, moduleRoot do LCA) |
| AssertE2ETest | 5 | assert JVM + Native |
| FloatingPointGapE2ETest | 5 | FP XMM encode/decode/arrays (FLT001) |
| KofCacheE2ETest | 5 | cache E2E |
| KofHigherOrderTest | 5 | map/filter/reduce |
| KofIntOverflowNativeTest | 5 | Int 32 bits no Native |
| KofTimeE2ETest | 12 | time now/sleep/interval (JVM/Native/JS — TIME001) |
| KofWebTlsTest | 5 | TLS/HTTPS listenSecure |
| KofObservabilityTest | 7 | health/metrics/histogram/requestId/traceId (W3C) |
| FunctionSyntaxTest | 12 | formas de declaração de função |
| KofEnumSwitchTest / KofEnumTest | 4 / 4 | switch exaustivo SEM031 / values/valueOf SEM030 |
| KofHttpE2ETest | 8 | kof.http client (sockets reais) |
| KofMqE2ETest | 5 | kof.mq (MQ001) |
| KofWebStreamE2ETest | 4 | WebSocket/SSE end-to-end |
| LambdaE2ETest | 17 | lambdas + if-expr |
| RouterE2ETest | 4 | kof.ui Router Fase 7 |
| StdlibE2ETest | 4 | now/readFile/writeFile |
| KofJsBrowserE2ETest | 22 | KofJS no Chrome headless + DOM (pula se Chrome ausente) |
| KofJsSourceMapTest | 1 | source map V3 do KofJS (VLQ real) |
| ConfigGenTest | 3 | kof config gen |
| KofHttpResilienceE2ETest | 3 | kof.http timeout/retry/circuit |
| KofMapSetTest | 11 | Map/Set 3 targets + campo/retorno de classe (02/09) |
| KofSecurityG9Test | 3 | web security rateLimit/session/apiKey |
| KofValidationTest | 34 | 13 predicados de validação (3 targets) |
| TetrisEasterEggTest | 3 | registro easter egg |
| TuringCompleteE2ETest | 3 | completude de Turing |
| WindowE2ETest | 3 | Window size, close-to-exit |
| DebugInfoE2ETest | 2 | SourceFile + LineNumberTable (JVM) |
| IRStatisticsTest | 2 | observer de IR + estatísticas |
| NativeDebugTest | 1 | harnesses de debug nativo |
| NativeDebugTest2/3/4/5 | 1 cada | harnesses de debug nativo |
| NativeDwarfLineInfoTest | 1 | DWARF nativo `.debug_line` |
| NullSafetyE2ETest | 7 | `String?` narrowing JVM + readLine EOF null |
| NativeRiscv64E2ETest | 42 | riscv64 real (qemu), runtime asm puro, core + stdlib |
| NativeAarch64E2ETest | 42 | aarch64 real (qemu), asm puro via tradução riscv→aarch64 |
| **Total kof-compiler** | **823** | |
| kof-script | 8 | KofScriptGlobals / repl / --watch |
| kof-c-compiler | 5 | KofC C subset → ELF |
| kof-cli | 4 | LSP references + rename (mock) |
| **Total** | **840** (+31 skips condicionais: Mongo/MySQL/Postgres, windows/mac) | |

---

## Consolidação idiomática (guidelines 0.0.5)

Princípio: `intenção → Kof → compiler → backend` — nunca detalhes da plataforma vazando para a linguagem.

| Guideline | Estado |
|-----------|--------|
| `User(...)` sem `new`; primary constructor; `this` não obrigatório; field initializers no ctor | ✅ |
| Resolução de métodos independente da ordem textual | ✅ |
| Escapes `\n` `\t` `\r` `\u810810`; `listOf<T>()` vazio; `List<User>` + for-in tipado | ✅ |
| `++`/`--` em campos; `return` nu em void; lambdas com capturas | ✅ |
| args CLI (`main(args)`); default parameters; módulos multi-arquivo | ✅ |
| `Process` API (`kof.process` + `kof_process_run`) | ✅ |
| `kof.shell` (MVP `34e4344f`) | ✅ (`cmd`/`run`/`ok` JVM+JS; `pipeline` JVM real, JS/Native `PROC001` honesto) |

---

## Kof Debugger (em progresso)

Princípio: o programador depura **código Kof**, nunca o artefato do backend.

| Fase | Estado |
|------|--------|
| 1 — DebugInfo na IR (source location por op) | ✅ |
| 2 — JVM: SourceFile + LineNumberTable + LocalVariableTable | ✅ |
| 3 — `kof-debug` MVP (DAP stdio + JDWP cru): launch, breakpoints por linha Kof, stopped, stack trace, continue, disconnect | ✅ |
| 4 — Kof Editor (breakpoints, toolbar, variables) | planejado |
| 5 — Native (DWARF) | ✅ parcial 02/09 (`.debug_line` real, `NativeDwarfLineInfoTest`; locais/expressões pendentes) |
| 6 — JS (source maps) | ✅ parcial 01/09 (V3 nível de linha, `KofJsSourceMapTest`; colunas/expressões pendentes) |
| 7 — Avançado: locals por frame, stepping, exception breakpoints, avaliação | planejado |

`kof debug app.kf` abre uma sessão DAP funcional no JVM (compila com metadata de debug, lança com JDWP). Docs: `debugger-architecture.md`, `debug-adapter.md`, `debugging/debugging-{jvm,native,js}.md`.

---

## Bugs Restantes (reais)

> **Lista completa com reprodução + correção sugerida: `docs/bugs-and-gaps/known-bugs.md`.**

Fechados (✅): #1 GC automático no Native (sweep 03/09; auto-collect 19/09, §260); #2 `spawn` no Native (CONC001 31/08) e o SIGSEGV `spawn→await→spawn` (alinhamento de stack no `pthread_create`, 01/09); #3 JSN002; #4/#5 JSN001/JSN003; #6 lambdas sem captura; #7 `Box<T>` println nativo; #8 falso-positivo `SEM025`; #9 `await`/join; #10 `kof fmt`; #11 Map/Set; #12 pattern matching; #13 null safety; #14 imports multi-arquivo; #15 `List.get` nativo; #16 status/headers web; #17 kof.web nativo (WEB002, 03/09); #18 MySQL/MariaDB wire + prepared statements binários; #19 `kof_sec_secret_get`; #20 FP no Native (FLT001); #22 core riscv64/aarch64 (NATIVE002 fechado 19/09); #23 `kof.cache` nativo; #24 `spawn`-statement não juntado.

Abertos:
- **#25 `throw <não-String>` / `catch <não-String>`** gera bytecode JVM inválido (`ClassFormatError`, disfarçado de "JavaFX launcher error"). Exceções são Strings; o compilador deve rejeitar em compile-time. Detalhe `known-bugs.md` #1.
- **#26 Captura mutável no Native** — ler variável boxeada DENTRO da lambda após mutação EXTERNA produz lixo (`offset = 20; f(5)`); JVM correto. Detalhe `known-bugs.md` #2.

---

## Próximos Passos (ordem P1→P5)

**P1 — Linguagem:** ✅ `Map/Set`+`enum`+`await`+`List.map/filter/reduce`; ✅ pattern matching (3 targets, `KofPatternMatchingTest` 10/10); ✅ nullability básica (27/08); ✅ módulos multi-arquivo (`moduleRoot` do LCA, `PackagesE2ETest` 6/6).

**P2 — Web:** ✅ resposta rica `status(201,body)`/`headerSet` (JVM `KofWebE2ETest` 9/9; Native parcial `WEB001`; JS ✅ 03/09); ✅ `kof.cache`; ✅ WebSocket + SSE; ✅ scheduler `every`/`cancel` (`at(cron)` JVM/JS real, Native `CRON001` §274); ✅ `kof.http` timeout/retry/circuit (falta HTTP/2).

**P3 — Dados:** ✅ Query DSL tipada nível 3 (`User.query(db){ where; orderBy; limit }`, ORM003/ORM004, `KofOrmE2ETest` 22); pooling + `kof.db`/`kof.orm` fora do JVM; ✅ MySQL/MariaDB nativo.

**P4 — Observabilidade:** ✅ `histogram` + `/metrics` (OBS002 fechado); ✅ `app.health` + tracing W3C (`traceId`/`spanId`/`spanStart`/`spanEnd`, lifecycle `application`); OpenTelemetry pendente.

**P5 — DX:** ✅ `kof fmt`/`kof init`/REPL; ✅ LSP hover/completion/references/rename + source maps JS V3 (Native DWARF + VS Code pendentes).

---

## Roadmap — Estado por Fase (31/08)

### Concluído — Disponível

- Compiler foundation (Lexer/Parser/AST/Type system/Semantic/Kof IR); backends JVM, Native x86_64, JS (GraalJS); classes, records, herança, interfaces, exceptions, generics, coleções, strings, control flow.
- `kof build/run/serve/test/debug/bench/fmt`; `kof.web` (rotas/middleware, WebSocket RFC 6455, SSE, TLS, `configure`/`stats`); `kof.db` (JDBC + SQLite nativo); `kof.orm` (entity/CRUD/migrate/MongoDB); `kof.log` nativo; `kof.config` (3 targets); `kof.mq`.
- Cliente HTTP JVM + JS + Native (03/09) + retry/circuit; `kof.security` v1 + web security G9; `kof.validation`; `kof.observability`; `kof.ui`.
- `kof.process` (`process.spawn` só JVM, `PROC001` Native/JS, 18/09).
- **Concorrência**: `spawn`/`await` JVM + Native (CONC001) + Android (AND001) + JS event-loop (CONC003); `done`/`poll`; `cancel`/`cancelled` cooperativo; `selectAny`; `awaitTimeout`; `channel<T>()`; `scheduler.every/cancel`; `at(cron)` (Native `CRON001`).
- **`kof.media`** (arquivos, não base64) + `serveDir` Range 206/416; **KofAndroid Fase 2** (`--apk` standalone, release signing, label/permissões; CI-proven 18/09 §299/X9).
- enum 3 targets + switch exaustivo (SEM031); Map/Set (COL001); otimizador de IR; pattern matching; null safety; higher-order; módulos multi-arquivo.
- KofScript; KofC; LSP + diagnostics reais; GC do Native mark-sweep + auto-collect (§260); FP no Native (FLT001); JSON objetos/records + arrays FP (JSN001/002/003).
- releases multiplataforma (2 jobs: `test-and-bump` → `package-and-release`).

### Em desenvolvimento

- Standard Library (contratos em estabilização).
- Async/Concurrency: JS async (CONC003 ✅), Android (AND001 ✅), `spawn→await→spawn` (✅).
- KofAndroid Fase 2 ✅; residual do `kof.media` ✅ (video + Range fechados; câmera MEDIA002, paridade Native/JS MEDIA001 seguem como rótulos).
- MySQL/MariaDB nativo ✅ (wire + prepared binário). `native.risc`/`native.arm` core completo (NATIVE002 fechado 19/09, registro `docs/native-multiarch.md`).
- Debugger — MVP JVM + source maps JS; Native DWARF pendente.
- KofJS plataforma web no browser (GraalJS alpha).
- Gerenciador de pacotes: `kof deps` transitivo ✅ 16/09 (`kofdeps.lock` + delegação Maven, R9); registry MVP ✅ 19/09 (D2-A: `kof deploy --publish` + pull, `DepsRegistryTest` 6/6).

### Planejado

- especificação completa da linguagem; suíte de conformidade.
- full web platform (frontend declarativo + routing/forms/SSR).
- **gRPC no `kof.web`** (`app.grpc`, codegen `.proto` → IR, parity JVM primeiro; `docs/development/roadmap.md` § web).
- auto-hospedagem (compilador escrito em Kof).

Roadmap completo: `docs/development/roadmap.md`; execução: `docs/development/roadmap.md` §23 (ex-plan-platform-completion).
