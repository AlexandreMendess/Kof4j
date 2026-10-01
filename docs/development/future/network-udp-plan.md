last: none
doing: none-planned
next: promote-and-implement-D-KOF-NET
location: docs/development/future
state: decided

intent: kof-udp-datagram-network

**Rule 6 gate:** UDP/datagram support is a NEW network surface with NEW semantics
(packet boundaries, unordered delivery, loss allowed, per-target runtime). This
document is **plan only, zero code**. Promotion requires a maintainer decision
(`D-UDP`) and the future-promotion flow (`docs/development/future/README.md`
§"When to move", `AGENTS.md` §"Future promotion"): rewrite with `UNDER
DEVELOPMENT`, real state + how-to-finish, queue in `roadmap.md` §23, point
`docs/status.md` at it, claim in `DOING.md`.

**Source:** maintainer directive (01/10/2026): *"kof nao tem suporte a UDP
adiciona na fila pra por em network, isso é crucial."*

**Related:** `roadmap.md` §3 (Kof as a Backend Platform — network family:
HTTP/WebSocket/SSE), `DECISIONS.md` §D-SPRING, `docs/backend-parity.md`
(§`kof.http`), `docs/stdlib/stdlib.md` (the `net` stdlib extension), the TCP
socket runtime in `runtime/RuntimeNet.java` + `KofWeb.java`.

---

# Kof UDP (datagram network) — plan

## 0. The one-sentence purpose

> Kof must be able to **send and receive datagrams** — connectionless IP
> messages — with the same intention-first surface that already covers TCP
> (`kof.web` server, `kof.http` client) and without asking the programmer to
> think in packet syscalls.

The acceptance question for every design decision is:

> "Can a Kof programmer open a datagram endpoint, send one message and receive
> one, using words from the domain — not `setsockopt`/`recvfrom` mechanics?"

## 1. Measured absence (01/10/2026)

Swept the tree; UDP does not exist anywhere:

```
grep -rniE '\budp\b|datagram|SOCK_DGRAM|udp_' kof-compiler/src/main --include=*.java
  → 1 hit: a TCP/UDP port-range comment in JvmStringValidationRuntime.java:241
    (validation of a port number — no datagram capability)
```

- `KofNet.java` is **URI parsing only** (`scheme`/`host`/`port`/`path`/`query`/
  `fragment` + `queryEncode`/`queryDecode`) — the `net` stdlib extension of
  `PLAN-STDLIB-EXPANSION` S8. It never opens a socket.
- The real transport is TCP: `runtime/RuntimeNet.java` (JVM `Socket`) and the
  native server runtime (`KofWeb` + `NativeRuntime` raw syscalls). Nothing
  binds `SOCK_DGRAM`.
- `docs/backend-parity.md` has **no** UDP/datagram column or cell.

So the gap is real, measured, and total: there is no UDP on any target.

## 2. Existing foundation to reuse (why this is a library-first front)

UDP is **not** a new core primitive: the socket machinery already exists per
target and only needs a datagram path plus an intention surface.

| Layer | Existing anchor | UDP reuse |
|---|---|---|
| JVM | `RuntimeNet.java` (`java.net.Socket`) | `java.net.DatagramSocket` / `DatagramPacket` behind the same runtime seam |
| Native | raw syscalls (`socket`/`bind`/`recv`/`send`/`close`) in the web/native runtime | add `SOCK_DGRAM`; `sendto`/`recvfrom` are natural extensions |
| JS | host `dgram` (node) / no browser datagram | node = real, browser = honest gap (R7) |
| Script | interpreted over the JVM runtime | inherits the JVM path (or honest refusal, as with other JVM-only faces) |

This keeps the front **library-first** (`D-KOF-FIRST-IMPL`) and additive: no
lexer/parser change, no new grammar.

## 3. Surface sketch (proposal — NOT decided)

Intent-first, mirroring the existing `kof.web`/`kof.http` idioms rather than a
foreign `Socket`/`DatagramSocket` mirror (the Kof philosophy forbids translated
idioms):

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

Shape questions the maintainer owns (see §5): namespace name (`kof.udp` vs an
extension of `kof.net`), the message value (`String` vs `Buffer(U8)` bytes — a
datagram is binary by nature), the peer type (an opaque address record or a
`"host:port"` string), and blocking vs non-blocking receive.

## 4. Cross-target reality (honest, never a promise)

Per `AGENTS.md` §Platform ("never promise unsupported parity") each target gets
its own measured face and its **own gap code** where it cannot bind:

- **JVM** — full (DatagramSocket/DatagramPacket).
- **Native x86-64 / riscv64 / aarch64** — real raw syscalls; feasible, needs
  `sendto`/`recvfrom` (no connection state) and address marshalling.
- **JS (node)** — real `dgram`; **browser** has no datagram API → honest gap.
- **Script** — inherits JVM or an explicit refusal.

A gap code (e.g. `NETUDP001`) and a `backend-parity.md` row are part of the
promotion, not of this plan.

## 5. Open questions (DECISION REQUIRED — rule 6)

> **ANSWERED 01/10 by the maintainer (chat votes) — frozen in `D-KOF-NET`:** unified namespace `kof.net` (Q1=no separate kof.udp; extends the URI-only `net`); payload `Byte[]` on the wire both transports (Q2); address = `"host:port"` String, receive yields source addr the same way (Q3); blocking verbs + `spawn` per connection/endpoint (Q4); bound 64 KiB with `NET00x` refusal, no transparent fragmentation (Q5); unicast only in v1 — broadcast/multicast deferred to their own decision (Q6); Q7: endpoints obey the existing `app.security`/policy model (no new face). TCP joins the same front (maintainer chose TCP+UDP together, rejecting TCP-first).

1. **Namespace** — new `kof.udp`, or extend `kof.net` (today URI-only) with a
   transport section?
2. **Message type** — `String`, `Buffer(U8)`, or a `Datagram` record carrying
   both bytes + peer? (Buffer already exists; binding `Buffer(U8)` to a
   datagram is a natural fit.)
3. **Peer/address** — opaque address value vs `"host:port"` `String`.
4. **Blocking model** — blocking `receive` (TCP-like), timeout, and/or a
   callback handler (`udp.listen`) as in §3.
5. **Maximum datagram size** — bound it (e.g. 64 KiB IPv4 limit) and refuse
   above it, or truncate? (R6: never silently truncate.)
6. **Broadcast/multicast** — v1 or a later face? (`SO_BROADCAST`,
   `IP_ADD_MEMBERSHIP`.)
7. **Security** — do UDP endpoints obey the same `app.security`/policy model as
   the HTTP/WS surfaces?

## 6. Promotion flow

1. Maintainer records `D-UDP` in `DECISIONS.md` (answers §5).
2. Rewrite this document with status `UNDER DEVELOPMENT` and move it out of
   `future/` (+PT) in the same commit that claims it (`D-FUTURE-PROMOTION`,
   one-at-a-time).
3. Queue it in `roadmap.md` §3/§23 and point `docs/status.md` at it.
4. Claim in `DOING.md`, implement the JVM slice first (RED-first E2E over a real
   loopback socket), then the native and JS faces with their honest gaps, and
   synchronize docs/training + `backend-parity.md`.
