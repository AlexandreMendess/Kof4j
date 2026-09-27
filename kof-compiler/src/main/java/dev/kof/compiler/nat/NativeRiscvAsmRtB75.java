package dev.kof.compiler.nat;

// S5.5 fatia 3 (db-parity-plan, gaps-db lane, 24/09): faces de ESCRITA do
// kof.orm sobre o wire MySQL no cross — `orm.delete` e `orm.deleteAll`.
// aarch64 herda via tradutor.
//
// Semântica espelhada do x86 (a referência do contrato, D-DB-GAPS): o x86
// `.Lorm_da_my`/`.Lorm_del_my` usam `.Lorm_sa_exec` (RuntimeOrmMysqlExec): OK
// devolve affectedRows; o delete mapeia sucesso para true, mas ERR do servidor
// LANCE `mysql: <msg>`. O cross espelha com a B76 (`kof_orm_mysql_exec`, port
// do mesmo exec). Lei D-DECISION-BATCH-2609: o `true` silencioso do caminho de
// erro era §493; sucesso continua idempotente e sem excecao.
//
// Dialeto: nomes com backtick (medido: `FROM "t"` = ERROR 1064 no MariaDB).
// O literal do bind vem de `kof_orm_mysql_lit` (promovido da B53 para evitar
// duas cópias do renderizador — regra 11): box §284 int/long/bool/double/
// float -> kof_*_to_string, KofString -> kof_db_mysql_render, null -> NULL,
// outra forma -> ORM001.
//
// ABIs (riscv):
//   kof_orm_mysql_lit(a0=value) -> a0 = KofString* literal
//   kof_orm_delete_mysql(a0=id,a1=key,a2=table,a3=schema) -> Bool a0
//   kof_orm_delete_all_mysql(a0=id,a1=table,a2=schema) -> Bool a0
// Rótulos .L75_* (namespace por peça). id inválido -> throw a MESMA mensagem
// do host ("unknown db connection: " + id).
public final class NativeRiscvAsmRtB75 {

    private NativeRiscvAsmRtB75() {}

