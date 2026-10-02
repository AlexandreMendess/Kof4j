last: fatia 4a POUSADA 01/10 (metade JS) — `JsRuntimeUiNet` exporta os dez wrappers `kofNet*` sobre o host `KofJsNetBridge` (GraalJS `kof_platform`, mesmo `java.net` do runtime JVM — paridade por construção); `KofJsRunner.exposePlatform` instala a ponte ao lado de `KofJsProcessBridge`. `KofNet.supportedOn`/`socketRuntimeReady` agora aceitam `Target.JS`; riscv64/aarch64 ainda recusam NET002 (fatia 4b, toolchain cross ausente no host). Hostless/navegador mantém o erro honesto do Proxy `kof_platform` (R7), provado para `net`. Prova NetJsE2ETest 6/6 + KofJsHostlessRuntimeTest 3/3
doing: fatia-4b-portes-cross
next: fatia 4b — portar o front x86 para asm riscv64 + translator aarch64 (ou NET002 honesto até portar; bloqueada pela ausência de binutils cross no host — goldens autoritativos no CI); fatia 5 = face Script + decisão do navegador em compile-time + corpus `training/idioms/net.md` (+PT)
location: docs/development
state: em-desenvolvimento

intent: frente unificada kof.net TCP+UDP (D-KOF-NET)

# `net` do Kof — frente de rede unificada (TCP + UDP)

## 0. Contrato (congelado por `D-KOF-NET`, votos regra-6 da mantenedora 01/10)

UM namespace `kof.net` (ao lado dos acessores URI que já vivem nele). Verbos bloqueantes; `spawn` é a única concorrência. Payload `Byte[]` nas duas mãos, nos dois transportes. UDP endereçado por String `"host:port"`; limite de datagrama 64 KiB com recusa `NET00x`; unicast-only no v1; política = modelo `app.security` existente; troca de chaves (`SECN005`) é decisão de superfície SEPARADA.

```
// TCP (conexão)
net.listen(port: Int) -> Listener
listener.accept() -> Conn              // bloqueia
net.connect(host: String, port: Int) -> Conn
conn.send(data: Byte[]) -> Int         // bytes escritos
conn.receive(maxBytes: Int) -> Byte[]  // bloqueia até um read; len-0 => fechado
conn.close() ; listener.close()
// UDP (datagrama)
net.bind(port: Int) -> Endpoint
endpoint.sendTo(addr: String, data: Byte[]) -> Int
endpoint.receive(maxBytes: Int) -> Byte[]   // um datagrama; origem guardada para peer()
endpoint.peer() -> String                   // "host:port" do último receive, "" antes
endpoint.close()
```

**Superfície como POUSADA (fatias 1–3)**, que é o contrato congelado acima: `receive` num endpoint devolve `Byte[]` (um datagrama) e a origem é lida por `peer()` — a sonda da fatia 1 resolveu a questão aberta do record `Datagram` pela forma escalar (sem record novo; `peer()` espelha o precedente de acessor de handle de `db`/`orm`). `sendTo` é o verbo de envio UDP. O tipo do receiver (Listener/Conn/Endpoint) é imposto em compile-time, então `send` não pode ser chamado num endpoint nem `sendTo` num conn.

## 1. Costuras medidas (01/10, re-confirmadas no código, não na memória)

| Alvo | O que existe | O que a frente acrescenta |
|---|---|---|
| **JVM** | `java.net.ServerSocket`/`Socket` usados pelo runtime WEB (`JvmRuntimeWebServer.kof_web_listen`, `KofHttpServer.acceptLoop`) — nunca expostos como verbos `net` crus; o prefixo `kof_net_*` já é usado pelos acessores URI (`JvmStringNetRuntime`) | nova classe de runtime `JvmRuntimeSockets` (listen/connect/accept/send/receive/close + `DatagramSocket` bind/send/receive); faces typer/StdCatalog `net.listen/accept/connect/send/receive/bind/close` |
| **Native x86-64** | helpers de socket por syscall RAW JÁ EMITIDOS: `runtime/RuntimeNet.java` = `kof_net_socket/bind/listen/accept/read/write/close` (consumidos pelo cliente MySQL/db nativo) | adicionar `kof_net_connect` (syscall socket+connect) + primitivas de datagrama (`socket SOCK_DGRAM`/`sendto`/`recvfrom`) + a amarração stdlib para o código Kof alcançá-las |
| **riscv64 / aarch64** | nenhuma família `kof_net` de sockets nos arquivos `nat/` (grep medido — só nomes de parse URI) | porte das primitivas x86 (asm riscv, translator aarch) ou gap honesto de compilação até o porte |
| **JS** | `JsRuntimeOps.java:40` roteia nomes `kof_net_`; `JsRuntimeUiNet` tinha só faces URI; o host é GraalJS `kof_platform` (`KofJsRunner.exposePlatform`), sem subprocesso node e sem seletor de navegador em compile-time | **fatia 4a POUSADA:** wrappers `kofNet*` + `KofJsNetBridge` (host `java.net`, mesmas classes do JVM); hostless/navegador = erro honesto do Proxy `kof_platform` (R7), provado |
| **Script** | o interpretador despacha builtins da stdlib | faces de runtime espelhando a semântica JVM (ainda NET002 — sem corpo de socket no interpretador) |

