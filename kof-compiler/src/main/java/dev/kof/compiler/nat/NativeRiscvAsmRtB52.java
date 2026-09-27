package dev.kof.compiler.nat;

// DB-3/DB-1 cross, slice C (22/09, lane gaps-db): kof_orm_migrate no riscv64 —
// F1c de RuntimeOrm1 x86, sobre o kof.db SQLite de RtB46/RtB47. aarch64 herda
// via tradutor.
//
// Semântica espelhada do x86 (a referência do contrato, D-DB-GAPS):
// - cria `kof_migrations` ("name" VARCHAR(255) PRIMARY KEY, "applied_at"
//   BIGINT) com o rc IGNORADO (como o host);
// - SELECT COUNT(*) ... WHERE "name" = ? bindado; prepare falhou -> false;
//   ja aplicada (count > 0) -> true sem re-rodar o DDL;
// - nao aplicada: sqlite3_exec do DDL do usuário (rc != 0 -> false, como o
//   .Lorm_exec), applied_at = epoch-ms (kof_time_now, port do clock_gettime
//   do x86) e INSERT bindado (o rc do step e ignorado, igual ao host);
// - id ruim/nulo -> throw "unknown db connection: " + id (mesma mensagem do
//   .Lorm_conn; MySQL inalcançável no cross).
//
// ABI: kof_orm_migrate(id@a0, name@a1, sql@a2) -> Bool a0. Rótulos .L52_*
// (namespace por peça; nenhum .L50_*/.L51_* referenciado de fora — §445).
public final class NativeRiscvAsmRtB52 {

    private NativeRiscvAsmRtB52() {}

    static String RISCV_RUNTIME_ASM_B_52 = """
            .section .text
            # ---------------------------------------------------------------
            # .L52_conn(a0=id*) -> a0=handle sqlite | lança (port .Lorm_conn)
            # ---------------------------------------------------------------
            .L52_conn:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                beqz a0, .L52_conn_bad
                call kof_db_type                  # 1=sqlite (0=null/ruim)
                li   t0, 1
                bne  a0, t0, .L52_conn_bad
                mv   a0, s0
                call kof_db_resolve
                beqz a0, .L52_conn_bad
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret
            .L52_conn_bad:
                beqz s0, .L52_conn_bad_null
                la   a0, .L52_bc_pre
                li   a1, 23
                call kof_string_from_literal
                mv   a1, s0
                call kof_string_concat            # "unknown db connection: "+id
                j    .L52_conn_throw
            .L52_conn_bad_null:
                la   a0, .L52_bc_pre
                li   a1, 23
                call kof_string_from_literal
            .L52_conn_throw:
                call kof_throw_string             # nunca volta (chain/pânico)
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret

            # ---------------------------------------------------------------
            # kof_orm_migrate(id*, name*, sql*) -> Bool
            # slots: 0 &stmt
            # ---------------------------------------------------------------
            .globl kof_orm_migrate
            .type kof_orm_migrate, @function
            kof_orm_migrate:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                mv   s1, a1                        # name
                mv   s2, a2                        # sql
                call .L52_conn
                mv   s0, a0                        # conn
                # 1) CREATE TABLE IF NOT EXISTS "kof_migrations" (rc ignorado)
                mv   a0, s0
                la   a1, .L52_mig_ddl
                li   a2, 0
                li   a3, 0
                li   a4, 0
                call sqlite3_exec
                # 2) SELECT COUNT(*) ... WHERE "name" = ?
                sd   zero, 0(sp)
                mv   a0, s0
                la   a1, .L52_mig_sel
                li   a2, -1
                mv   a3, sp
                li   a4, 0
                call sqlite3_prepare_v2
                bnez a0, .L52_false
                ld   s3, 0(sp)
                mv   a0, s3
                li   a1, 1
                addi a2, s1, 24                    # payload do name
                li   a3, -1
                li   a4, 0                         # SQLITE_STATIC (KofString viva)
                call sqlite3_bind_text
                mv   a0, s3
                call sqlite3_step
                li   t0, 100                       # SQLITE_ROW
                bne  a0, t0, .L52_norow
                mv   a0, s3
                li   a1, 0
                call sqlite3_column_int64
                mv   s4, a0
                j    .L52_fin
            .L52_norow:
                li   s4, 0
            .L52_fin:
                mv   a0, s3
                call sqlite3_finalize
                bgtz s4, .L52_true                 # já aplicada
                # 3) DDL do usuário (rc != 0 -> false)
                mv   a0, s0
                addi a1, s2, 24
                li   a2, 0
                li   a3, 0
                li   a4, 0
                call sqlite3_exec
                bnez a0, .L52_false
                # 4) applied_at = epoch-ms
                call kof_time_now
                mv   s4, a0
                # 5) INSERT INTO "kof_migrations" ("name","applied_at") VALUES (?, ?)
                sd   zero, 0(sp)
                mv   a0, s0
                la   a1, .L52_mig_ins
                li   a2, -1
                mv   a3, sp
                li   a4, 0
                call sqlite3_prepare_v2
                bnez a0, .L52_false
                ld   s3, 0(sp)
                mv   a0, s3
                li   a1, 1
                addi a2, s1, 24
                li   a3, -1
                li   a4, 0
                call sqlite3_bind_text
                mv   a0, s3
                li   a1, 2
                mv   a2, s4
                call sqlite3_bind_int64
                mv   a0, s3
                call sqlite3_step                  # rc ignorado (host)
                mv   a0, s3
                call sqlite3_finalize
            .L52_true:
                li   a0, 1
                j    .L52_out
            .L52_false:
                li   a0, 0
            .L52_out:
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                addi sp, sp, 64
                ret

            .section .rodata
            .L52_bc_pre:
                .ascii "unknown db connection: "
            .L52_mig_ddl:
                .asciz "CREATE TABLE IF NOT EXISTS \\"kof_migrations\\" (\\"name\\" VARCHAR(255) PRIMARY KEY, \\"applied_at\\" BIGINT)"
            .L52_mig_sel:
                .asciz "SELECT COUNT(*) FROM \\"kof_migrations\\" WHERE \\"name\\" = ?"
            .L52_mig_ins:
                .asciz "INSERT INTO \\"kof_migrations\\" (\\"name\\", \\"applied_at\\") VALUES (?, ?)"
            .section .text
            """;
}
