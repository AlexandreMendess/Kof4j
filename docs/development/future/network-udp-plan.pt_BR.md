last: none
doing: none-planned
next: promover-e-implementar-D-KOF-NET
location: docs/development/future
state: decided

intent: kof-udp-datagram-network

**Gate da regra 6:** suporte a UDP/datagrama é uma SUPERFÍCIE de rede NOVA com
semântica NOVA (fronteiras de pacote, entrega fora de ordem, perda permitida,
runtime por alvo). Este documento é **apenas plano, zero código**. A promoção
exige uma decisão da mantenedora (`D-UDP`) e o fluxo de promoção de futuro
(`docs/development/future/README.pt_BR.md` §"When to move", `AGENTS.md`
§"Future promotion"): reescrever com `UNDER DEVELOPMENT`, estado real + como
finalizar, enfileirar em `roadmap.md` §23, apontar `docs/status.pt_BR.md` para
ele, reivindicar em `DOING.pt_BR.md`.

**Fonte:** diretriz da mantenedora (01/10/2026): *"kof nao tem suporte a UDP
adiciona na fila pra por em network, isso é crucial."*

**Relacionados:** `roadmap.md` §3 (Kof como Plataforma de Backend — família de
rede: HTTP/WebSocket/SSE), `DECISIONS.md` §D-SPRING, `docs/backend-parity.md`
(§`kof.http`), `docs/stdlib/stdlib.md` (a extensão `net` da stdlib), o runtime de
socket TCP em `runtime/RuntimeNet.java` + `KofWeb.java`.

---

# Kof UDP (rede de datagramas) — plano

## 0. O propósito em uma frase

> Kof precisa **enviar e receber datagramas** — mensagens IP sem conexão — com
> a mesma superfície orientada a intenção que já cobre TCP (servidor `kof.web`,
> cliente `kof.http`) e sem pedir ao programador que pense em syscalls de
> pacote.

A pergunta de aceitação para cada decisão de design é:

> "Um programador Kof consegue abrir um endpoint de datagrama, enviar uma
> mensagem e receber uma, usando palavras do domínio — e não mecânica de
> `setsockopt`/`recvfrom`?"

## 1. Ausência medida (01/10/2026)

Varredura na árvore; UDP não existe em lugar nenhum:

```
grep -rniE '\budp\b|datagram|SOCK_DGRAM|udp_' kof-compiler/src/main --include=*.java
  → 1 hit: um comentário de faixa de porta TCP/UDP em JvmStringValidationRuntime.java:241
    (validação de número de porta — nenhuma capacidade de datagrama)
```

- `KofNet.java` é **apenas parsing de URI** (`scheme`/`host`/`port`/`path`/
  `query`/`fragment` + `queryEncode`/`queryDecode`) — a extensão `net` da
  stdlib do `PLAN-STDLIB-EXPANSION` S8. Ele nunca abre um socket.
- O transporte real é TCP: `runtime/RuntimeNet.java` (`Socket` do JVM) e o
  runtime nativo do servidor (`KofWeb` + syscalls cruas de `NativeRuntime`).
  Nada binda `SOCK_DGRAM`.
- `docs/backend-parity.md` **não** tem coluna ou célula de UDP/datagrama.

Logo, a lacuna é real, medida e total: não há UDP em nenhum alvo.

## 2. Fundação existente a reusar (por que esta frente é library-first)

UDP **não** é um primitivo novo de core: a maquinaria de socket já existe por
alvo e só precisa de um caminho de datagrama mais uma superfície de intenção.

| Camada | Âncora existente | Reuso do UDP |
|---|---|---|
| JVM | `RuntimeNet.java` (`java.net.Socket`) | `java.net.DatagramSocket` / `DatagramPacket` atrás da mesma costura do runtime |
| Native | syscalls cruas (`socket`/`bind`/`recv`/`send`/`close`) no runtime web/nativo | adicionar `SOCK_DGRAM`; `sendto`/`recvfrom` são extensões naturais |
| JS | `dgram` do host (node) / sem datagrama no navegador | node = real, navegador = lacuna honesta (R7) |
| Script | interpretado sobre o runtime JVM | herda o caminho JVM (ou recusa honesta, como nas outras faces JVM-only) |

Isto mantém a frente **library-first** (`D-KOF-FIRST-IMPL`) e aditiva: sem
mudança no lexer/parser, sem nova gramática.

## 3. Esboço de superfície (proposta — NÃO decidida)