Autoridade `grep`: `ServerSocket` em `KofHttpServer.java`/`JvmRuntimeWebServer.java`/`JvmWebCoreRuntime.java`; `kof_net_socket…` em `runtime/RuntimeNet.java`; faces URI `net` em `KofNet.java` + `training/idioms/stdlib.md` §net (sem colisão de verbos: as faces URI são `net.scheme/host/port/path/query/fragment/queryEncode/queryDecode`).

## 2. Fatias (cada uma = reivindicar-commit-testar-push, RED-first)

1. ~~**Sonda de compilação do contrato (JVM)**~~ — **POUSADA 01/10.** Faces registradas em `KofNet` (`staticMethod` + `instanceMethod`, handles como 1º argumento como `web`/`db`) e em `MemberCallNamespaces`; descritores JVM em `JvmRuntimeCallDescriptors`/`JvmRuntimeReturnDescriptors` (13 casos `kof_net_*`, medidos corretos no `javap`). Códigos: `NET002` = verbo de socket sem runtime. **Verde falso medido fechado:** a sonda provou que `javap KofRuntime.class` tinha só os 8 verbos de URI + `split` — NENHUM `kof_net_listen` — então aceitar no JVM era um verde de compilação que morreria `NoSuchMethodError` no class load. A costura `KofNet.socketRuntimeReady` (via `KofNet.supportedOn` + `lowerNet`) devolvia `false` para todo verbo de socket em TODO alvo — virada na fatia 2 no instante em que o runtime gerado passou a carregar os métodos. Prova: `NetSurfaceE2ETest` 7/7, `StdCatalogTest` 11/11, `StdCatalogSignaturesTest` 13/13, `ConformanceMatrixTest` 14/14, `KofNetTest` 4/4 (1 skip de ambiente).
2. ~~**Runtime JVM + TCP + UDP E2E**~~ — **POUSADA 01/10.** `JvmRuntimeSockets` (fragmento novo ligado ao `JvmRuntime`, `java.net` só dentro dele — mesma higiene do `JvmRuntimeWebServer`) emite os três handles reais (`NetListener`/`NetConn`/`NetEndpoint`) e `kof_net_listen/accept/connect/bind/send/receive/sendTo/receiveFrom/peer/close`. TCP: `ServerSocket`(SO_REUSEADDR)→`accept`→`Socket` eco de fluxo com worker `spawn`; UDP: `DatagramSocket` bind/send/receive com a origem capturada para o `peer()`. **Dois defeitos medidos corrigidos:** (a) o `receive` enchia `maxBytes` em laço — DEADLOCK para qualquer protocolo interativo; agora volta assim que HÁ dado (semântica de `read`), `[]` no EOF, e o framing acumula em Kof; (b) o limite de datagrama `65535` passava no front mas o SO respondia "mensagem muito longa" — o limite honesto é o do PAYLOAD UDP/IPv4 `65507` (= 65535 − 20 IPv4 − 8 UDP), recusado por nome como `NET003` ANTES do syscall. O virar do portão está acoplado à existência do método. Prova: `NetTcpE2ETest` 9/9, `NetSurfaceE2ETest` 7/7, `KofNetTest` 4/4.
3. ~~**Front Native x86-64**~~ — **POUSADA 01/10.** `NativeNetFront` (+`NativeNetFrontTcp`/`NativeNetFrontUdp`/`NativeNetFrontPeer`) emite os dez verbos `kof_net_*` sobre handles opacos do heap (`tag` 1=Listener/2=Conn/3=Endpoint; `fd`; `hasPeer`+última origem). `net.listen` = SOCK_STREAM+SO_REUSEADDR→bind→listen; `accept`; `net.connect` = SOCK_STREAM+`kof_plat_net_connect` (só IPv4 dotted-quad, v1); `net.bind` = SOCK_DGRAM; `send`/`receive` sobre o fd do fluxo; `sendTo`/`receiveFrom` via syscall crua `sendto(44)`/`recvfrom(45)` com a origem capturada; `peer()` monta `"a.b.c.d:port"` com `kof_int_to_string`+`kof_string_concat`; `close` polimórfico por tag (tag errada = NET005). **Costura liberada:** `RuntimeNet` largou os `kof_net_bind/listen/accept/close` sem consumidor (medido por grep) e os dois chamadores de `kof_net_close` (MySQL `RuntimeDb4`, HTTP nativo `NativeHttpCore`) passaram a `kof_plat_close`. Prova: `NetNativeE2ETest` 6/6, `NetTcpE2ETest` 9/9, `NetSurfaceE2ETest` 7/7, `KofNetTest` 4/4.
4. **Ponte de host JS (4a) + portes cross (4b)** —
   **4a POUSADA 01/10.** O alvo JS é GraalJS-em-JVM com um mapa de host `kof_platform` (`KofJsRunner.exposePlatform`), não um subprocesso node; a ponte usa o MESMO `java.net` do runtime JVM. `JsRuntimeUiNet` ganhou os dez wrappers `kofNet*` (erros do host normalizados para `String` para que `catch (String m)` veja a mesma mensagem do JVM), `KofJsNetBridge` instala `netListen/netAccept/netConnect/netBind/netSend/netReceive/netSendTo/netReceiveFrom/netPeer/netClose` no mapa da plataforma, e `KofNet.supportedOn`/`socketRuntimeReady` aceitam `Target.JS`. Mesma semântica de `JvmRuntimeSockets`: receive volta assim que há dado, `[]` no EOF, `NET003` em 65508, `NET004` em endereço malformado, `NET005` em tag errada, `close` polimórfico. Prova: `NetJsE2ETest` **6/6** (ida-e-volta UDP + `peer()` real, NET003, NET004, todo verbo resolve, connect a porta morta recusa, oráculo URI inalterado) + `KofJsHostlessRuntimeTest` **3/3** (hostless/navegador: `net.listen` dá o erro honesto do shim `not available`, nunca `ReferenceError`). **Limite medido:** `spawn` no JS é uma Promise de mesma thread (`JsRuntimeUiLayout.kofSpawn`), então um `accept()` bloqueante do host dentro de `spawn { }` não corre em paralelo — a concorrência TCP server-side no JS NÃO é honestamente provável no modelo JS atual (a ida-e-volta UDP não precisa de concorrência; o eco TCP real é provado no JVM + Native).
   **4b PENDENTE.** Portar o front x86 para asm riscv64 + translator aarch64 (ou `NET002` honesto até portar — regra: sem escrita gigante, sem drop silencioso). Prova: casos cross do `NetNativeE2ETest` (+qemu). **BLOQUEADA no host:** binutils riscv64/aarch64 ausentes (só `qemu-*` presente), então o artefato cross não pode ser montado/linkado/executado localmente — goldens cross autoritativos no CI. Manter `NET002` até o porte ser realmente montável+provável (nunca aceitar sem símbolo executável).
