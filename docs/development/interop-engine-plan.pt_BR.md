# X2 — motor oficial `interop` (Python/R) — plano de implementação

**Status:** `EM DESENVOLVIMENTO` — claimado 26/09 pela lane compiler 9092 (no
mesmo commit desta doc). **Autoridade da decisão:** `D-COMPLETE-FIRST` item 2
(`DECISIONS.md` §`D-COMPLETE-FIRST`, mantenedora 26/09) — o PACOTE COMPLETO é a
decisão; stubs, fachadas e "gaps aceitos" não são opções (a regra 11 se aplica a
toda face que chega na superfície da linguagem).

## Landing log (deltas medidos contra a tabela — a tabela fica como o claim; isto é o que pousou)

**Fatia 3 — timeout / cancel / reuso POUSADA 27/09 (código `e3bcd0c3b` + §527 `1cfc0df06`, testes nos mesmos commits; face cross segue no §514).** Deltas medidos ao construir:
1. **O deadline mora no FILHO, não no pai.** Timeout no pai é impossível em Kof puro (o handle do `KofProcess` é um `Long` interno que nunca cruza a superfície Kof, e `readLine` bloqueia) e um kill do pai deixaria o interpretador órfão girando; então a própria linguagem do motor dispara a parada (python `signal.setitimer` SIGALRM→`TimeoutError`, SIGINT→`KeyboardInterrupt`; R `setTimeLimit(elapsed=)` + `tools::signalHandler("SIGINT")`) e o wire carrega status novos: `KOFTIME`→`INTEROP007`, `KOFCANCEL`→`INTEROP008`. Zero chute de exit code, zero superfície no compiler (regra 12).
2. **O wire cresceu para 3 linhas**: linha 1 `KOFPID <pid>` (o pid do próprio filho) alimenta o `cancel()` = `process.run("kill","-2",pid)`; status `KOFOK`/`KOFERR`/`KOFTIME`/`KOFCANCEL`/`""`(morto) na linha 2, payload JSON na linha 3. Protocolo interno — não é contrato externo.
3. `timeout(Int ms)` default **30000** (precedente de bounded wait do §418); `0` = sem timer, declarado (R7). O reuso depois de 007/008 é por construção (replay = filho novo) e travado em teste.
4. **estado de sessão fica como corte declarado** desta fatia (o claim da tabela sobrevive): estado de longa duração no interpretador é contrato diferente do replay one-shot e não muda o que 007/008 significam.
5. **§527 achado no caminho**: o E2E do cancel foi a primeira suéite a fazer await de tasks VOID com `try/catch` em volta em escala — o await de void deixava o `Object` do runtime na pilha da JVM e o verificador matava a classe no LOAD. Corrigido no único site de lowering + `VoidAwaitStackFrameE2ETest` 4/4.

**Prova:** `InteropTimeoutE2ETest` 4/4 (007 com wall-clock limitado + motor reusado depois; JVM≡x86 no 007; 008 via task spawn — escopo declarado JVM; `cancel()` ocioso no-op idempotente); `InteropTimeoutScriptE2ETest` 1/1 (SCRIPT ≡ golden JVM byte a byte); `InteropRE2ETest` +2 gated-R (007/008 contra R real na CI) + geração das 3 linhas travada SEM R em qualquer host (lição `efa8805ab`). O host de recusa espelha `timeout`/`cancel` (`INTEROP005`, nunca método-desconhecido).

