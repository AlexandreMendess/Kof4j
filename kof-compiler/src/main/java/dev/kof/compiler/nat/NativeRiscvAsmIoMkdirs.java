package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 24/09): fatia 4 — mkdir -p do
// kof.io no cross (riscv64 + aarch64; aarch64 herda pelo tradutor):
// createDirectories()/mkdirs() -> `kof_io_dir_create_dirs`. Port do x86
// (RuntimeIo3.kof_io_dir_create_dirs): percorre a KofStr, a cada '/' cria o
// prefixo [0,i) ignorando EEXIST (-17); no fim cria o caminho inteiro,
// aceitando EEXIST. Syscall mkdirat=34 (identico nas duas archs).
//
// Contrato riscv: path@a0 (KofStr); retorno 1|0 em a0.
public final class NativeRiscvAsmIoMkdirs {

    private NativeRiscvAsmIoMkdirs() {}

    static String RISCV_RUNTIME_ASM_IO_MKDIRS = """
            .section .text
            # kof_io_mkdir_raw(cstr@a0, mode@a1) -> mkdirat ret (uso interno)
            .type kof_io_mkdir_raw, @function
            kof_io_mkdir_raw:
                mv   a2, a1                 # mode
                mv   a1, a0                 # pathname (C-string)
                li   a0, -100               # AT_FDCWD
                li   a7, 34                 # mkdirat
                ecall
                ret

            # kof_io_dir_create_dirs(path@a0) -> 1|0 (mkdir -p)
            .globl kof_io_dir_create_dirs
            .type kof_io_dir_create_dirs, @function
            kof_io_dir_create_dirs:
                addi sp, sp, -560
                sd   ra, 552(sp)
                sd   s0, 544(sp)
                sd   s1, 536(sp)
                sd   s2, 528(sp)
                sd   s3, 520(sp)
                mv   s0, a0                 # path (KofStr)
                lw   s1, 16(s0)             # byteLen
                li   t0, 512
                bgeu s1, t0, .Lkof_iodcd_err
                mv   s3, sp                 # scratch[0..511]
                li   s2, 1                  # i = 1
            .Lkof_iodcd_scan:
                bgeu s2, s1, .Lkof_iodcd_final
                add  t0, s0, s2
                lbu  t0, 24(t0)             # byte i da path
                li   t1, 47                 # '/'
                bne  t0, t1, .Lkof_iodcd_adv
                # copia path[0..i) para scratch e NUL-termina
                li   t2, 0
            .Lkof_iodcd_copy:
                bgeu t2, s2, .Lkof_iodcd_copy_done
                add  t3, s0, t2
                lbu  t3, 24(t3)
                add  t4, s3, t2
                sb   t3, 0(t4)
                addi t2, t2, 1
                j    .Lkof_iodcd_copy
            .Lkof_iodcd_copy_done:
                add  t4, s3, s2
                sb   zero, 0(t4)
                mv   a0, s3
                li   a1, 493                # 0755
                call kof_io_mkdir_raw
                beqz a0, .Lkof_iodcd_adv
                li   t0, -17                # EEXIST
                beq  a0, t0, .Lkof_iodcd_adv
                j    .Lkof_iodcd_err
            .Lkof_iodcd_adv:
                addi s2, s2, 1
                j    .Lkof_iodcd_scan
            .Lkof_iodcd_final:
                addi a0, s0, 24             # path completa
                li   a1, 493
                call kof_io_mkdir_raw
                beqz a0, .Lkof_iodcd_ok
                li   t0, -17                # EEXIST tambem e sucesso
                beq  a0, t0, .Lkof_iodcd_ok
            .Lkof_iodcd_err:
                li   a0, 0
                j    .Lkof_iodcd_done
            .Lkof_iodcd_ok:
                li   a0, 1
            .Lkof_iodcd_done:
                ld   s3, 520(sp)
                ld   s2, 528(sp)
                ld   s1, 536(sp)
                ld   s0, 544(sp)
                ld   ra, 552(sp)
                addi sp, sp, 560
                ret
            """;
}
