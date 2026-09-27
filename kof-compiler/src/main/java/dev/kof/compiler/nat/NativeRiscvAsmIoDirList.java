package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 24/09): fatia 7 — dir listing
// (kof.io Directory.list()) no cross (riscv64 + aarch64; aarch64 herda pelo
// tradutor). Port de RuntimeIo3.kof_io_dir_list:
//   openat(AT_FDCWD, path+24, O_RDONLY=0, 0); loop getdents64(=61) num
//   buffer kof_alloc(8192); para cada linux_dirent64 pula "."/".." e adiciona o
//   nome (kof_io_strlen + kof_io_make_string + kof_list_add); fecha; insertion
//   sort no backing array (ponteiros 8B, igual x86) com comparacao byte-a-byte
//   unsigned (.Lkof_iodl_less). Em erro de openat retorna 0 (null), como o JVM.
// List layout (RtB0): size@16, backing ptr@24, stride 8.
// Syscalls riscv64/aarch64: openat=56, close=57, getdents64=61.
//
// NAO usar O_DIRECTORY aqui: o valor e ARCH-DIVERGENTE (riscv64 generic
// 0x10000 x arm64 0x4000). Como a mesma asm serve as duas arches, abrimos com
// O_RDONLY (0) — getdents64 funciona num diretorio aberto O_RDONLY em ambas;
// com 0x10000 no aarch64 o openat da EINVAL (achado 24/09, NativeIoDirListCrossTest).
public final class NativeRiscvAsmIoDirList {

    private NativeRiscvAsmIoDirList() {}

    static String RISCV_RUNTIME_ASM_IO_DIRLIST = """
            .section .text
            # kof_io_dir_list(path@a0) -> List|0
            .globl kof_io_dir_list
            .type kof_io_dir_list, @function
            kof_io_dir_list:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   s0, 80(sp)
                sd   s1, 72(sp)
                sd   s2, 64(sp)
                sd   s3, 56(sp)
                sd   s4, 48(sp)
                sd   s5, 40(sp)
                sd   s6, 32(sp)
                sd   s7, 24(sp)
                mv   s0, a0
                li   a0, -100
                addi a1, s0, 24
                li   a2, 0                  # O_RDONLY
                li   a3, 0
                li   a7, 56
                ecall
                bltz a0, .Lkof_iodl_err
                mv   s1, a0                 # fd
                li   a0, 8192
                call kof_alloc
                mv   s2, a0                 # buf
                call kof_list_new
                mv   s3, a0                 # list
            .Lkof_iodl_loop:
                mv   a0, s1
                mv   a1, s2
                li   a2, 8192
                li   a7, 61                 # getdents64
                ecall
                blez a0, .Lkof_iodl_done
                mv   s4, a0                 # bytes lidos
                add  s7, s2, s4             # end
                mv   s5, s2                 # cursor
            .Lkof_iodl_entry:
                bgeu s5, s7, .Lkof_iodl_loop
                lh   t0, 16(s5)             # d_reclen
                beqz t0, .Lkof_iodl_loop
                lbu  t1, 19(s5)             # d_name[0]
                li   t2, 46                 # '.'
                bne  t1, t2, .Lkof_iodl_add
                lbu  t1, 20(s5)
                beqz t1, .Lkof_iodl_skip    # "."
                li   t2, 46
                bne  t1, t2, .Lkof_iodl_add
                lbu  t1, 21(s5)
                beqz t1, .Lkof_iodl_skip    # ".."
            .Lkof_iodl_add:
                addi a0, s5, 19
                call kof_io_strlen
                mv   a1, a0
                addi a0, s5, 19
                call kof_io_make_string
                mv   a1, a0
                mv   a0, s3
                call kof_list_add
            .Lkof_iodl_skip:
                lh   t0, 16(s5)
                add  s5, s5, t0
                j    .Lkof_iodl_entry
            .Lkof_iodl_done:
                mv   a0, s1
                li   a7, 57
                ecall
                mv   a0, s3
                call .Lkof_iodl_sort
                mv   a0, s3
                j    .Lkof_iodl_ret
            .Lkof_iodl_err:
                li   a0, 0
            .Lkof_iodl_ret:
                ld   s7, 24(sp)
                ld   s6, 32(sp)
                ld   s5, 40(sp)
                ld   s4, 48(sp)
                ld   s3, 56(sp)
                ld   s2, 64(sp)
                ld   s1, 72(sp)
                ld   s0, 80(sp)
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret

            # insertion sort do backing array (ponteiros 8B); a0 = list
            .type kof_io_dir_list_sort, @function
            .Lkof_iodl_sort:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                mv   s0, a0
                lw   s1, 16(s0)             # n
                ld   s2, 24(s0)             # data
                li   s3, 1                  # i
            .Lkof_iodl_s_i:
                bge  s3, s1, .Lkof_iodl_s_done
                slli t0, s3, 3
                add  t0, s2, t0
                ld   s4, 0(t0)              # key
                addi s5, s3, -1             # j
            .Lkof_iodl_s_j:
                bltz s5, .Lkof_iodl_s_place
                slli t0, s5, 3
                add  t0, s2, t0
                ld   a0, 0(t0)              # data[j]
                mv   a1, s4
                call .Lkof_iodl_less
                bnez a0, .Lkof_iodl_s_place
                slli t0, s5, 3
                add  t0, s2, t0
                ld   t1, 0(t0)
                addi t2, s5, 1
                slli t2, t2, 3
                add  t2, s2, t2
                sd   t1, 0(t2)
                addi s5, s5, -1
                j    .Lkof_iodl_s_j
            .Lkof_iodl_s_place:
                addi t2, s5, 1
                slli t2, t2, 3
                add  t2, s2, t2
                sd   s4, 0(t2)
                addi s3, s3, 1
                j    .Lkof_iodl_s_i
            .Lkof_iodl_s_done:
                ld   s5, 8(sp)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # less(a@a0, b@a1) -> 1 se a < b (bytes unsigned), senao 0
            .type kof_io_dir_list_less, @function
            .Lkof_iodl_less:
                lw   t0, 16(a0)             # la
                lw   t1, 16(a1)             # lb
                li   t2, 0
            .Lkof_iodl_less_loop:
                bge  t2, t0, .Lkof_iodl_less_ldone
                bge  t2, t1, .Lkof_iodl_less_false
                add  t3, a0, t2
                lbu  t3, 24(t3)
                add  t4, a1, t2
                lbu  t4, 24(t4)
                bltu t3, t4, .Lkof_iodl_less_true
                bltu t4, t3, .Lkof_iodl_less_false
                addi t2, t2, 1
                j    .Lkof_iodl_less_loop
            .Lkof_iodl_less_ldone:
                blt  t2, t1, .Lkof_iodl_less_true
            .Lkof_iodl_less_false:
                li   a0, 0
                ret
            .Lkof_iodl_less_true:
                li   a0, 1
                ret
            """;
}
