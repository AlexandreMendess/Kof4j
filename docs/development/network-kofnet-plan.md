last: slice 3 LANDED 01/10 (x86-64 half) — `NativeNetFront`/`NativeNetFrontTcp`/`NativeNetFrontUdp`/`NativeNetFrontPeer` emit the ten `kof_net_*` front verbs over opaque heap handles (tag 1/2/3); TCP echo + UDP round-trip run over live loopback with a real `spawn` worker; `kof_net_close` became polymorphic by tag. The raw x86 seam (`RuntimeNet`) kept only `socket/read/write` — its unused `bind/listen/accept/close` were removed and the two `kof_net_close` consumers (MySQL/HTTP) moved to `kof_plat_close`. Gate accepts NATIVE x86-64 only; JS/riscv/aarch still refuse NET002. Proof NetNativeE2ETest 6/6 + NetTcpE2ETest 9/9 + NetSurfaceE2ETest 7/7 + KofNetTest 4/4
doing: slice-4-js-cross-ports
next: slice 4 — port the x86 front to riscv64 asm + aarch64 translator (or honest NET002 until ported) and add the JS/node `net`+`dgram` host bridge; slice 5 = browser gap + corpus `training/idioms/net.md` (+PT)
location: docs/development
state: under-development

intent: kof.net unified TCP+UDP front (D-KOF-NET)

# Kof `net` — unified network front (TCP + UDP)

## 0. Contract (frozen by `D-KOF-NET`, maintainer rule-6 votes 01/10)

ONE namespace `kof.net` (alongside the URI accessors that already live there). Blocking verbs; `spawn` is the only concurrency. `Byte[]` payload both directions, both transports. UDP addressed by `"host:port"` String; 64 KiB datagram bound with `NET00x` refusal; unicast-only v1; policy = existing `app.security` model; key exchange (`SECN005`) is a SEPARATE surface decision.

```
// TCP (connection)
net.listen(port: Int) -> Listener
listener.accept() -> Conn              // blocks
net.connect(host: String, port: Int) -> Conn
conn.send(data: Byte[]) -> Int         // bytes written
conn.receive(maxBytes: Int) -> Byte[]  // blocks up to a read; len-0 => closed
conn.close() ; listener.close()
// UDP (datagram)
net.bind(port: Int) -> Endpoint
endpoint.send(addr: String, data: Byte[]) -> Int
endpoint.receive(maxBytes: Int) -> Datagram   // record Datagram(Byte[] bytes, String from)
endpoint.close()
```

The `Datagram` receive-carrier is the one open shape inside the frozen contract: Kof has no tuple; the maintainer's "no new record" preference was about the wire payload (honored — `Byte[]` on SEND). The probe in slice 1 validates the `record Datagram` face compiles+runs on all implementable targets; if the maintainer rejects the record later, the receive form changes — until then the probe decides nothing beyond itself.

## 1. Measured seams (01/10, re-confirmed against code, not memory)

| Target | What exists | What the front adds |
|---|---|---|
| **JVM** | `java.net.ServerSocket`/`Socket` used by the WEB runtime (`JvmRuntimeWebServer.kof_web_listen`, `KofHttpServer.acceptLoop`) — never exposed as raw `net` verbs; `kof_net_*` name prefix already used by URI accessors (`JvmStringNetRuntime`) | new runtime class `JvmRuntimeSockets` (listen/connect/accept/send/receive/close + `DatagramSocket` bind/send/receive); typer/StdCatalog faces `net.listen/accept/connect/send/receive/bind/close` |
| **Native x86-64** | raw-syscall socket helpers ALREADY EMITTED: `runtime/RuntimeNet.java` = `kof_net_socket/bind/listen/accept/read/write/close` (consumed by the native MySQL/db client) | add `kof_net_connect` (socket+connect syscall) + datagram primitives (`socket SOCK_DGRAM`/`sendto`/`recvfrom`) + the stdlib binding so Kof code reaches them |
| **riscv64 / aarch64** | no `kof_net` socket family found in `nat/` files (grep measured — only URI parse names) | port of the x86 primitives (asm for riscv, translator for aarch) or honest compile-time gap until ported |
| **JS** | `JsRuntimeOps.java:40` routes `kof_net_` names through the host bridge (db client path); browser has NO socket face | node seam: host `net`/`dgram` via the same bridge pattern; BROWSER = honest compile gap `NET001` (never a silent drop) |
| **Script** | interpreter dispatches stdlib builtins | runtime faces mirroring JVM semantics |

`grep` authority: `ServerSocket` in `KofHttpServer.java`/`JvmRuntimeWebServer.java`/`JvmWebCoreRuntime.java`; `kof_net_socket…` in `runtime/RuntimeNet.java`; `net` URI faces in `KofNet.java` + `training/idioms/stdlib.md` §net (no verb collision: URI faces are `net.scheme/host/port/path/query/fragment/queryEncode/queryDecode`).

## 2. Slices (each = claim-commit-test-push, RED-first)

