package dev.kof.compiler.nat;

// DB-3/DB-1 cross, slice A (22/09, lane gaps-db): primeiras faces do ORM no
// riscv64 — kof_orm_delete_all + kof_orm_count (F1a de RuntimeOrm1 x86)
// sobre o runtime kof.db SQLite já portado (RtB46/RtB47; link-by-use
// -lsqlite3 via NativeCrossLink). aarch64 herda via tradutor.
//
// Semântica espelhada do x86 (a referência do contrato, D-DB-GAPS):
// - id ruim/nulo/type fora de {1,2} -> lança a MESMA mensagem do .Lorm_conn
//   (prefixo "unknown db connection: " + id; id nulo -> só o prefixo).
// - delete_all: prepare falhou ou step != SQLITE_DONE -> false; sucesso ->
//   true (espelho do rc do sqlite3_exec do x86 — SQL error nunca lança).
// - count: prepare falhou/erro/zero-row -> 0 (no x86 o callback nunca roda);
//   valor exato por sqlite3_column_int64 (COUNT é INTEGER; o x86 usa
//   texto+atol — mesmo valor, sem perda).
//
// S5.5 fatia 1 (24/09, lane gaps-db): com o wire mysql REAL no cross (S5.4),
// o handle type 2 chegou ao ORM. `kof_orm_count` agora ramifica por
// `kof_db_type`: type 2 -> `kof_db_mysql_scalar_int(fd, sql)` com a citação
// de DIALETO mysql (backtick — medido: `FROM "t"` = ERROR 1064 no MariaDB);
// o ramo sqlite segue idêntico. As demais faces seguem sqlite-only (S5.5
// fatias 2-4); o type 2 delas permanece recusa honesta.
//
// ABI: kof_orm_delete_all(id@a0, table@a1, schema@a2) -> Bool a0;
//      kof_orm_count(id@a0, table@a1, schema@a2) -> Long a0.
// schema@a2 não é lido nas duas faces (igual ao x86). Rótulos .L50_*
// (namespace por peça, disciplina do RiscvSlices).
public final class NativeRiscvAsmRtB50 {

    private NativeRiscvAsmRtB50() {}

