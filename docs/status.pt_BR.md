[English](status.md) | [Português](status.pt_BR.md)

# Status do Projeto Kof

last: native-record-equality
doing: 0.5.0-release-prep
next: post-0-5-0-kof-libraries
location: status
state: active
constraint: pr619-maintainer-only
decision: D-KOF-FIRST-IMPL

**Última atualização:** 24 de setembro de 2026
**Versão:** 0.5.0-beta (pom `revision`)

**24/09 — CONSOLIDACAO DA JANELA 21/09→24/09 (conferida contra `git log`/tips; suíte completa do reator 3818 run / 0F / 0E em 24/09; a PR de release `beta-0.5.0 → main` **#619** está ABERTA e o merge é da MANTENEDORA — nova regra 10 do AGENTS.md: nenhum agente mergear/aprova/fecha sob hipótese alguma).**
>
> - **Igualdade de record no Native está COMPLETA (família §104b/§114 fechada)** — 24/09, lane compiler/nat 9092 + lane issues: `List.contains`/`Set.contains`/dedup do `set.add` comparam record por CONTEÚDO (tag 2 → `kof_obj_equals` + `kof_equals_table`, `4cce594e7`); chaves de `Map` por conteúdo (`f31ac11f4`, `mapKeyTag`); `containsValue(record)` por conteúdo — lado-valor tag 7 pousado da PR #616 do contribuidor **Publio Santos** (verificado RED 3/3 → GREEN 3/3 em x86-64+riscv64+aarch64, `b7c13ba1f`, **#615 fechada pelo caminho oficial de evidência**); `equals` sintetizado cobre campo record aninhado (`523dfabb5`); faces do `hashCode` String/Double/record-aninhado todas por conteúdo (`67acf5a77`/`e2f0629f6`/`2e90aa7c2`). Oráculo E2E `NativeRecordCollectionEqualityE2ETest` + `NativeRecordHashCodeE2ETest` byte-idêntico JVM≡Native em 3 arquiteturas.
> - **Paridade do harness de teste no Windows fechada (issues de Jonas Rocha, fixadas pela lane issues)**: **#612** — todo `-cp` de JVM filho nos testes usa `java.io.File.pathSeparator` (18 arquivos) + guard estático (`ClasspathSeparatorGuardTest`) que falha nomeando `arquivo:linha` na regressão (`cdbf45145`); **#603** — os 18 vermelhos de Windows do `KofOrmE2ETest` fixados: `kofPath()` põe barra normal em literal Kof (o `\` cru colapsava como escape pela regra lexical documentada — a linguagem estava certa, o harness não), `/tmp` → `tempDir`, stderr do filho agora DENTRO da mensagem de falha, Postgres assume por credencial real (`970c847a`); **#617** — `File.mkdir()` deixa de ser no-op silencioso + `ClassFormatError`: membro desconhecido em builtin do kof.io é **SEM102** limpo no compile com o hint `Directory.createDirectories()` (`aed5fe7b4`); **#618** triada NÃO-bug (o argumento arquivo do `kof run` tem o diretório como módulo, por design; documentado EN+PT no `learn/32-cli-tooling`). **Zero issues abertas.**
> - **Endurecimento Native**: §485 — o `receive` do canal drenava a fila sem zerar `tail` → SIGSEGV NULL determinístico em x86_64+riscv64+aarch64, repro single-threaded (`7f5a2e054`); §486 fechada (bridges de retorno covariante, duas faces); **B-3b baremetal: o payload Kof REAL RODA pelo caminho BIOS sob SeaBIOS** (`KO-BIOS PAYLOAD`, `e3644d596` — 5 bugs achados+corrigidos na medição, incl. alocador de arena BIOS no `RuntimeMemory`).
> - **Ciclo de vida do tech-debt aberto e fechado em 48h (da mantenedora)**: o ledger `tech-debt.md` aberto 23/09 com 6 §NNN vivos — **todos re-medidos ✅ até 24/09** (§205 print boxed, §248 default methods em 4 alvos, §271 bridges de interface genérica, §278 face `kof.security` Android via JCA + stub do `kof.gpu`, §283 exit do scheduler aarch64, §423 canais portados pro cross) e `check_500` verde → **a mantenedora matou o ledger, a ferramenta `debt-scout` (38 arquivos: contrato, detectores, 13 testes, workflow agendado) e a pasta `technical-debt/`** (`f4a987166`). DECISIONS mantêm a história anotada KILLED.
> - **Organização de docs (regra dos três estados por ordem da mantenedora)**: os 2 PROPOSALs RATIFICADOS saíram de `docs/development/` — `PROPOSAL-1.0-EXIT-GATE` → `docs/` (o contrato normativo do 1.0) e `PROPOSAL-VERSIONING-RELEASE` → `docs/distribution/` ao lado do `VERSIONING.md` operante (materializado pela PR #582 em 22/09); X5/X6 variância marcados IMPLEMENTED (cond.2 do release gate GREEN); ALLOWLIST do `check_release_050_gate` enxugada → `loose_docs GREEN`.
> - **Fluxo de contribuidores (tudo fechado com prova, autores creditados)**: #608/#614/#609 (face JVM de record+interface genérica do Publio pousada à mão como `081202e0c` após conflitos de rebase, autoria preservada), #610/#611 (diamante de `default` conflitante → SEM101 + overload por aridade, `374b2b4bb`), #604/#605 (gate CodeQL conta só CodeQL), #613 (face nativa da família de bridges, caminho de verifier independente p/ risk HIGH).
> - **Prep da release (0.5.0)**: `release-beta-0.5.0-prep` acompanha o corte; bots de CI do tip (quality/security/warning) verdes; estado restante do gate por `scripts/check_release_050_gate.sh` (parity/bugs_gaps = ambiente/lane medida, decisions GREEN, edges GREEN).

>
> **20/09 — CONSOLIDACAO DA NOITE 19/09→20/09 (lane docs, conferida contra `git log`/tip `1080238f`; CI `Build+Tests` verde nos tips da noite, tally do reator completo ~3034 testes 0F/0E na corrida das 23:5x da `.18`).**
>
> - **D-TROOL FECHADA — `Bool` permanece de 2 valores; `Troolean` (`true/false/null`) e o tipo trivalente de Kleene da linguagem** (decisao da mantenedora 19/09, roadmap 2.6.5–2.6.7 ✅, DECISIONS §D-TROOL; landed `916b9fb7`/`d61836eb`/`5f0757e8`): `Bool?` escrito pelo usuario e rejeitado (SEM095) com ponteiro ao Troolean; `&&`/`||`/`!` desugaram em cadeias Kleene estritas sobre a maquina boxed (backends intocados); `if (t)` ≡ `if (t == true)`; lei travada em `TrooleanLawE2ETest` 13/13; corpus sincronizado (`training/language/types.md`, fake-idioms, CHANGELOG, backend-parity) — EN+PT.
> - **O rio da erasure JVM landou (familias §355/§356/§357, `16f16081`/`c8d55a10`/`0aa6a307`)** — 8 issues fechadas com prova (#399/#363/#375/#385/#366/#365/#295/#368): variaveis de tipo carregam o bound ate todos os emit sites, interfaces genericas baixam com descritores apagados e pontes covariantes varrem PAIS E interfaces (box/unbox de primitivo na fronteira), campo `T[]`/`as T[]` apagam com honestidade, e a decisao so-JVM `SEM098` nomeia o caso array-primitivo-em-slot-apagado. O §358 grava o gap NATIVO PRE-EXISTENTE (link do `toString` em `T` sem bound nos cross) para a lane nat — medido igual no tip limpo, nao e regressao.
> - **Manha 20/09 (medido pela lane docs no jar real): gate do §362 pousado** (`57a0d5f0 — chamadas de construtor fantasma agora SEM023/SEM014 no call-site, 7/7 + sonda 4-alvos verde) **e a varredura da fatia 2 achou o §371/#550** — pela CLI distribuidda o carregador de slices do runtime (leitura de fonte relativa ao CWD) nunca faz prune, forcando `usesDb`+link dinamico e quebrando `kof build --target native.risc|arm` com COMP001 ate em `hello.kf` fora da arvore do modulo; as suites E2E rodam com cwd=modulo, so o jar real expoe isso (classe P0, roteado ao cluster nat).
> - **Tarde 20/09 (lane docs, regra do jar limpo + E2E dos donos): §370 fechou o ciclo** — `35bfaec1` re-medido com a regra md5: coerção numérica de `extern` (Int→Float, Double→Float, Int→Double) byte a byte JVM≡Native, SEM014 nos não convertíveis; #549 encerrada com a prova da lane (5749129761).> - **§373/#443 pousou (`d969bc3a`)** — `List`/`Set`/`Map` bare em posição DECLARADA resolvem para a coleção builtin no ÚNICO ponto de convergência (`qualifyDeep` 2b, guarda de shadow do §243 preservada); descriptor fantasma `LList;` morto, `BareCollectionFieldE2ETest` 8/8 (RED 6/8) e o mesmo print nos 4 alvos. **X7-5 attach real no JVM+Native (`81401629`)** — JDWP cru reconstruído contra o wire do JDK 25.0.4.1 medido byte a byte (§376; oráculos: jdb da própria JDK + `JDWP.java` do src.zip), falso-verde consertado com E2E real (§377).> - **Colheita da tarde de bugs de processo:** §371/#550 (CLI distribuída quebra o build cross pelo CWD do loader), §372/#551 (regressão do gate do §368 sobre o rio da erasure, bissectada), #552 (`BuiltinCallTyper` 612≥600), caça Q4 do §373 → §374/#553 (boxing de argumento em coleção bare) e §378/#554 (gate EN×PT cego a FIXED unilíngue). Tudo roteado às lanes compiladora/nativa/tooling com o teste-prova definido nos watchers do DOING.
 A mesma varredura bissectou uma **regressao do gate do §368** (`5cd078c1`): SEM012 dispara antes do rio de erasure (§355-357) e mascara SEM098 — `GenericFieldArrayEraseE2ETest` 3F+1E + `MakealivePrimitivesE2ETest` 1F, todos verdes em `5cd078c1^` (§372/#551); e o gate CI ≤500 esta vermelho porque `BuiltinCallTyper.java` cruzou a 612 linhas sob fatia-1+§362 (#552).
> - **§360 CORRIGIDO — as ops do handle de `process.spawn` eram CODIGO MORTO no JVM** (`4ed9bb2f`): o dispatcher nunca roteava um receiver `Long` para a branch de handle, entao `readLine/write/exitCode/kill/alive` baixavam para `invokevirtual java/lang/Long.readLine` cru e morriam em EXECUCAO. Roteamento consertado; e a face JS landou (`081a48f8` + `2d20e5d4` `runWith(argv,cwd,env)` JVM+JS): spawn+handle+pipeline no JS via `KofJsProcessBridge` com paridade byte-a-byte — `ProcessSpawnE2ETest` 4/4, `ShellE2ETest` 15/15; linha 2.2 do tracker ✅ FECHADA (ultimo residuo era o pipeline JS).
> - **X7 (debug info) estado real medido: o DWARF x86 JA existia e agora os CROSS tambem** — tabelas de linha `.file/.loc` (`NativeDwarfCrossTest`, `5d9c855c`/`23b7ecf7` provaram o `.debug_line` no ELF riscv/aarch64 real) e DIEs de CU/subprogram com `frame_base` por ABI (`890b58bf`); source maps JS ✅; `kof profile` honesto em nivel de processo. As linhas X7/§19.5 do tracker foram corrigidas contra o codigo (estavam abertas item ja implementado).
> - **X10 FECHADA (`27826838`)** — catalogo de assinaturas do LSP em **32/32 namespaces** (entrada `json` na tabela com trava comportamental no SEM025 real do `MemberCallNamespaces`), hover + `signatureHelp` + `completionItem/resolve` de UMA fonte gerada (`scripts/gen_signatures.py`), 265 membros / 282 formas estaveis.
> - **Dois bugs NOVOS catalogados com matriz de 4 alvos (lane docs; os fixes pertencem ao cluster `.22`)**: **§361** — ESCRITA em campo nullable-primitivo grava o inteiro cru onde o slot boxado e obrigatorio (`b.n = 42` → JVM `VerifyError` na CARGA, Native SIGSEGV, Script/JS imprimem `42`; ponteiro de raiz no gate de box do `ExpressionFieldAssignLowerer` sem `isNullablePrimitive` — o gemeo nao espelhado do §295(b)); **§362 / issue #545** — chamada a construtor INEXISTENTE passa em compilacao (`P(1,2)`, `C(1)`, `C("s")` → JVM morre em EXECUCAO, Script imprime `0`, JS imprime `1`, Native falha no `ld` com simbolo de ctor indefinido; esperado: SEM em compile, a familia do precedente #469/#470). O fix do §361 pousou (`e293c4a5`, teste 9/9) mas a lane docs RE-MEDIU no tip reconstruido nos 4 alvos e ele e PARCIAL: a face de escrita `Char?` continua morrendo (JVM/Script `VerifyError` no `putfield`, erro de cast em execucao no Native na leitura; JS ok) e um §365 novo foi aberto (campo nullable nunca-escrito le `0` no JS vs `null` nos demais — pre-existente, nao veio do fix); roteado no #278 com as matrizes. Duas faces medidas na mesma passada: **§366** (Script perde o stdout de `process.run` em silencio — JVM `x`/Script vazio; **#547**) e **§367** (`println(result)` vaza `KofRuntime$ProcessResult@<hash>` — identidade Java na superficie Kof, regra 8; **#548** com o precedente `KofJsHostlessRuntimeTest`); `training/idioms/stdlib` corrigido para a história real do JS (`spawn`/`pipeline` REAIS sob o host Kof `KofJsRunner`, node puro = diagnostico honesto — `94eb4433`).
> - **Melhorias de disciplina do ledger (licoes multi-agente da noite)**: a regra §NNN agora exige pegar o numero do TIP REMOTO (colisao dupla §355 renumerado 2x → §359/§360 assentados com notas de procedencia); lixo de marcadores de stash commitados por uma lane foi cirurgicamente re-limpo (`029ac684`/`515414db`, tres lados preservados); o `check_known_bugs_status.sh` aprendeu o token 🔓 + caso no `--selftest`; `docs-lang` mantem 100%.
> - **Registro de decisoes cresceu (`7dfc6441`, DECISIONS EN+PT)**: `D-MAKEALIVE` (o compilador que compila a si mesmo), `D-KOF-AS-CLOUD` (postura da plataforma), `D-BOOTSTRAP`, `D-DB-GAPS` (fila do ORM: 13 entradas nativas `kof_orm_*` medidas faltando — recon feito, fatias planejadas), e a **Lei da Simplicidade** promovida a regra 11 do AGENTS.md (o que alcanga a superficie da linguagem tem de ser curto/idiomatico/intencao-primeiro). A regra dos 3 estados foi aplicada: os planos workflow/shell concluidos foram PROMOVIDOS `development/`→`docs/` no mesmo push que os fechou.
>
> **18/09 — R3 FFI (JVM) generalizada — `extern` casa a ABI escalar completa
> (dono = 192.168.100.18, lane development).** `CompilerPipeline.isExternBound`
> agora aceita **qualquer aridade** sobre {Int, Long, Float, Double, Boolean,
> String} em toda posição e no retorno, com resultado `String` lido de volta do
> `char*` nativo. Um único downcall FFM `kof_ffi(lib, name, sig, Object[])`
> substitui os helpers `kof_ffi_i`/`_si`/`_dd`; o lowering empacota os args num
> `Object[]` (`KofNewArray`, boxando primitivos) e o emissor JVM desboxa/confere
> o retorno boxado (`emitKofRuntimeCall`). `FfiE2ETest` 8→9 (soma
> `atol(String):Long` → `labs(Long):Long` → `9`, provando o layout `Long` ponta a
> ponta — o Kof não tem literal `long`, então o `Long` vem do `atol`) + novo
> `FfiSignatureTest` 4/4 travando o mapeamento escalar → layout FFM → `Type`
> completo incl. `Float`/`Boolean`, cujo caminho genérico de downcall já é exercido
> pelos e2es de Int/Long/Double/String/void (`pow` 2.0^10 →
> `1024.0`, `strstr("hello world","wor")` → `world`; `srand(Int)` default `void`
> via `kof_ffi_void`). **Paridade JS FECHADA (fatia 3.6, mesmo dia):** a mesma ABI
> escalar agora binda no target JS por um bridge FFM no host `KofJsFfiBridge`
> (`extern`→`kofFfi`→`ProxyExecutable` `kof_platform.ffi` no runner GraalJS/node);
> `FfiE2ETest` soma 7 casos `assertJvmJsParity` provando igualdade byte-a-byte JVM↔JS
> (doubles `3.0`/`1024.0`, `Long` via `atol`→`labs`, `char*`→String, `void`); o browser
> não tem host → degrade honesto em runtime (R7), e assinaturas não-escalares seguem
> `FFI002`. **Callbacks bindam na JVM *e* no host runner JS (fatia 3.4, C1→C3, mesmo dia):** um `extern` com
> parâmetro de tipo-função baixa para `kof_ffi` (token aninhado `(<ret><params>)`) e o
> runtime monta um ponteiro de função C via `Linker.upcallStub` sobre o valor de função
> Kof — `JvmFfiCallbackE2ETest` computa `42/42/6.0/7.5` em ABIs de callback
> Int/Long/Double/mistas, byte-a-byte JVM↔JS (`jvmAndJsCallbacksMatchByteForByte`); no JS o
> valor de função é um **objeto** `Lambda…`, então a ponte do runner chama
> `fn.getMember("invoke").execute(...)`; contrato síncrono/não-escapante, ABI só primitiva. Paridade ainda não alcançada: struct/pointer (D6),
> variadics e handles opacos seguem `FFI001`;
> Native `FFI001` (§61) permanece gap honesto por target
> (R7). Decomposição
> completa em §R3-fatias / §R3-3.4 do plano universal.
>
> **18/09 — §132 FECHADO (#83-JS) — o KofJS roda o supervisor OTP com paridade
> (dono = 192.168.100.18, lane development).** O `time.sleep` agora é um **ponto de
> await** no backend JS: o compilador colore como async o método que alcança
> `kof_time_sleep` via o fixpoint `computeAsyncColoring` já existente (o mesmo que já
> regia `await`) e emite `await kof_time_sleep(ms)`; `kofTimeSleep` devolve uma Promise
> (node/browser: `setTimeout` real; GraalJS embutido: fila de sleepers drenada pela bomba
> do host `KofJsRunner`, que é o event-loop mínimo que uma única thread JS não consegue
> ser sozinha). Como o host consegue dormir E avançar microtasks, as tasks spawnadas
> irmãs/filhas agora rodam enquanto uma task dorme — o idiom `while(!done(h)){ time.sleep(10) }`
> progride e o worker do supervisor dispara. `OTP002` é levantado (o `kof.supervisor` em JS
> não é mais recusado no compile-time); `KofSupervisorE2ETest#supervisorJsParity` roda
> `APP` → `restarts=2 escaladas=2 fabrica=3 parou vivos=0`. Relógio real preservado
> (`time.now()`/`Date.now()` inalterados) — `KofTimeE2ETest` continua honesto. riscv/aarch
> permanecem `OTP001` (unwinding entre threads §129, lane nat). Prova: `AsyncSleepJsE2ETest`
> 3/3 + reator verde.
> **15/09 — §129 FECHADO (DECISIONS §2 opção B) — o Native x86 desenrola por
> thread (dono = 192.168.100.18, lane development).** O `kof_exc_chain` agora é
> **TLS por thread** (`.section .tbss,"awT",@nobits` + `%fs:kof_exc_chain@tpoff`)
> em vez de um `.data` global, e o `kof_spawn_trampoline` instala um frame de
> handler por worker: um `throw` sem handler interno publica a causa no handle em
> vez de longjmpar para o `try` da thread main (o crash/hang cross-thread). O
> `await`/`awaitTimeout`/`selectAny` do consumidor relançam a causa (paridade
> JVM). `CompilerSupervisor` agora só emite `OTP001` para riscv/aarch (clone cru,
> sem TLS). Isso **destrava o OTP S2-Native** — o supervisor puro-Kof agora roda
> no Native x86. Prova: `KofSupervisorE2ETest` 15/15
> (`supervisorNativeParityX86`/`supervisorNativeS2ParityX86`), `KofConcurrency2Test`
> 40/0 incl. 4 casos novos de throw em worker no native, `ExceptionsE2ETest` 11/0,
> `NativeE2ETest` 65/0. Suíte 1777/1 (o 1 = SIGSEGV Native pré-existente
> `[ifexpr-heterogeneous-direct]`, §205, outra lane). **Atualização 15/09: esse vermelho foi FECHADO no mesmo dia pela §205 fatia 1** (`97d08e54`, rebaixamento ramo-a-ramo no print direto — `conformanceCoreControl` 1/1 medido no tip; o residual `via-var`/`as Object` fica como PARCIAL, catalogado no §205).
> **19/09 — §129 FECHADO também em riscv64/aarch64; `OTP001` REMOVIDO (lane nat/compilador `.17`).** O port cross usa **tabela de cadeia por-TID** `kof_exc_slots` (256 × 16 B `[tid, chain]`, chave `gettid`=a7 178, probe linear) + helper `kof_exc_slot()` — o mecanismo "TLS real via `clone`" da mantenedora foi implementado e provado **ABI-inseguro** (sobrescrever `tp`/`TPIDR_EL0` quebra a TLS da libc → SIGSEGV em `snprintf`/`strtod`; regressão riscv `crossNativeConcurrencyHelpersRun`), então foi abandonado pela segunda opção, segura. O `CompilerSupervisor` não emite mais `OTP001`; `kof.supervisor` roda em todos os targets nativos. `planning-otp-supervision.md`(+PT) promovido p/ `docs/` (planejamento concluído). Prova: `KofConcurrency2Test` 48/0 (`spawnWorkerThrowIsolatedFromSiblingsCrossArch` + `spawnWorkerThrowUnhandledPropagatesCrossArch` verdes nas 2 arches), `KofSupervisorE2ETest` 16/0 (`crossGateOtp001` agora roda o `APP` sob qemu em riscv/aarch), E2E riscv/aarch 45/0 cada.
>
> **14/09 — LINHA DE BASE DA ESTABILIZAÇÃO DE RELEASE (dono = 192.168.100.17,
> lane docs/estabilização).** Run limpo 4-módulos (`rm -rf */target`): **1819
> testes, 3 falhas, 0 erros, 7 skips** (compiler 1552 + script 37 + kof-c 5 +
> cli 225). As 3 vermelhas são **cross-arch de OUTRAS lanes**, catalogadas com
> repro + pointer de causa raiz (NÃO desta lane, NÃO atacar sem dono):
> **residual §181** — `riscv64CastSaturation` + `aarch64CastSaturation`, a
> ÚNICA linha divergente do golden é `(-inf) as Int` (`0` vs `-2147483648`; o
> resto re-confirmado verde no HEAD com `67db6c50` no history), lane nat viva
> (fix 22:42 13/09); **§192** — `KofMathTest.parseOrDefaultCrossArch` TRAVA no
> riscv (um `parse*OrDefault` que lança antes de um `parseDouble` corrompe o
> `kof_exc_chain` via aliasing do slot default/chain `sd a1,24(sp)` no B41 →
> loop de escalação de expoente sem convergência; repro de 2 linhas + PC do
> loop no known-bugs §192), lane stdlib/nat. Todo o resto verde (paridade
> JVM/Script/JS/x86 segura). **A release NÃO congela enquanto esses 3 não
> zerarem** — gate = 0 FAILURE fora dos erros de `node` ausente (13) + BD
> externa (5) + guardas de toolchain. §176 fechado (`a5eedbe2`, causa raiz =
> constant-fold stale da fatia JS web). Decompiler segue despriorizado
> (decisão da mantenedora — meta = estabilizar a release).
>
> **12/09 — §107 face ESCALAR FECHADA nos 3 alvos nativos** (`println(<coleção>)`
> imprimia lixo de ponteiro; `kof_{list,set,map}_to_string` + tag compile-time
> x86 `f3b3821c` + cross riscv/aarch B39 `411e9ce5`, golden JVM byte-idêntico
> sob qemu; record/aninhado fica `?` HONESTO até §104b-ii, FP-coleção no cross
> FLT001 em compilação — nunca silêncio; bug colateral §138 destravou o build
> cross). **12/09 — issue #97 (tree-shaking, frente da mantenedora): S-1/T0
> FEITA** `a3996600` — `ArtifactSize` (parser ELF64 puro-Java) + `ArtifactSizeTest`
> (gate anti-inchaço 5% travado nos números medidos: hello x86 138.928B/627
> símbolos inalcançáveis, runtime JS 177KB, riscv .bss 260KB) + `kof build
> --print-sizes`; plano `PLAN-TREE-SHAKING.md` promovido `future/`→`development/`
> (S-2 = mapa provides/needs por fatia, próximo degrau). **12/09 —
> re-auditorias doc-vs-realidade:** `native-multiarch.md` dizia "JSON/HTTP/net/
> collections só x86" (falso — todos rodam sob qemu, medido `8caa14c1`);
> tabela-topo de `known-bugs.md` trazia varredura apócrifa de 08/09 (39/62/63/
> 64 já ✅) — corrigida com fila real de 16 abertas, cada uma com bloqueio
> mensurável (decisão/lane/congelado) `42c716ed`.
> **11/09 — OTP núcleo (issue #83) entregue em JVM+Script:** pacote virtual
> `kof.supervisor` (host puro-Kof, `import kof.supervisor`), com observar-falha,
> reinício individual (fábrica nova), limite de reinícios + escalate e stop
> controlado (`KofSupervisorE2ETest` 6/6). Native/JS bloqueados no compile-time
> com diagnóstico claro (`OTP001`/`OTP002`, §129/§132 — nunca silêncio). Impeditivo
> §130 (SEM024 falso em método re-analisado) corrigido junto.
> **11/09 — fetch assíncrono no KofJS (§133):** `spawn http.get(url)` + `await`
> agora resolve corpo real no Node/browser (fetch→Promise pela máquina
> `Handle<T>` existente; zero AST novo). O fallback `return ""` silencioso do
> runtime JS acabou — face síncrona em JS puro fica honesta (Promise cru,
> HTTP003); GraalJS/KofJsRunner intacto. Prova: `KofHttpE2ETest` 8/8.
> **11/09 — MATH001 fechado** (`kof.math` Double nos 5 targets; §120 + §105 na série remota).
> **11/09 — TIME002 fechado** (`addDays`/`diffDays` nos 5 targets).
> **11/09 — SG-011B fechado (sobrecarga top-level, oracle JVM):** funções
> homônimas de assinaturas diferentes coexistem e resolvem no call site;
> duplicata exata/colisão só-de-retorno → SEM047, ambígua → SEM057. Cada
> backend referencia o candidato pela ASSINATURA (descritor JVM, símbolo
> sufixado no Native, nome sufixado no JS, dispatch por tipo no interpretador)
> — saída byte-idêntica nos 6 targets (`TopLevelOverloadE2ETest` 5/5; §136,
> contrato ratificado na §135). **Sobrecarga de MÉTODO de classe (§131)
> FECHADA 13/09** (`18a64d45`, 4 backends: `MethodSet` na symtable +
> seleção por assinatura no typer + slot/símbolo próprio por overload no
> Native + mangle JS; `CoreRegressionE2ETest.methodOverloadByArity`).

---

## Build

``` 
mvn clean package    → PASSA
mvn test             → 3225 testes (2762 kof-compiler + 50 kof-script + 7 kof-c-compiler + 406 kof-cli), 0 falhas / 0 erros, 221 skip (sem qemu no host → 84 cross skip; guardas de toolchain/DB externo + guard de sysroot §255; `node` presente — todos os `*Js` verdes) — 16/09 ~15:54 clone limpo de `9572949f` (o flake §252 nativo calado de novo) (o anterior 1662/13-erros = host sem node, 13/09)
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

- **`Set<T>` como tipo declarado no JVM**: descriptor `kof.Set` → `java/util/HashSet`
  (`NoClassDefFoundError: kof/Set` fechado); parser de membros de classe com
  retorno genérico (`Set<Int> foo()`, `List<String> bar()`).
- **Null-safety narrowing no JVM corrigido**: `if (s != null) { s.length }` /
  `s.substring(...)` emitiam `getfield "?".length`/`"".substring` (bytecode
  inválido); `if (x != null)` usava `if_icmp*` em referência. `mapOf(k1,v1,...)`
  infere o tipo do primeiro par. Forma prefixada `String? s = null` passa a
  parsear.
- **stdlib honesta**: `File.readText`/`readFile` → `String?` (Native devolve
  `null` em vez de encerrar); `File.size()` lança em vez do sentinela `-1`;
  `Map.get` → `V?` para valores de referência; `readLine()` → `String?`
  (`null` no EOF, JVM e Native).
- Ver `CHANGELOG.md` [0.2.6-beta] 02/09 e `docs/backend-parity.md`.

---

## Performance & Benchmarks (docs/architecture/performance.md)

- **Otimizador de IR** (`Optimizer.java`, sempre ativo): constant folding,
  branch simplification (condições constantes → jumps diretos), dead stack
  effects (push+pop, dup+pop, load/store round trips), unreachable code
  elimination (CFG reachability com regiões try/catch preservadas),
  jump-to-next elimination, identidades aritméticas (x+0, x*1, x/1, ...).
  Debug positions preservadas (ops sobreviventes).
- **Perfis debug/release**: `kof build|run --release` remove metadata de
  debug (SourceFile/LineNumberTable no JVM, source map no JS).
- **`kof bench`**: `kof bench [paths...] [--target jvm|native|js]
  [--iterations N] [--quick] [--baseline <file>] [--update-baseline <file>]
  [--threshold <ratio>] [--json] [--fail-on-regression]`.
  Compila → executa → valida stdout contra `expected.txt` → mede tempo
  (mediana) e RSS (Linux, `/usr/bin/time -v`) → compara baseline →
  sinaliza `PERFORMANCE REGRESSION`.
- **Estrutura `benchmarks/`**: 37 benchmarks em 17 categorias (micro,
  algorithms, collections, strings, math, objects, inheritance, interfaces,
  generics, json, io, concurrency, startup, memory, stress, applications).
- **Baselines**: `benchmarks/baselines/<target>-<version>.json` (33 jvm,
  29 native, 32 js).
- **CI**: `.github/workflows/benchmark.yml` — roda jvm+native com
  `--fail-on-regression --threshold 1.20`.
- `scripts/run-benchmarks.sh` — suite completa + atualização de baselines.
- Regra de features novas: docs/architecture/performance.md §40-§41 (Definition of Done
  inclui benchmark, stress, memory, resource e debug metadata).

### Correções de backend descobertas pelos benchmarks e E2E

| Bug | Correção |
|-----|----------|
| Chamadas via interface com retorno primitivo geravam descritor `Object` (`()Ljava/lang/Object` + `iadd` = bytecode inválido) | `analyzeInterface` agora define os symbols em `members()` (eram invisíveis ao `resolveInHierarchy`) |
| `l.get(i)`/`l.remove(i)`/`l.size`/`l.contains(...)` como statement não emitiam `KofPop` → stack desbalanceado em merge points (Frame.merge crash / VerifyError) | `hasReturnValue` cobre métodos de List que deixam valor |
| `if (long > long)` / `if (float > f)` / `if (double > d)` geravam `IF_ICMP` sobre não-ints (stack underflow) | `KofConditionalJump` ganhou `operandType`; JVM emite `LCMP`/`FCMPL`/`DCMPL` + jumps de 1 operando |
| `while (longExpr < intLiteral)` gerava `LCMP` sobre [long, int] (stack underflow) | shortcut de comparação faz widening dos operandos (`emitComparisonShortcut`) |
| JS: call com efeito descartada em statement com Pop (ex.: `users.remove(0)` silenciosamente não executava) | handler de `KofPop` no JsBackend preserva `JsCall`/`JsSequence` como statement |
| `Box<Int>` / `Box<T>` com `b.get()` retornando `T` imprimia `T` como `String` no Native → segfault `0x7` (`NativeE2ETest.execGenericClass`) | `ExpressionTyper.inferExprType` (`ExpressionTyper.java:14`) substitui `T` via `CompilerTypes.substituteTypeVariableIn` (`CompilerTypes.java:423`); `println` nativo via `kof_int_to_string` (`NativeRiscvCrossOps.java:201`) |
| `record Ponto` `hashCode()` reportava `SEM025` falso-positivo | `MemberResolver.java:65` ignora `isObjectMethod(hashCode/equals/toString)` |
| **Regressão `dc849f6` (01/09):** `kof_list_add` sem `POP` no JVM (assumiu que o IR emitiria `KofPop`) → `hasReturnValue` trata `add` como void, então o boolean do `ArrayList.add` ficava na pilha → frame crash (`Index out of bounds`) em 15 testes | POP restaurado no emit `kof_list_add` + `hasReturnValue` blinda `add/push/append/set/clear/put` de coleção (nada de `KofPop` duplo) + `cache` só é namespace se não for local/param (`c7b23a1`…`7c6aca9` + POP `7c6aca9`) |
| **Surefire: `NativeDebugTest2/3/4/5` nunca rodavam na suíte** — o padrão default `*Test.java` não casa com `…Test2.java` (só `-Dtest` explícito os pegava) | `<includes>*Test*.java</includes>` no surefire do `kof-compiler` — suíte voltou a 752 |
| **`spawn { lambda c/ captura }` → `VerifyError`/`ClassFormatError`** (JVM) / valor errado (Native): o lowering `SpawnStmt` criava a lambda com `List.of()` (zero capturas), então o corpo resolvia a variável externa para `this` | `SpawnStmt` (JVM + Native) agora coleta via `collectCaptures(le, locals)` e emite o construtor da lambda com os loads das capturas (mesmo padrão do case genérico) — `SpawnE2ETest.spawnLambdaCapturesOuterLocal` |
| **`&&`/`||` sem short-circuit no JS**: o JsBackend emitia `KofBinaryOp.AND/OR` como `&`/`|` (bitwise), que avalia os DOIS lados → efeitos colaterais do lado de não eram executados | `&&`/`||` booleanos (operandType `bool`) agora viram `&&`/`||` JS (short-circuit nativo); `&`/`|` bitwise intacto — `KofJsE2ETest.logicalAndOrShortCircuit` + `bitwiseAndOrStillWorks` |
| **`Channel<T>` rejeitado como parâmetro de função**: o tipo do parâmetro saía `ClassType(package="")` e o `isChannel` exigia `kof.concurrent` → dispatch caía no genérico → bytecode inválido (JVM), `undefined reference Channel_receive` (Native), `c.receive()` inexistente (JS) | `Type.of`/`toType` tratam `Channel` como builtin (`kof.concurrent`, paridade com `List`); `JvmTypeMapper` mapeia `Channel` → `java/util/concurrent/LinkedBlockingQueue` (descritor+internalName) — `KofConcurrency2Test.channelAsFunctionParameter{Jvm,Native,Js}` |
| **`println`/`print` antes de `spawn` → SIGSEGV no Native** (`pthread_create`): a convenção args-by-stack (push) chegava 8 bytes desalinhada no site do `call pthread_create` (`rsp%16==8` vs `0` exigido pela ABI SysV) → glibc segfaultava em `pthread_attr_copy` escrevendo no frame | alinhamento de stack no C call: `andq $-16, %rsp` antes do `call pthread_create` em `kof_spawn_handle_new`, preservando `r15` (callee-saved) e restaurando o frame do caller — `SpawnE2ETest.nativePrintBeforeSpawnDoesNotSegfault` |
| **AES-GCM no JS ignorava tamper no ciphertext** (`SECN002`, 01/09): `kofSecB64Decode` tolerava tamanho não múltiplo de 4 (bits restantes descartados silenciosamente), então `decryptAesGcm(ct + "AA")` decodificava e o tag mismatch passava despercebido — divergente do `java.util.Base64` do JVM (que lança) | `kofSecB64Decode(s, strict)`: `strict=true` rejeita tamanho %4 ≠ 0; `decryptAesGcm` passa `strict=true` em `iv` e `ctTag`; JWT (b64-url sem padding) segue com `strict=false` — `KofSecurityTest.aesGcmJsRoundTrip` (tamper+chave errada) + paridade cross-target JVM↔JS |
| **OBS002: histogram/metrics no Native** (01/09): implementado em asm — bugs encontrados no smoke-test: (1) appender caía no fluxo principal após o seed (sem `jmp`) → crash em `kf_memcpy` com len lixeira; (2) loop de export com comparação invertida (`cmpq %idx, %len; jge` saía imediatamente) → string vazia; (3) `kf_free` clobbrou `%rsi` (fragmento) no meio do append → `kf_string_concat` com ptr corrompido; (4) `call` com `rsp%16==8` (ABI SysV) | store `.Lkof_obs_histograms` (32B: name+sum+count) + `kof_observability_metrics` via `kof_string_concat`; appender com `pushq` de alinhamento + fragmento em `%r10` (scratch); `cmpq %len, %idx` corrigido — `KofObservabilityTest.observabilityNative` (paridade de conteúdo com o JVM, byte-identical no smoke-test) |
| **`transaction {}` no Native dava link error** (`kf_db_transaction` não existia; o gate `KofDb.supportedOn` já liberava o Native) + rollback não desfazia (01/09): (1) a lambda não tinha `rdi` (=this, onde ficam as capturas) antes do `call *%rax` → lia `db` no campo errado; (2) `r12` (handle) clobberado pela lambda no caminho do throw; (3) KofStrings de BEGIN/COMMIT/ROLLBACK sem NUL final → `sqlite3_exec` lia "begin\x01" (erro ignorado) e o autocommit persistia os inserts | `kf_db_transaction` em asm: BEGIN via `kf_db_execute`, `movq %rbx, %rdi` (this) antes do invoke, COMMIT/ROLLBACK **re-carregam o handle do BSS** (`.Ldb_default_handle`, gravado no `connect`), re-throw via `kf_throw_string` (a chain aponta p/ o try externo); KofStrings com `.asciz` (NUL) — `KofDbE2ETest.nativeTransaction{Commits,RollsBackOnFailure}` |
| **MQ001: kof.mq no Native** (01/09): pub/sub + filas in-process implementadas em asm | store `.bss` (topics/queues/seq) + nodes 40B `[next, KofString*, KofList*, _, _]`; `kof_mq_find_topic`/`_queue` (busca por `kof_string_equals`, callee-saved `rbx/r12` pois `kf_string_equals` clobbra `rdi/rsi/rax`); `subscribe`/`push` criam o node na 1ª vez e `kof_list_add`; `publish` itera os subs com `kof_list_get` + invoke-com-arg (`rdi`=fn, `rsi`=msg); `unsubscribe` compara por **identidade** do objeto fn; `queue()` = `"mq-<n>"` (seq); `pop` remove head (`null` se vazio) — `KofMqE2ETest` 4/4 (JVM+Native+JS, paridade de output) |
| **`Set<T>`/`Map<K,V>` como campo/retorno de classe → `NoClassDefFoundError: kof/Set`** (01/09): dois bugs. (1) `JvmTypeMapper.classDescriptor`/`toInternalName` mapeavam só `List`→`ArrayList` e `Channel`→`LinkedBlockingQueue`, mas **não** `Set`/`Map` → o descriptor do campo/retorno ficava `Lkof/Set;`/`Lkof/Map;` (classe inexistente) enquanto o runtime real é `java.util.HashSet`/`HashMap`; (2) o parser de membro de classe (`ClassMemberParser.parseClassMember`) só reconhecia `Type name(` para método (lookahead de 2 tokens), então retorno **genérico** `Set<Int> all(` caía no ramo de campo e quebrava em `(` (PARSE016/020-023/044) | `JvmTypeMapper`: `Set`→`Ljava/util/HashSet;`, `Map`→`Ljava/util/HashMap;` (desc + internalName); `ClassMemberParser.parseClassMember`: novo ramo `isGenericReturnTypeAhead()` + `consumeGenericTypeArgs()` antes do fallback de campo (mesma forma do top-level `parseFunctionDeclaration`) — `KofMapSetTest.setMapAsFieldAndReturn` (3 targets, campo de classe + param de construtor + retorno de método) |

---

## Segurança (kof.security, docs/stdlib/security.md)

- **`kof.security` implementado** (v1): `passwords`, `crypto`, `jwt`,
  `secrets`, `security`, `auth` — secure by default, gaps de target com
  diagnóstico claro em compile-time (SECN001/002/003).
- **JVM**: PBKDF2-HMAC-SHA256 (600k iterações), SHA-256/512, HMAC, AES-GCM,
  SecureRandom, JWT HS256 (sig/exp/iss/aud), env secrets, constant-time,
  redaction, contexto web `auth.*` (Bearer JWT).
- **Native**: SHA-256, SHA-512 e HMAC em assembly puro (x86-64, sem libc,
  FIPS 180-4 / RFC 2104 — valores idênticos ao JVM), PBKDF2-HMAC-SHA256,
  AES-GCM (round-trip E2E `aesGcmNativeRoundTrip`), JWT HS256, random via
  `getrandom`, secrets via `/proc/self/environ`, constant-time, redaction.
- **JS**: SHA-256/512 e HMAC em JS puro, PBKDF2 com delegação ao platform
  (runner embarcado), JWT, secrets, constant-time, AES-GCM (01/09, SECN002).
- **Testes**: `KofSecurityTest` — 27 testes (unit + E2E nos 3 targets +
  adversariais: tamper, expiração, confusão de algoritmo, token malformado,
  chave errada, issuer/audience).
- **Benchmarks**: `benchmarks/security/` (password-hash, jwt, hash-speed,
  aes-gcm).
- **Docs**: `docs/stdlib/security.md` (auditoria + matriz + arquitetura + estado),
  `docs/stdlib/stdlib.md`, `learn/36-security.md`, `training/language/security.md`,
  `training/examples/security.kf`.

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

- **JVM**: JDBC idiomático (`db.connect`, `db.execute`, `db.query`,
  `query<T>` tipado por record/entity, credentials opcionais,
  `transaction {}` com commit/rollback real).
  - **Native**: SQLite via link direto da `.so` (sem driver JDBC) — roundtrip
    E2E real (`nativeSqliteRoundtrip`). **`transaction {}` com commit/rollback
    real** (01/09): `kof_db_transaction` em asm — BEGIN via `kof_db_execute`,
    invoca a lambda (vtable[0]=invoke, `rdi`=this p/ capturas), COMMIT no
    sucesso, ROLLBACK + re-throw no erro (EH `kof_exc_chain`/`kof_throw_string`
    — a exceção chega no handler com `%rdi` e a chain apontando p/ o try
    externo; conexão default = última aberta, paridade `KOF_DB_DEFAULT` do JVM).
    MySQL/MariaDB via wire protocol sobre
    sockets nativos: **handshake + auth scramble SHA-1 + auth-switch
    (mysql_native_password) + COM_QUERY + parse de resultset (coldefs + rows
    + EOF) + binds `?` (substituição de literal client-side, `nativeMysqlWireProtocol`
    — 31/08)**. Prepared statements via COM_STMT_PREPARE (binário) pendente.
- **JS** (16/09, DB001 fechado): `connect`/`connect2`/`close`/`execute`/`query`/`transaction` delegam a `kof_platform.db*` no host GraalJS (`KofJsRunner`+`KofJsDbBridge`) — mesma JVM/classpath do caminho JDBC, entao o `DriverManager` ve h2/sqlite-jdbc exatamente como o target JVM faz; saida byte-parity (`KofDbE2ETest.js*` 4 casos). **`db.query<T>` tipado = `DB002` FECHADO 18/09** (a wire e nao-tipada — a ponte host nao tem `Class.forName` p/ classes JS — mas o guest binda cada linha JSON com o mesmo helper `__kof_decode_<T>` que o `json.decode<List<T>>` usa; `KofDbE2ETest.jsTypedQuery*` byte-parity c/ a JVM).
- **riscv64/aarch64**: SQLite fechado 15/09 — link-by-use `libsqlite3` + fatias de runtime `kof_db_*` `RtB46/RtB47` (`KofDbE2ETest.crossNativeSqliteRoundtrip` sob qemu); DSN só `sqlite:`, transaction via EH chain (a cadeia §129 é por-TID desde 19/09; transações concorrentes em workers diferentes ainda compartilham o slot global `.Ldb_tx_handle` — residual catalogado no `NativeRiscvAsmRtB47`).
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
    var pg = orm.page<User>(db, 20, 40)                   // paginação (limit, offset)
    println(orm.count<User>(db))
    orm.delete<User>(db, 1)
    orm.migrate(db, "add-phone", "ALTER TABLE user ADD phone VARCHAR")
}
```

- Schema declarado na linguagem (`entity`) — o compilador conhece campos,
  tipos e constraints em compile-time (nunca reflection para descobrir
  schema); `generated`, `unique`, PK não-numérica.
- Backends SQL: H2/SQLite/MySQL/MariaDB/PostgreSQL via JDBC (JVM).
- CRUD completo + consultas: `saveAll` (batch), `where` com operadores
- **Tipagem de coluna (P3-10)**: `where`/`where_op`/`count` com coluna literal
  que não é campo da entidade → `ORM003` em compile-time (JVM); coluna
  dinâmica (variável) segue liberada
  (`"="`, `">"`, `"<"`, `">="`, `"<="`, `"!="`...), `count` com filtro,
  `page` (limit/offset) e `deleteAll`.
- **MongoDB**: `save/find/all/where/delete/count` sobre o driver oficial via
  reflexão compatível (`Bson`/`Class`, sem ClientSession); teste E2E com
  container real (skip condicional; serviço Mongo no CI).
- Migrations versionadas: tabela `kof_migrations`, cada migração roda uma vez.
- Native reporta `ORM001`; JS FECHADO 18/09 (`KofJsOrmBridge`, mesmo SQL do JVM, E2E byte-paridade).
 - Testes: `KofDbE2ETest` (9), `KofOrmE2ETest` (31; MariaDB/PostgreSQL/MongoDB
   com skip condicional quando o container não está no ar).
 - Docs: `docs/stdlib/DATABASE_VISION.md` (níveis 0-4 implementados, incluindo
   o nível 3 = query DSL tipada `User.query(db){ where; orderBy; limit }` — 01/09).

---

## Infraestrutura de distribuição

- `VERSION` como fonte única; `<revision>` no Maven; `KofVersion` com
  `version.properties`; `scripts/bump-version.sh`.
- CLI (26 commands): `build, run, serve, check, test, script, repl, c, fmt,
  config gen, bench, profile, inspect, decompile, translate, compare, migrate,
  debug, info, lsp, install, deps, editor, new, init, version`.
 - `kof lsp` — Language Server via stdio (initialize, didOpen/didChange/
   didClose → publishDiagnostics do frontend real, hover, completion,
   **references + rename** — word-boundary, single-file; `LspServerTest` 4/4).
- Launchers `bin/kof` (Unix) e `bin/kof.bat` (Windows) com JDK embutido
  (Temurin 25, Tooling API Level 21).
- `scripts/package.sh` — layout oficial de distribuição, `--jdk` para JDK
  embutido, SHA256SUMS.
- GitHub Actions: `ci.yml` (PR — testes, golden, integração, multiplatform)
  e `release.yml` (main → testes → bump → package 3 plataformas → changelog
  → GitHub Release).
- Editor support: `editor/kof.tmLanguage.json` (grammar TextMate).

---

## Targets

| Target | Backend | Execução | Status |
|--------|---------|----------|--------|
| `jvm` | `JvmBackend` (ASM) | bytecode V21, exception table, virtual threads | estável |
| `native` | `NativeBackend` (x86_64) | ELF x86_64, syscalls, free-list alloc + GC mark-sweep (03/09; auto-coleta ✅ pousou 19/09 — §260 FECHADO, D1-A) | estável |
| `native.risc` | `NativeBackend` (riscv64) | ELF riscv64 via `riscv64-linux-gnu-as/ld` + qemu (core+stdlib 02-05/09, 26/26 — ver `docs/native-multiarch.md`) | estável (core) |
| `native.arm` | `NativeBackend` (aarch64) | ELF aarch64 via `aarch64-linux-gnu-as/ld` + qemu (core+stdlib 03-05/09, 26/26 via tradução — ver `docs/native-multiarch.md`) | estável (core) |
| `js` | `JsBackend` + `KofJsRunner` | ES Modules via GraalJS, `kof.http` via `Java HttpClient` interop | alpha |
| `kofc` | `KofCcompiler` | C subset (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`) → nativo x86_64 | nativo-only |

O mesmo frontend e a mesma Kof IR alimentam os três backends.

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
| println / print | ✅ | ✅ | ✅ |
| variáveis, aritmética, bitwise, hex literals | ✅ | ✅ | ✅ |
| if/else, if-expr | ✅ | ✅ | ✅ |
| while, for, do-while, for-in, break/continue | ✅ | ✅ | ✅ |
| switch | ✅ | ✅ | ✅ |
| funções (todas as formas) | ✅ | ✅ | ✅ |
| classes, campos, métodos | ✅ | ✅ | ✅ |
| `constructor(...)` e primary `class X(...)` | ✅ | ✅ | ✅ |
| records (toString/equals/hashCode) | ✅ | ✅ | ✅ |
| herança, `super`, override, virtual dispatch | ✅ | ✅* | ✅ | Native: `super.metodo()` = SUP001 |
| interfaces | ✅ | ✅ | ✅ |
| generics por erasure | ✅ | ✅ | ✅ |
| lambdas `(x: Int) -> expr` + capturas | ✅ | ✅ | ✅ |
| exceptions reais (try/catch/finally + unwinding) | ✅ | ✅ | ✅ |
| `assert(cond[, msg])` | ✅ | ✅ | ✅ |
| `spawn` (concorrência, join implícito) | ✅ | ✅ (pthread, 31/08) | ✅ |
| strings (concat `+`, `==`, indexOf, trim, split...) | ✅ | ✅ | ✅ |
| arrays | ✅ | ✅ | ✅ |
| `List<T>`, `listOf`, `map/filter/reduce` | ✅ | ✅ | ✅ |
| `Box<T>` generics com `T` primitivo/Boxed (ex.: `Box<Int>`) | ✅ | ✅ | ✅ | 25/08 fix `substituteTypeVariable` |
| JSON encode/decode (objetos/records no JVM) + arrays nativos | ✅ | ✅ | ✅ |
| JSON decode `List<User>` (objetos aninhados) | ✅ | — | ✅ |
| kof.io (File/Path/Directory, readFile, writeFile) | ✅ | ✅ | ✅ |
| kof.time (now/sleep/interval) | ✅ | ✅ (now/sleep/**interval** — reusa o scheduler, SCHED001) | ✅ (now/sleep/**interval** — fila cooperativa bombeada por `time.sleep` no GraalJS; `setInterval` no browser/Node, TIME001 fechado 02/09) |
| kof.web (`web.app()`, rotas, middleware, WebSocket/SSE, `configure`/`stats`) | ✅ | — | — |
| kof.http (`http.get/post/put/delete/status` + `timeout/retry/circuit`) | ✅ | ✅ **HTTP002 fechado 03/09** (`NativeHttpRuntime` — HTTP/1.1 asm, IPv4; https → throw claro; retry/circuit no-op) | ✅ (27/08 JS via `Java HttpClient` interop; 30/08 retry/circuit paridade) |
| kof.config (env, arquivos, profiles, typed) | ✅ | ✅ (asm próprio) | ✅ |
| kof.mq (publish/subscribe/queue) | ✅ | ✅ (01/09, pub/sub + filas in-process, asm) | ✅ |
| kof.log (`log.info/warn/error/debug`) | ✅ | ✅ (asm; UTC, sem JSON) | ✅ (LOG001 fechado 01/09) |
| kof.security (passwords, crypto, JWT, secrets) | ✅ | ✅ | ✅ |
| kof.db (JDBC, query<T>, transaction) + SQLite nativo | ✅ | ✅ (SQLite + transaction; MySQL wire x86-64 real, ORM 13 faces 22/09; **riscv64/aarch64 ✅ 15/09** link-by-use libsqlite3) | ✅ 16/09 (nao-tipado `connect/execute/query/close/transaction` na ponte GraalJS) + ✅ 18/09 tipado `query<T>` (`DB002` fechado — bind no guest via `__kof_decode_<T>`) |
| kof.orm (entity, CRUD, where, migrate, MongoDB) | ✅ | ORM001 | ✅ FECHADO 18/09 |
| String.toInt/toLong/toDouble/toFloat | ✅ | ✅ | ✅ |
| kof.ui (Color, Palette, Theme, Window) | ✅ | ✅ (JS render) | ✅ |
| default parameters em funções | ✅ | ✅ | ✅ |
| `readLine()` | ✅ | ✅ | ✅ |
| `KofCcompiler` C subset → nativo | — | ✅ (27/08) | — |
| `KofScript` top-level `var`/`val` → `KofScriptGlobals` (execução direta via `KofInterpreter`) | ✅ | ✅ | ✅ |

### Concorrência (`spawn`)

```kof
spawn processarFila()
spawn {
    println("background")
}
```

- JVM: virtual threads; o programa espera as tarefas (join implícito).
- Native: pthread_create + trampoline + `await`/pthread_join + allocator
  thread-safe (futex) + `done`/`poll`/`cancel`/`cancelled`/`selectAny` — ✅ 31/08 (CONC001 fechado).
- JS: concorrência real via `async`/`await`/`Promise` do GraalJS — `CONC003`
  **fechado de fato 03/09** (a marcação anterior `7402101` era sobre código
  morto no lowering, não a feature; `spawn`/`await`/`channel<T>()` agora
  deferem de verdade via microtask, `KofJsRunner` drena `kofActiveTasks` até
  todas as tasks terminarem — ver `docs/language-reference/concurrency.md` seção 4,
  `docs/targets/KOFJS.md`).
- Zero API de plataforma exposta (Thread/Runnable são internos do runtime).
- **Modelo de memória (SG-020)**: spec de happens-before em
  `docs/language-reference/concurrency-memory-model.md` — SC em todos os targets,
  6 bordas de HB (spawn/await/channel/cancel/locais/race), provas
  `staticsAreSequentiallyConsistent`/`noWordTearingOnLong` em
  `KofConcurrency2Test`.
- Ver: `docs/language-reference/concurrency.md`.

### HTTP (`kof serve`)

API legada (handler top-level):

```kof
handle(String method, String path, String body, String query, String headers): String {
    if (path == "/hello") {
        return "{\"msg\": \"hi\"}"
    }
    return null   // 404
}
```

Stack web nativa (Fase 1 — independência do Spring):

```kof
record User(String name, Int age)

main() {
    var app = web.app()
    app.use {
        if (header("x-auth") == "secret") {
            return null
        }
        return "{\"error\": \"unauthorized\"}"
    }
    app.get("/hello") {
        return "Hello from Kof"
    }
    app.get("/users/:id") {
        return "user " + param("id") + " q=" + query("name")
    }
    app.post("/user") {
        var user = json.decode<User>(body())
        return json.encode(user)
    }
    app.listen(8080)
}
```

- `web.app()` + rotas com lambda trailing; path params (`:id`), query,
  headers, body, `method()`, `path()`; middleware `app.use { ... }`.
- Engine HTTP gerado dentro do runtime do programa (sem servlet container,
  sem Spring); cada conexão em virtual thread.
- `app.configure(...)` / `app.stats(...)` (JVM, 04/09): connection cap,
  limites configuráveis e contadores SSE/WebSocket.
- `kof serve <file.kf>` detecta `main()` e executa apps `web.app()`;
  a API legada `handle(...)` continua funcionando.
- Ver: `docs/stdlib/stdlib-web.md` e `KofWebE2ETest` (9 testes E2E com sockets reais).

### Media (`kof.media`) — arquivos, não strings

A linguagem NÃO transporta imagem/áudio como `String` gigante (nem base64
literal no fonte, nem data-URI colado à mão — o padrão que o
Kof-editor-theme-maker era forçado a adotar com `pageCss(): String` e
`kofPngData(): String`). O app trata o ARQUIVO:

```kof
main() {
    var app = web.app()
    app.serveDir("/img", "assets")      // GET /img/logo.png → bytes do disco, image/png
    app.get("/thumb") {
        var img = Image.open("assets/logo.png")   // javax.imageio
        img.saveAs("assets/thumb.jpg", "jpeg")
        return "w=" + img.width() + " h=" + img.height()
    }
    app.get("/rec") {
        var m = Mic.record(2)            // javax.sound.sampled (16kHz mono PCM)
        m.saveWav("assets/gravacao.wav")
        return "ms=" + m.durationMs()
    }
    app.get("/clip") {
        var v = Video.open("assets/clip.mp4")
        return "ms=" + v.durationMs() + " " + v.format()
    }
    app.serveDir("/media", "assets")      // Range 206 p/ <video> no browser
    app.listen(8080)
}
```

- **`Image`** (`ImageData`): `open` (PNG/JPEG/GIF/BMP), `width/height/format`,
  `save`, `saveAs(path, fmt)`, `bytes`/`bytesAs`, `dataUri` (opcional, em
  runtime — nunca literal no fonte), `close`.
- **`Audio`**: `openWav`/`saveWav` (WAV RIFF PCM 16-bit), `sampleRate`,
  `durationMs`, `pcmBytes`.
- **`Mic`**: `record(seconds)` do microfone padrão, `list()`.
- **`Video`**: `open` + metadados do container (`path/size/format/durationMs`,
  MP4/MOV lidos do box `mvhd`; outros containers → 0) + `bytes`/`close`.
  O app NÃO decodifica frames — sem lib externa no JVM (gap honesto); a API
  serve o arquivo (serveDir + Range) para o navegador reproduzir.
- **`app.serveDir(prefix, dir)`** (`web`): fallback de rotas dinâmicas —
  devolve o ARQUIVO em binário com content-type pela extensão (HTML/CSS/JS/
  imagens/áudio/**vídeo**/fontes/PDF...), `Cache-Control`, proteção contra
  path traversal e **Range requests** (`206 Partial Content` + `Content-Range`
  + `Accept-Ranges: bytes`, `416` para range inválido) — necessário para
  `<video>`/`<audio>` navegarem/seekarem no browser. Sem isso, o app só
  tinha `String` por rota → CSS/HTML/imagens viravam strings concatenadas e
  base64 colado no fonte.
- Caminhos relativos resolvem contra a raiz do projeto (`-Dkof.root`,
  definido pelo CLI `run`/`serve` como o diretório do `.kf`).
- **Targets**: JVM (javax.imageio + javax.sound; vídeo como container +
  streaming). **Gaps honestos**: decodificação de frames de vídeo (sem lib
  externa), câmera (MEDIA002), mic sem hardware (MEDIA003), paridade
  Native/JS (MEDIA001 — ART sem javax.imageio; app Android roda no WebView
  KofJS).
- Ver: `KofMediaE2ETest` (16 testes: serving binário byte-a-byte,
  content-type, traversal bloqueado, 404, dimensões reais, conversão
  PNG→JPEG, WAV info/copy, mic sem hardware, metadados de MP4, Range
  206/416/200).

### Configuração nativa (`kof.config`)

```kof
main() {
    var port = config.int("server.port", 8080)
    var url = config.str("database.url", "jdbc:h2:mem")
    var debug = config.bool("app.debug", false)
    var home = config.env("HOME")
    if (config.has("database.url")) { ... }
}
```

- Precedência: arquivo explícito (`KOF_CONFIG`) > env `KOF_<KEY>` >
  profile (`kof.<KOF_PROFILE>.config`) > arquivo padrão (`kof.config`).
- Tipagem em compile-time; valores ausentes/inválidos → default.
- Native: implementação asm própria completa — precedência total
  (KOF_CONFIG > env KOF_<KEY> > perfil > kof.config), typed com default
  em valor inválido, trim e comentários (`NativeConfigE2ETest`, 8 testes).
  ✅ (JVM/Native/JS — CONF001 fechado; ver `docs/stdlib/stdlib-config.md`).
  Prova: `KofConfigE2ETest` 11/11 (JVM + Native + JS, arquivo/env/profile reais).

### Logging nativo (`kof.log`)

```kof
log.debug("detail")
log.info("request started")
log.warn("slow response")
log.error("failed: " + message)
```

- Formato `timestamp LEVEL mensagem`; info/debug → stdout, warn/error →
  stderr; nível via `KOF_LOG_LEVEL` (debug < info < warn < error < off).
- Funciona dentro de handlers web. **Native**: implementação asm própria
  (data civil Hinnant, env scan próprio) — timestamp UTC e `KOF_LOG_JSON`
  sem efeito por enquanto; JS `console.*` (LOG001 fechado 01/09). Docs: `docs/stdlib/stdlib-logging.md`
  (`KofLogE2ETest` 10 JVM + `NativeLogE2ETest` 7).

### Testes da linguagem (G6 — suíte estruturada)

```kof
test "soma simples" {
    assert(2 + 2 == 4)
}

test "string igual" {
    assert("kof" == "kof", "strings iguais")
}

main() { /* ignorado pelo kof test */ }
```

- `test "nome" { }` vira função em compile-time (desugar → `kof_test_N`);
  o runner é sintetizado pelo compilador — zero reflection.
- `kof test <file.kf|dir> [--target jvm|native|js]` reporta
  `PASS nome` / `FAIL nome: mensagem` + resumo; exit code ≠ 0 se houver
  falha. Cada teste roda isolado (try/catch por teste).
- Arquivos sem blocos `test` mantêm o contrato antigo (PASS/FAIL por
  exit code do programa inteiro).
- **process.exit(code)**: primitivo novo nos 3 targets (JVM System.exit,
  Native syscall, JS sentinel no KofJsRunner) — sem stack trace.
- G7 fechado: `jwt.*` tem entrada explícita na matriz de targets — Native
  reporta `SECN004` em compile-time (antes: erro de link silencioso).
- Ver: `learn/23-testing.md`, `StructuredTestE2ETest` (11 testes).

---

## Testes (3225 = 2762 kof-compiler + 50 kof-script + 7 kof-c-compiler + 406 kof-cli — suíte completa verde, 0 regressões / 0 erros, 221 skip; medição 20/09 ~18:14 pelo job CI Build+Tests do tip `404d8be6` (1º verde na `beta-0.5.0`); flake §252 nativo calado de novo. Host sem qemu: cross → skip honesto)

| Suíte | Quantidade | Cobertura |
|-------|-----------|-----------|
| CompilerDriverTest | 252 | compilação, semântica, fases, isolamento |
| NativeE2ETest | 65 | execução real de binários nativos |
| KofJsE2ETest | 40 | execução real JS (GraalJS) + short-circuit `&&`/`||` vs bitwise |
| JvmE2ETest | 31 | execução real de bytecode JVM |
| KofSecurityTest | 28 | kof.security: senhas, crypto, JWT, secrets, adversariais |
| OptimizerTest | 22 | passes de otimização da IR |
| KofOrmE2ETest | 32 | kof.orm: entity, CRUD, where (+ORM003 validação de coluna tipada, P3-10), **Query DSL `User.query(db){ where; orderBy; limit }` (nível 3, ORM001)**, migrate, unique, MongoDB (3 skips condicional) |
| KofConcurrency2Test | 33 | spawn stmt/expr, selectAny, cancel/cancelled, done/poll, awaitTimeout, channel (+`Channel<T>` como parâmetro de função, 3 targets) |
| IoE2ETest | 16 | kof.io multiplatform (+ `readText`/`size` contratos honestos 02/09) |

| ComponentCoreE2ETest | 14 | kof.ui Component: view/onMount/onDispose |
| CoreRegressionE2ETest | 50 | regressões de uso real (BOM, toInt, ARITH001...) |
| JsonE2ETest | 15 | JSON JVM + Native |
| UiE2ETest | 29 | kof.ui: widgets, estilo, bindings, múltiplas janelas, Table/Ul/Ol/Form/Fieldset/Event (link JVM+Native) |
| AndroidInteropE2ETest | 12 | android: interop Java (external classpath) |
| KofConfigE2ETest | 11 | kof.config: env, arquivo, profiles, precedência, typed, CONF001 |
| KofWebWsE2ETest | 11 | WebSocket RFC 6455: handshake + frame + lifecycle |
| StructuredTestE2ETest | 11 | test "nome" {} nos 3 targets + process.exit |
| BackendParityTest | 16 | paridade JVM/Native/JS |
| KofLogE2ETest | 11 | kof.log JVM: níveis, stderr, off, JSON, correlation |
| KofPatternMatchingTest | 12 | switch case String s / Point(x,y) 3 targets |
| KofWebE2ETest | 12 | stack web nativa (web.app, rotas, JSON, middleware, `app.health` bypass) |
| ExceptionsE2ETest | 9 | try/catch/finally JVM + Native |
| KofDbE2ETest | 24 | kof.db: JDBC, query<T>, transaction, rollback, SQLite nativo, transaction Native (commit+rollback), **DB001 no JS (bridge GraalJS 16/09: roundtrip js + transaction commit/rollback/aninhado byte-parity c/ JVM)**, **DB002 fechado no JS (18/09: `query<T>` tipado bind no guest via `__kof_decode_<T>`, byte-parity c/ JVM)**, **roundtrip SQLite cross riscv64+aarch64 (qemu) 15/09** |
| KofHttpServerTest | 8 | serve engine (sockets reais) |
| KofMediaE2ETest | 16 | kof.media + serveDir: Image/Audio/WAV/Video(MP4), Range 206/416, conteúdo binário (não base64) |
| NativeConfigE2ETest | 8 | kof.config Native (asm): precedência, typed, comentários |
| SpawnE2ETest | 10 | spawn (JVM/Native pthread/JS seq) + join implícito + **lambda c/ captura** + **println antes de spawn** + **`spawn→await→spawn`** (alinhamento de stack no `pthread_create`) |
| IdiomaticE2ETest | 7 | idiomas consolidados (chaining, primary ctor) |
| JsonCompleteE2ETest | 7 | JSON completo: Float/Double, arrays decode (JVM) |
| KofAwaitTest | 8 | spawn/await Handle<T> tipado (JVM) |
| KofWebSseE2ETest | 7 | SSE: sse.send/event/close (sockets reais) |
| KofWsFrameTest | 7 | frame codec RFC 6455: máscara, limites, ping/pong |
| NativeLogE2ETest | 7 | kof.log Native (asm): níveis, stderr, formato civil, off |
| IdiomaticCoreE2ETest | 6 | field initializers, \u810810, listOf<T>() |
| PackagesE2ETest | 12 | pacotes/módulos multi-arquivo (import a.b.C + moduleRoot do LCA, P1-4) |
| AssertE2ETest | 5 | assert JVM + Native |
| FloatingPointGapE2ETest | 5 | FP XMM: encode/decode/arrays (FLT001) |
| KofCacheE2ETest | 5 | suíte E2E/compilação |
| KofHigherOrderTest | 5 | funções de ordem superior (map/filter/reduce) |
| KofIntOverflowNativeTest | 5 | aritmética Int 32 bits no Native |
| KofTimeE2ETest | 12 | time now/sleep/interval (JVM/Native/**JS** — TIME001 fechado 02/09: fila cooperativa bombeada por `time.sleep` no GraalJS) |
| KofWebTlsTest | 5 | TLS/HTTPS: listenSecure + kof.http sobre TLS |
| KofObservabilityTest | 7 | health/metrics/histogram/requestId/traceId+spanId (W3C) (JVM/Native/JS) |
| FunctionSyntaxTest | 12 | formas de declaração de função |
| KofEnumSwitchTest | 4 | switch exaustivo sobre enum + SEM031 |
| KofEnumTest | 4 | enum: values/valueOf/name, SEM030, mapeamento JVM |
| KofHttpE2ETest | 8 | kof.http client (sockets reais, JVM + JS) |
| KofMqE2ETest | 5 | kof.mq publish/subscribe/queue (JVM+Native+JS — MQ001 fechado 01/09) |
| KofWebStreamE2ETest | 4 | WebSocket/SSE end-to-end (persistent-conn) |
| LambdaE2ETest | 17 | lambdas + if-expr |
| RouterE2ETest | 4 | kof.ui Router Fase 7: go/replace/back/forward |
| StdlibE2ETest | 4 | now/readFile/writeFile |
| KofJsBrowserE2ETest | 22 | **KofJS no browser real** (Chrome headless + HTTP + DOM) — kof.ui renderiza de verdade: widgets/Event/canvas/forms (pula se Chrome ausente) |
| KofJsSourceMapTest | 1 | **source map V3 do KofJS** (mappings VLQ reais, nível de linha: função gerada → linha Kof via `KofDebugInfo`; antes era stub `"mappings":""`) |
| ConfigGenTest | 3 | kof config gen: template kof.config do código |
| KofHttpResilienceE2ETest | 3 | kof.http timeout/retry/circuit (JVM + JS paridade) |
| KofMapSetTest | 11 | Map/Set 3 targets (asm próprio no Native) + `Set<T>`/`Map<K,V>` como campo/retorno de classe (JVM: `NoClassDefFoundError` → `HashSet`/`HashMap`; parse de método de classe c/ retorno genérico) + `Map.get` → `V?` (02/09) |
 | KofObservabilityTest | 7 | health/metrics/histogram/requestId/traceId+spanId (W3C) (JVM/Native/JS; Native histogram = gap OBS002) |

| KofSecurityG9Test | 3 | web security: rateLimit/session/apiKey |
| KofValidationTest | 34 | 13 predicados de validação (3 targets) |
| TetrisEasterEggTest | 3 | registro easter egg oculto |
| TuringCompleteE2ETest | 3 | completude de Turing (loops/while/recursão) |
| WindowE2ETest | 3 | Window: size, close-to-exit |
| DebugInfoE2ETest | 2 | SourceFile + LineNumberTable (JVM) |
| IRStatisticsTest | 2 | observer de IR + estatísticas de otimização |
| NativeDebugTest | 1 | harnesses de debug nativo |
| NativeDebugTest2 | 1 | harnesses de debug nativo (2) |
| NativeDebugTest3 | 1 | harnesses de debug nativo (3) |
| NativeDebugTest4 | 1 | harnesses de debug nativo (4) |
| NativeDebugTest5 | 1 | harnesses de debug nativo (5) |
 | NativeDwarfLineInfoTest | 1 | **DWARF nativo**: `.debug_line` real no binário (`objdump --dwarf=decodedline` → arquivo Kof + linha por instrução) |
| NullSafetyE2ETest | 7 | `String?` narrowing JVM + readLine EOF null (02/09) |
  | NativeRiscv64E2ETest | 42 | **riscv64 real (qemu)**: runtime em **asm puro** (raw syscalls, sem C; `as`+`ld` estático) — core (println, var, if/else, aritmética, classes, arrays, List, switch, try/catch, pattern matching, String methods, recursão) + **stdlib 05/09**: JSON (encode/decode incl. escalares int/long/bool/string), HTTP, spawn/await, cache, time.now, mq (queue/pub-sub), Map/Set, higher-order (map/filter/reduce), String.toInt, metrics `# TYPE`, FP (conversões; `println(double)`→FLT001), gates honestos SECN000/SCHED001/TIME001 (DB001 fechado no cross 15/09 + JS 16/09) |
  | NativeAarch64E2ETest | 42 | **aarch64 real (qemu)**: runtime em **asm puro** via tradução riscv→aarch64 (`translateRiscvToAarch64`), raw syscalls — mesmo core + stdlib do riscv64 (tradutor quote-aware p/ strings com `#`) |
 | **Total kof-compiler** | **823** | |
 | kof-script | 8 | KofScriptGlobals / repl / --watch |
 | kof-c-compiler | 5 | KofC C subset → ELF |
 | kof-cli | 4 | LSP references + rename (mock) |
 | **Total** | **840** (+31 skips condicionais: Mongo/MySQL/Postgres, windows/mac; conferir total no CI a cada release) | |
## Consolidação idiomática (guidelines 0.0.5)

Princípio: `intenção → Kof → compiler → backend` — nunca detalhes da
plataforma vazando para a linguagem.

| Guideline | Estado |
|-----------|--------|
| `User(...)` sem `new` (retrocompatível) | ✅ |
| Primary constructor `class User(String name)` | ✅ |
| `this` não obrigatório | ✅ |
| Field initializers aplicados no construtor | ✅ (0.0.5) |
| Resolução de métodos independente da ordem textual | ✅ |
| Escapes `\n` `\t` `\r` `\u810810` | ✅ (0.0.5) |
| `listOf<T>()` vazio preserva o tipo | ✅ (0.0.5) |
| `List<User>` + for-in tipado | ✅ |
| `++`/`--` em campos | ✅ |
| `return` nu em void | ✅ |
| lambdas com capturas | ✅ (sem testes dedicados ainda) |
| args CLI (`main(args)`) | ✅ |
| default parameters | ✅ |
| módulos multi-arquivo | ✅ (resolução unificada: import a.b.C + moduleRoot do LCA) |
| `Process` API | ✅ (`kof.process` + `kof_process_run`) |
| `Shell` API | ✅ (`kof.shell` — `cmd`/`run`/`ok` JVM+JS reais; `pipeline` JVM real, JS/Native `PROC001` honesto; plano `docs/shell-plan.md`, 18/09 `34e4344f`) |

Ver as guidelines completas no todo da sessão.

---

## Kof Debugger (em progresso)

Princípio: o programador depura **código Kof**, nunca o artefato do backend.

| Fase | Estado |
|------|--------|
| 1 — DebugInfo na IR (source location por op) | ✅ |
| 2 — JVM: SourceFile + LineNumberTable + LocalVariableTable | ✅ |
| 3 — `kof-debug` MVP (DAP over stdio + JDWP cru): launch, breakpoints por linha Kof, `stopped`, stack trace com funções/linhas Kof, continue, disconnect | ✅ |
| 4 — Kof Editor (breakpoints, toolbar, variables) | planejado |
| 5 — Native (DWARF) | ✅ parcial 02/09 (line info real: `.debug_line` via `.file`/`.loc` GAS — arquivo Kof + linha por instrução, `objdump --dwarf=decodedline`; `NativeDwarfLineInfoTest`. Variáveis locais/expressões e breakpoints DAP no nativo pendentes) |
| 6 — JS (source maps) | ✅ parcial 01/09 (source map V3 em nível de linha: função gerada → linha Kof, `KofJsSourceMapTest`; colunas/expressões pendentes) |
| 7 — Avançado: locals por frame, stepping, exception breakpoints, avaliação | planejado |

`kof debug app.kf` já abre uma sessão DAP funcional no target JVM:
a sessão compila com metadata de debug, lança o JVM com JDWP e responde a
`initialize` / `launch` / `setBreakpoints` / `configurationDone` /
`continue` / `threads` / `stackTrace` / `disconnect` — o breakpoint
para na linha Kof e o call stack mostra funções e linhas Kof.

Docs: `debugger-architecture.md`, `debugging.md`, `debug-adapter.md`,
`debugging/debugging-jvm.md`, `debugging/debugging-native.md`, `debugging/debugging-js.md`.

---

## Bugs Restantes (reais)

> **Lista completa com reprodução + correção sugerida: `docs/bugs-and-gaps/known-bugs.md`**
> (23+ bugs verificados 02/09 — rodada 3 de usuários: kof-ui reutiliza ID de
> widget após remove, lambda→lambda e lambda-em-lista invocados quebram, PKG005
> rejeita nomes iguais em pacotes diferentes, Native perde construtor de
> classe de outro pacote (undefined reference), ExternalClasspath não resolve
> superclasse fora dos entries).

1. ~~GC automático no Native~~ — ✅ sweep real 03/09 (`kof_gc_sweep` fechado);
   **auto-collect ✅ pousou 19/09** (D1-A, §260 FECHADO — o gatilho em free-list
   exausta agora é SOUND: blanket-spill dos 15 GPRs no `kof_gc_collect_now`,
   gate `kof_spawn_count==0`, flag one-shot; `a904317e`).
   `kof_gc_collect_now` segue disponível pra uso explícito
2. ~~`spawn` no Native: CONC001~~ — ✅ fechado 31/08: pthread_create + trampoline + await/pthread_join + allocator thread-safe (futex) + join implícito + `done`/`poll`/`cancel`/`cancelled`/`selectAny` (cancel cooperativo por TID + selectAny polling 1ms; `SemanticAnalyzer` desambigua `cancel(Handle<T>)→Bool` vs `scheduler.cancel(String)→VOID`)
   - ✅ ~~bug pré-existente SEPARADO: `spawn→await→spawn` SIGSEGV no 2º `pthread_create`~~ — **resolvido 01/09**: mesmo mecanismo do println-antes-do-spawn. O site do `call pthread_create` exige `rsp ≡ 0 (mod 16)` pela ABI SysV; após `pthread_join` (do `await`) a stack chegava 8 bytes desalinhada e a glibc segfaultava em `pthread_attr_copy`. Alinhamento de stack no C call (`andq $-16, %rsp` em `kof_spawn_handle_new`, preservando `r15` + frame do caller). `SpawnE2ETest.nativeSpawnAwaitSpawnDoesNotSegfault` (sem o fix: SIGSEGV 3/3; com: ok 3/3). **Nota**: alinhamento já tinha sido auditado "conforme ABI" e descartado como causa numa sessão anterior — a medição agora crava que o site do `call pthread_create` efetivamente chegava desalinhado nos casos com output/join antes do spawn.
3. ~~JSON de objetos/records no Native: JSN002~~ — ✅ fechado (composição compile-time)
4. ~~JSON Float/Double: JSN001~~ — ✅ fechado 31/08 (parser FP completo: fração+expoente, arrays Double[])
5. ~~JSON decode de arrays~~ — ✅ JSN003 fechado: Int[]/Long[]/Bool[]/String[]; JSN001 fechou Double[]/Float[] (31/08)
6. ~~Lambdas sem captura~~ — ✅ captura implementada (mutable via box `BoxN`; `Lambda0`/`Box0`)
7. ~~Generics `Box<T>` com println nativo~~ — ✅ 25/08 `Box<Int>`/`T` substituído + `kof_int_to_string`
8. ~~`SEM025` falso-positivo em `hashCode/equals/toString`~~ — ✅ `isObjectMethod` em 25/08
9. ~~`await`/join~~ — ✅ nos 3 targets (JVM virtual threads, JS event-loop, Native pthread)
10. ~~`kof fmt`: planned (P5)~~ — ✅ implementado: `kof fmt` via parser real
    (`KofFormatter`), idempotente (2c3e794)
11. ~~Map/Set~~ — ✅ `List.map/filter/reduce` + `Map/Set` JVM/Native/JS (26/08)
12. Pattern matching: ✅ `switch (x) { case String s: ... }` + `case Point(x,y)` em `Parser/Semantic/CompilerDriver` + `Native rbx→rcx` + `JS typeof` (27/08 `Point(x,y)` `JVM:30 Native:30 JS:30` `KofPatternMatchingTest 10/10` + `KofWebE2ETest 9/9`)
13. Null safety `String?`: ✅ básica `String?` `Int?` `?`-check em compile-time `Type.NullableType` `JvmBackend:110` `SemanticAnalyzer:1637` `isAssignable` `var s:String?=null` `s==null` `t="hello"` `jvm: null/hello native: null/hello js: null/hello` (27/08)
14. ~~Módulos multi-arquivo imports perdidos em projetos grandes~~ — ✅ 27/08 `CompilerDriver.java:243` `import a.b.C` file import `+` `a.b` dir import, `largeproj` `a/b/C.kf` `decls=2` `Main.class+a/b/C.class` ok
15. ~~`List.get` native~~ — ✅ verificado `listOf(1,2,3).get(1) → 2` nativo `kof_list_get` bounds OK (caso `List.of` era `listOf`)
16. Web: status codes/headers customizados por handler: ✅ `kof.web.status(201, body)` + `headerSet("X","y")` em `KofWeb.java:248` `kof_web_status` + `JvmWebCoreRuntime.java:20` `KOF_WEB_STATUS` + `JvmRuntimeWebDispatch.java:173` `kof_web_dispatch` `+wired` `kof_web_build` headers `+wired` `status_text 201 Created 202 Accepted` `JVM: 201/hellox 202/value` `KofWebE2ETest 9/9` (27/08)
17. ~~Web: kof.web nativo sem servidor~~ — ✅ fechado 03/09 WEB002 (T1-T4 `NativeWebRuntime.java`): accept loop HTTP/1.1 com parse de request-line, match literal de rotas, dispatch handler via trampolim vtable[0], body() da request; suíte `KofWebNativeE2ETest` 4/4. Pendente: path params `{id}`, `param()/query()/header()`, keep-alive (Connection: close por request), SSE/WS (WEB003/4), TLS (WEB002-secure).
18. ~~MySQL/MariaDB no Native: wire protocol~~ — ✅ 31/08: handshake + scramble SHA-1 + auth-switch + COM_QUERY + resultset (coldefs/rows/EOF) + **binds `?`** — e ✅ 03/09: **prepared statements binários (COM_STMT_PREPARE/EXECUTE)** reais. `kof_db_mysql_prepare`/`kof_db_mysql_exec`/`kof_db_mysql_prep_query` em `NativeDbPrepared.java` (módulo novo, ≤500 linhas): PREPARE (0x16) → OK + drena metadata (params coldefs + EOF, cols coldefs + EOF, capturando name+type), EXECUTE (0x17, null-bitmap + type pairs + valores crus Int 4B/8B, strings lenenc); parse de binary-rows no resultset. `db.execute`/`db.query` com binds usam o binário; fallback COM_QUERY substituição só se PREPARE falhar. Binds com aspas/SQL-injection intactos (sem escape manual). Validado contra MySQL 8.0 real (127.0.0.1:13306), strace confirma 0x16/0x17 na wire. `KofDbE2ETest` 12/12 (+ `nativeMysqlPreparedBinary`). (01/09 reverso; 03/09 resolvido com `NativeDbPrepared.java` ≤500 linhas).
19. ~~`kof_sec_secret_get` no Native~~ — ✅ resolvido: reescrito no padrão linear dos demais; segfault e fragmentos errados eliminados.
20. ~~Ponto flutuante no Native~~ — ✅ FLT001 fechado: FP é XMM real (`vcvtsi2sd`, `mulsd`); dtoa via snprintf alinhado; `kof_string_to_double` parse completo (fração+expoente).
21. ~~idem~~
22. riscv64/aarch64 **core completo** — ✅ **02/09 riscv64 + 03/09 aarch64 reais**: `Target.NATIVE_RISCV64`/`NATIVE_AARCH64` + CLI `native.risc`/`native.arm` + dispatch + **lowering real** (stack machine: riscv64 `sp`/`s11`/`ra`, aarch64 `sp`/`x29`/`x30` via tradução linha-a-linha) + **runtime em asm puro** (raw syscalls `write` 64 / `exit` 93, bump allocator, sem C — binários estáticos via `as`+`ld`; Kof é Kof) + qemu; `NativeRiscv64E2ETest 26/26` + `NativeAarch64E2ETest 26/26` (core: println String/Int, var, if/else, aritmética, classes virtual/fields, arrays, List, switch, try/catch/throw, pattern matching, String methods, recursão). **Paridade stdlib 05/09**: JSON (encode/decode escalares+listas), HTTP, spawn/await (clone+futex), cache, time.now, mq, Map/Set, higher-order, String.toInt, metrics `# TYPE`, FP-conversões — sweeps de paridade com **0 divergências** nos 3 targets. **UPDATE 19/09 — NATIVE002 FECHADO**: DB001 cross (SQLite, 15/09), FP/§107 record+aninhado cross (FLT001 + face (4), 19/09), colunas por-arch + CI cross fechados; recusas honestas restantes por domínio: SECN000 (non-goal R11), OTP001 (decisão §129 TLS registrada, lane nat), JSN004, `kof.ui` — códigos de gap, nunca silenciosos. **Registro de implementação: `docs/native-multiarch.md`** (promovido de `development/` 19/09)
23. ~~`kof.cache` nativo: segfault em `set_ttl` (index `%rax` clobberado) + `get/ttl` (exp em `%rdi` clobberado) + `println(null)` segfault~~ — ✅ 30/08: registradores preservados (`%r14/%r13/%r15`), branch `jle` de expiração corrigido, `kof_print_string` guarda null, `find_slot` sobrescreve chave existente; `KofCacheE2ETest 5/5 x3 targets`
24. ~~`spawn`-statement (fire-and-forget) no Native não era juntado~~ — ✅ 01/09: o `kof_spawn` (stmt) criava a thread mas **não registrava** o handle na lista que `kof_spawn_join_all` percorre; e `join_all` só era emitido no bloco `!endsWithReturn`, que nunca roda para o main (o driver sempre fecha o main com `KofReturnVoid` → `endsWithReturn`). Resultado: o processo saía antes do worker imprimir. Fix: `kof_spawn` agora delega a `kof_spawn_result` (registra o handle) e `join_all` é emitido no **epílogo do return** de main (idempotente — limpa a lista). `SpawnE2ETest 4/4`
25. **`throw <não-String>` / `catch <não-String>` gera bytecode inválido no JVM** (documentado 02/09) — `throw 42` compila mas o `.class` falha no load (`ClassFormatError`, disfarçado de "JavaFX launcher error"). Exceções são Strings; o compilador deveria **rejeitar** `throw <não-String>` em compile-time. Reprodução + arquivos prováveis em **`docs/bugs-and-gaps/known-bugs.md` #1**.
26. **Captura mutável no Native: ler variável boxeada DENTRO da lambda após mutação EXTERNA produz lixo** (documentado 02/09) — `var f = (x) -> x + offset; offset = 20; f(5)` retorna ponteiro/offset no Native (JVM correto). A direção "lambda escreve" funciona. `NativeBackend.resolveFieldOffset` resolve o layout do box contra a classe da lambda (fallback HEADER_SIZE). Reprodução + arquivos em **`docs/bugs-and-gaps/known-bugs.md` #2**.

---

## Próximos Passos (ordem P1→P5)

**P1 — Linguagem (em progresso):**
1. ✅ `Map/Set` + `enum` + `await` + `List.map/filter/reduce` (JVM/Native/JS)
2. ✅ `Pattern matching` — `switch (x) { case String s: ... }` + `case Point(x,y)` `JVM/Native/JS` `30` `10/10`
3. ✅ `Nullability` `String?`/`Int?` + `?`-check `Type.NullableType` `jvm/native/js null/hello` (27/08 básica)
4. ✅ `Módulos multi-arquivo` — `kof build <dir>` com resolução unificada: `import a.b.C` file fix done + `moduleRoot` derivado do **menor ancestral comum** das fontes (3-arg `compileSources` resolve imports cross-diretório sem raiz explícita; `PackagesE2ETest` 6/6)

**P2 — Web completa (próxima listinha):**
5. ✅ Resposta rica `status(201, body)`/`headerSet("X","y")` `JVM` `201 Created 202 Accepted` `X-Custom/X-Test` `KofWebE2ETest 9/9` (27/08) **`Native parcial` (03/09 — server base devolve 200+body; context-fns `status()`/`headerSet()` são gaps `WEB001`)** `JS ✅ 03/09` (GraalJS HttpServer real — context-fns `status`/`headerSet` 16/09)
6. ✅ `kof.cache` `get/set/set(key,v,ttl)/ttl/delete/clear` — ✅ JVM/Native/JS (30/08; fix nativo: clobber de `%rax/%rdi` em `set_ttl/get/ttl` + `println(null)` segfault; `KofCacheE2ETest 5/5 x3 targets`)
7. ✅ `WebSocket` `app.ws("/chat") { }` + `SSE` `sse.send/event/close` — ✅ JVM (30/08; PRs 14-17: persistent-conn/route-kinds, SSE, handshake RFC 6455, frame codec+máscara; `KofWebSseE2ETest 7/7` `KofWebWsE2ETest 11/11` `KofWsFrameTest 7/7`; hardening/limites/contadores 04/09 — `KofWebHardeningTest 6/6`)
8. ✅ `Scheduler` `every(ms) { }`/`cancel(id)` (`at(cron)` = cron real de 5 campos UTC no JVM/JS, gap em compile-time no Native `CRON001` — §274, 17/09) — ✅ JVM (`ScheduledExecutor`, 27/08) + JS (`setInterval`) + **Native SCHED001** (31/08: thread por job — trampoline `usleep` ms→us + `active` flag com futex — `cancel(id)` cooperativo; `KofConcurrency2Test` `schedulerEveryNative/Jvm`)
9. ✅ `kof.http` `timeout`/`retry`/`circuit breaker` — ✅ JVM+JS (30/08; retry repete em exceção+HTTP 5xx, circuito abre após N falhas por 30s com fail-fast, `circuit(0)` recupera; `KofHttpResilienceE2ETest 3/3` JVM+JS) — falta `HTTP/2`

**P3 — Data produção:**
10. ✅ Query DSL tipada (nível 3) — validação **tipada** de coluna em `orm.where<T>`/`where_op`/`count` (`ORM003`) + **sintaxe `User.query(db) { where age > 25; orderBy name desc; limit 10 }`** (01/09): o compilador baixa o bloco para `db.query<T>` (SQL preparada em compile-time a partir do schema da entidade, identificadores quotados, valores como binds `?`; múltiplos `where` → `AND`; colunas inexistentes → `ORM003`, where sem comparação / operador não suportado / >4 binds → `ORM004`; o lowering é agnóstico de target (emite o mesmo `db.queryN` no JVM e no Native) e o E2E roda no JVM (H2) — o workflow de entidade no Native segue `ORM001` — `KofOrmE2ETest` 22)
11. Connection pooling + `kof.db`/`kof.orm` fora do JVM (JS via WASM, Native ORM sobre SQLite)
12. ~~MySQL/MariaDB nativo (handshake+query)~~ — ✅ 31/08 (wire protocol: handshake+scramble+auth-switch+COM_QUERY+resultset); ✅ 03/09 **prepared statements binários** (COM_STMT_PREPARE/EXECUTE + parse de binary-rows; ver #18)

 **P4 — Observabilidade:**
 13. ✅ Métricas `histogram` + endpoint `/metrics` (Prometheus) — ✅ 01/09: `observability.histogram(name, value)` (sum+count) + `observability.metrics()` exportando counters/gauges/histograms em **text exposition format** (JVM + JS + **Native** — `OBS002` fechado: store asm 32B + export via `kof_string_concat`, paridade de conteúdo com o JVM). O app expõe via `app.get("/metrics") { return observability.metrics() }` — sem endpoint especial.
  14. ✅ Health `app.health("/health")` + tracing leve — ✅ 01/09 `app.health(path)` (built-in, responde `{"status":"UP","ready":true,"alive":true}` **antes dos middlewares** — sonda de load balancer não passa por auth); `observability.health()/readiness()/liveness()` (3 targets). **Tracing W3C**: `observability.traceId()` (32 hex) + `observability.spanId()` (16 hex) — IDs puros, sem store, **3 targets** (JVM `SecureRandom`, JS `Math.random`, Native `getrandom`); **spans com timing** `spanStart/spanEnd` (JSON {traceId, spanId, durationMicros}, 3 targets — 01/09) + **lifecycle** `application { onStart/onShutdown }` (desugar → prólogo/epílogo do main, 3 targets — 01/09); `KofObservabilityTest.tracingJvmNativeJs` + `spansWithTiming` + `applicationLifecycle*`. **OpenTelemetry** (export/propagação completa) pendente

 **P5 — DX:**
 15. ✅ `kof fmt` (parser real) + `kof init` + `REPL` — ✅ todos implementados (`Fmt.java`, `init` em `Main.java:211`, `repl` em `CmdScript.java:100`); `fmt` idempotente
  16. ✅ LSP hover/completion/**references**/**rename** + Debugger Native DWARF/JS source maps + VS Code extension — LSP hover/completion ✅ + `textDocument/references` + `textDocument/rename` (word-boundary, single-file; `LspServerTest` 4/4). **JS source maps V3 (nível de linha) ✅ 01/09** (`KofJsSourceMapTest`); Native DWARF + VS Code pendentes

## Roadmap — Estado por Fase (31/08)

### Concluído — Disponível

- Compiler foundation — Lexer, Parser, AST, Type system foundation, Semantic analysis, Kof IR
- JVM backend; Native backend (x86_64); JS backend (GraalJS)
- classes, records, inheritance, interfaces, constructors (sobrecarga), exceptions, generics, collections, string operations, control flow
- `kof build`, `kof run`, `kof serve`, `kof test`, `kof debug` (MVP JVM, DAP sobre stdio), `kof bench` (37 benchmarks + baselines), `kof fmt` (parser real, idempotente)
- `kof.web` — rotas e middleware (JVM); WebSocket RFC 6455 + SSE nativo (JVM, 0.2.6-beta); TLS/HTTPS `web.listenSecure` (JVM); limites/observabilidade `configure`/`stats`
- `kof.db` — JDBC + SQLite nativo; `kof.orm` — entity, CRUD, migrate, MongoDB (JVM)
- `kof.log` nativo; `kof.config` (arquivo > env > profile, tipado, `${key}`, 3 targets); `kof.mq` pub/sub (JVM)
- cliente HTTP (JVM) + JS via `Java HttpClient` interop + **Native 03/09** (`NativeHttpRuntime.java` — HTTP/1.1 asm: parse URL, socket+connect, request parse, status; https throw; DNS↦127.0.0.1 fallback) + retry/circuit (3 targets, 30/08)
- `kof.security` v1 (JVM/Native/JS); web security G9 — rateLimit, sessões, API keys (3 targets)
- `kof.validation` (13 predicados, 3 targets); `kof.observability` (health/métricas/request IDs, 3 targets); `kof.ui` widgets com render KofJS
- `kof.process` execução de processos externos; `process.run`/`process.exit` no JVM+JS; `process.spawn` (stdin/stdout vivos) é **só JVM** — um `PROC001` honesto no Native *e* no JS (o backend JS não liga nenhum `kof_process_spawn`; medido + com gate 18/09, `DomainGapCodesTest`)
- **Concorrência**: `spawn`/`await` JVM (virtual threads) + **Native (pthread — CONC001 fechado 31/08)** + **Android (platform threads — AND001 fechado 31/08, ART sem virtual threads → fallback)** + JS event-loop (CONC003 fechado 03/09); `done`/`poll` não-bloqueantes; `cancel`/`cancelled` cooperativo (JVM + Native por TID); `selectAny` (JVM + Native + JS); `awaitTimeout(r, ms)` — valor no prazo, exceção capturável no estouro (JVM + Native + JS, deadline-poll `kofAwaitTimeout`); `channel<T>()` com `send`/`receive` (JVM LinkedBlockingQueue + Native FIFO futex + JS array); `scheduler.every/cancel` (JVM `ScheduledExecutor` + JS `setInterval` + **Native SCHED001**: thread por job com trampoline `usleep` ms→us + flag `active` futex); `at(cron)` é cron real de 5 campos UTC no JVM/JS (Native `CRON001`; §274) — `KofConcurrency2Test` 15/15, `SpawnE2ETest` 5/5
- **`kof.media` (31/08)** — gestão de arquivos multimídia sem base64 literal: `Image.open/save/saveAs/dataUri` (javax.imageio, PNG/JPEG/GIF/BMP), `Audio.openWav/saveWav` (WAV RIFF PCM 16-bit), `Mic.record` (javax.sound.sampled), `Video.open` (metadados do container MP4/MOV + streaming); `web` `app.serveDir(prefix, dir)` serve ARQUIVO do disco com content-type correto + **Range requests (206/416)** p/ vídeo navegável + proteção de path-traversal; raiz do app via `-Dkof.root` (CLI `run`/`serve`). Gaps: frames de vídeo (sem lib externa), câmera (MEDIA002), sem hardware de mic (MEDIA003), paridade Native/JS (MEDIA001) — `KofMediaE2ETest` 16/16
- **KofAndroid Fase 2 (31/08)** — `--apk` standalone (aapt2/d8/zipalign/apksigner direto do CLI) + release signing `--keystore/--storepass/--keypass/--alias` + label/permissões derivados do programa (`detectAppLabel`/`@Permissions`)
- enum nos 3 targets + switch exaustivo (SEM031); Map/Set nos 3 targets (COL001 fechado)
- otimizador de IR sempre ativo; pattern matching (switch com tipos + destructuring, 3 targets); null safety básica (`String?`, 3 targets); higher-order em coleções (map/filter/reduce, 3 targets); módulos multi-arquivo (`import a.b.C`)
- KofScript — top-level `var`/`val` (`KofScriptGlobals`, repl, `--watch`); KofC compiler — C subset → ELF x86_64 (`kof c`)
- LSP com hover/completion + diagnostics reais; widening de return
- Native GC — mark-sweep 03/09 ✅: `kof_gc_mark` (stack+bss conservador) + `kof_gc_sweep` (limpa morto para free-list; flag bit1 @24) + `kof_gc_collect_now`; **auto-collect na exaustão ✅ 19/09** (D1-A, §260 FECHADO — o gatilho agora é SOUND: blanket-spill dos 15 GPRs + gate `kof_spawn_count==0` + flag one-shot; `a904317e`). `KofGcE2ETest` 3/3
- Ponto flutuante real no Native (FLT001 fechado 31/08 — XMM); JSON objetos/records no Native (JSN002 fechado) + arrays FP (JSN001/003)
- releases multiplataforma (2 jobs: `test-and-bump` → `package-and-release`; linux-x86_64 / macos-arm64 / windows-x86_64)

### Em desenvolvimento

- Standard Library (contratos em estabilização)
- Async / Concurrency: ~~JS async real sobre Promises (CONC003)~~ — ✅ 03/09 (`async`/`await`/`Promise` do GraalJS, coloração async por fixpoint no compilador, `KofJsRunner` drena a fila de microtasks — ver `docs/language-reference/concurrency.md`); ~~Android `AND001`~~ — ✅ 31/08 (platform threads no ART, fallback quando `Thread.startVirtualThread` ausente); ~~bug pré-existente `spawn→await→spawn`~~ — ✅ resolvido 01/09 (alinhamento de stack no `pthread_create` — ver "Bugs Restantes" #2)
- ~~KofAndroid Fase 2~~ — ✅ 31/08 (`--apk` standalone + `--keystore` release signing + label/permissões derivados do programa)
- ~~`kof.media` residual (31/08)~~ — ✅ 31/08: **video** (`Video.open` + metadados do container + streaming) e **Range requests** (206/416) fechados; restam câmera (MEDIA002 — sem lib externa no JVM; **rótulo, não código emitido** — o `KofMedia` emite MEDIA001/MEDIA003) e paridade Native/JS (MEDIA001 — ART sem javax.imageio; app Android roda no WebView KofJS)
- MySQL/MariaDB nativo — **wire protocol ✅ 31/08** (handshake + scramble SHA-1 + auth-switch + COM_QUERY + resultset; binds `?` via substituição client-side; `nativeMysqlWireProtocol`) + **prepared statements binários ✅ 03/09** (COM_STMT_PREPARE/EXECUTE + binary-rows, `NativeDbPrepared` — ver "Bugs Restantes" #18)
- `native.risc` (riscv64) + `native.arm` (aarch64) **core completo (02-03/09)** — plumbing + codegen/runtimes em asm puro + qemu, `NativeRiscv64E2ETest 26/26` + `NativeAarch64E2ETest 26/26` (core + stdlib 05/09: JSON/HTTP/spawn/cache/time/mq/Map/Set/higher-order/toInt/metrics; gates DB001/SECN000/SCHED001/TIME001) — **registro: `docs/native-multiarch.md`** (NATIVE002 FECHADO 19/09, doc promovido)
- Debugger — MVP JVM (DAP sobre stdio) + **JS source maps V3 em nível de linha (01/09)**; Native DWARF pendente
- KofJS — plataforma web no browser (ES Modules via GraalJS já em alpha)
- Gerenciador de pacotes: **dependências transitivas do `kof deps` ✅ 16/09** — `kofdeps.lock` + delegação ao Maven (R9: nunca reimplementar o resolvedor de grafo do Maven); degradação honesta sem `mvn` no PATH; `DepsTransitiveTest` 10/10 incl. E2E com Maven real; **registry MVP ✅ 19/09 (D2-A)**: publish (`kof deploy --publish` = Release + tar.gz + SHA256SUMS) + pull (`owner/repo[@ver]` → asset da release, `SHA256SUMS` verificado antes de instalar, cache `~/.kof/deps/kof/`, `latest` pinna; REG001–004 honestos; `DepsRegistryTest` 6/6)

### Planejado

- especificação completa da linguagem; suíte de conformidade
- full web platform (frontend declarativo + routing/forms/SSR)
- **gRPC no `kof.web`** (31/08) — comunicação RPC gRPC como primeira classe da plataforma web: `app.grpc { service ... }` (stubs a partir de `.proto`, server streaming + unary sobre HTTP/2 no JVM) + client `grpc.call(endpoint, method, msg)`; codegen `.proto` → IR; parity JVM primeiro (ver `docs/development/roadmap.md` § web)
- auto-hospedagem (compilador escrito em Kof)

Roadmap completo: `docs/development/roadmap.md`; execução: `docs/development/roadmap.md` §23 (ex-plan-platform-completion)
