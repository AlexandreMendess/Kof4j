package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 24/09): fatia 11 — kof_io_path_normalize
// no cross (riscv64 + aarch64). Port fiel de RuntimeIo1/RuntimeIo2 (x86_64):
// colapsa ".", "..", separadores repetidos e barras finais; preserva raiz "/"
// (nao sobe acima dela). Resultado vazio relativo -> "." (emptyResult).
// KofStr: len@16, bytes@24. kof_alloc(n)@a0, kof_io_make_string(data,len).
public final class NativeRiscvAsmIoNormalize {

    private NativeRiscvAsmIoNormalize() {}

    static String RISCV_RUNTIME_ASM_IO_NORMALIZE = """
            .section .text
            # kof_io_path_normalize(path@a0) -> KofStr*
            .globl kof_io_path_normalize
            .type kof_io_path_normalize, @function
            kof_io_path_normalize:
                addi sp, sp, -640
                sd   ra, 632(sp)
                sd   s0, 624(sp)
                sd   s1, 616(sp)
                sd   s2, 608(sp)
                sd   s3, 600(sp)
                sd   s4, 592(sp)
                sd   s5, 584(sp)
                sd   s6, 576(sp)
                sd   s7, 568(sp)
                sd   s8, 560(sp)
                sd   s9, 552(sp)
                mv   s0, a0                 # path
                sd   zero, 512(sp)          # abs = 0
                lw   t0, 16(s0)
                beqz t0, .Lkof_ionorm_scan
                lbu  t0, 24(s0)
                li   t1, 47
                bne  t0, t1, .Lkof_ionorm_scan
                li   t0, 1
                sd   t0, 512(sp)
            .Lkof_ionorm_scan:
                li   s2, 0                  # segCount
                li   s3, 0                  # i
                li   s4, 0                  # segStart
            .Lkof_ionorm_collect:
                lw   t0, 16(s0)
                bge  s3, t0, .Lkof_ionorm_final
                addi t1, s0, 24
                add  t1, t1, s3
                lbu  t1, 0(t1)
                li   t2, 47
                bne  t1, t2, .Lkof_ionorm_advance
                sub  t3, s3, s4             # seg len
                beqz t3, .Lkof_ionorm_seg_done
                li   t4, 1
                bne  t3, t4, .Lkof_ionorm_check_dotdot
                addi t1, s0, 24
                add  t1, t1, s4
                lbu  t1, 0(t1)
                li   t2, 46
                beq  t1, t2, .Lkof_ionorm_seg_done
            .Lkof_ionorm_check_dotdot:
                li   t4, 2
                bne  t3, t4, .Lkof_ionorm_push
                addi t1, s0, 24
                add  t1, t1, s4
                lbu  t2, 0(t1)
                li   t4, 46
                bne  t2, t4, .Lkof_ionorm_push
                lbu  t2, 1(t1)
                bne  t2, t4, .Lkof_ionorm_push
                blez s2, .Lkof_ionorm_dotdot_empty
                addi s2, s2, -1
                j    .Lkof_ionorm_seg_done
            .Lkof_ionorm_dotdot_empty:
                ld   t0, 512(sp)
                bnez t0, .Lkof_ionorm_seg_done
                slli t0, s2, 3
                add  t0, sp, t0
                sw   s4, 0(t0)
                sw   t3, 4(t0)
                addi s2, s2, 1
                j    .Lkof_ionorm_seg_done
            .Lkof_ionorm_push:
                slli t0, s2, 3
                add  t0, sp, t0
                sw   s4, 0(t0)
                sw   t3, 4(t0)
                addi s2, s2, 1
            .Lkof_ionorm_seg_done:
                addi s3, s3, 1
                mv   s4, s3
                j    .Lkof_ionorm_collect
            .Lkof_ionorm_advance:
                addi s3, s3, 1
                j    .Lkof_ionorm_collect
            .Lkof_ionorm_final:
                sub  t3, s3, s4
                beqz t3, .Lkof_ionorm_build
                li   t4, 1
                bne  t3, t4, .Lkof_ionorm_final_dotdot
                addi t1, s0, 24
                add  t1, t1, s4
                lbu  t1, 0(t1)
                li   t2, 46
                beq  t1, t2, .Lkof_ionorm_build
            .Lkof_ionorm_final_dotdot:
                li   t4, 2
                bne  t3, t4, .Lkof_ionorm_final_push
                addi t1, s0, 24
                add  t1, t1, s4
                lbu  t2, 0(t1)
                li   t4, 46
                bne  t2, t4, .Lkof_ionorm_final_push
                lbu  t2, 1(t1)
                bne  t2, t4, .Lkof_ionorm_final_push
                blez s2, .Lkof_ionorm_final_dotdot_empty
                addi s2, s2, -1
                j    .Lkof_ionorm_build
            .Lkof_ionorm_final_dotdot_empty:
                ld   t0, 512(sp)
                bnez t0, .Lkof_ionorm_build
                slli t0, s2, 3
                add  t0, sp, t0
                sw   s4, 0(t0)
                sw   t3, 4(t0)
                addi s2, s2, 1
                j    .Lkof_ionorm_build
            .Lkof_ionorm_final_push:
                slli t0, s2, 3
                add  t0, sp, t0
                sw   s4, 0(t0)
                sw   t3, 4(t0)
                addi s2, s2, 1
            .Lkof_ionorm_build:
                li   t5, 0                  # total
                ld   t0, 512(sp)
                beqz t0, .Lkof_ionorm_total_segs
                li   t5, 1
            .Lkof_ionorm_total_segs:
                blez s2, .Lkof_ionorm_total_done
                li   t6, 0
            .Lkof_ionorm_total_loop:
                bge  t6, s2, .Lkof_ionorm_total_done
                slli t0, t6, 3
                add  t0, sp, t0
                lw   t1, 4(t0)
                add  t5, t5, t1
                beqz t6, .Lkof_ionorm_total_next
                addi t5, t5, 1
            .Lkof_ionorm_total_next:
                addi t6, t6, 1
                j    .Lkof_ionorm_total_loop
            .Lkof_ionorm_total_done:
                ld   t0, 512(sp)
                bnez t0, .Lkof_ionorm_alloc
                bgtz s2, .Lkof_ionorm_alloc
                li   t5, 1
                li   t0, -1
                sd   t0, 520(sp)
                j    .Lkof_ionorm_alloc2
            .Lkof_ionorm_alloc:
                sd   zero, 520(sp)
            .Lkof_ionorm_alloc2:
                mv   a0, t5
                call kof_alloc
                mv   s5, a0
                li   s6, 0
                ld   t0, 512(sp)
                beqz t0, .Lkof_ionorm_build_rel
                add  t1, s5, s6
                li   t2, 47
                sb   t2, 0(t1)
                addi s6, s6, 1
            .Lkof_ionorm_build_rel:
                ld   t0, 520(sp)
                li   t1, -1
                bne  t0, t1, .Lkof_ionorm_copy_segs
                add  t1, s5, s6
                li   t2, 46
                sb   t2, 0(t1)
                addi s6, s6, 1
                j    .Lkof_ionorm_done
            .Lkof_ionorm_copy_segs:
                li   s7, 0
            .Lkof_ionorm_seg_loop:
                bge  s7, s2, .Lkof_ionorm_done
                beqz s7, .Lkof_ionorm_seg_no_sep
                add  t0, s5, s6
                li   t1, 47
                sb   t1, 0(t0)
                addi s6, s6, 1
            .Lkof_ionorm_seg_no_sep:
                slli t0, s7, 3
                add  t0, sp, t0
                lw   s8, 0(t0)
                lw   t3, 4(t0)
            .Lkof_ionorm_seg_inner:
                blez t3, .Lkof_ionorm_seg_next
                addi t4, s0, 24
                add  t4, t4, s8
                lbu  t1, 0(t4)
                add  t0, s5, s6
                sb   t1, 0(t0)
                addi s6, s6, 1
                addi s8, s8, 1
                addi t3, t3, -1
                j    .Lkof_ionorm_seg_inner
            .Lkof_ionorm_seg_next:
                addi s7, s7, 1
                j    .Lkof_ionorm_seg_loop
            .Lkof_ionorm_done:
                mv   a0, s5
                mv   a1, s6
                call kof_io_make_string
                ld   s9, 552(sp)
                ld   s8, 560(sp)
                ld   s7, 568(sp)
                ld   s6, 576(sp)
                ld   s5, 584(sp)
                ld   s4, 592(sp)
                ld   s3, 600(sp)
                ld   s2, 608(sp)
                ld   s1, 616(sp)
                ld   s0, 624(sp)
                ld   ra, 632(sp)
                addi sp, sp, 640
                ret
            """;
}