    static String RISCV_RUNTIME_ASM_B_50 = """
            .section .text
            # ---------------------------------------------------------------
            # .L50_conn(a0=id*) -> a0=handle sqlite | lança (port .Lorm_conn)
            # ---------------------------------------------------------------
            .L50_conn:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                beqz a0, .L50_conn_bad
                call kof_db_type                  # 1=sqlite (0=null/ruim)
                li   t0, 1
                bne  a0, t0, .L50_conn_bad
                mv   a0, s0
                call kof_db_resolve
                beqz a0, .L50_conn_bad
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret
            .L50_conn_bad:
                beqz s0, .L50_conn_bad_null
                la   a0, .L50_bc_pre
                li   a1, 23
                call kof_string_from_literal
                mv   a1, s0
                call kof_string_concat            # "unknown db connection: "+id
                j    .L50_conn_throw
            .L50_conn_bad_null:
                la   a0, .L50_bc_pre
                li   a1, 23
                call kof_string_from_literal
            .L50_conn_throw:
                call kof_throw_string             # nunca volta (chain/pânico)
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret

            # ---------------------------------------------------------------
            # kof_orm_delete_all(id*, table*, schema*) -> Bool
            # ---------------------------------------------------------------
            .globl kof_orm_delete_all
            .type kof_orm_delete_all, @function
            kof_orm_delete_all:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                mv   s0, a0
                mv   s1, a1
                mv   a0, s0
                call kof_db_type                   # S5.5 fatia 3: type 2 -> mysql
                li   t0, 2
                beq  a0, t0, .L50_da_mysql
                mv   a0, s0
                call .L50_conn
                mv   s2, a0                        # handle
                la   a0, .L50_da_pre
                li   a1, 13
                call kof_string_from_literal
                mv   s3, a0
                mv   a0, s3
                mv   a1, s1
                call kof_string_concat
                mv   s3, a0
                la   a0, .L50_quote
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s3
                call kof_string_concat            # DELETE FROM "table"
                mv   s3, a0
                sd   zero, 0(sp)                   # &stmt
                mv   a0, s2
                addi a1, s3, 24                    # payload da KofString
                li   a2, -1
                mv   a3, sp
                li   a4, 0
                call sqlite3_prepare_v2
                bnez a0, .L50_da_false
                ld   a0, 0(sp)
                call sqlite3_step
                mv   s4, a0
                ld   a0, 0(sp)
                call sqlite3_finalize
                li   t0, 101                       # SQLITE_DONE
                beq  s4, t0, .L50_da_true
            .L50_da_false:
                li   a0, 0
                j    .L50_da_out
            .L50_da_true:
                li   a0, 1
            .L50_da_out:
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                addi sp, sp, 64
                ret
            # S5.5 fatia 3: DELETE FROM `t` pelo wire mysql (peça B75).
            # Espelha o x86 `.Lorm_da_my`: exec genérico (affected|0), NÃO lança
            # no ERR — a divergência JVM↔Native é pré-existente (§493).
            .L50_da_mysql:
                mv   t0, s0
                mv   t1, s1
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                addi sp, sp, 64
                mv   a0, t0
                mv   a1, t1
                j    kof_orm_delete_all_mysql

            # ---------------------------------------------------------------
            # kof_orm_count(id*, table*, schema*) -> Long
            # ---------------------------------------------------------------
            .globl kof_orm_count
            .type kof_orm_count, @function
            kof_orm_count:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                mv   s0, a0
                mv   s1, a1
                mv   a0, s0
                call kof_db_type                  # S5.5: type 2 -> wire mysql
                li   t0, 2
                beq  a0, t0, .L50_cnt_mysql
                mv   a0, s0
                call .L50_conn
                mv   s2, a0
                la   a0, .L50_cnt_pre
                li   a1, 22
                call kof_string_from_literal
                mv   s3, a0
                mv   a0, s3
                mv   a1, s1
                call kof_string_concat
                mv   s3, a0
                la   a0, .L50_quote
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s3
                call kof_string_concat            # SELECT COUNT(*) FROM "table"
                mv   s3, a0
                sd   zero, 0(sp)
                mv   a0, s2
                addi a1, s3, 24
                li   a2, -1
                mv   a3, sp
                li   a4, 0
                call sqlite3_prepare_v2
                bnez a0, .L50_cnt_zero
                ld   a0, 0(sp)
                call sqlite3_step
                mv   s4, a0
                li   t0, 100                       # SQLITE_ROW
                bne  s4, t0, .L50_cnt_fin0
                ld   a0, 0(sp)
                li   a1, 0
                call sqlite3_column_int64
                mv   s4, a0
                ld   a0, 0(sp)
                call sqlite3_finalize
                mv   a0, s4
                j    .L50_cnt_out
            .L50_cnt_fin0:
                ld   a0, 0(sp)
                call sqlite3_finalize
            .L50_cnt_zero:
                li   a0, 0
                j    .L50_cnt_out
            # S5.5 fatia 1: SELECT COUNT(*) FROM `table` pelo wire mysql.
            .L50_cnt_mysql:
                la   a0, .L50_cnt_pre_bt
                li   a1, 22
                call kof_string_from_literal
                mv   s3, a0
                mv   a0, s3
                mv   a1, s1
                call kof_string_concat
                mv   s3, a0
                la   a0, .L50_bt
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s3
                call kof_string_concat
                mv   a1, a0                        # sql (backtick)
                mv   a0, s0
                call kof_db_resolve
                call kof_db_mysql_scalar_int
            .L50_cnt_out:
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                addi sp, sp, 64
                ret

            .section .rodata
            .L50_bc_pre:
                .ascii "unknown db connection: "
            .L50_da_pre:
                .ascii "DELETE FROM \\""
            .L50_cnt_pre:
                .ascii "SELECT COUNT(*) FROM \\""
            .L50_quote:
                .ascii "\\""
            .L50_cnt_pre_bt:
                .ascii "SELECT COUNT(*) FROM `"
            .L50_bt:
                .ascii "`"
            .section .text
            """;
}
