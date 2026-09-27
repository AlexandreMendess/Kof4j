package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 26/09): fatia 16 — kof_io_file_move_to
// no cross (riscv64 + aarch64). Contrato JVM: Files.move SEM REPLACE_EXISTING ->
// destino existente = 0; sucesso = 1. Implementacao: newfstatat(79) no destino
// (existe -> 0), senao renameat2(276, flags=0). KofStr bytes@24; AT_FDCWD=-100.
// NOTA: renameat(38) e o numero correto no kernel, mas o qemu-riscv64 8.2.2
// nao o despacha ("Unknown syscall 38"); renameat2(276) e universal e flags=0
// tem semantica de renameat.
public final class NativeRiscvAsmIoMove {

    private NativeRiscvAsmIoMove() {}

    static String RISCV_RUNTIME_ASM_IO_MOVE = """
            .section .text
            # kof_io_file_move_to(src@a0, dst@a1) -> 1|0
            .globl kof_io_file_move_to
            .type kof_io_file_move_to, @function
            kof_io_file_move_to:
                addi sp, sp, -184
                sd   ra, 176(sp)
                sd   s0, 168(sp)
                sd   s1, 160(sp)
                mv   s0, a0
                mv   s1, a1
                li   a7, 79                 # newfstatat(AT_FDCWD, dst, sp, 0)
                li   a0, -100
                addi a1, s1, 24
                mv   a2, sp
                li   a3, 0
                ecall
                bltz a0, .Lkof_iomv_do
                li   a0, 0                  # dst existe -> sem sobrescrever
                j    .Lkof_iomv_ret
            .Lkof_iomv_do:
                li   a7, 276                # renameat2(AT_FDCWD, src, AT_FDCWD, dst, 0)
                li   a0, -100
                addi a1, s0, 24
                li   a2, -100
                addi a3, s1, 24
                li   a4, 0
                ecall
                bltz a0, .Lkof_iomv_no
                li   a0, 1
                j    .Lkof_iomv_ret
            .Lkof_iomv_no:
                li   a0, 0
            .Lkof_iomv_ret:
                ld   ra, 176(sp)
                ld   s0, 168(sp)
                ld   s1, 160(sp)
                addi sp, sp, 184
                ret
            """;
}
