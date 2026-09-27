package dev.kof.compiler.nat;

// S5.2 (db-parity-plan, gaps-db lane, 23/09): query texto COMPLETA no cross —
// port de `kof_db_query` (caminho mysql/COM_QUERY de RuntimeDb5/Db6, x86) para
// riscv64: envia COM_QUERY, lê as definições de coluna (nome), itera todas as
// linhas e monta, por linha, um JSON object {"col":valor,...} numa
// List<KofString>. Regras de valor seguem o contrato JVM (`kof_db_row_to_json`
// em JvmConfigRuntime, o oráculo Kof): NULL -> literal `null` (SEM aspas);
// celulas com digits apenas -> numero cru; demais (incl. string VAZIA) ->
// string com aspas (json_encode_string).
// DIVERGÊNCIA HONESTA vs x86 (catalogada como bug §NNN, não silenciosa): o
// caminho mysql do x86 (`RuntimeDb5 .Ldb_mysql_null`) anexa make_string len=0
// (vazio CRU, sem aspas) — produz JSON INVÁLIDO (`{"n":,`); idem p/ string
// vazia (numloop len=0 cai em is_num). A B70 NÃO copia o bug: emite o `null`
// da JVM, igual à B47 cross-sqlite. Erro (ERR/leitura) -> lista VAZIA
// (contrato do x86 `.Ldb_query_bad`).
//
// Contrato riscv:
//   kof_db_mysql_query(a0=fd, a1=sql KofString) -> a0 = List<KofString>
public final class NativeRiscvAsmRtB70 {

    private NativeRiscvAsmRtB70() {}

