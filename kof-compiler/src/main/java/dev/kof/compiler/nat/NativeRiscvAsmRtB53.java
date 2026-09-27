package dev.kof.compiler.nat;

// DB-3/DB-1 cross, slice D (22/09, lane gaps-db): kof_orm_count_where no
// riscv64 — F3a de RuntimeOrm3 x86, sobre o kof.db SQLite de RtB46/RtB47.
// aarch64 herda via tradutor.
//
// Semântica espelhada do x86 (a referência do contrato, D-DB-GAPS):
// SQL `SELECT COUNT(*) FROM "t" WHERE "f" = ?` montado no buffer (o valor
// NUNCA entra concatenado — injection-proof como o PreparedStatement do
// host); value despachado pelo box de erasure §284 ([magic u64][tag u64]
// [valor 8B], RtB49) ou KofString (type@0=1, len@16, payload@24) ou null:
// int (tag 0, lw com sign-extend = movslq do x86), long (2), bool (3),
// double (4, fmv.d.x -> fa0), float (5, fmv.w.x + fcvt.d.s -> fa0);
// KofString -> bind_text transient; null -> bind_null; outro shape ->
// throw a MESMA mensagem do x86 (`.Lorm3_badv`, R6 — nunca bind de lixo).
// prepare falhou/sem stmt -> 0; step sem ROW -> 0; sucesso -> count exato
// (column_int64).
//
// ABI: kof_orm_count_where(id@a0, field@a1, value@a2, table@a3, schema@a4)
// -> Long a0. Rótulos .L53_* (namespace por peça; zero ref cross-peça, §445).
public final class NativeRiscvAsmRtB53 {

    private NativeRiscvAsmRtB53() {}

