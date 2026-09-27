package dev.kof.compiler.nat;

// S5.2 (db-parity-plan, gaps-db lane, 23/09): cabecalho de resultset texto no
// cross. Envia COM_QUERY e usa o reader de pacotes (B68) para pular as
// definicoes de coluna + EOF e alcancar a PRIMEIRA linha: devolve ncols, o
// ponteiro e o comprimento do payload da linha (que o chamador divide em
// celulas lenenc). Depende de B68 (reader), B63 (lenenc) e da HAL de socket.
//
// Contrato riscv:
//   kof_db_mysql_query_text(a0=fd, a1=sql KofString)
//     -> a0 = ncols (>=1) | -1; a1 = ptr do payload da 1a linha; a2 = rowlen
public final class NativeRiscvAsmRtB69 {

    private NativeRiscvAsmRtB69() {}

    static String RISCV_RUNTIME_ASM_B_69 = """
            .section .data
            .align 3
            .L69_req:
                .zero 8192
            .section .text
            # ---------------------------------------------------------------
            # kof_db_mysql_query_text(a0=fd,a1=sql) -> a0=ncols|-1, a1=row, a2=rowlen
            # ---------------------------------------------------------------
            .globl kof_db_mysql_query_text
            .type kof_db_mysql_query_text, @function
            kof_db_mysql_query_text:
                li   t6, 80
                sub  sp, sp, t6
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                sd   s2, 24(sp)
                sd   s3, 32(sp)
                sd   s4, 40(sp)
                sd   s5, 48(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s1)          # sql len
                li   t0, 8000
                bgt  s2, t0, .L69_fail
                # header: len = s2 + 1, seq 0, payload COM_QUERY(3)+sql
                addi t0, s2, 1
                la   t1, .L69_req
                andi t2, t0, 0xff
                sb   t2, 0(t1)
                srli t2, t0, 8
                andi t2, t2, 0xff
                sb   t2, 1(t1)
                srli t2, t0, 16
                andi t2, t2, 0xff
                sb   t2, 2(t1)
                sb   zero, 3(t1)
                li   t2, 3
                sb   t2, 4(t1)
                addi t3, s1, 24
                li   t2, 0
            .L69_copy:
                bge  t2, s2, .L69_copy_done
                add  t4, t3, t2
                lbu  t5, 0(t4)
                add  t6, t1, t2
                addi t6, t6, 5
                sb   t5, 0(t6)
                addi t2, t2, 1
                j    .L69_copy
            .L69_copy_done:
                mv   a0, s0
                la   a1, .L69_req
                addi a2, s2, 5
                call kof_plat_write
                mv   a0, s0
                call kof_db_mysql_reset
                call kof_db_mysql_next
                beqz a0, .L69_fail
                lbu  t0, 0(a0)
                li   t1, 0xFF
                beq  t0, t1, .L69_fail
                call kof_db_mysql_lenenc
                mv   s3, a0               # ncols
                blez s3, .L69_fail
                li   s4, 0
            .L69_skip_cols:
                bge  s4, s3, .L69_cols_done
                call kof_db_mysql_next
                beqz a0, .L69_fail
                addi s4, s4, 1
                j    .L69_skip_cols
            .L69_cols_done:
                call kof_db_mysql_next    # EOF apos as colunas
                beqz a0, .L69_fail
                call kof_db_mysql_next    # primeira linha
                beqz a0, .L69_fail
                mv   s5, a1               # rowlen
                mv   a1, a0               # row ptr
                mv   a0, s3
                mv   a2, s5
                j    .L69_ret
            .L69_fail:
                li   a0, -1
                li   a1, 0
                li   a2, 0
            .L69_ret:
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                ld   s1, 16(sp)
                ld   s2, 24(sp)
                ld   s3, 32(sp)
                ld   s4, 40(sp)
                ld   s5, 48(sp)
                li   t6, 80
                add  sp, sp, t6
                ret
            .section .text
            """;
}