    static String RISCV_RUNTIME_ASM_B_75 = """
            # ---------------------------------------------------------------
            # kof_orm_mysql_lit(a0=value) -> a0 = KofString* literal SQL
            # ---------------------------------------------------------------
            .globl kof_orm_mysql_lit
            .type kof_orm_mysql_lit, @function
            kof_orm_mysql_lit:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                mv   s0, a0
                beqz s0, .L75_lt_null
                la   t0, .L75_magic
                ld   t0, 0(t0)
                ld   t1, 0(s0)
                bne  t0, t1, .L75_lt_str
                lw   t1, 8(s0)
                beqz t1, .L75_lt_int
                li   t2, 2
                beq  t1, t2, .L75_lt_long
                li   t2, 3
                beq  t1, t2, .L75_lt_bool
                li   t2, 4
                beq  t1, t2, .L75_lt_dbl
                li   t2, 5
                beq  t1, t2, .L75_lt_flt
                j    .L75_lt_bad
            .L75_lt_int:
                lw   a0, 16(s0)
                call kof_int_to_string
                j    .L75_lt_out
            .L75_lt_long:
                ld   a0, 16(s0)
                call kof_long_to_string
                j    .L75_lt_out
            .L75_lt_bool:
                lw   a0, 16(s0)
                call kof_int_to_string
                j    .L75_lt_out
            .L75_lt_dbl:
                ld   t0, 16(s0)
                fmv.d.x fa0, t0
                call kof_double_to_string
                j    .L75_lt_out
            .L75_lt_flt:
                lw   t0, 16(s0)
                fmv.w.x fa0, t0
                fcvt.d.s fa0, fa0
                call kof_double_to_string
                j    .L75_lt_out
            .L75_lt_str:
                lw   t0, 0(s0)
                li   t1, 1
                bne  t0, t1, .L75_lt_bad
                lw   t0, 4(s0)
                bnez t0, .L75_lt_bad
                ld   t0, 8(s0)
                bnez t0, .L75_lt_bad
                mv   a0, s0
                call kof_db_mysql_render
                j    .L75_lt_out
            .L75_lt_null:
                la   a0, .L75_nullv
                li   a1, 4
                call kof_string_from_literal
                j    .L75_lt_out
            .L75_lt_bad:
                la   a0, .L75_badv
                li   a1, 57
                call kof_string_from_literal
                call kof_throw_string
            .L75_lt_out:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                addi sp, sp, 48
                ret

            # .L75_badconn(a0=id): throw "unknown db connection: "+id
            .L75_badconn:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                la   a0, .L75_bc_pre
                li   a1, 23
                call kof_string_from_literal
                mv   a1, s0
                call kof_string_concat
                call kof_throw_string
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret

            # ---------------------------------------------------------------
            # kof_orm_delete_all_mysql(id*, table*, schema*) -> Bool
            # ---------------------------------------------------------------
            .globl kof_orm_delete_all_mysql
            .type kof_orm_delete_all_mysql, @function
            kof_orm_delete_all_mysql:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                mv   s0, a0
                mv   s1, a1
                lw   t0, 16(s1)
                addi a0, t0, 39                    # 24 + 15 + tblLen
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                sw   zero, 20(s2)
                addi s3, s2, 24                    # cursor
                mv   a0, s3
                la   a1, .L75_da_pre
                li   a2, 13
                call kof_memcpy
                addi s3, s3, 13
                mv   a0, s3
                addi a1, s1, 24
                lw   a2, 16(s1)
                call kof_memcpy
                lw   t0, 16(s1)
                add  s3, s3, t0
                li   t0, 96
                sb   t0, 0(s3)
                addi s3, s3, 1
                sb   zero, 0(s3)
                addi t0, s2, 24
                sub  t0, s3, t0
                sw   t0, 16(s2)
                mv   a0, s0
                call kof_db_resolve
                beqz a0, .L75_da_bad
                mv   a1, s2
                call kof_orm_mysql_exec          # §493: espelha o x86 .Lorm_sa_exec, LANCA no ERR
                li   a0, 1
                j    .L75_da_out
            .L75_da_bad:
                mv   a0, s0
                call .L75_badconn
            .L75_da_out:
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                addi sp, sp, 64
                ret

            # ---------------------------------------------------------------
            # kof_orm_delete_mysql(id*, key*, table*, schema*) -> Bool
            # ---------------------------------------------------------------
            .globl kof_orm_delete_mysql
            .type kof_orm_delete_mysql, @function
            kof_orm_delete_mysql:
                addi sp, sp, -112
                sd   ra, 104(sp)
                sd   s0, 96(sp)
                sd   s1, 88(sp)
                sd   s2, 80(sp)
                sd   s3, 72(sp)
                sd   s4, 64(sp)
                sd   s5, 56(sp)
                sd   s6, 48(sp)
                sd   s7, 40(sp)
                sd   s8, 32(sp)
                sd   s9, 24(sp)
                mv   s0, a0                        # id
                mv   s1, a1                        # key
                mv   s2, a2                        # table
                mv   s3, a3                        # schema
                mv   a0, s1
                call kof_orm_mysql_lit             # s9 = literal
                mv   s9, a0
                mv   a0, s3
                call kof_orm_parse_schema          # a0=ftab a3=pkIdx
                mv   s5, a0
                mv   s6, a3
                slli t0, s6, 5
                add  s6, s5, t0                    # entry da PK
                ld   s8, 0(s6)                     # pk name ptr
                lw   s7, 8(s6)                     # pk name len
                lw   t0, 16(s2)                    # tblLen
                add  t0, t0, s7
                lw   t1, 16(s9)                    # litLen
                add  t0, t0, t1
                addi a0, t0, 51                    # 24 + 27 + 1
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                sw   zero, 4(s4)
                sd   zero, 8(s4)
                sw   zero, 20(s4)
                addi s6, s4, 24                    # cursor
                mv   a0, s6
                la   a1, .L75_del_pre
                li   a2, 12
                call kof_memcpy
                addi s6, s6, 12
                li   t0, 96
                sb   t0, 0(s6)
                addi s6, s6, 1
                mv   a0, s6
                addi a1, s2, 24
                lw   a2, 16(s2)
                call kof_memcpy
                lw   t0, 16(s2)
                add  s6, s6, t0
                li   t0, 96
                sb   t0, 0(s6)
                addi s6, s6, 1
                mv   a0, s6
                la   a1, .L75_w
                li   a2, 7
                call kof_memcpy
                addi s6, s6, 7
                li   t0, 96
                sb   t0, 0(s6)
                addi s6, s6, 1
                mv   a0, s6
                mv   a1, s8
                mv   a2, s7
                call kof_memcpy
                add  s6, s6, s7
                li   t0, 96
                sb   t0, 0(s6)
                addi s6, s6, 1
                mv   a0, s6
                la   a1, .L75_q
                li   a2, 3
                call kof_memcpy
                addi s6, s6, 3
                mv   a0, s6
                addi a1, s9, 24
                lw   a2, 16(s9)
                call kof_memcpy
                lw   t0, 16(s9)
                add  s6, s6, t0
                sb   zero, 0(s6)
                addi t0, s4, 24
                sub  t0, s6, t0
                sw   t0, 16(s4)
                mv   a0, s0
                call kof_db_resolve
                beqz a0, .L75_del_bad
                mv   a1, s4
                call kof_orm_mysql_exec          # §493: espelha o x86 .Lorm_sa_exec, LANCA no ERR
                li   a0, 1
                j    .L75_del_out
            .L75_del_bad:
                mv   a0, s0
                call .L75_badconn
            .L75_del_out:
                ld   ra, 104(sp)
                ld   s0, 96(sp)
                ld   s1, 88(sp)
                ld   s2, 80(sp)
                ld   s3, 72(sp)
                ld   s4, 64(sp)
                ld   s5, 56(sp)
                ld   s6, 48(sp)
                ld   s7, 40(sp)
                ld   s8, 32(sp)
                ld   s9, 24(sp)
                addi sp, sp, 112
                ret

            .section .rodata
            .L75_bc_pre:
                .ascii "unknown db connection: "
            .L75_nullv:
                .ascii "NULL"
            .L75_del_pre:
                .ascii "DELETE FROM "
            .L75_da_pre:
                .ascii "DELETE FROM `"
            .L75_w:
                .ascii " WHERE "
            .L75_q:
                .ascii " = "
            .L75_badv:
                .ascii "orm.count bind value: unsupported type on Native (ORM001)"
            .L75_magic:
                .quad 0x4B4F46425F425801
            .section .text
            """;
}