1. ~~**Surface contract compile probe (JVM)**~~ — **LANDED 01/10.** Faces registered in `KofNet` (`staticMethod` + `instanceMethod`, handles as 1st argument like `web`/`db`) and in `MemberCallNamespaces`; JVM descriptors in `JvmRuntimeCallDescriptors`/`JvmRuntimeReturnDescriptors` (13 `kof_net_*` cases, measured correct in `javap`). Codes: `NET002` = socket verb without runtime (was used for Native/JS; now also JVM). **Measured false green closed:** the probe proved `javap KofRuntime.class` has only the 8 URI verbs + `split` — NO `kof_net_listen` — so JVM acceptance was a compile-green that would die `NoSuchMethodError` at class load. the `KofNet.socketRuntimeReady` seam (via `KofNet.supportedOn` + `lowerNet`) returns `false` for every socket verb on EVERY target — flip it in slice 2 the moment the generated runtime carries the methods; no-silent-fallback holds until slice 2. Proof: `NetSurfaceE2ETest` **7/7** (`jvmRefusesUntilRuntimeExists`, `everyVerbRefusedOnEveryTarget`, per-target `NET002`, arity is a named SEM/NET diagnostic, URI accessors still green on all artifact targets, catalog lists exactly the namespace verbs), `StdCatalogTest` 11/11, `StdCatalogSignaturesTest` 13/13, `ConformanceMatrixTest` 14/14, `KofNetTest` 4/4 (1 env-skip).
2. ~~**JVM runtime + TCP + UDP E2E**~~ — **LANDED 01/10.** `JvmRuntimeSockets` (a new fragment wired into `JvmRuntime`, `java.net` only inside it — same hygiene as `JvmRuntimeWebServer`) emits the three real handles (`NetListener`/`NetConn`/`NetEndpoint`) plus `kof_net_listen/accept/connect/bind/send/receive/sendTo/receiveFrom/peer/close`. TCP: `ServerSocket`(SO_REUSEADDR)→`accept`→`Socket` stream echo with a `spawn` worker; UDP: `DatagramSocket` bind/send/receive with the source captured for `peer()`. **Two measured defects fixed:** (a) `receive` looped to fill `maxBytes` — a DEADLOCK for any interactive protocol (worker waits for 4096 while the client waits for the echo of the 4 it sent); it now returns as soon as data is available (`read` semantics), `[]` at EOF, and framing accumulates in Kof; (b) the datagram bound `65535` passed the front but the OS answered "message too long" — the honest bound is the IPv4 UDP PAYLOAD max `65507` (= 65535 − 20 IPv4 − 8 UDP), refused by name as `NET003` BEFORE the syscall. The gate flip is coupled to the method existing: `KofNet.supportedOn`/`socketRuntimeReady` accept JVM only now that `javap KofRuntime.class` lists the verbs. Proof: `NetTcpE2ETest` **9/9** (TCP echo byte-identical, UDP round-trip + real `peer()` `host:port`, `NET003` on 65508 and ACCEPT on 65507, malformed address refused, EOF = empty array, every handle closes, every verb resolves at CLASS LOAD — the fatia-1 trap, Native/JS still `NET002`), `NetSurfaceE2ETest` 7/7, `KofNetTest` 4/4.
3. ~~**Native x86-64 front**~~ — **LANDED 01/10.** `NativeNetFront` (+`NativeNetFrontTcp`/`NativeNetFrontUdp`/`NativeNetFrontPeer`) emits the ten `kof_net_*` verbs over opaque heap handles (`tag` 1=Listener/2=Conn/3=Endpoint; `fd`; `hasPeer`+last source). `net.listen` = SOCK_STREAM+SO_REUSEADDR→bind→listen; `accept`; `net.connect` = SOCK_STREAM+`kof_plat_net_connect` (IPv4 dotted-quad only, v1); `net.bind` = SOCK_DGRAM; `send`/`receive` over the stream fd; `sendTo`/`receiveFrom` via raw `sendto(44)`/`recvfrom(45)` with the origin captured; `peer()` renders `"a.b.c.d:port"` through `kof_int_to_string`+`kof_string_concat`; `close` polymorphic by tag (bad tag = NET005). **Seam freed:** `RuntimeNet` dropped its unused `kof_net_bind/listen/accept/close` (grep-measured: no raw consumer) and the two `kof_net_close` consumers (MySQL `RuntimeDb4`, native HTTP `NativeHttpCore`) moved to `kof_plat_close`. Gate `KofNet.supportedOn`/`socketRuntimeReady` accepts NATIVE x86-64 now that the symbols exist; JS/riscv/aarch still refuse NET002. Proof: `NetNativeE2ETest` **6/6** (TCP echo byte-identical, UDP round-trip + real `peer()`, NET003 on 65508, malformed address refused, EOF = empty array, every verb resolves at LINK time), `NetTcpE2ETest` 9/9, `NetSurfaceE2ETest` 7/7, `KofNetTest` 4/4.
4. **Cross ports (riscv64 + aarch64)** — port the x86 front to riscv64 asm + aarch64 translator (or honest `NET002` until ported — rule: no giant write, no silent drop). Proof: `NetNativeE2ETest` cross cases (+qemu).
5. **JS/Script + parity honesty** — node bridge faces where the host allows; browser/other = compile-time `NET00x` named gaps in `docs/backend-parity.md` + conformance matrix cell. Corpus: `training/idioms/net.md` (+PT) with ONLY measured forms; CHANGELOG/README/status EN+PT hygiene.

## 3. How to finish

All five slices green on JVM + Native x86-64 (+qemu cross, honest gaps elsewhere) ⇒ front is real; KofShare data plane may then be written 100% Kof (`D-KOFSHARE-100KOF`). The key-exchange face (X25519/Ed25519 in `kof.security`, `SECN005`) is NOT part of this plan — it needs its own rule-6 surface decision.

## 4. Guards (never break)

* no-silent-fallback: a target that cannot bind a socket REFUSES at compile time with `NET00x`, never emits broken bytecode.
* parity matrix + conformance matrix cells updated EN+PT in the same slice that lands a target.
* `check_stdlib_boundary.sh` — `net` is already registered base-stdlib (boundary line 19); verbs extend it, no new namespace.
* `≤500` file rule for every new runtime file (split by responsibility).
* KofShare never imports interop again for transport — the probes' oracle status only.