Orientada a intenção, espelhando os idiomas existentes `kof.web`/`kof.http` em
vez de espelhar `Socket`/`DatagramSocket` estrangeiros (a filosofia Kof proíbe
idiomas traduzidos):

```kof
udp.listen(9000) { datagram, peer ->
    println(peer + ": " + datagram)
    udp.send(peer, "pong")
}

main() {
    var socket = udp.open()
    udp.send(socket, "127.0.0.1:9000", "ping")
    println(udp.receive(socket))
    udp.close(socket)
}
```

Questões de forma que pertencem à mantenedora (ver §5): nome do namespace
(`kof.udp` vs extensão de `kof.net`), o valor da mensagem (`String` vs bytes
`Buffer(U8)` — um datagrama é binário por natureza), o tipo de peer (um record
opaco de endereço ou uma string `"host:port"`), e recepção bloqueante vs não
bloqueante.

## 4. Realidade cross-target (honesta, nunca uma promessa)

Conforme `AGENTS.md` §Platform ("never promise unsupported parity") cada alvo
tem sua própria face medida e seu **próprio código de gap** onde não conseguir
bindar:

- **JVM** — completo (DatagramSocket/DatagramPacket).
- **Native x86-64 / riscv64 / aarch64** — syscalls cruas reais; viável, precisa
  de `sendto`/`recvfrom` (sem estado de conexão) e marshalhamento de endereço.
- **JS (node)** — `dgram` real; **navegador** não tem API de datagrama → lacuna
  honesta.
- **Script** — herda JVM ou uma recusa explícita.

Um código de gap (p.ex. `NETUDP001`) e uma linha em `backend-parity.md` fazem
parte da promoção, não deste plano.

## 5. Questões abertas (DECISION REQUIRED — regra 6)

> **RESPONDIDAS 01/10 pela mantenedora (votos no chat) — congeladas em `D-KOF-NET`:** namespace unificado `kof.net` (Q1=sem `kof.udp` separado; estende o `net` só-de-URI); payload `Byte[]` nos dois transportes (Q2); endereço = String `"host:port"`, o receive entrega a origem na mesma forma (Q3); verbos bloqueantes + `spawn` por conexão/endpoint (Q4); limite 64 KiB com recusa `NET00x`, sem fragmentação transparente (Q5); unicast apenas no v1 — broadcast/multicast adiados para decisão própria (Q6); Q7: endpoints obedecem ao modelo `app.security`/política existente (sem face nova). TCP entra na mesma frente (a mantenedora escolheu TCP+UDP juntos, rejeitando TCP-primeiro).

1. **Namespace** — novo `kof.udp`, ou estender `kof.net` (hoje URI-only) com uma
   seção de transporte?
2. **Tipo da mensagem** — `String`, `Buffer(U8)`, ou um record `Datagram`
   carregando os dois (bytes + peer)? (Buffer já existe; bindar `Buffer(U8)` a
   um datagrama é encaixe natural.)
3. **Peer/endereço** — valor opaco de endereço vs `String` `"host:port"`.
4. **Modelo de bloqueio** — `receive` bloqueante (como TCP), timeout, e/ou um
   handler por callback (`udp.listen`) como em §3.
5. **Tamanho máximo de datagrama** — limitar (p.ex. limite IPv4 de 64 KiB) e
   recusar acima, ou truncar? (R6: nunca truncar em silêncio.)
6. **Broadcast/multicast** — v1 ou face posterior? (`SO_BROADCAST`,
   `IP_ADD_MEMBERSHIP`.)
7. **Segurança** — endpoints UDP obedecem ao mesmo modelo `app.security`/policy
   das superfícies HTTP/WS?

## 6. Fluxo de promoção

1. A mantenedora registra `D-UDP` em `DECISIONS.md` (responde §5).
2. Reescrever este documento com status `UNDER DEVELOPMENT` e movê-lo para fora
   de `future/` (+PT) no mesmo commit que o reivindica (`D-FUTURE-PROMOTION`,
   um de cada vez).
3. Enfileirá-lo em `roadmap.md` §3/§23 e apontar `docs/status.pt_BR.md` para ele.
4. Reivindicar em `DOING.pt_BR.md`, implementar a fatia JVM primeiro (E2E
   RED-first sobre um socket loopback real), depois as faces native e JS com
   suas lacunas honestas, e sincronizar docs/training + `backend-parity.md`.
