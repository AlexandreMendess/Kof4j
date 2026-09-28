[English](memory-safety-plan.md) | [Português](memory-safety-plan.pt_BR.md)

# Memory safety — ownership, lifetime, borrowing, aliasing (D-MEMORY-SAFETY)

last: phase-4-slice-4.2-pinned (spawn-capture-return 4-alvos, #659)
doing: memory-safety
next: phase-4.3-faces-callback (medir antes de prometer)
location: memory-safety-plan
state: active
intent: compiler-provable-memory-safety
constraint:
  - core-edits-wait-for-queue
  - no-foreign-borrow-checker
decision: D-MEMORY-SAFETY

Critério de sucesso: o compilador consegue provar que um programa não pode produzir uma classe de erro (use-after-free, double-free, referência pendente, escape de lifetime inválido, mutable aliasing unsafe, null inesperado, data race acidental) — ou que ela vive atrás de uma fronteira explicitamente nomeada. NÃO é uma feature chamada `ownership`.

## Restrições duras

- O Kof já tem null safety — nunca reinventar/substituir/duplicar; só estudar nullability × ownership × lifetime × borrowing.
- Kof-first: nenhuma suposição de que o Kof funciona como Rust, C++, Java, Kotlin, Swift ou Zig (rules 8/10).
- Arquitetura antes de código; a implementação espera a fila atual.
- Lei da Simplicidade (rule 11): garantias fortes sem corrente infinita de anotações de lifetime.
- Cross-target por construção (JVM/Native/JS/WASM mesma semântica; GC nunca desculpa divergência; a FFI define o dono em cada travessia).
- Diagnósticos e testes fazem parte da feature (válido/inválido/diagnóstico-esperado/regressão/por-backend).
- Proibido: copiar o borrow checker do Rust, inventar sintaxe (`let`/`const`/marcadores de move estrangeiros), reescrever a null safety, refactor big-bang, ownership de um único backend, esconder ownership no runtime.

## Fases

| Fase | Entregável | Estado |
|---|---|---|
| 0 Investigação | `docs/spec/memory-safety-investigation.md` (EN+PT): estado, riscos, varredura do § ledger, proposta, alternativas, compatibilidade, plano incremental; 20 perguntas respondidas com file:line | fechada 25/09 |
| 1 Especificação | `docs/spec/memory-safety.md` (EN+PT): Ownership/Lifetime/Borrowing/Aliasing/Mutability/Move/Copy/Clone/Drop/Escape/Closure Capture/Concurrency/FFI/Unsafe Boundaries, cada um classificado permitido/proibido/requer-sync/compile-time/runtime/dependente-de-tipo | fechada 25/09 (opção A da mantenedora) |
| 2 Infraestrutura do compilador | `OwnerKind`/`MemRule`/`ManagedResource`/`CaptureMode`/`MoveDetector`+`MoveTransfer` em `dev.kof.compiler.memory`; `MemoryModelTest` 8/8; zero mudança de comportamento | fechada 26/09 (`9bcddfe90`, fila exausta) |
| 3 Primeiras garantias | use-after-move; dangling; escapes inválidos; mutable aliasing; dupla ownership/destruição | em curso (fatias abaixo) |
| 4 Closures & async | captura de closure; callbacks; async/futures; iteradores/geradores | 4.1+4.2 pousadas 28/09 (#658/#659); face callbacks pendente (4.3) |
| 5 Native & FFI | ponteiros/alocação/C ABI; tabela de ownership Kof↔C↔Rust↔JVM↔Python | pendente |
| 6 JVM / JS / WASM | mesma semântica em todos os backends | pendente |

## Fatias da Fase 3 (superfície de emissão = o que existe na superfície do usuário)

| Fatia | Face | Estado |
|---|---|---|
| 1 Retilínea | O-01/`MEM001` dupla reivindicação + O-02/`MEM002` uso-após-claim via alias — `OwnershipPass` ligado em `StatementAnalyzer.analyzeBody` (frontend compartilhado = mesma análise nos 4 alvos); `MemorySafetyE2ETest` | pousada 26/09 (`c23dcb30d`) |
| 2 Cruzamento de fluxo | claim/leitura condicionais (if/while/try/switch) — snapshot herdado pelo braço, resultado NÃO propaga (anti-falso-positivo por construção); `BlockStmt` incondicional propaga | pousada 26/09 |
| 3 Escape/dangling | L-04/`MEM013` (captura estende vida) + faces de dangling da spec §3 | pousada 26/09 |
| 4 Aliasing mutável | B-05/`MEM022` — mutação mudadora de tamanho (`add`/`remove`/`clear`/`addAll`) da coleção iterada no próprio `for-in`; WARNING + zero-FP por construção (`for-in` é loop por índice que relê `size` a cada volta) | pousada 26/09 |
| 5 Containers & não fechados | O-03/`MEM003` (release de container — RESOLVIDO: `D-MEMORY-CLEAR` opção a; garantia de runtime, sem face de compilação) + L-05/`MEM014` conexão db (web pousou 3.1b; `kof.io` não tem handle de arquivo com close) | db `MEM014` pousada 26/09; O-03 CORRIGIDO 27/09 (`NativeX86MemClearTest` + `NativeRiscvMemClearTest` + `MemoryClearE2ETest` 4 alvos) |
| 3.2 | MEM021 aliasing mutável em spawn — `spawn` capturando objeto mutável que o pai também muta, sem `await`/`join_all` entre (spec B-04/C-03); ERRO no padrão claro de corrida, silêncio fora dele; zero falso-positivo exigido | landada 28/09 (portada para `lab`: `MemorySafetyE2ETest` 40/40 local, cert de CI pendente) |

- MEM005 (ownership FFI) JÁ SATISFEITA na fronteira: o Native recusa externs record/array/out-buffer com `FFI001` na linha da declaração; JVM/JS fazem copy-back; retornos String são copiados na fronteira. A fatia 3.3 documenta a face compile de O-05 — nenhum diagnóstico duplicado inventado (regra 11).
- Corrigido contra a spec: ler o PRÓPRIO claimer após o seu close é L-02/`MEM011` (RUNTIME, native sem GC), não MEM001 — corrigido contra a spec §3 linhas 84/87.
- Evidência: `ResourceLeakE2ETest` 5/5 (web), `DbResourceLeakE2ETest` 4/4 (db), `MemorySafetyE2ETest` 29/29 (9 faces novas). O `kof.io` não tem handle com close (stateless por caminho) — ausência, não gap.
- Reordenação 26/09: a fatia 4 estava especificada como B-03/`MEM020` + B-04/`MEM021`, ambas movidas (nomeadas, não gaps aceitos): B-03 exige a superfície de borrow FFI (fase 5); B-04 exige a captura closure/async (fase 4).

## Fatias da Fase 4

| Fatia | Face | Estado |
|---|---|---|
| 4.1 | paridade de captura B-06 — todas as faces de captura (basica, mutacao-externa `15/25`, lambda-escreve, lambda-que-retorna-lambda, triple-nested) travadas nos 4 alvos: JVM/Native como antes + Script via `KofInterpreter` + JS via `KofJsRunner`, golden identico (`LambdaE2ETest` 36/36; #658) | pousada 28/09 — mudanca de comportamento ZERO: a paridade ja era verdadeira (medida, nao assumida); o pin e o produto, precedente O-03/`D-MEMORY-CLEAR` ("garantia provada por teste") |
| 4.2 | paridade de captura async/futures — face do §46 `var h = spawn { return n * 2 }; await h` (COM captura) travada nos 4 alvos (`SpawnE2ETest.jvm|script|js...` + os pins Native preexistentes), golden `42` — a alegacao "interpreter/JVM/JS → 42" vivia so na prosa do §46 ate ser medida; a passada do verifier independente entao pegou que TODAS essas faces so LEEM a captura (captura read-only e rebaixada SEM caixa: JVM `LambdaTask0.<init>(I)`, por valor), entao `SpawnE2ETest.*AwaitMutatedCapture` (golden `44`, 4 alvos) entrou na matriz — e a SEGUNDA passada do verifier pegou que 44 sozinho ainda passaria num snapshot por valor (o pai nunca toca `n`), entao `*VisibleToParent` (`44/22`, 4 alvos) trava a visibilidade filho→pai da caixa via join — `SpawnE2ETest` 21/21; o probe RACE dele (pai reatribuindo escalar capturado apos o spawn, sem await entre) compila silencioso: MUTATORS de MEM021 sao de OBJETO (add/remove/clear/addAll) — escopo da corrida escalar escalado a mantenedora (regra 6), jamais decidido aqui | pousada 28/09 (#659) — mudanca de comportamento zero (a paridade ja era verdadeira); prosa historica do §46 corrigida EN/PT |

- `MEM023` NAO tem face de compilacao hoje: o lowering caixa toda captura mutada por construcao (`mutatedCapturedNames` → `CapturedVarBox`, `StatementLowererLocalBoxing.java:37`), entao a forma proibida (captura mutada sem caixa escapando) e inconstruivel — garantia provada pelas baterias 4.1/4.2, precedente O-03/`D-MEMORY-CLEAR` (nenhum diagnostico inventado, regra 11). Registrado na nota da spec §3.2.
- 4.3 (pendente, medir-antes): faces de callback (lambdas passadas a stdlib/APIs estilo `job`); "iteradores/geradores" da linha da fase recebeu seu veredito em 28/09: NAO existe superficie de gerador (nenhum token `yield` no lexer Kof — `for-in`/colecoes sao a face iteravel) → ausencia, nao gap.

## Pedidos de decisão (regra 6)

- **O-03/`MEM003` release de container** — RESOLVIDO 27/09 por `D-MEMORY-CLEAR` (opção a): `clear()` DEVE anular todo slot antes de encolher, então a garantia é propriedade de runtime provada por teste, nunca face de compilação; nenhum diagnóstico `MEM003` é criado. Implementado nos runtimes nativos (`RuntimeList`/`RuntimeMap` x86, `NativeRiscvAsmRtB0`/`NativeRiscvAsmMapset0` cross); JVM (`ArrayList`/`HashSet`/`HashMap.clear`) e JS (`length=0`/`clear()`) já soltam as referências. Prova: `NativeX86MemClearTest` + `NativeRiscvMemClearTest` leem os slots do backing da memória após o `clear()` (lista via `add` real, map com pares key/val plantados) — RED 1/1+2/2 pré-fix, GREEN pós-fix; `MemoryClearE2ETest` trava o comportamento uniforme esvaziado+reutilizável em JVM/Script/JS/Native + riscv64/aarch64.
- **O-02 padrão de move** resolvido 26/09 por `D-COMPLETE-FIRST`: `var a = b; b = null` colidia com N-02/SEM048 (literais null vedados) → re-expressar O-02 sem literal null, como pacote completo (passe + emissão + prova por alvo).
- **B-04/`MEM021` reatribuição escalar (impossibilidade NÃO alcançada — mantenedora, regra 6; #660)** — o pai reatribuindo o MESMO escalar capturado após o `spawn`, sem `await`/`join_all` entre, não é pego por `MEM021` nem tem código de gap: `SpawnCaptureScanner.MUTATORS` lista só mutadores de OBJETO (`add`/`remove`/`clear`/`addAll`), e a spec B-04 fala de "objeto mutável" — o caso escalar não está nem proibido nem registrado. Medido (triagem, reproduzido no `lab`): `var n = 21; var h = spawn { n = n + 1; return n * 2 }; n = 100; println(await h); println(n)` compila SEM diagnóstico em JVM/JS/Script e corre (`202` depois `101`, exit 0). Decisão necessária: (A) estender `MEM021` ao caminho escalar; (B) registrar como corrida silenciosa aceita na spec B-04 com nota explícita + código `XXX00x` honesto; (C) outra. Bloqueado na mantenedora — nenhuma edição de agente até a decisão.

## DoD do pacote

Passe + wiring + `MemorySafetyE2ETest` por alvo (JVM/Script/JS/Native mesmas fontes, mesmos diagnósticos) + nota de corpus em `training/idioms/concurrency.md`+`interop.md` quando a emissão pousar; cada fatia pousa completa ou não pousa (`D-COMPLETE-FIRST`).

## Definition of done (frente inteira)

As 12 perguntas do §27 respondidas na spec, a lista de classes de bugs impossíveis explícita, e a implementação casando com a spec com a matriz de segurança do §22 verde por backend — "estruturas chamadas `Ownership`/`Borrow`/`Lifetime`" NÃO é done.

Relacionamentos: `DECISIONS.md` §`D-MEMORY-SAFETY`; `PARITY-GAPS.md` (o bloqueador atrás do qual esta frente fila); regra 6 (semântica congelada — qualquer semântica de ownership que mude ordem de avaliação ou contratos de operador é decisão da mantenedora, nunca edição de agente).
