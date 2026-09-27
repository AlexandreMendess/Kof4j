[English](40-low-level.md) | [Português](40-low-level.pt_BR.md)

# 40 — Low Level: FFI, Native Profiles and Bare Metal

> **Status: implemented (JVM / Native x86-64 / riscv64 / aarch64 / JS) — 0.5.0-beta**
>
> Kof compiles to real machine code (own assembler + linker, System V AMD64
> ABI) and binds real C libraries. This chapter is the surface: `extern` for
> C FFI, `--profile` for freestanding executables, and the bare-metal path.

## `extern` — bind a C function

```kf
extern "/lib/x86_64-linux-gnu/libm.so.6" cos(Double x): Double
extern "/lib/x86_64-linux-gnu/libc.so.6" atoi(String s): Int

main() {
    println(cos(0.0))   // 1.0
    println(atoi("42")) // 42
}
```

- The Kof function NAME is the C symbol (no alias syntax).
- Full SCALAR signatures bind: `Int`, `Long`, `Float`, `Double`, `Boolean`,
  `String` (char\* ↔ UTF-8 `String`) and `void`, in every position, any arity.
- Numeric arguments follow the ordinary Kof conversion rule: `f(4)` into a
  `Float` slot converts (Int→Float), `sqrt(9)` (Int→Double slot) — the same
  result on JVM, Native and JS. What Kof does NOT convert (a `String` in a
  numeric slot, `Double`→`Int` narrowing) is **SEM014** at the call site.
- A `record` of scalar fields crosses BY VALUE (arg and return); a scalar
  `T[]` crosses as a pointer with copy-in; a `Buffer(U8)` crosses INOUT.
- Diagnostics are honest: non-scalar/unsupported shape or missing library is
  **FFI001** at the declaration line; genuinely unsupported shapes (String[],
  List, Handle) are **FFI002**. Never a silent stub.

## Callbacks — hand a Kof function to C

```kf
extern "libcallback.so" kof_cb_add(Int a, Int b, (Int, Int) -> Int cb): Int

main() {
    println(kof_cb_add(20, 22, (x: Int, y: Int) -> x + y))  // 42
}
```

The lambda becomes a real C function pointer (synchronous, non-escaping;
primitive + String-arg ABI). On the JS host runner callbacks bind with
byte-for-byte JVM parity; a browser degrades with an honest runtime error.

## Per-target support

| Face | JVM | Native x86-64 | riscv64/aarch64 | JS |
|------|-----|----------------|------------------|-----|
| Scalar `extern` | ✅ FFM downcall | ✅ direct (link-by-use) | ✅ qemu-measured | ✅ host bridge |
| Callbacks | ✅ upcall stub | FFI001 (honest) | FFI001 | ✅ host runner |
| Struct / array / out-buffer | ✅ | FFI001 | FFI001 | ✅ (JVM==JS) |

String return on Native is a boundary copy (the C buffer is never freed);
`NULL` char\* maps to Kof `null`.

## Native profiles — `--profile`

```bash
kof build app/ --target native --profile freestanding
```

- `host` (default): the standard ELF with the full Kof runtime.
- `freestanding`: no libc — the runtime provides the `kof_plat_*` HAL seams
  (print/exit/random/alloc) itself; the same Kof source compiles.

The compiler also carries the bare-metal profiles (`bios`/`mbr`, `uefi`,
`uefi-ring`) used by the B-series front — the payload boots through SeaBIOS
under qemu. They are not yet exposed as a shipped CLI flag; when they are,
this chapter is the contract that describes them.

## What the native backend really emits

```kf
main() = print("Hello")
```

compiles (no toolchain — the assembler and linker are Kof's own) to an ELF
whose `_start` issues raw syscalls. Details, backend options and the
multi-arch story live in [Native — Multiplatform](native/README.md).

## Next step

[Glossary →](glossary.md)
