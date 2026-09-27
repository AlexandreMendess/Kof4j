package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 26/09): fatia 13 — kof_io_dir_delete
// RECURSIVO no cross (riscv64 + aarch64), alinhado ao contrato JVM
// (JvmRuntimeIo.java:232-248: walk + reverseOrder + deleteIfExists, 1/0).
// Estrategia: stat -> se nao existe devolve 0; se dir, abre (getdents64) e
// recursa em cada entrada (pulando . e ..) montando child = path + "/" + name,
// depois unlinkat(AT_REMOVEDIR); se nao-dir, unlinkat(0). Ausente/falha = 0.
// Syscalls riscv64/aarch64 (generic): newfstatat=79, openat=56, getdents64=61,
// close=57, unlinkat=35; AT_FDCWD=-100, AT_REMOVEDIR=512, O_RDONLY=0.
// KofStr: len@16, bytes@24; struct stat st_mode@+16; S_IFMT=0xF000, S_IFDIR=0x4000.
public final class NativeRiscvAsmIoDirDelete {

    private NativeRiscvAsmIoDirDelete() {}

    static String RISCV_RUNTIME_ASM_IO_DIRDELETE = """
            .section .rodata
            .Lkof_iodd_slash:
                .byte 47
            .section .text
            # kof_io_dir_delete(path@a0) -> 1|0
            .globl kof_io_dir_delete
            .type kof_io_dir_delete, @function
            kof_io_dir_delete:
                addi sp, sp, -240
                sd   ra, 232(sp)
                sd   s0, 224(sp)
                sd   s1, 216(sp)
                sd   s2, 208(sp)
                sd   s3, 200(sp)
                sd   s4, 192(sp)
                sd   s5, 184(sp)
                mv   s0, a0
                li   a7, 79                 # newfstatat(AT_FDCWD, path+24, statbuf@sp, 0)
                li   a0, -100
                addi a1, s0, 24
                mv   a2, sp
                li   a3, 0
                ecall
                bltz a0, .Lkof_iodd_zero
                lw   t0, 16(sp)             # st_mode
                li   t1, 0xF000
                and  t0, t0, t1
                li   t2, 0x4000
                bne  t0, t2, .Lkof_iodd_file
                li   a7, 56                 # openat(AT_FDCWD, path+24, O_RDONLY, 0)
                li   a0, -100
                addi a1, s0, 24
                li   a2, 0
                li   a3, 0
                ecall
                bltz a0, .Lkof_iodd_zero
                mv   s1, a0
                li   a0, 8192
                call kof_alloc
                mv   s2, a0
            .Lkof_iodd_loop:
                li   a7, 61                 # getdents64(fd, buf, 8192)
                mv   a0, s1
                mv   a1, s2
                li   a2, 8192
                ecall
                blez a0, .Lkof_iodd_loop_done
                mv   s3, a0
                mv   s4, s2
            .Lkof_iodd_entry:
                lhu  t0, 16(s4)             # d_reclen
                beqz t0, .Lkof_iodd_loop_done
                lbu  t1, 19(s4)             # d_name[0]
                li   t2, 46
                bne  t1, t2, .Lkof_iodd_add
                lbu  t1, 20(s4)
                beqz t1, .Lkof_iodd_skip
                li   t2, 46
                bne  t1, t2, .Lkof_iodd_add
                lbu  t1, 21(s4)
                beqz t1, .Lkof_iodd_skip
            .Lkof_iodd_add:
                addi a0, s4, 19
                call kof_io_strlen
                mv   a1, a0
                addi a0, s4, 19
                call kof_io_make_string
                mv   s5, a0
                la   a0, .Lkof_iodd_slash
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat      # path + "/"
                mv   a1, s5
                call kof_string_concat      # + name
                call kof_io_dir_delete      # recursa
            .Lkof_iodd_skip:
                lhu  t0, 16(s4)
                add  s4, s4, t0
                add  t1, s2, s3
                blt  s4, t1, .Lkof_iodd_entry
                j    .Lkof_iodd_loop
            .Lkof_iodd_loop_done:
                li   a7, 57                 # close(fd)
                mv   a0, s1
                ecall
                li   a7, 35                 # unlinkat(AT_FDCWD, path+24, AT_REMOVEDIR)
                li   a0, -100
                addi a1, s0, 24
                li   a2, 512
                ecall
                bnez a0, .Lkof_iodd_zero
                li   a0, 1
                j    .Lkof_iodd_done
            .Lkof_iodd_file:
                li   a7, 35                 # unlinkat(AT_FDCWD, path+24, 0)
                li   a0, -100
                addi a1, s0, 24
                li   a2, 0
                ecall
                bnez a0, .Lkof_iodd_zero
                li   a0, 1
                j    .Lkof_iodd_done
            .Lkof_iodd_zero:
                li   a0, 0
            .Lkof_iodd_done:
                ld   s5, 184(sp)
                ld   s4, 192(sp)
                ld   s3, 200(sp)
                ld   s2, 208(sp)
                ld   s1, 216(sp)
                ld   s0, 224(sp)
                ld   ra, 232(sp)
                addi sp, sp, 240
                ret
            """;
}
