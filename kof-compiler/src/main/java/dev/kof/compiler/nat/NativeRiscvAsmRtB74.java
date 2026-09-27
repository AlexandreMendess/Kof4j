package dev.kof.compiler.nat;

// S5.5 fatia 1 (db-parity-plan, gaps-db lane, 24/09): `kof.orm` sobre o wire
// mysql no cross. Primitiva `kof_db_mysql_scalar_int` — roda um COM_QUERY
// (reusa `kof_db_mysql_query_text`, B69: já envia o request, pula as
// definições de coluna e devolve a 1ª linha) e devolve o 1º campo como inteiro
// (dígitos decimais não-negativos). É o que `orm.count` (e, na fatia 3,
// `SELECT LAST_INSERT_ID()`) consome.
//
// Contrato riscv:
//   kof_db_mysql_scalar_int(a0=fd, a1=sql KofString) -> a0 = Long (0 em erro,
//   sem linha, célula NULL ou não-numérica).
//
// Depende de B69 (query_text), B63 (lenenc) e da HAL de socket — o pruning do
// RiscvSlices arrasta as peças por símbolo global.
public final class NativeRiscvAsmRtB74 {

    private NativeRiscvAsmRtB74() {}

    static String RISCV_RUNTIME_ASM_B_74 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_db_mysql_scalar_int(a0=fd, a1=sql) -> a0 = Long
            # ---------------------------------------------------------------
            .globl kof_db_mysql_scalar_int
            .type kof_db_mysql_scalar_int, @function
            kof_db_mysql_scalar_int:
                li   t6, 32
                sub  sp, sp, t6
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                call kof_db_mysql_query_text      # a0=ncols|-1, a1=1a linha, a2=rowlen
                blez a0, .L74_zero
                mv   s0, a1                       # row ptr
                beqz s0, .L74_zero
                lbu  t0, 0(s0)
                li   t1, 0xFB                     # NULL -> 0
                beq  t0, t1, .L74_zero
                mv   a0, s0
                call kof_db_mysql_lenenc          # a0=len, a1=ptr do 1o campo
                mv   t2, a0                       # len
                mv   t1, a1                       # ptr
                li   t3, 0                        # valor
                li   t4, 0                        # i
            .L74_loop:
                bge  t4, t2, .L74_done
                add  t5, t1, t4
                lbu  t5, 0(t5)
                addi t5, t5, -48                  # '0'
                li   t6, 10
                mul  t3, t3, t6
                add  t3, t3, t5
                addi t4, t4, 1
                j    .L74_loop
            .L74_done:
                mv   a0, t3
                j    .L74_ret
            .L74_zero:
                li   a0, 0
            .L74_ret:
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                ld   s1, 16(sp)
                li   t6, 32
                add  sp, sp, t6
                ret
            .section .text
            """;
}