    static String RISCV_RUNTIME_ASM_B_53 = """
            .section .text
            # ---------------------------------------------------------------
            # .L53_conn(a0=id*) -> a0=handle sqlite | lança (port .Lorm_conn)
            # ---------------------------------------------------------------
            .L53_conn:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                beqz a0, .L53_conn_bad
                call kof_db_type                  # 1=sqlite (0=null/ruim)
                li   t0, 1
                bne  a0, t0, .L53_conn_bad
                mv   a0, s0
                call kof_db_resolve
                beqz a0, .L53_conn_bad
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret
            .L53_conn_bad:
                beqz s0, .L53_conn_bad_null
                la   a0, .L53_bc_pre
                li   a1, 23
                call kof_string_from_literal
                mv   a1, s0
                call kof_string_concat            # "unknown db connection: "+id
                j    .L53_conn_throw
            .L53_conn_bad_null:
                la   a0, .L53_bc_pre
                li   a1, 23
                call kof_string_from_literal
            .L53_conn_throw:
                call kof_throw_string             # nunca volta (chain/pânico)
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret

            # ---------------------------------------------------------------
            # Builder do SQL (cursor@app s6) — mesmos helpers locais do RtB51
            # ---------------------------------------------------------------
            # .L53_ap(a0=ptr, a1=len): copia o bloco e avança o cursor
            .L53_ap:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   a1, 0(sp)
                mv   a2, a1
                mv   a1, a0
                mv   a0, s6
                call kof_memcpy
                ld   t0, 0(sp)
                add  s6, s6, t0
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # .L53_ch(a0=byte): emite 1 byte e avança o cursor
            .L53_ch:
                sb   a0, 0(s6)
                addi s6, s6, 1
                ret

            # .L53_qq(a0=KofString*): emite "payload" com aspas duplas
            .L53_qq:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                li   a0, 34
                call .L53_ch
                addi a0, s0, 24
                lw   a1, 16(s0)
                call .L53_ap
                li   a0, 34
                call .L53_ch
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret

            # .L53_bt(a0=KofString*): emite `payload` com backticks (dialeto mysql)
            .L53_bt:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                li   a0, 96
                call .L53_ch
                addi a0, s0, 24
                lw   a1, 16(s0)
                call .L53_ap
                li   a0, 96
                call .L53_ch
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret

            # .L53_lit foi promovido para kof_orm_mysql_lit na peça B75
            # (S5.5 fatia 3 — uma cópia só do renderizador, regra 11).

            # ---------------------------------------------------------------
            # kof_orm_count_where(id*, field*, value*, table*, schema*) -> Long
            # slot: 0 &stmt
            # ---------------------------------------------------------------
            .globl kof_orm_count_where
            .type kof_orm_count_where, @function
            kof_orm_count_where:
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
                sd   s8, 16(sp)
                sd   s9, 8(sp)
                mv   s9, a0                        # id (S5.5: dispatch type)
                mv   s1, a1                        # field
                mv   s2, a2                        # value (box/KofString/null)
                mv   s3, a3                        # table
                mv   s4, a4                        # schema (não lido, igual x86)
                mv   a0, s9
                call kof_db_type                   # S5.5: type 2 -> wire mysql
                li   t0, 2
                beq  a0, t0, .L53_cw_mysql
                mv   a0, s9
                call .L53_conn
                mv   s0, a0                        # conn
                # cap = 88 + tblLen + fldLen
                lw   t0, 16(s3)
                lw   t1, 16(s1)
                add  t0, t0, t1
                addi t0, t0, 88
                mv   a0, t0
                call kof_alloc
                mv   s5, a0                        # base
                mv   s6, a0                        # cursor
                la   a0, .L53_s1
                li   a1, 21
                call .L53_ap
                mv   a0, s3
                call .L53_qq                       # "t"
                la   a0, .L53_s2
                li   a1, 7
                call .L53_ap
                mv   a0, s1
                call .L53_qq                       # "f"
                la   a0, .L53_s3
                li   a1, 4
                call .L53_ap
                sb   zero, 0(s6)                   # NUL p/ o prepare
                # ---- prepare --------------------------------------------------
                sd   zero, 0(sp)
                mv   a0, s0
                mv   a1, s5
                li   a2, -1
                mv   a3, sp
                li   a4, 0
                call sqlite3_prepare_v2
                bnez a0, .L53_zero
                ld   s7, 0(sp)                     # stmt (0 = falhou -> 0)
                beqz s7, .L53_zero
                # ---- bind ? (box §284 / KofString / null) ----------------------
                beqz s2, .L53_bn
                la   t0, .L53_magic
                ld   t0, 0(t0)
                ld   t1, 0(s2)
                bne  t0, t1, .L53_strchk
                lw   t1, 8(s2)                     # tag
                beqz t1, .L53_bint
                li   t2, 2
                beq  t1, t2, .L53_bquad
                li   t2, 3
                beq  t1, t2, .L53_bquad
                li   t2, 4
                beq  t1, t2, .L53_bdbl
                li   t2, 5
                beq  t1, t2, .L53_bflt
                j    .L53_bad
            .L53_bint:
                lw   a2, 16(s2)                    # sign-extend (movslq do x86)
                mv   a0, s7
                li   a1, 1
                call sqlite3_bind_int64
                j    .L53_go
            .L53_bquad:
                ld   a2, 16(s2)
                mv   a0, s7
                li   a1, 1
                call sqlite3_bind_int64
                j    .L53_go
            .L53_bdbl:
                ld   t0, 16(s2)
                fmv.d.x fa0, t0
                mv   a0, s7
                li   a1, 1
                call sqlite3_bind_double
                j    .L53_go
            .L53_bflt:
                lw   t0, 16(s2)
                fmv.w.x fa0, t0
                fcvt.d.s fa0, fa0                  # widen p/ REAL como o setFloat
                mv   a0, s7
                li   a1, 1
                call sqlite3_bind_double
                j    .L53_go
            .L53_strchk:
                lw   t0, 0(s2)                     # KofString tag (1,0,0)?
                li   t1, 1
                bne  t0, t1, .L53_bad
                lw   t0, 4(s2)
                bnez t0, .L53_bad
                ld   t0, 8(s2)
                bnez t0, .L53_bad
                addi a2, s2, 24
                lw   a3, 16(s2)
                li   a4, -1                        # SQLITE_TRANSIENT
                mv   a0, s7
                li   a1, 1
                call sqlite3_bind_text
                j    .L53_go
            .L53_bn:
                mv   a0, s7
                li   a1, 1
                call sqlite3_bind_null
                j    .L53_go
            .L53_bad:
                mv   a0, s7
                call sqlite3_finalize
                la   a0, .L53_badv
                li   a1, 57
                call kof_string_from_literal
                call kof_throw_string
            # ---- step + column_int64(0) ------------------------------------
            .L53_go:
                mv   a0, s7
                call sqlite3_step
                li   t0, 100                       # SQLITE_ROW
                bne  a0, t0, .L53_fin0
                mv   a0, s7
                li   a1, 0
                call sqlite3_column_int64
                mv   s8, a0                        # guarda antes do finalize
                mv   a0, s7
                call sqlite3_finalize
                mv   a0, s8
                j    .L53_out
            .L53_fin0:
                mv   a0, s7
                call sqlite3_finalize
            .L53_zero:
                li   a0, 0
                j    .L53_out
            # S5.5 fatia 2: SELECT COUNT(*) FROM `t` WHERE `f` = <lit> no wire
            # mysql, com o value renderizado em LITERAL (sem bind `?`).
            .L53_cw_mysql:
                mv   a0, s2
                call kof_orm_mysql_lit             # literal (peça B75, compartilhado)
                mv   s8, a0
                # cap = 24(hdr) + 36(const) + tblLen + fldLen + litLen
                lw   t0, 16(s3)
                lw   t1, 16(s1)
                add  t0, t0, t1
                lw   t1, 16(s8)
                add  t0, t0, t1
                addi a0, t0, 60
                call kof_alloc
                mv   s5, a0                        # base
                li   t0, 1
                sw   t0, 0(s5)
                sw   zero, 4(s5)
                sd   zero, 8(s5)
                sw   zero, 20(s5)
                addi s6, s5, 24                    # cursor
                la   a0, .L53_s1
                li   a1, 21
                call .L53_ap
                mv   a0, s3
                call .L53_bt                       # `table`
                la   a0, .L53_s2
                li   a1, 7
                call .L53_ap
                mv   a0, s1
                call .L53_bt                       # `field`
                la   a0, .L53_s4
                li   a1, 3
                call .L53_ap
                addi a0, s8, 24
                lw   a1, 16(s8)
                call .L53_ap                       # <lit>
                sb   zero, 0(s6)
                addi t0, s5, 24
                sub  t0, s6, t0
                sw   t0, 16(s5)                    # len = cursor - payload
                mv   a0, s9
                call kof_db_resolve
                mv   a1, s5
                call kof_db_mysql_scalar_int
            .L53_out:
                ld   ra, 88(sp)
                ld   s0, 80(sp)
                ld   s1, 72(sp)
                ld   s2, 64(sp)
                ld   s3, 56(sp)
                ld   s4, 48(sp)
                ld   s5, 40(sp)
                ld   s6, 32(sp)
                ld   s7, 24(sp)
                ld   s8, 16(sp)
                ld   s9, 8(sp)
                addi sp, sp, 96
                ret

            .section .rodata
            .L53_bc_pre:
                .ascii "unknown db connection: "
            .L53_s1:
                .ascii "SELECT COUNT(*) FROM "
            .L53_s2:
                .ascii " WHERE "
            .L53_s3:
                .ascii " = ?"
            .L53_s4:
                .ascii " = "
            .L53_nullv:
                .ascii "NULL"
            .L53_badv:
                .ascii "orm.count bind value: unsupported type on Native (ORM001)"
            .L53_magic:
                .quad 0x4B4F46425F425801
            .section .text
            """;
}
