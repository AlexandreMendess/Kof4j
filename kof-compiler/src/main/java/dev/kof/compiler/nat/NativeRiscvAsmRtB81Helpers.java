package dev.kof.compiler.nat;

// S5.5 fatia 5d (db-parity-plan, gaps-db lane, 24/09): classificador de
// limit/offset do orm.page mysql no cross — port de .Lorm8_pv
// (RuntimeOrm8) para kof_orm_mysql_pv. Extraido da B81 pelo gate 500.
//
// kof_orm_mysql_pv(a0=box|0) -> a0 int64 ((int) valor do host): int direto,
// long/dbl/flt truncam p/ int32, KofString = atoi superset honesto,
// null/desconhecido = 0. Sem call interno -> ret seguro (nao toca ra).
public final class NativeRiscvAsmRtB81Helpers {

    private NativeRiscvAsmRtB81Helpers() {}

    static String RISCV_RUNTIME_ASM_B_81H = """
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_mysql_pv(a0=box|0) -> a0 int64 ((int) valor do host)
            # ---------------------------------------------------------------
            .globl kof_orm_mysql_pv
            .type kof_orm_mysql_pv, @function
            kof_orm_mysql_pv:
                beqz a0, .L81_pv0
                la   t0, .L81_magic
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .L81_pv_str
                lw   t1, 8(a0)                     # tag
                beqz t1, .L81_pvi
                li   t2, 1
                beq  t1, t2, .L81_pvq
                li   t2, 2
                beq  t1, t2, .L81_pvq
                li   t2, 4
                beq  t1, t2, .L81_pvd
                li   t2, 5
                beq  t1, t2, .L81_pvf
                j    .L81_pv0
            .L81_pvi:
                lw   a0, 16(a0)
                ret
            .L81_pvq:
                ld   a0, 16(a0)
                sext.w a0, a0
                ret
            .L81_pvd:
                ld   t3, 16(a0)
                fmv.d.x fa0, t3
                fcvt.l.d a0, fa0, rtz
                sext.w a0, a0
                ret
            .L81_pvf:
                lw   t3, 16(a0)
                fmv.w.x fa0, t3
                fcvt.l.s a0, fa0, rtz
                sext.w a0, a0
                ret
            .L81_pv_str:
                lw   t0, 0(a0)                     # KofString (1,0,0)?
                li   t1, 1
                bne  t0, t1, .L81_pv0
                lw   t0, 4(a0)
                bnez t0, .L81_pv0
                lw   t0, 8(a0)
                bnez t0, .L81_pv0
                lw   t1, 16(a0)                    # len
                addi t2, a0, 24                    # body
                li   a0, 0
                li   t3, 0
                li   t4, 0
                blez t1, .L81_pv_ret0
                lbu  t5, 0(t2)
                li   t6, 45                        # '-'
                bne  t5, t6, .L81_pv_plus
                li   t4, 1
                addi t3, t3, 1
                j    .L81_pv_loop
            .L81_pv_plus:
                li   t6, 43                        # '+'
                bne  t5, t6, .L81_pv_loop
                addi t3, t3, 1
            .L81_pv_loop:
                bge  t3, t1, .L81_pv_done
                add  t5, t2, t3
                lbu  t5, 0(t5)
                li   t6, 48
                blt  t5, t6, .L81_pv_done
                li   t6, 57
                bgt  t5, t6, .L81_pv_done
                li   t6, 10
                mul  a0, a0, t6
                addi t5, t5, -48
                add  a0, a0, t5
                addi t3, t3, 1
                j    .L81_pv_loop
            .L81_pv_done:
                beqz t4, .L81_pv_ret
                neg  a0, a0
            .L81_pv_ret:
                sext.w a0, a0
                ret
            .L81_pv_ret0:
                li   a0, 0
                ret
            .L81_pv0:
                li   a0, 0
                ret

            .section .rodata
            .L81_magic:
                .quad 0x4B4F46425F425801
            .section .text
            """;
}
