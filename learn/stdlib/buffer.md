[English](buffer.md) | [Português](buffer.pt_BR.md)

# kof.buffer — the nominal byte buffer

> **Status: `buffer.alloc(Int) -> Buffer(U8)` + `Buffer.bytes() -> Byte[]` on
> JVM and JS (`JsRuntimeBuffer`, same contract) · Native = honest gap · the
> FFI out-buffer face is `Buffer(U8)` INOUT (see [40 — Low Level](../40-low-level.md)).**

| Face | Members (measured) |
|------|--------------------|
| `buffer` | `alloc(Int size) -> Buffer(U8)` |
| `Buffer(U8)` | `bytes() -> Byte[]` |

```kf
var buf = buffer.alloc(1024)          // nominal Buffer(U8) — NOT a String, NOT a List
extern "libdevice.so" device_read(Int n, Buffer(U8) out): Int
var n = device_read(1024, buf)
println(buf.bytes().size)             // bytes came back through the out-buffer
```

- A buffer is a DISTINCT ABI kind: length + direction + mutability — it is
  NEVER a reuse of `String`/`Byte[]` (`D6`, 20–21/09: copy-in / call /
  copy-back).
- Lifetime is owned by the handle (`D-R3-HANDLE-LIFETIME`), not by GC
  heuristics.

**See also:** [40 — Low Level](../40-low-level.md) — the FFI out-buffer face.
