# kof-c-compiler cross targets — sliced plan (C1–C4)

[English](kof-c-cross.md) | [Português](kof-c-cross.pt_BR.md)

**Status:** COMPLETE — **C1 + C2 + C3 + C4 + C3-residual LANDED** (23/09). Owner: FFI/kof-c front. Moved to `docs/` per three-states rule.

## Why

The in-repo C compiler (`kof-c-compiler`, CLI `kof c`) only emitted x86-64
freestanding executables. The Native cross FFI tests need a C fixture with
**struct-by-value parameters**, and this host has no cross C compiler (no
`gcc`/`clang`/`zig` for riscv64/aarch64 — only binutils + qemu, mounted by
`scripts/setup-cross-toolchain.sh` in `/tmp/kof-cross`). Instead of depending
on an external cross cc, the maintainer approved growing the in-repo compiler
to emit the cross targets.

This is a multi-session effort; the end goal is a C fixture the Native cross
FFI tests can link (unblocking the struct-param slice).

## Baseline subset

`int g;` globals and functions with `int` parameters/return and locals —
`signed int` only, no floats. Control flow `if`/`while`, integer /
bitwise / comparison / shift binary ops (one operator per expression without
parentheses), `&ident` and `*(int*)ident` deref/addr, calls with up to six
register arguments, `print`/`print_arg`, raw `asm` bytes. `struct` types with
`int` fields, member access and struct-by-value parameters (≤ 8 B). Free-standing:
raw syscalls only, no libc.

## Slices

- **C1 — LANDED (22/09):** target-aware emission.
  `KofCTarget` (`x86_64`/`riscv64`/`aarch64`), `KofCEmitter` interface,
  `KofCEmitterBase` (shared AST walk) and per-ISA emitters
  `KofCEmitterX86` / `KofCEmitterRiscv` / `KofCEmitterAarch`;
  `KofCCompiler.compile(path, out, target)` picks the target assembler and
  linker; `kof c --target` exposes it. Proof: the whole subset is
  byte-identical on riscv64/aarch64 under qemu and matches the x86_64 oracle
  (`KofCCrossCompilerTest` 7/7; `KofCCompilerTest` 7/7 unchanged). Missing
  cross toolchain → honest skip (`assumeTrue`).
- **C2 — LANDED (22/09):** parameters, return values, locals and calls
  (`int f(int a, int b) { int t; ... return t; }`) on the three ISAs with the
  C ABI. Frame: saved frame/return pair + one 8-byte slot per parameter/local
  (`rbp`/`s0`/`x29` base). Arguments follow SysV (`rdi,rsi,rdx,rcx,r8,r9`),
  LP64 (`a0..a5`) and AAPCS64 (`x0..x5`) — up to six register arguments; the
  return lands in the accumulator (`rax`/`a0`/`x0`), which is the ABI return
  register on every target. Calls evaluate arguments onto the stack and pop
  them into the argument registers, so a later argument can reuse the
  accumulator without clobbering an earlier one. Honest diagnostics:
  unknown call, arity mismatch, `print()` with arguments and more than six
  parameters/arguments are rejected before any binary is emitted (R6/Q7).
  Proof: `KofCParamsCompilerTest` 7/7 on x86_64/riscv64/aarch64 (two-parameter
  return, locals + loop, void mutation of a global, nested calls, early
  return, pointer parameter dereferenced in the callee, all six argument
  registers) plus the four rejection cases.
- **C3 — LANDED (22/09, first cut):** `struct` types with **4-byte C `int`
  fields** (matching `AbiLayout.Scalar.INT`) and **by-value struct parameters**
  up to **8 bytes** (one eightbyte). A struct local/global is a single 8-byte
  slot; field access loads/stores 32-bit (`movsxd`/`lw`/`ldursw`) sign-extended;
  an ≤ 8 B struct argument travels in one integer register — the exact path the
  libc `div_t` uses. Grammar: `struct S { int a; int b; };`, `struct S v;`
  (global/local/param) and `v.field`. Honest diagnostics: a struct larger than
  8 bytes, an unknown struct, an unknown field or a field on a non-struct are
   rejected before any binary is emitted (R6/Q7). Proof: `KofCStructCompilerTest`
   7/7 on x86_64/riscv64/aarch64 (field round trip, by-value parameter, negative
   field sign-extension, struct + scalar args mixed, global struct) plus the four
   rejection cases.