5. **Script + decisão do navegador + corpus** — **medido 01/10:** NÃO há distinção node/navegador em compile-time (`Target` tem um só `JS`). Então "navegador = gap de compile-time" não é expressável hoje: o mecanismo honesto é o shim de runtime do Proxy `kof_platform` (hostless ⇒ erro claro `not available`, precedente R7 de FFI/HTTP, agora provado para `net`); um gap de navegador real em compile-time exigiria um seletor de plataforma novo = decisão da mantenedora. Script ainda recusa NET002 (o interpretador não tem corpo de socket). Corpus: `training/idioms/net.md` (+PT) SOMENTE com formas medidas; higiene CHANGELOG/README/status EN+PT.

## 3. Como terminar

As cinco fatias verdes em JVM + Native x86-64 + JS (+qemu cross, gaps honestos onde não) ⇒ a frente é real; o data plane do KofShare pode então ser escrito 100% Kof (`D-KOFSHARE-100KOF`). A face de troca de chaves (X25519/Ed25519 no `kof.security`, `SECN005`) NÃO faz parte deste plano — exige decisão regra-6 de superfície própria.

## 4. Guardas (nunca quebrar)

* no-silent-fallback: alvo que não pode abrir socket RECUSA em compile-time com `NET00x`, nunca emite bytecode quebrado.
* matriz de paridade + célula da matriz de conformância atualizadas EN+PT na mesma fatia que pousa um alvo.
* `check_stdlib_boundary.sh` — `net` já é base-stdlib registrada (linha 19 da boundary); verbos estendem, nenhum namespace novo.
* regra `≤500` por arquivo novo de runtime (dividir por responsabilidade).
* KofShare nunca importa interop de novo para transporte — as sondas ficam só como oráculo.
