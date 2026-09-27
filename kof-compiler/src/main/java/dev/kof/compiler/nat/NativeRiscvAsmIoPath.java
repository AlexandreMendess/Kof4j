package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 24/09): fatia 9 — faces puras de
// path do kof.io (sem syscall) no cross (riscv64 + aarch64). Port de RuntimeIo1:
//   kof_io_strip_trailing  len sem '/' finais (len<=1 nao mexe)
//   kof_io_path_is_absolute  1 se len>0 e byte[0]=='/'
//   kof_io_file_name       ultimo componente apos o ultimo '/'; sub vazio -> a
//                          propria path; sem '/' -> a propria path
//   kof_io_path_file_name  alias de kof_io_file_name
//   kof_io_path_parent     prefixo antes do ultimo '/' (i==0 -> "/"); sem '/' -> 0
//   kof_io_path_extension  extensao sem o '.'; ausente -> "" (make_string(".",0))
// KofStr: len@16, bytes@24.
public final class NativeRiscvAsmIoPath {

    private NativeRiscvAsmIoPath() {}

    static String RISCV_RUNTIME_ASM_IO_PATH = """
            .section .rodata
            .Lkof_iopath_slash:
                .byte 47
            .Lkof_iopath_dot:
                .byte 46
            .section .text

            # kof_io_strip_trailing(path@a0) -> len em a0
            .globl kof_io_strip_trailing
            .type kof_io_strip_trailing, @function
            kof_io_strip_trailing:
                lw   t0, 16(a0)
                addi t1, a0, 24
            .Lkof_iost_loop:
                li   t2, 1
                ble  t0, t2, .Lkof_iost_done
                add  t3, t1, t0
                lbu  t4, -1(t3)
                li   t5, 47
                bne  t4, t5, .Lkof_iost_done
                addi t0, t0, -1
                j    .Lkof_iost_loop
            .Lkof_iost_done:
                mv   a0, t0
                ret

            # kof_io_path_is_absolute(path@a0) -> 1|0
            .globl kof_io_path_is_absolute
            .type kof_io_path_is_absolute, @function
            kof_io_path_is_absolute:
                lw   t0, 16(a0)
                blez t0, .Lkof_ioabs_no
                lbu  t1, 24(a0)
                li   t2, 47
                bne  t1, t2, .Lkof_ioabs_no
                li   a0, 1
                ret
            .Lkof_ioabs_no:
                li   a0, 0
                ret

            # kof_io_file_name(path@a0) -> KofStr*
            .globl kof_io_file_name
            .type kof_io_file_name, @function
            kof_io_file_name:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                mv   s0, a0
                call kof_io_strip_trailing
                mv   s1, a0                 # len
                addi s2, s1, -1             # i
            .Lkof_iofn_find:
                bltz s2, .Lkof_iofn_noslash
                add  t0, s0, s2
                lbu  t0, 24(t0)
                li   t1, 47
                beq  t0, t1, .Lkof_iofn_found
                addi s2, s2, -1
                j    .Lkof_iofn_find
            .Lkof_iofn_found:
                addi t0, s2, 1              # start = i+1
                sub  s3, s1, t0             # sub = len - start
                blez s3, .Lkof_iofn_self
                addi a0, s0, 24
                add  a0, a0, t0
                mv   a1, s3
                call kof_io_make_string
                j    .Lkof_iofn_ret
            .Lkof_iofn_self:
                mv   a0, s0
                j    .Lkof_iofn_ret
            .Lkof_iofn_noslash:
                addi a0, s0, 24
                mv   a1, s1
                call kof_io_make_string
            .Lkof_iofn_ret:
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            .globl kof_io_path_file_name
            .type kof_io_path_file_name, @function
            kof_io_path_file_name:
                j    kof_io_file_name

            # kof_io_path_parent(path@a0) -> KofStr*|0
            .globl kof_io_path_parent
            .type kof_io_path_parent, @function
            kof_io_path_parent:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                mv   s0, a0
                call kof_io_strip_trailing
                mv   s1, a0
                addi s2, s1, -1
            .Lkof_iopp_find:
                bltz s2, .Lkof_iopp_none
                add  t0, s0, s2
                lbu  t0, 24(t0)
                li   t1, 47
                beq  t0, t1, .Lkof_iopp_found
                addi s2, s2, -1
                j    .Lkof_iopp_find
            .Lkof_iopp_found:
                bnez s2, .Lkof_iopp_prefix
                la   a0, .Lkof_iopath_slash
                li   a1, 1
                call kof_io_make_string
                j    .Lkof_iopp_ret
            .Lkof_iopp_prefix:
                addi a0, s0, 24
                mv   a1, s2
                call kof_io_make_string
                j    .Lkof_iopp_ret
            .Lkof_iopp_none:
                li   a0, 0
            .Lkof_iopp_ret:
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_io_path_extension(path@a0) -> KofStr*
            .globl kof_io_path_extension
            .type kof_io_path_extension, @function
            kof_io_path_extension:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                mv   s0, a0
                call kof_io_strip_trailing
                mv   s1, a0                 # len
                addi s2, s1, -1             # r14: slash idx
            .Lkof_ioext_slash:
                bltz s2, .Lkof_ioext_dot_from
                add  t0, s0, s2
                lbu  t0, 24(t0)
                li   t1, 47
                beq  t0, t1, .Lkof_ioext_dot_from
                addi s2, s2, -1
                j    .Lkof_ioext_slash
            .Lkof_ioext_dot_from:
                addi s3, s1, -1             # j
            .Lkof_ioext_dot:
                ble  s3, s2, .Lkof_ioext_empty
                add  t0, s0, s3
                lbu  t0, 24(t0)
                li   t1, 46
                beq  t0, t1, .Lkof_ioext_found
                addi s3, s3, -1
                j    .Lkof_ioext_dot
            .Lkof_ioext_found:
                sub  s4, s1, s3
                addi s4, s4, -1             # ext len
                blez s4, .Lkof_ioext_empty
                addi a0, s0, 24
                add  a0, a0, s3
                addi a0, a0, 1
                mv   a1, s4
                call kof_io_make_string
                j    .Lkof_ioext_ret
            .Lkof_ioext_empty:
                la   a0, .Lkof_iopath_dot
                li   a1, 0
                call kof_io_make_string
            .Lkof_ioext_ret:
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            """;
}
