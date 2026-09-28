[English](memory-safety-plan.md) | [Português](memory-safety-plan.pt_BR.md)

# Memory safety — ownership, lifetime, borrowing, aliasing (D-MEMORY-SAFETY)

last: phase-3-slice-3.2
doing: phase-4-promotion-required
next: nada-na-fila (superfície de emissão da fase 3 completa)
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
| 3 Primeiras garantias | use-after-move; dangling; escapes inválidos; mutable aliasing; dupla ownership/destruição | **completa 28/09** (todas as faces de emissão abaixo pousadas; faces de runtime = fases seguintes) |
| 4 Closures & async | captura de closure; callbacks; async/futures; iteradores/geradores | pendente |
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
| 3.2 | MEM021 aliasing mutável em spawn — `spawn` capturando objeto mutável que o pai também muta, sem `await`/`join_all` entre (spec B-04/C-03); ERROR na corrida clara, silencioso fora dela; zero falso-positivo exigido | **pousada 28/09** (`OwnershipPass` faces B-04/C-03 + split `SpawnCaptureScanner`; `MemorySafetyE2ETest` 40/40 — 5 corridas claras RED-first + 7 silenciosos; evidência zero-FP = varredura balanceada do corpus: 0 mutadores em corpo de spawn pré-existentes) |

- MEM005 (ownership FFI) JÁ SATISFEITA na fronteira: o Native recusa externs record/array/out-buffer com `FFI001` na linha da declaração; JVM/JS fazem copy-back; retornos String são copiados na fronteira. A fatia 3.3 documenta a face compile de O-05 — nenhum diagnóstico duplicado inventado (regra 11).
- Corrigido contra a spec: ler o PRÓPRIO claimer após o seu close é L-02/`MEM011` (RUNTIME, native sem GC), não MEM001 — corrigido contra a spec §3 linhas 84/87.
- Evidência: `ResourceLeakE2ETest` 5/5 (web), `DbResourceLeakE2ETest` 4/4 (db), `MemorySafetyE2ETest` 29/29 (9 faces novas). O `kof.io` não tem handle com close (stateless por caminho) — ausência, não gap.
- Reordenação 26/09: a fatia 4 estava especificada como B-03/`MEM020` + B-04/`MEM021`, ambas movidas (nomeadas, não gaps aceitos): B-03 exige a superfície de borrow FFI (fase 5); B-04 exige a captura closure/async (fase 4).

## Pedidos de decisão (regra 6)

- **O-03/`MEM003` release de container** — RESOLVIDO 27/09 por `D-MEMORY-CLEAR` (opção a): `clear()` DEVE anular todo slot antes de encolher, então a garantia é propriedade de runtime provada por teste, nunca face de compilação; nenhum diagnóstico `MEM003` é criado. Implementado nos runtimes nativos (`RuntimeList`/`RuntimeMap` x86, `NativeRiscvAsmRtB0`/`NativeRiscvAsmMapset0` cross); JVM (`ArrayList`/`HashSet`/`HashMap.clear`) e JS (`length=0`/`clear()`) já soltam as referências. Prova: `NativeX86MemClearTest` + `NativeRiscvMemClearTest` leem os slots do backing da memória após o `clear()` (lista via `add` real, map com pares key/val plantados) — RED 1/1+2/2 pré-fix, GREEN pós-fix; `MemoryClearE2ETest` trava o comportamento uniforme esvaziado+reutilizável em JVM/Script/JS/Native + riscv64/aarch64.
- **O-02 padrão de move** resolvido 26/09 por `D-COMPLETE-FIRST`: `var a = b; b = null` colidia com N-02/SEM048 (literais null vedados) → re-expressar O-02 sem literal null, como pacote completo (passe + emissão + prova por alvo).

## DoD do pacote

Passe + wiring + `MemorySafetyE2ETest` por alvo (JVM/Script/JS/Native mesmas fontes, mesmos diagnósticos) + nota de corpus em `training/idioms/concurrency.md`+`interop.md` quando a emissão pousar; cada fatia pousa completa ou não pousa (`D-COMPLETE-FIRST`).

## Definition of done (frente inteira)

As 12 perguntas do §27 respondidas na spec, a lista de classes de bugs impossíveis explícita, e a implementação casando com a spec com a matriz de segurança do §22 verde por backend — "estruturas chamadas `Ownership`/`Borrow`/`Lifetime`" NÃO é done.

Relacionamentos: `DECISIONS.md` §`D-MEMORY-SAFETY`; `PARITY-GAPS.md` (o bloqueador atrás do qual esta frente fila); regra 6 (semântica congelada — qualquer semântica de ownership que mude ordem de avaliação ou contratos de operador é decisão da mantenedora, nunca edição de agente).
