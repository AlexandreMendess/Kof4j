last: fatia 2 POUSADA 01/10 — `JvmRuntimeSockets` emite um corpo java.net REAL (ServerSocket/Socket/DatagramSocket); eco TCP + ida-e-volta UDP rodam sobre loopback vivo; dois defeitos medidos corrigidos (`receive` bloqueante enchia maxBytes e travava protocolos interativos; o limite UDP 65535 passava do payload IPv4 real 65507 e o SO rejeitava). JVM aceita; Native/JS ainda recusam NET002. Prova NetTcpE2ETest 9/9 + NetSurfaceE2ETest 7/7 + KofNetTest 4/4
doing: fatia-3-udp-cross-e-paridade
next: fatia 3 — o UDP já está verde no JVM dentro da fatia 2; agora a face Native (`SOCK_DGRAM`/`sendto`/`recvfrom` sobre a costura crua `kof_net_*` existente) com NET002 preservado até pousar, depois fatia 4 = ponte JS/node `dgram`, fatia 5 = lacuna do navegador + corpus `training/idioms/net.md` (+PT)
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
endpoint.send(addr: String, data: Byte[]) -> Int
endpoint.receive(maxBytes: Int) -> Datagram   // record Datagram(Byte[] bytes, String from)
endpoint.close()
```

O portador `Datagram` do receive é a única forma aberta dentro do contrato congelado: o Kof não tem tupla; a preferência "sem record novo" da mantenedora era sobre o payload da linha (honrada — `Byte[]` no ENVIO). A sonda da fatia 1 valida se a face `record Datagram` compila+roda em todos os alvos implementáveis; se a mantenedora rejeitar o record depois, a forma do receive muda — até lá a sonda não decide nada além de si mesma.

## 1. Costuras medidas (01/10, re-confirmadas no código, não na memória)

| Alvo | O que existe | O que a frente acrescenta |
|---|---|---|
| **JVM** | `java.net.ServerSocket`/`Socket` usados pelo runtime WEB (`JvmRuntimeWebServer.kof_web_listen`, `KofHttpServer.acceptLoop`) — nunca expostos como verbos `net` crus; o prefixo `kof_net_*` já é usado pelos acessores URI (`JvmStringNetRuntime`) | nova classe de runtime `JvmRuntimeSockets` (listen/connect/accept/send/receive/close + `DatagramSocket` bind/send/receive); faces typer/StdCatalog `net.listen/accept/connect/send/receive/bind/close` |
| **Native x86-64** | helpers de socket por syscall RAW JÁ EMITIDOS: `runtime/RuntimeNet.java` = `kof_net_socket/bind/listen/accept/read/write/close` (consumidos pelo cliente MySQL/db nativo) | adicionar `kof_net_connect` (syscall socket+connect) + primitivas de datagrama (`socket SOCK_DGRAM`/`sendto`/`recvfrom`) + a amarração stdlib para o código Kof alcançá-las |
| **riscv64 / aarch64** | nenhuma família `kof_net` de sockets nos arquivos `nat/` (grep medido — só nomes de parse URI) | porte das primitivas x86 (asm riscv, translator aarch) ou gap honesto de compilação até o porte |
| **JS** | `JsRuntimeOps.java:40` roteia nomes `kof_net_` pela ponte do host (caminho do cliente db); navegador NÃO tem face de socket | ponte node: `net`/`dgram` do host pela MESMA padronagem da ponte; NAVEGADOR = gap honesto de compilação `NET001` (nunca drop silencioso) |
| **Script** | o interpretador despacha builtins da stdlib | faces de runtime espelhando a semântica JVM |

Autoridade `grep`: `ServerSocket` em `KofHttpServer.java`/`JvmRuntimeWebServer.java`/`JvmWebCoreRuntime.java`; `kof_net_socket…` em `runtime/RuntimeNet.java`; faces URI `net` em `KofNet.java` + `training/idioms/stdlib.md` §net (sem colisão de verbos: as faces URI são `net.scheme/host/port/path/query/fragment/queryEncode/queryDecode`).

## 2. Fatias (cada uma = reivindicar-commit-testar-push, RED-first)

1. ~~**Sonda de compilação do contrato (JVM)**~~ — **POUSADA 01/10.** Faces registradas em `KofNet` (`staticMethod` + `instanceMethod`, handles como 1º argumento como `web`/`db`) e em `MemberCallNamespaces`; descritores JVM em `JvmRuntimeCallDescriptors`/`JvmRuntimeReturnDescriptors` (13 casos `kof_net_*`, medidos corretos no `javap`). Códigos: `NET002` = verbo de socket sem runtime (antes usado para Native/JS; agora também JVM). **Verde falso medido fechado:** a sonda provou que `javap KofRuntime.class` tem só os 8 verbos de URI + `split` — NENHUM `kof_net_listen` — então aceitar no JVM era um verde de compilação que morreria `NoSuchMethodError` no class load. a costura `KofNet.socketRuntimeReady` (via `KofNet.supportedOn` + `lowerNet`) devolve `false` para todo verbo de socket em TODO alvo — vire-a na fatia 2 no instante em que o runtime gerado carregar os métodos; o no-silent-fallback se sustenta até a fatia 2. Prova: `NetSurfaceE2ETest` **7/7** (`jvmRefusesUntilRuntimeExists`, `everyVerbRefusedOnEveryTarget`, `NET002` por alvo, aridade é diagnóstico SEM/NET nomeado, acessores de URI ainda verdes em todos os alvos de artefato, o catálogo lista exatamente os verbos de namespace), `StdCatalogTest` 11/11, `StdCatalogSignaturesTest` 13/13, `ConformanceMatrixTest` 14/14, `KofNetTest` 4/4 (1 skip de ambiente).
2. ~~**Runtime JVM + TCP + UDP E2E**~~ — **POUSADA 01/10.** `JvmRuntimeSockets` (fragmento novo ligado ao `JvmRuntime`, `java.net` só dentro dele — mesma higiene do `JvmRuntimeWebServer`) emite os três handles reais (`NetListener`/`NetConn`/`NetEndpoint`) e `kof_net_listen/accept/connect/bind/send/receive/sendTo/receiveFrom/peer/close`. TCP: `ServerSocket`(SO_REUSEADDR)→`accept`→`Socket` eco de fluxo com worker `spawn`; UDP: `DatagramSocket` bind/send/receive com a origem capturada para o `peer()`. **Dois defeitos medidos corrigidos:** (a) o `receive` enchia `maxBytes` em laço — DEADLOCK para qualquer protocolo interativo (o worker esperava 4096 enquanto o cliente esperava o eco dos 4 que mandou); agora volta assim que HÁ dado (semântica de `read`), `[]` no EOF, e o framing acumula em Kof; (b) o limite de datagrama `65535` passava no front mas o SO respondia "mensagem muito longa" — o limite honesto é o do PAYLOAD UDP/IPv4 `65507` (= 65535 − 20 IPv4 − 8 UDP), recusado por nome como `NET003` ANTES do syscall. O virar do portão está acoplado à existência do método: `KofNet.supportedOn`/`socketRuntimeReady` aceitam só JVM agora que o `javap KofRuntime.class` lista os verbos. Prova: `NetTcpE2ETest` **9/9** (eco TCP byte-idêntico, ida-e-volta UDP + `peer()` real `host:port`, `NET003` em 65508 e ACEITA em 65507, endereço malformado recusado, EOF = array vazio, todo handle fecha, todo verbo resolve no CLASS LOAD — a armadilha da fatia 1, Native/JS ainda `NET002`), `NetSurfaceE2ETest` 7/7, `KofNetTest` 4/4.
3. **Runtime JVM + E2E UDP** — `DatagramSocket` bind/send/receive + recusa de 64 KiB `NET00x`; echo golden. Prova: `NetUdpE2ETest`.
4. **Native x86-64 + portes cross** — amarrar o asm já existente nos verbos `net`; adicionar primitivas de connect+datagrama; riscv64 asm + translator aarch64 (ou gap honesto + entrada no ledger se o escopo quebrar — regra: sem escrita gigante). Prova: `NetE2ETest` native (+qemu).
5. **JS/Script + honestidade de paridade** — faces da ponte node onde o host permitir; navegador/outros = gaps nomeados `NET00x` em compile-time em `docs/backend-parity.md` + célula da matriz de conformância. Corpus: `training/idioms/net.md` (+PT) SOMENTE com formas medidas; higiene CHANGELOG/README/status EN+PT.

## 3. Como terminar

As cinco fatias verdes em JVM + Native x86-64 (+qemu cross, gaps honestos onde não) ⇒ a frente é real; o data plane do KofShare pode então ser escrito 100% Kof (`D-KOFSHARE-100KOF`). A face de troca de chaves (X25519/Ed25519 no `kof.security`, `SECN005`) NÃO faz parte deste plano — exige decisão regra-6 de superfície própria.

## 4. Guardas (nunca quebrar)

* no-silent-fallback: alvo que não pode abrir socket RECUSA em compile-time com `NET00x`, nunca emite bytecode quebrado.
* matriz de paridade + célula da matriz de conformância atualizadas EN+PT na mesma fatia que pousa um alvo.
* `check_stdlib_boundary.sh` — `net` já é base-stdlib registrada (linha 19 da boundary); verbos estendem, nenhum namespace novo.
* regra `≤500` por arquivo novo de runtime (dividir por responsabilidade).
* KofShare nunca importa interop de novo para transporte — as sondas ficam só como oráculo.
