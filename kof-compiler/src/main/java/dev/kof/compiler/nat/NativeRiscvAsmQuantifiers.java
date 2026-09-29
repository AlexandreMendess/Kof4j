package dev.kof.compiler.nat;

// D-MULTIPARADIGMA-PHASE1A (slice 1a) — eager short-circuit quantifiers
// any/all/none em riscv64, espelho do x86 RuntimeListQuantifiers: loop com
// reload do elemento por iteração, invoke da lambda via slot, verdade =
// nonzero (igual ao filter), Bool cru 0/1 em a0. Só mnemônicos que o
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
            """;
}
