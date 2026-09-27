[English](buffer.md) | [Português](buffer.pt_BR.md)

# kof.buffer — o buffer nominal de bytes

> **Status: `buffer.alloc(Int) -> Buffer(U8)` + `Buffer.bytes() -> Byte[]` no
> JVM e JS (`JsRuntimeBuffer`, mesmo contrato) · Native = gap honesto · a face
> de out-buffer da FFI é `Buffer(U8)` INOUT (veja [40 — Baixo Nível](../40-low-level.pt_BR.md)).**

| Face | Membros (medidos) |
|------|--------------------|
| `buffer` | `alloc(Int size) -> Buffer(U8)` |
| `Buffer(U8)` | `bytes() -> Byte[]` |

```kf
var buf = buffer.alloc(1024)          // Buffer(U8) nominal — NÃO é String, NÃO é List
extern "libdevice.so" device_read(Int n, Buffer(U8) out): Int
var n = device_read(1024, buf)
println(buf.bytes().size)             // os bytes voltaram pelo out-buffer
```

- Um buffer é um tipo de ABI DISTINTO: comprimento + direção + mutabilidade —
  NUNCA é um reuse de `String`/`Byte[]` (`D6`, 20–21/09: copy-in / chamada /
  copy-back).
- O tempo de vida é dono do handle (`D-R3-HANDLE-LIFETIME`), não de heurística
  de GC.

**Veja também:** [40 — Baixo Nível](../40-low-level.pt_BR.md) — a face de
out-buffer da FFI.