    static String RISCV_RUNTIME_ASM_B_70 = """
            .section .data
            .align 3
            .L70_req:
                .zero 4096
            .L70_names:
                .zero 1024
            .section .text
            # ---------------------------------------------------------------
            # kof_db_mysql_query(a0=fd, a1=sql) -> a0 = List<KofString>
            # ---------------------------------------------------------------
            .globl kof_db_mysql_query
            .type kof_db_mysql_query, @function
            kof_db_mysql_query:
                li   t6, 80
                sub  sp, sp, t6
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                sd   s2, 24(sp)
                sd   s3, 32(sp)
                sd   s4, 40(sp)
                sd   s5, 48(sp)
                sd   s6, 56(sp)
                sd   s7, 64(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s7, 16(s1)
                li   t0, 4000
                bgt  s7, t0, .L70_empty
                la   t1, .L70_req
                addi t0, s7, 1
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
            .L70_copy:
                bge  t2, s7, .L70_copy_done
                add  t4, t3, t2
                lbu  t5, 0(t4)
                add  t6, t1, t2
                addi t6, t6, 5
                sb   t5, 0(t6)
                addi t2, t2, 1
                j    .L70_copy
            .L70_copy_done:
                mv   a0, s0
                la   a1, .L70_req
                addi a2, s7, 5
                call kof_plat_write
                mv   a0, s0
                call kof_db_mysql_reset
                call kof_db_mysql_next
                beqz a0, .L70_empty
                lbu  t0, 0(a0)
                li   t1, 0xFF
                beq  t0, t1, .L70_empty
                mv   s5, a0
                call kof_list_new
                mv   s2, a0
                mv   a0, s5
                call kof_db_mysql_lenenc
                mv   s3, a0
                li   t0, 64
                bgt  s3, t0, .L70_done
                li   s4, 0
            .L70_cols:
                bge  s4, s3, .L70_cols_done
                call kof_db_mysql_next
                beqz a0, .L70_done
                call kof_db_mysql_lenenc
                add  a0, a1, a0
                call kof_db_mysql_lenenc
                add  a0, a1, a0
                call kof_db_mysql_lenenc
                add  a0, a1, a0
                call kof_db_mysql_lenenc
                add  a0, a1, a0
                call kof_db_mysql_lenenc
                mv   t0, a0
                mv   t1, a1
                la   t2, .L70_names
                slli t3, s4, 3
                add  t2, t2, t3
                sd   t1, 0(t2)
                la   t2, .L70_names
                slli t3, s4, 2
                addi t3, t3, 512
                add  t2, t2, t3
                sw   t0, 0(t2)
                add  a0, a1, a0
                call kof_db_mysql_lenenc
                addi s4, s4, 1
                j    .L70_cols
            .L70_cols_done:
                call kof_db_mysql_next
                beqz a0, .L70_done
                lbu  t0, 0(a0)
                beqz t0, .L70_done
            .L70_rows:
                call kof_db_mysql_next
                beqz a0, .L70_done
                lbu  t0, 0(a0)
                li   t1, 0xFF
                beq  t0, t1, .L70_done
                li   t1, 0xFE
                beq  t0, t1, .L70_done
                mv   s5, a0
                call kof_json_builder_new
                mv   s6, a0
                mv   a0, s6
                li   a1, 123
                call kof_json_builder_char
                li   s4, 0
            .L70_col:
                bge  s4, s3, .L70_row_end
                beq  s4, zero, .L70_colname
                mv   a0, s6
                li   a1, 44
                call kof_json_builder_char
            .L70_colname:
                la   t3, .L70_names
                slli t1, s4, 3
                add  t3, t3, t1
                ld   t0, 0(t3)
                la   t3, .L70_names
                slli t1, s4, 2
                addi t1, t1, 512
                add  t3, t3, t1
                lw   t1, 0(t3)
                mv   a0, t0
                mv   a1, t1
                call kof_io_make_string
                call kof_json_encode_string
                mv   t0, a0
                mv   a0, s6
                mv   a1, t0
                call kof_json_builder_str
                mv   a0, s6
                li   a1, 58
                call kof_json_builder_char
                lbu  t1, 0(s5)
                li   t2, 0xFB
                beq  t1, t2, .L70_null
                mv   a0, s5
                call kof_db_mysql_lenenc
                mv   t1, a0
                add  s5, a1, a0
                mv   a0, a1
                mv   a1, t1
                call kof_io_make_string
                mv   s7, a0
                lw   t1, 16(s7)
                beqz t1, .L70_is_str
                li   t0, 0
            .L70_numloop:
                lw   t1, 16(s7)
                bge  t0, t1, .L70_is_num
                add  t2, s7, t0
                lbu  t2, 24(t2)
                li   t3, 48
                blt  t2, t3, .L70_is_str
                li   t3, 57
                bgt  t2, t3, .L70_is_str
                addi t0, t0, 1
                j    .L70_numloop
            .L70_is_num:
                mv   a0, s6
                mv   a1, s7
                call kof_json_builder_str
                j    .L70_val
            .L70_is_str:
                mv   a0, s7
                call kof_json_encode_string
                mv   t0, a0
                mv   a0, s6
                mv   a1, t0
                call kof_json_builder_str
                j    .L70_val
            .L70_null:
                # NULL -> literal `null` SEM aspas (contrato JVM
                # kof_db_row_to_json; o x86 anexa vazio cru = JSON inválido).
                mv   a0, s6
                li   a1, 110
                call kof_json_builder_char
                mv   a0, s6
                li   a1, 117
                call kof_json_builder_char
                mv   a0, s6
                li   a1, 108
                call kof_json_builder_char
                mv   a0, s6
                li   a1, 108
                call kof_json_builder_char
                addi s5, s5, 1
            .L70_val:
                addi s4, s4, 1
                j    .L70_col
            .L70_row_end:
                mv   a0, s6
                li   a1, 125
                call kof_json_builder_char
                mv   a0, s6
                call kof_json_builder_result
                mv   t0, a0
                mv   a0, s2
                mv   a1, t0
                call kof_list_add
                j    .L70_rows
            .L70_done:
                beqz s2, .L70_empty
                mv   a0, s2
                j    .L70_ret
            .L70_empty:
                call kof_list_new
                mv   s2, a0
                mv   a0, s2
            .L70_ret:
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                ld   s1, 16(sp)
                ld   s2, 24(sp)
                ld   s3, 32(sp)
                ld   s4, 40(sp)
                ld   s5, 48(sp)
                ld   s6, 56(sp)
                ld   s7, 64(sp)
                li   t6, 80
                add  sp, sp, t6
                ret
            .section .text
            """;
}
