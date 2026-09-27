package dev.kof.compiler.nat;

// S5.5 fatia 5a (db-parity-plan, gaps-db lane, 24/09): helpers de leitura de
// celula do resultset MySQL no cross para o orm.find (peca B78) — extraidos
// pelo gate 500. Inteiros com sinal opcional e bool §397 (numerico != 0;
// texto literal "true" case-insensitive), espelho de .Lorm_rd_atoi/.Lorm_rd_bool
// do x86 (RuntimeOrmMysqlKeyLit). Nomes globais (kof_orm_mysql_*), reusaveis
// pelas demais faces row-object (all/where/page).
public final class NativeRiscvAsmRtB78Helpers {

    private NativeRiscvAsmRtB78Helpers() {}

    static String RISCV_RUNTIME_ASM_B_78H = """
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_mysql_atoi(a0=ptr, a1=len) -> a0 (sinal opcional)
            # ---------------------------------------------------------------
            .globl kof_orm_mysql_atoi
            .type kof_orm_mysql_atoi, @function
            kof_orm_mysql_atoi:
                li   t0, 0
                li   t1, 0
                blez a1, .L78ai_done
                lbu  t2, 0(a0)
                li   t3, 45
                bne  t2, t3, .L78ai_loop
                li   t1, 1
                addi a0, a0, 1
                addi a1, a1, -1
            .L78ai_loop:
                blez a1, .L78ai_sign
                lbu  t2, 0(a0)
                li   t3, 48
                blt  t2, t3, .L78ai_sign
                li   t3, 57
                bgt  t2, t3, .L78ai_sign
                li   t3, 10
                mul  t0, t0, t3
                addi t2, t2, -48
                add  t0, t0, t2
                addi a0, a0, 1
                addi a1, a1, -1
                j    .L78ai_loop
            .L78ai_sign:
                beqz t1, .L78ai_done
                neg  t0, t0
            .L78ai_done:
                mv   a0, t0
                ret

            # ---------------------------------------------------------------
            # kof_orm_mysql_bool(a0=ptr, a1=len) -> a0 0/1
            #  (numerico != 0; texto literal "true" case-insensitive, §397)
            # ---------------------------------------------------------------
            .globl kof_orm_mysql_bool
            .type kof_orm_mysql_bool, @function
            kof_orm_mysql_bool:
                blez a1, .L78b_false
                lbu  t0, 0(a0)
                li   t1, 45
                bne  t0, t1, .L78b_notsign
                addi a0, a0, 1
                addi a1, a1, -1
            .L78b_notsign:
                blez a1, .L78b_false
                lbu  t0, 0(a0)
                li   t1, 48
                blt  t0, t1, .L78b_text
                li   t1, 57
                bgt  t0, t1, .L78b_text
            .L78b_numloop:
                blez a1, .L78b_false
                lbu  t0, 0(a0)
                li   t1, 48
                bne  t0, t1, .L78b_true
                addi a0, a0, 1
                addi a1, a1, -1
                j    .L78b_numloop
            .L78b_text:
                li   t1, 4
                blt  a1, t1, .L78b_false
                lbu  t0, 0(a0)
                ori  t0, t0, 32
                li   t1, 116
                bne  t0, t1, .L78b_false
                lbu  t0, 1(a0)
                ori  t0, t0, 32
                li   t1, 114
                bne  t0, t1, .L78b_false
                lbu  t0, 2(a0)
                ori  t0, t0, 32
                li   t1, 117
                bne  t0, t1, .L78b_false
                lbu  t0, 3(a0)
                ori  t0, t0, 32
                li   t1, 101
                bne  t0, t1, .L78b_false
            .L78b_true:
                li   a0, 1
                ret
            .L78b_false:
                li   a0, 0
                ret
            .section .text
            """;
}
