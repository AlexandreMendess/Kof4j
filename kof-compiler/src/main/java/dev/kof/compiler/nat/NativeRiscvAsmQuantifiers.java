package dev.kof.compiler.nat;

// D-MULTIPARADIGMA-PHASE1A (slices 1a+1b) — eager short-circuit search ops
// any/all/none + find/count(pred) em riscv64, espelho do x86
// RuntimeListQuantifiers: loop com reload do elemento por iteração, invoke
// da lambda via slot, verdade = nonzero (igual ao filter), Bool/Int crus
// em a0, find ausente = 0 (contrato Map.get-missing). Só mnemônicos que o
// NativeAarch64Translator conhece (aarch64 deriva daqui). Concatenado em
// NativeRiscvAsm.
public final class NativeRiscvAsmQuantifiers {

    private NativeRiscvAsmQuantifiers() {}

    static String RISCV_QUANTIFIERS_ASM = """
            .text
            # kof_list_any(a0=list, a1=fn) -> a0 1/0
            .globl kof_list_any
            kof_list_any:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0
                mv   s1, a1
                li   s2, 0
            .Llany_loop:
                lw   t0, 16(s0)
                bge  s2, t0, .Llany_false
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3
                bnez a0, .Llany_true
                addi s2, s2, 1
                j    .Llany_loop
            .Llany_true:
                li   a0, 1
                j    .Llany_done
            .Llany_false:
                li   a0, 0
            .Llany_done:
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_list_all(a0=list, a1=fn) -> a0 1/0
            .globl kof_list_all
            kof_list_all:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0
                mv   s1, a1
                li   s2, 0
            .Llall_loop:
                lw   t0, 16(s0)
                bge  s2, t0, .Llall_true
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3
                beqz a0, .Llall_false
                addi s2, s2, 1
                j    .Llall_loop
            .Llall_false:
                li   a0, 0
                j    .Llall_done
            .Llall_true:
                li   a0, 1
            .Llall_done:
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_list_none(a0=list, a1=fn) -> a0 1/0
            .globl kof_list_none
            kof_list_none:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0
                mv   s1, a1
                li   s2, 0
            .Llnone_loop:
                lw   t0, 16(s0)
                bge  s2, t0, .Llnone_true
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3
                bnez a0, .Llnone_false
                addi s2, s2, 1
                j    .Llnone_loop
            .Llnone_false:
                li   a0, 0
                j    .Llnone_done
            .Llnone_true:
                li   a0, 1
            .Llnone_done:
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_list_find(a0=list, a1=fn, a2=tag) -> a0 boxed | 0.
            # Same box contract as x86 (NativeBoxTags numbering); tag in a2
            # is caller-saved, so it is parked in s3 on entry.
            .globl kof_list_find
            kof_list_find:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s3, a2
                li   s2, 0
            .Llfind_loop:
                lw   t0, 16(s0)
                bge  s2, t0, .Llfind_miss
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3
                bnez a0, .Llfind_hit
                addi s2, s2, 1
                j    .Llfind_loop
            .Llfind_hit:
                li   t0, 1
                beq  s3, t0, .Llfind_pass
                li   t0, 6
                beq  s3, t0, .Llfind_pass
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a0, 0(t1)
                li   t0, 2
                beq  s3, t0, .Llfind_long
                li   t0, 3
                beq  s3, t0, .Llfind_bool
                li   t0, 4
                beq  s3, t0, .Llfind_double
                li   t0, 5
                beq  s3, t0, .Llfind_float
                call kof_box_int
                j    .Llfind_done
            .Llfind_long:
                call kof_box_long
                j    .Llfind_done
            .Llfind_bool:
                call kof_box_bool
                j    .Llfind_done
            .Llfind_double:
                call kof_box_double
                j    .Llfind_done
            .Llfind_float:
                call kof_box_float
                j    .Llfind_done
            .Llfind_pass:
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a0, 0(t1)
                j    .Llfind_done
            .Llfind_miss:
                li   a0, 0
            .Llfind_done:
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_list_count_pred(a0=list, a1=fn) -> a0 count
            .globl kof_list_count_pred
            kof_list_count_pred:
                addi sp, sp, -56
                sd   ra, 48(sp)
                sd   s0, 40(sp)
                sd   s1, 32(sp)
                sd   s2, 24(sp)
                sd   s3, 16(sp)
                mv   s0, a0
                mv   s1, a1
                li   s2, 0
                li   s3, 0
            .Llcount_loop:
                lw   t0, 16(s0)
                bge  s2, t0, .Llcount_done
                ld   t1, 24(s0)
                slli t2, s2, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s1
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3
                beqz a0, .Llcount_next
                addi s3, s3, 1
            .Llcount_next:
                addi s2, s2, 1
                j    .Llcount_loop
            .Llcount_done:
                mv   a0, s3
                ld   s3, 16(sp)
                ld   s2, 24(sp)
                ld   s1, 32(sp)
                ld   s0, 40(sp)
                ld   ra, 48(sp)
                addi sp, sp, 56
                ret
            """;
}
