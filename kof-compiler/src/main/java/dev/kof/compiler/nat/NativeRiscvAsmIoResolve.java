package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 24/09): fatia 10 — kof_io_path_resolve
// no cross (riscv64 + aarch64). Port de RuntimeIo1.kof_io_path_resolve:
//   child absoluto (child[0]=='/') -> child
//   base vazio                      -> child
//   base termina em '/'             -> base + child
//   senao                           -> base + "/" + child
// Usa kof_string_from_literal + kof_string_concat (ja presentes no cross).
public final class NativeRiscvAsmIoResolve {

    private NativeRiscvAsmIoResolve() {}

    static String RISCV_RUNTIME_ASM_IO_RESOLVE = """
            .section .rodata
            .Lkof_iopr_slash:
                .byte 47
            .section .text
            # kof_io_path_resolve(base@a0, child@a1) -> KofStr*
            .globl kof_io_path_resolve
            .type kof_io_path_resolve, @function
            kof_io_path_resolve:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                mv   s0, a0                 # base
                mv   s1, a1                 # child
                lbu  t0, 24(s1)
                li   t1, 47
                beq  t0, t1, .Lkof_iopr_b
                lw   t0, 16(s0)
                beqz t0, .Lkof_iopr_b
                add  t1, s0, t0
                addi t1, t1, 24
                lbu  t1, -1(t1)             # base.last
                li   t2, 47
                beq  t1, t2, .Lkof_iopr_concat
                la   a0, .Lkof_iopr_slash
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat      # base + "/"
                mv   a1, s1
                call kof_string_concat      # + child
                j    .Lkof_iopr_ret
            .Lkof_iopr_concat:
                mv   a0, s0
                mv   a1, s1
                call kof_string_concat
                j    .Lkof_iopr_ret
            .Lkof_iopr_b:
                mv   a0, s1
            .Lkof_iopr_ret:
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            """;
}