- **C3-residual — LANDED (23/09):** struct **return by value** (≤16 B) + **multi-eightbyte parameters** (≤48 B / 6 eightbytes). Return ≤8 B in the accumulator (`rax`/`a0`/`x0`), 9–16 B in `rax+rdx` / `a0+a1` / `x0+x1` (SysV/LP64/AAPCS64 pair — documented deviation from SysV memory for 16 B, chosen for parity). Parameters classified per eightbyte (INTEGER only — float/HFA → `FFI001`), packed from the Kof `record` fields and popped in stack order. Frame: variable of `k` eightbytes occupies `k` slots at `low = next+2k-2` (extent `k*8` from `low` downwards, never beyond `rbp`); second return chunk at `low-1`. Honest diagnostics: param >48 B or return >16 B → `at most 48/16` (R6). Proof: `KofCStructCompilerTest` now **14/14** on x86_64/riscv64/aarch64 (adds 3-field 12 B, 5-field 20 B mixed, return 8 B/16 B, arg+return combined) plus 4 rejection cases; cross parity byte-identical to x86 oracle.
- **C4 — LANDED (22/09):** reusable object output plus linking.
  `KofCCompiler.compileObject(cFile, oFile, target)` assembles a standalone
  `.o` — no `_start` and **no `main` requirement** — with every defined function
  emitted as `.globl`; the print helpers are only emitted when `print()` is
  actually called, so an object that does not print carries no unused globals to
  collide at link time. `compile(cFile, outDir, target, extraObjects)` appends
  fixture objects to the `ld` line, and external functions are declared with a
  plain C prototype (`int f(int a);` — resolved at link, validated for arity).
  `kof c -c` exposes object mode. Proof: `KofCObjectCompilerTest` 5/5 — a
  struct-by-value fixture (`int take(struct Pair p)`) built as an object on
  x86_64/riscv64/aarch64 and linked into a driver that only sees the prototype,
  executed under qemu and printing the golden `42`; plus object-without-`main`
  succeeds and executable-without-`main` still fails. This is the path the
  Native cross FFI struct-param fixture consumes.
- **Scope boundary on `int` width:** scalar `int` variables keep the toy's
  8-byte slot model (pointer-holding `int`s like `int p; p = &x;` rely on it);
  the 32-bit C width is implemented where the C ABI observes memory layout —
  struct fields and struct-by-value packing. Switching plain scalar `int` to 32
  bits needs a real pointer type (`int*`) and is a separate slice (rule 6).

## Notes

- **Consuming a fixture object (C4-x, link half):** an `extern` whose
  `library()` is a prebuilt `.o` for the arch enters the cross `ld` line
  positionally (`NativeCrossLink.ffiLinkArg` preserves the path), so a fixture
  built with `compileObject` can be called from Kof (`extern "<path>.o"
  f(...)`). The ABI half (struct-by-value parameter packing on the cross)
  **LANDED as 3.7 fatia 4 (22/09)**: the emitter packs each INTEGER eightbyte
  of the Kof `record` into its integer register (`a0`/`a1`; AAPCS64 `x0`/`x1`
  via the translator), proof `FfiCrossStructParamE2ETest` under qemu on both
  archs (`dev.kof.compiler.nat`).
- **riscv64 `gp` relaxation:** the linker relaxes `la` of nearby globals to
  gp-relative (`addi t0, gp, off`); a raw `_start` does not initialise `gp`,
  so the access faults. `_start` must do
  `la gp, __global_pointer$` under `.option norelax`.
- **Honest gaps:** an unsupported construct must fail with a diagnostic,
  never emit a wrong binary (R6/Q7).
