package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 26/09): fatia 15 — metadados de
// arquivo no cross (riscv64 + aarch64): kof_io_file_modified_time (millis;
// ausente LANCA "file not found: " + path, igual a JVM/x86) e
// kof_io_file_is_symlink (lstat via AT_SYMLINK_NOFOLLOW=256; 1/0).
// Syscalls: newfstatat=79. struct stat: st_mode@+16, st_mtime@+88, mtime_nsec@+96.
// KofStr: len@16, bytes@24. S_IFMT=0xF000, S_IFLNK=0xA000.
public final class NativeRiscvAsmIoMeta {

    private NativeRiscvAsmIoMeta() {}

    static String RISCV_RUNTIME_ASM_IO_META = """
            .section .rodata
            .Lkof_iomt_prefix:
                .ascii "file not found: "
            .section .text
            # kof_io_file_modified_time(path@a0) -> millis
            .globl kof_io_file_modified_time
            .type kof_io_file_modified_time, @function
            kof_io_file_modified_time:
                addi sp, sp, -184
                sd   ra, 176(sp)
                sd   s0, 168(sp)
                mv   s0, a0
                li   a7, 79
                li   a0, -100
                addi a1, s0, 24
                mv   a2, sp
                li   a3, 0
                ecall
                bltz a0, .Lkof_iomt_err
                ld   t0, 88(sp)             # tv_sec
                li   t1, 1000
                mul  t0, t0, t1
                ld   t1, 96(sp)             # tv_nsec
                li   t2, 1000000
                li   t3, 0
            .Lkof_iomt_div:
                blt  t1, t2, .Lkof_iomt_div_done
                sub  t1, t1, t2
                addi t3, t3, 1
                j    .Lkof_iomt_div
            .Lkof_iomt_div_done:
                add  a0, t0, t3
                j    .Lkof_iomt_ret
            .Lkof_iomt_err:
                la   a0, .Lkof_iomt_prefix
                li   a1, 16
                call kof_string_from_literal
                mv   a1, s0
                call kof_string_concat
                call kof_throw_string
            .Lkof_iomt_ret:
                ld   ra, 176(sp)
                ld   s0, 168(sp)
                addi sp, sp, 184
                ret

            # kof_io_file_is_symlink(path@a0) -> 1|0
            .globl kof_io_file_is_symlink
            .type kof_io_file_is_symlink, @function
            kof_io_file_is_symlink:
                addi sp, sp, -184
                sd   ra, 176(sp)
                sd   s0, 168(sp)
                mv   s0, a0
                li   a7, 79
                li   a0, -100
                addi a1, s0, 24
                mv   a2, sp
                li   a3, 256
                ecall
                bltz a0, .Lkof_iosl_no
                lw   t0, 16(sp)
                li   t1, 0xF000
                and  t0, t0, t1
                li   t2, 0xA000
                bne  t0, t2, .Lkof_iosl_no
                li   a0, 1
                j    .Lkof_iosl_ret
            .Lkof_iosl_no:
                li   a0, 0
            .Lkof_iosl_ret:
                ld   ra, 176(sp)
                ld   s0, 168(sp)
                addi sp, sp, 184
                ret
            """;
}