**Fatia 2 — records↔JSON POUSADA 26/09 (commit `f73c284cd`, §520; testes no mesmo commit).** Deltas medidos ao construir:
1. **Face congelada como planejado: `String callJson(nomeFn, argsJson)`** — um passthrough cru da linha de resultado JSON; o usuario compoe `json.encode(args)` e `json.decode<R>(wire)`. Zero face nova de compilador (regra 11): a dobra escalar de `decode<Record>` ja existia nos 4 alvos do motor.
2. **Quatro causas raiz entre o plano e a realidade (§520):** o coletor de schema pulava a tabela INTEIRA de todo record com campo String (o teste de campo-aninhado engolia `java.lang.String`); a dobra de decode nativa entregava o corpo SEM aspas do `find_value` ao `decode_string` (contrato de literal com aspas — todo campo String decodificava vazio); o `encode_string` deixava bytes de controle crus (wire JVM ≠ x86, medido); e o interpretador nao tinha ramo tag-4 no `encode_list` (o `KofObj` via um andador de reflexao lendo fields de instancia real → `{}`). As quatro corrigidas na raiz, nao mascaradas.
3. **Wire = separadores COMPACTOS canonicos** (`json.dumps(separators=(",",":")) no prelude): o `": "` do python quebrava a busca de token de todo decode escalar nativo sobre resultado remoto.
4. **Prova:** round-trip arg+resultado JVM≡x86≡JS≡SCRIPT byte a byte com aspa/newline dentro da face String (`InteropPyRecordE2ETest` 3/3 + `InteropPyRecordScriptE2ETest`); falha remota nomeada `INTEROP006`; elemento-colecao aninhado recusado `JSN002` em compile-time (R6). DECODE de `List<Record>` no x86 PERMANECE `JSN004` (declarado, inalterado). RESTA na fatia 2: o motor R (`Rscript` ausente neste host — `assumeTrue` + medir o CI), depois a fatia 3 (cross) com o §514.

**Fatia 2b — motor R `KofR` POUSADO 27/09 (`996777923`), CERTIFICADO na CI no tip `9ec0a4eb9` (Build+Tests completed/success com `Rscript` + `jsonlite` reais no runner: JVM≡x86≡JS executados contra R de verdade; o golden do round-trip de record é byte-idêntico ao do Python — o motor é detalhe, o wire é o contrato). A fatia 2 está agora CONCLUÍDA (Py + records + R), 4/4 alvos certificados. Deltas medidos:**
1. **O canal de pipe não sobreviveu ao transplante para o R** — duas causas que só a CI-com-R conseguiu expor (o dev-host sem R pulava-as): (a) o `kof_process_spawn` do JVM redireciona o stdin do filho para `/dev/null` (`JvmRuntimeCore.java:304`) — `h.write` é no-op lá (o Py nunca morreu nisso porque a spec dele viaja por argv); (b) o `Rscript -e <expr>` trata argumento posicional como FICHEIRO a sourcear, nunca como dado. Canal final, congelado no host: a spec viaja **embutida na expressão `-e` como literal de string R escapado** (`kofREscape`: backslash→backslash-backslash primeiro, depois aspas→backslash-aspas — a ordem provada pelo golden), com `exprGenerationIsVerifiableWithoutR` travando a construção da expressão em QUALQUER host, com ou sem R.
2. **Source reaplicada por `eval(parse(text=s$source))` dentro da expressão** — modelo de replay (sessão = source) idêntico ao Py; mesma face `callJson`, mesmos `INTEROP004/005/006`.
3. **Lição de escape do SCRIPT (a CI pegou o que o local pulou):** o text block Java do programa do teste comeu uma camada de escape (`PARSE043`); corrigido na fonte com fonte única do programa + a guarda que nunca pula `scriptProgramParsesWithoutR` — o programa tem de PARSEAR mesmo onde R não existe.

**Fatia 1 POUSADA 26/09 (testes no mesmo commit).** Deltas medidos ao construir:
1. **Modelo = replay sem estado sobre `-c`, não um `python -` de vida longa.**
   Medido: `python3 -` num pipe NÃO executa nada antes do EOF do stdin, e o handle
   F10 não tem `closeStdin` — o RPC-por-stdin estava fisicamente morto. O motor
   roda o programa inteiro por chamada: `process.spawn("python3", "-u", "-c",
   fonte + prelude, spec)` — a sessão É a fonte (definições persistem entre
   chamadas; mutações de globals não — contrato declarado, não stub escondido).
   Uma face de sessão viva exige tipo de handle nomeado = superfície nova de
   compilador = regra 6, registrada como fatia futura, não improvisada.
2. **Alvos = {JVM, NATIVE x86, JS, SCRIPT}.** O SCRIPT era esperado recusar e roda
   o motor REAL — o interpretador resolve `kof_process_spawn` por reflexão no
   MESMO `KofRuntime` gerado (paridade de construção, medido:
   `InteropPyScriptE2ETest` golden ≡ JVM). riscv64/aarch64 recusam `INTEROP005`
   porque o asm cross nunca recebeu `kof_json_encode_double`/`encode_long` (JSN001
   fechou só x86 — catalogado §514, ABERTO, dona lane native); ANDROID/MCU/RISCV32
   recusam até a face de processo ser EXECUTADA e provada (R7).
3. **§513 achado e corrigido na raiz no mesmo commit:** o arg `List<Double>` do
   motor expôs que o `json.encode` colapsava slots crus Double/Long em `encode_int`
   (walkers x86 de lista+map) e que o `List<Bool>` do JVM castava
   `Boolean`→`Integer`. Prova: `JsonNativeEncodeFpE2ETest` (oráculo JVM + JVM≡x86,
   falharia no código pré-fix). Listas de `Float` ficam tag-0 — catalogado no §513.
4. **Superfície como pousou (gate regra 11):** `var py = KofPy(fonte)` +
   `py.callInt("sq", listOf(5))` / `callDouble` / `callBool` / `callString` — o
   tipo do RESULTADO é o nome do método, os args são uma lista Kof tipada
   homogênea; sem argv manual, sem JSON manual, sem loops de leitura no código do
   usuário. Args/retornos record = fatia 2 (sinergia dobra X6 — caminhos
   `json.decode<Record>` escalares já existem no lado JVM, medido em
   `JsonCompleteE2ETest`).

**Contrato (verbatim da decisão):** marshalling bidirecional tipado
(`Int`/`Double`/`Bool`/`String`/`List`/`Map`/`record` ↔ JSON), gerenciamento real
de processo (spawn, stdin/stdout, timeout, exit, cancel), estado de sessão,
erros nomeados `INTEROP00x`, E2E por alvo, corpus sincronizado. Nasce
`experimental` (R5).

## Design KOF-first (medido 26/09, não memória)

- **Zero namespaces novos.** O namespace `interop` + HOST_IMPORT `kof.interop`
  já existem (X6, `D-INTEROP-REFLECT`; linha 68 do ledger
  `scripts/stdlib_boundary.txt`, layer `interop experimental`) — o motor
  ESTENDE isso. O gate `check_stdlib_boundary.sh` segue verde sem linha nova.
- **Motor escrito em Kof, não em Java** (precedente `D-KOF-AS-CLOUD`/
  `D-BOOTSTRAP` do `interop-host.kf`): o loop RPC, o marshalling e o estado de
  sessão vivem num host injetado pelo compilador
  (`dev/kof/interop-py-host.kf`), composto da plataforma que já existe (regra
  de ferro 2): `kof.process` (JVM `ProcessBuilder`, x86 `RuntimeProcess`
  pipe2/execvp, cross portas `Rt` — medidas vivas em todas as faces
  executáveis) + `kof.json` (encode/decode, mapas tagueados, determinismo de
  chaves ordenadas §106).
- **Codecs são fronteira, nunca fundação** (princípio X6 reusado): o
  marshalling de escalar/List/Map/record passa por
  `json.encode`/`json.decode` — sem parser manual (tabela de anti-padrões).
- **Diagnósticos nomeados (R6):** códigos livres medidos 26/09 —
  `INTEROP001`/`002` (schema X6) e `INTEROP003` (§510 estático externo
  não-JVM) estão tomados; o motor reclama **`INTEROP004`** (interpretador não
  encontrado no spawn — erro de runtime honesto, nunca resultado vazio
  silencioso), **`INTEROP005`** (alvo/face sem backing — recusa em
  compile-time, o padrão de gate do §510), **`INTEROP006`** (falha remota —
  erro do motor / traceback exposto, nomeado). Cada código ganha linha na
  matriz de paridade + pino no `DomainGapCodesTest` quando pousa.
- **Superfície (gate regra 11 antes de pousar):** o usuário escreve
  intenção — `py.call("area", r)` — não mecanismo (sem strings de argv, sem
  JSON manual, sem loops de reader em código do usuário). Os nomes exatos são
  congelados na fatia 1 junto da linha do training; a Lei da Simplicidade
  vale no commit da superfície.

## RECON da fatia 2 — FEITO 26/09 (sonda compilada+executada nos tres alvos de compilacao)

Fatos medidos (oraculo JVM `[{"x":1,"y":2}]\n5\n6`):
- `json.encode(listOf(record))`: JVM ✓, JS ✓, **x86 QUEBRADO — despeja o ponteiro
  cru do objeto** (§516, catalogado ABERTO, dona = esta lane/fatia 2). O SCRIPT
  interpreta o mesmo runtime gerado (paridade por construcao — remedir no E2E).
- `json.decode<Record>` ESCALAR: funciona em todo lugar PORQUE o compilador ja
  dobra em compile-time (JSN002 em `ExpressionJsonCallLowerer`: por campo
  `kof_json_find_value` + decoders escalares + construtor canonico). O
  `json.encode(record)` escalar tambem e dobrou (concat de string + leitura de campo).
- A maquina do conserto do §516 ja existe sem uso: `NativeJsonSchema` emite
  `.Lsch_<Name>` (token, offset, typeCode, aux) + `.Lsch_registry` +
  `kof_json_schema_find` — ZERO consumidores hoje.

Superficie congelada da fatia 2 (gate regra 11 — nenhuma superficie nova de compilador):
- Args `List<record>`: o host mantem `json.encode(args)`; exige o §516 consertado
  primeiro (tag 4 + walker `kof_json_encode_object` ~40 linhas, mesmo patch no
  walker de map).
- Retorno `record`: face `String KofPy.callJson(fn, args)` (a linha crua do payload —
  INTEROP004/006 identicos) + o usuario compoe o idiom JA EXISTENTE
  `json.decode<MyRecord>(payload)` (uma linha, Kof puro, dobra por-campo = zero
  reflexao em runtime, saida identica nos 4 alvos). Um `callRecord<R>` tipado dentro
  do host e impossivel sem face nova de compilador (o type-var apagado nao seleciona
  a dobra) = regra 6 — NAO improvisar.
- Plano de prova: `JsonNativeRecordListE2ETest` (oraculo JVM + JVM≡x86, RED pre-fix
  no §516) + `InteropPyRecordE2ETest` (round-trip record-arg + record-resultado via
  callJson+decode<record>, goldens medidos JVM/x86/JS/SCRIPT; edges Q3: campo String
  com aspas/\n, campos Bool/Double, args lista-vazia, INTEROP006 com funcao que
  retorna record).

## Fatias (cada uma um corte vertical COMPLETO com prova — nunca fachada)

| # | Fatia | Escopo da entrega COMPLETA | Prova |
|---|---|---|---|
| 1 | **Motor Python no JVM** | host `.kf` + wiring typer/lowerer; sessão = `python3 -u` de longa vida sobre `kof.process`; args tipados → linha JSON no **stdin**, resultado tipado ← linha JSON no stdout (RECON primeiro: medir a superfície de escrita no stdin do `kof.process` — se a API Kof não tiver, a fatia 1 estende o `kof.process` honestamente para TODOS os alvos dele, é trabalho de plataforma, não de interop); `INTEROP004` quando falta o interpretador; `INTEROP006` nomeia a falha remota | E2E com `assumeTrue(python3 presente)` (precedente node/qemu); round-trip por tipo incl. record; idempotência; arestas de erro |
| 2 | **Motor R** | a mesma máquina do host sobre `Rscript` (host não tem → guarda `assumeTrue`; disponibilidade no CI ubuntu medida na fatia) | E2E + binding da fonte via `interop.schema` existente (sinergia X6, zero reflexão nova) |
| 3 | **timeout / cancel / estado de sessão** | ✅ **POUSADA 27/09** (timeout/cancel/reuso; `INTEROP007`/`INTEROP008`, deadline no motor — ver o landing log); estado de sessão = corte declarado; face cross espera o §514 | arestas E2E pousadas: hang limitado, cancel nomeado, reuso travado |
| 4 | **Faces Native / JS / Script** | MEDIR por face: porte real onde a plataforma dá backing, senão recusa `INTEROP005` em compile-time (JVM-first é R7, e o §510 provou que a face de recusa honesta é entrega completa do alvo) | goldens por alvo ou pinos de recusa |
| 5 | **Corpus + DoD de promoção** | `training/idioms/interop.md` EN+PT, seção do `learn/`, linhas da matriz de paridade, `ecosystem-coverage`, CHANGELOG | revisão de par do registro docs |

## Relacao com o ecossistema de conectores (`future/kof-connector-ecosystem-plan.md`) — "ficar de olho", nao promocao (maintainer 26/09)

- O plano de conectores **NAO e puxado para development** — abrir aquele front precisa da
  decisao explicita `D-CONNECTORS` (regra 6 + tres-estados). O que se registra aqui e a
  RELACAO, para nenhuma lane tratar o Core futuro como rewrite do que ja landed.
- Os motores X2 SAO a **forma processo** dos conectores §5.5 (Python) / §5.6 (R) do
  catalogo — o inventario do proprio plano de conectores (§2, adicionado 26/09) lista
  `KofPy`/`KofR` como substrato landed. O non-goal do catalogo ("processo+stdout nao e o
  *modelo*", §0.2) e respeitado como escrito: `KofPy`/`KofR` sao UM connector cada, nunca o
  modelo inteiro; a rota embedding/CPython-C-API/R-C-API segue nao-implementada e NAO e do X2.
- Se `D-CONNECTORS` abrir um dia: o protocolo wire + as faces do X2 seguem como o adaptador
  do connector de processo (o Core consome, nunca reescreve por principio); codigos
  `INTEROP00x`, goldens e testes permanecem.
- Fatias 3–5 (timeout/cancel/sessao, cross §514, corpus/DoD) NAO sao afetadas pela relacao —
  seguem a entrega completa do item 2 (D-COMPLETE-FIRST).

**Sem tocar:** arquivos IN PROGRESS de outras lanes (memory/, media cross);
PR #619 (regra 10).
