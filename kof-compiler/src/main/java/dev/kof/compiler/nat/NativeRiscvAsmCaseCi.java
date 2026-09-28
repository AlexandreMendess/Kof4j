package dev.kof.compiler.nat;

// D-STR-UNICODE (linha 11, fatia-5): compareToIgnoreCase riscv64 — fold DUPLO por
// code unit UTF-16 = algoritmo exato do JDK (toUpperCase; desempate toLowerCase;
// senao diferenca crua), sobre o stream de units decodificado do store UTF-8
// (astral = par surrogate, identico ao String do JVM; surrogados NAO dobram).
// Espelho byte-comportamental do RuntimeStringCaseCi (x86): golden
// StringUnicodeFacesMeasuredTest (JVM) == NativeStringCaseCiCrossTest (qemu).
// aarch64 herda via tradutor (padrao NativeRiscvAsmCase).
public final class NativeRiscvAsmCaseCi {

    private NativeRiscvAsmCaseCi() {}

    static String RISCV_STRCASECI_ASM = ("""
            # kof_ci_units(str@a0) -> a0 = buffer de code units UTF-16
            # (unidades @16; dados @24, 2 bytes por unit; astral => par).
            .globl kof_ci_units
            kof_ci_units:
                beqz a0, .Lkof_ci_u_null
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                sd   s6, 0(sp)
                mv   s0, a0
                lw   s1, 16(s0)
                slli t0, s1, 1
                addi a0, t0, 24
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                sw   zero, 16(s2)
                sw   zero, 20(s2)
                li   s5, 0
                li   s6, 0
            .Lkof_ci_u_loop:
                bge  s5, s1, .Lkof_ci_u_done
                add  t0, s0, s5
                lbu  t1, 24(t0)
                li   t2, 128
                blt  t1, t2, .Lkof_ci_u_ascii
                li   t2, 224
                blt  t1, t2, .Lkof_ci_u_two
                li   t2, 240
                blt  t1, t2, .Lkof_ci_u_three
                andi t1, t1, 7
                slli t1, t1, 18
                lbu  t3, 25(t0)
                andi t3, t3, 63
                slli t3, t3, 12
                or   t1, t1, t3
                lbu  t3, 26(t0)
                andi t3, t3, 63
                slli t3, t3, 6
                or   t1, t1, t3
                lbu  t3, 27(t0)
                andi t3, t3, 63
                or   t1, t1, t3
                addi t1, t1, -65536
                srli t3, t1, 10
                ori  t3, t3, 55296
                add  t4, s2, s6
                sh   t3, 24(t4)
                andi t1, t1, 1023
                ori  t1, t1, 56320
                sh   t1, 26(t4)
                addi s6, s6, 2
                addi s6, s6, 2
                addi s5, s5, 4
                j    .Lkof_ci_u_loop
            .Lkof_ci_u_two:
                andi t1, t1, 31
                slli t1, t1, 6
                lbu  t3, 25(t0)
                andi t3, t3, 63
                or   t1, t1, t3
                add  t4, s2, s6
                sh   t1, 24(t4)
                addi s6, s6, 2
                addi s5, s5, 2
                j    .Lkof_ci_u_loop
            .Lkof_ci_u_three:
                andi t1, t1, 15
                slli t1, t1, 12
                lbu  t3, 25(t0)
                andi t3, t3, 63
                slli t3, t3, 6
                or   t1, t1, t3
                lbu  t3, 26(t0)
                andi t3, t3, 63
                or   t1, t1, t3
                add  t4, s2, s6
                sh   t1, 24(t4)
                addi s6, s6, 2
                addi s5, s5, 3
                j    .Lkof_ci_u_loop
            .Lkof_ci_u_ascii:
                add  t4, s2, s6
                sh   t1, 24(t4)
                addi s6, s6, 2
                addi s5, s5, 1
                j    .Lkof_ci_u_loop
            .Lkof_ci_u_done:
                srli t0, s6, 1
                sw   t0, 16(s2)
                mv   a0, s2
                ld   s6, 0(sp)
                ld   s5, 8(sp)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            .Lkof_ci_u_null:
                li   a0, 0
                ret

            # kfci_fu(unit@a0) -> a0 (Character.toUpperCase por code unit)
            kfci_fu:
                li   t0, 128
                blt  a0, t0, .kfci_fu_ascii
                mv   t3, a0
                la   t6, .Lkof_ci_up_tab
                li   t4, @UPN@
                addi t4, t4, -1
                li   t0, 0
            .kfci_fu_bs:
                bgt  t0, t4, .kfci_fu_nf
                add  t5, t0, t4
                srli t5, t5, 1
                slli t2, t5, 2
                add  t2, t6, t2
                lhu  t1, 0(t2)
                beq  t3, t1, .kfci_fu_found
                blt  t3, t1, .kfci_fu_hi
                addi t0, t5, 1
                j    .kfci_fu_bs
            .kfci_fu_hi:
                addi t4, t5, -1
                j    .kfci_fu_bs
            .kfci_fu_found:
                lhu  a0, 2(t2)
                ret
            .kfci_fu_nf:
                mv   a0, t3
                ret
            .kfci_fu_ascii:
                li   t0, 97
                blt  a0, t0, .kfci_fu_id
                li   t0, 122
                bgt  a0, t0, .kfci_fu_id
                addi a0, a0, -32
                ret
            .kfci_fu_id:
                ret

            # kfci_fl(unit@a0) -> a0 (Character.toLowerCase por code unit)
            kfci_fl:
                li   t0, 128
                blt  a0, t0, .kfci_fl_ascii
                mv   t3, a0
                la   t6, .Lkof_ci_lo_tab
                li   t4, @LON@
                addi t4, t4, -1
                li   t0, 0
            .kfci_fl_bs:
                bgt  t0, t4, .kfci_fl_nf
                add  t5, t0, t4
                srli t5, t5, 1
                slli t2, t5, 2
                add  t2, t6, t2
                lhu  t1, 0(t2)
                beq  t3, t1, .kfci_fl_found
                blt  t3, t1, .kfci_fl_hi
                addi t0, t5, 1
                j    .kfci_fl_bs
            .kfci_fl_hi:
                addi t4, t5, -1
                j    .kfci_fl_bs
            .kfci_fl_found:
                lhu  a0, 2(t2)
                ret
            .kfci_fl_nf:
                mv   a0, t3
                ret
            .kfci_fl_ascii:
                li   t0, 65
                blt  a0, t0, .kfci_fl_id
                li   t0, 90
                bgt  a0, t0, .kfci_fl_id
                addi a0, a0, 32
                ret
            .kfci_fl_id:
                ret

            # kof_string_compare_to_ignore_case(a@a0, b@a1) -> Int
            .globl kof_string_compare_to_ignore_case
            kof_string_compare_to_ignore_case:
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
                beqz a0, .Lkof_ci_na
                beqz a1, .Lkof_ci_nbpos
                mv   s0, a0
                mv   s1, a1
                mv   a0, s0
                call kof_ci_units
                mv   s5, a0
                mv   a0, s1
                call kof_ci_units
                mv   s6, a0
                lw   s2, 16(s5)
                lw   s3, 16(s6)
                mv   s7, s2
                bge  s2, s3, .Lkof_ci_min
                mv   s7, s3
            .Lkof_ci_min:
                li   s4, 0
            .Lkof_ci_loop:
                bge  s4, s7, .Lkof_ci_tail
                slli t0, s4, 1
                add  t0, s5, t0
                lhu  a0, 24(t0)
                call kfci_fu
                sw   a0, 0(sp)
                slli t0, s4, 1
                add  t0, s6, t0
                lhu  a0, 24(t0)
                call kfci_fu
                lw   t1, 0(sp)
                beq  a0, t1, .Lkof_ci_next
                sw   a0, 4(sp)
                lw   a0, 0(sp)
                call kfci_fl
                sw   a0, 8(sp)
                lw   a0, 4(sp)
                call kfci_fl
                lw   t2, 8(sp)
                beq  a0, t2, .Lkof_ci_next
                sub  a0, t2, a0
                j    .Lkof_ci_epi
            .Lkof_ci_next:
                addi s4, s4, 1
                j    .Lkof_ci_loop
            .Lkof_ci_tail:
                sub  a0, s2, s3
                j    .Lkof_ci_epi
            .Lkof_ci_nbpos:
                li   a0, 1
                j    .Lkof_ci_epi
            .Lkof_ci_na:
                beqz a1, .Lkof_ci_zero
                li   a0, -1
                j    .Lkof_ci_epi
            .Lkof_ci_zero:
                li   a0, 0
            .Lkof_ci_epi:
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
            """)
            .replace("@UPN@", Integer.toString(dev.kof.compiler.runtime.RuntimeStringCase.count(true)))
            .replace("@LON@", Integer.toString(dev.kof.compiler.runtime.RuntimeStringCase.count(false)))
            + dev.kof.compiler.runtime.RuntimeStringCase.data(true, ".Lkof_ci_up_tab")
            + dev.kof.compiler.runtime.RuntimeStringCase.data(false, ".Lkof_ci_lo_tab");
}
