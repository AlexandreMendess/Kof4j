package dev.kof.compiler.nat;

// DB-3/DB-1 cross, slice E-parte-6 (23/09, lane gaps-db): face `page` no
// riscv64 — port de RuntimeOrm8 (F2c3), ULTIMA face de leitura row-object do
// ORM no cross. kof_orm_page(id,limit,offset,table,schema,className):
// SELECT * FROM "t" LIMIT ? OFFSET ? com os DOIS params bindados, cada valor
// convertido a int64 pelo `intValue()` do host (int direto, long/dbl/flt
// truncam p/ int32, KofString do coerce = atoi superset honesto, null=0) e o
// MESMO loop de campos do RtB58/59 (397 + read_field) acumulado em
// kof_list_new/kof_list_add; vazio = lista VAZIA. Falha -> throw
// "sqlite: " + errmsg.
//
// No riscv64 o 6o arg className vem em a5 (regs a0..a7). Frame 240; slots
// 0 table|8 schema|16 className|24 limit|32 offset|56 buf|64 &stmt|72 vtab|
// 80 typeId|88 size|96 list|104 nCols|112 entry|120 conn|128 id|136 limVal|
// 144 offVal; s0..s8 em 224..160, ra em 232. NUNCA usa s10 (ver RtB55).
public final class NativeRiscvAsmRtB60 {

    private NativeRiscvAsmRtB60() {}

    static String RISCV_RUNTIME_ASM_B_60 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_page(id,limit,offset,table,schema,className) -> list*
            # ---------------------------------------------------------------
            .globl kof_orm_page
            .type kof_orm_page, @function
            kof_orm_page:
                addi sp, sp, -240
                sd   ra, 232(sp)
                sd   s0, 224(sp)
                sd   s1, 216(sp)
                sd   s2, 208(sp)
                sd   s3, 200(sp)
                sd   s4, 192(sp)
                sd   s5, 184(sp)
                sd   s6, 176(sp)
                sd   s7, 168(sp)
                sd   s8, 160(sp)
                sd   a0, 128(sp)                   # id
                sd   a1, 24(sp)                    # limit box
                sd   a2, 32(sp)                    # offset box
                sd   a3, 0(sp)                     # table
                sd   a4, 8(sp)                     # schema
                sd   a5, 16(sp)                    # className
                ld   a0, 128(sp)
                call kof_db_type
                li   t0, 2
                beq  a0, t0, .L60_page_mysql
                ld   a0, 128(sp)
                call kof_orm_conn
                sd   a0, 120(sp)                   # conn
                ld   a0, 8(sp)
                call kof_orm_parse_schema          # a0=ftab a2=nFields
                mv   s1, a0
                mv   s2, a2
                ld   t0, 16(sp)
                addi a0, t0, 24
                lw   a1, 16(t0)
                call kof_orm_ctors
                beqz a0, .L60_noface
                sd   a0, 72(sp)                    # vtab
                sd   a1, 80(sp)                    # typeId
                sd   a2, 88(sp)                    # totalSize
                call kof_list_new
                mv   s4, a0
                # ---- SQL: SELECT * FROM "t" LIMIT ? OFFSET ? -------------
                ld   t1, 0(sp)
                lw   t2, 16(t1)                    # tblLen
                addi a0, t2, 35
                call kof_alloc
                sd   a0, 56(sp)
                mv   s8, a0
                mv   a0, s8
                la   a1, .L60_s1
                li   a2, 14
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   a1, 0(sp)
                call kof_orm_sb_qstr
                mv   s8, a0
                mv   a0, s8
                la   a1, .L60_lo
                li   a2, 17
                call kof_orm_sb_append
                mv   s8, a0
                sb   zero, 0(s8)
                # ---- prepare + bind LIMIT(1)/OFFSET(2) --------------------
                sd   zero, 64(sp)
                ld   a0, 120(sp)
                ld   a1, 56(sp)
                li   a2, -1
                addi a3, sp, 64
                li   a4, 0
                call sqlite3_prepare_v2
                bnez a0, .L60_prep_fail
                ld   s0, 64(sp)
                beqz s0, .L60_prep_fail
                ld   a0, 24(sp)
                call .L60_pv
                sd   a0, 136(sp)
                mv   a0, s0
                li   a1, 1
                ld   a2, 136(sp)
                call sqlite3_bind_int64
                ld   a0, 32(sp)
                call .L60_pv
                sd   a0, 144(sp)
                mv   a0, s0
                li   a1, 2
                ld   a2, 144(sp)
                call sqlite3_bind_int64
                # ---- loop de linhas ---------------------------------------
            .L60_row:
                mv   a0, s0
                call sqlite3_step
                li   t0, 100
                bne  a0, t0, .L60_end
                ld   a0, 88(sp)
                call kof_alloc
                mv   s3, a0
                mv   a0, s3
                ld   a1, 80(sp)
                ld   a2, 72(sp)
                call kof_init_object
                mv   a0, s0
                call sqlite3_column_count
                sw   a0, 104(sp)
                li   s5, 0
            .L60_fld:
                bge  s5, s2, .L60_rowdone
                slli t0, s5, 5
                add  t0, s1, t0
                sd   t0, 112(sp)
                lw   s7, 8(t0)
                li   s6, 0
            .L60_col:
                lw   t0, 104(sp)
                bge  s6, t0, .L60_nomatch
                mv   a0, s0
                mv   a1, s6
                call sqlite3_column_name
                mv   t2, a0
                ld   t1, 112(sp)
                ld   t1, 0(t1)
                li   t3, 0
            .L60_cmp:
                bge  t3, s7, .L60_cmpend
                add  t4, t2, t3
                lbu  t4, 0(t4)
                add  t5, t1, t3
                lbu  t5, 0(t5)
                bne  t4, t5, .L60_nextcol
                addi t3, t3, 1
                j    .L60_cmp
            .L60_cmpend:
                add  t4, t2, t3
                lbu  t4, 0(t4)
                bnez t4, .L60_nextcol
                j    .L60_read
            .L60_nextcol:
                addi s6, s6, 1
                j    .L60_col
            .L60_read:
                slli t0, s5, 3
                addi t0, t0, 16
                add  t0, s3, t0
                mv   a0, s0
                mv   a1, s6
                mv   a2, t0
                ld   t1, 112(sp)
                lw   a3, 12(t1)
                call kof_orm_read_field
                addi s5, s5, 1
                j    .L60_fld
            .L60_rowdone:
                mv   a0, s4
                mv   a1, s3
                call kof_list_add
                j    .L60_row
            .L60_end:
                mv   a0, s0
                call sqlite3_finalize
                mv   a0, s4
                j    .L60_ret
            # ---- fail: throw "sqlite: " + errmsg --------------------------
            .L60_prep_fail:
                ld   a0, 120(sp)
                call sqlite3_errmsg
                mv   s7, a0
                beqz s7, .L60_pre_only
                mv   a0, s7
                call kof_io_strlen
                mv   a1, a0
                mv   a0, s7
                call kof_string_from_literal
                mv   s7, a0
                la   a0, .L60_pre
                li   a1, 8
                call kof_string_from_literal
                mv   a1, s7
                call kof_string_concat
                call kof_throw_string
            .L60_pre_only:
                la   a0, .L60_pre
                li   a1, 8
                call kof_string_from_literal
                call kof_throw_string
            .L60_nomatch:
                ld   t0, 112(sp)
                lw   t1, 8(t0)
                addi a0, t1, 2
                call kof_alloc
                mv   s7, a0
                li   t0, 34
                sb   t0, 0(s7)
                ld   t0, 112(sp)
                ld   a1, 0(t0)
                lw   a2, 8(t0)
                addi a0, s7, 1
                call kof_memcpy
                ld   t0, 112(sp)
                lw   t1, 8(t0)
                addi t1, t1, 1
                add  t1, s7, t1
                li   t0, 34
                sb   t0, 0(t1)
                ld   t0, 112(sp)
                lw   a1, 8(t0)
                addi a1, a1, 2
                mv   a0, s7
                call kof_io_make_string
                mv   s7, a0
                la   a0, .L60_nc
                li   a1, 18
                call kof_string_from_literal
                mv   a1, s7
                call kof_string_concat
                call kof_throw_string
            .L60_noface:
                la   a0, .L60_face
                li   a1, 46
                call kof_string_from_literal
                call kof_throw_string
            .L60_ret:
                ld   ra, 232(sp)
                ld   s0, 224(sp)
                ld   s1, 216(sp)
                ld   s2, 208(sp)
                ld   s3, 200(sp)
                ld   s4, 192(sp)
                ld   s5, 184(sp)
                ld   s6, 176(sp)
                ld   s7, 168(sp)
                ld   s8, 160(sp)
                addi sp, sp, 240
                ret
            # ---- dispatch mysql (type 2): tail-call kof_orm_page_mysql ----
            .L60_page_mysql:
                ld   a0, 128(sp)                   # id
                ld   a1, 24(sp)                    # limit box
                ld   a2, 32(sp)                    # offset box
                ld   a3, 0(sp)                     # table
                ld   a4, 8(sp)                     # schema
                ld   a5, 16(sp)                    # className
                ld   ra, 232(sp)
                ld   s0, 224(sp)
                ld   s1, 216(sp)
                ld   s2, 208(sp)
                ld   s3, 200(sp)
                ld   s4, 192(sp)
                ld   s5, 184(sp)
                ld   s6, 176(sp)
                ld   s7, 168(sp)
                ld   s8, 160(sp)
                addi sp, sp, 240
                j    kof_orm_page_mysql

            # ---------------------------------------------------------------
            # .L60_pv(a0=box) -> a0 = (int) valor, sign-extended (intValue()
            # do host): int direto, long/dbl/flt truncam p/ int32, KofString
            # = atoi superset honesto, null/desconhecido = 0. Sem call interno
            # -> ret seguro (nao toca ra).
            # ---------------------------------------------------------------
            .L60_pv:
                beqz a0, .L60_pv0
                la   t0, .L60_magic
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                bne  t0, t1, .L60_pv_str
                lw   t1, 8(a0)                     # tag
                beqz t1, .L60_pvi
                li   t2, 1
                beq  t1, t2, .L60_pvq
                li   t2, 2
                beq  t1, t2, .L60_pvq
                li   t2, 4
                beq  t1, t2, .L60_pvd
                li   t2, 5
                beq  t1, t2, .L60_pvf
                j    .L60_pv0
            .L60_pvi:
                lw   a0, 16(a0)
                ret
            .L60_pvq:
                ld   a0, 16(a0)
                sext.w a0, a0
                ret
            .L60_pvd:
                ld   t3, 16(a0)
                fmv.d.x fa0, t3
                fcvt.l.d a0, fa0, rtz
                sext.w a0, a0
                ret
            .L60_pvf:
                lw   t3, 16(a0)
                fmv.w.x fa0, t3
                fcvt.l.s a0, fa0, rtz
                sext.w a0, a0
                ret
            .L60_pv_str:
                lw   t0, 0(a0)                     # KofString (1,0,0)?
                li   t1, 1
                bne  t0, t1, .L60_pv0
                lw   t0, 4(a0)
                bnez t0, .L60_pv0
                lw   t0, 8(a0)
                bnez t0, .L60_pv0
                lw   t1, 16(a0)                    # len
                addi t2, a0, 24                    # body
                li   a0, 0                         # val int32
                li   t3, 0                         # i
                li   t4, 0                         # neg
                blez t1, .L60_pv_ret0
                lbu  t5, 0(t2)
                li   t6, 45                        # '-'
                bne  t5, t6, .L60_pv_plus
                li   t4, 1
                addi t3, t3, 1
                j    .L60_pv_loop
            .L60_pv_plus:
                li   t6, 43                        # '+'
                bne  t5, t6, .L60_pv_loop
                addi t3, t3, 1
            .L60_pv_loop:
                bge  t3, t1, .L60_pv_done
                add  t5, t2, t3
                lbu  t5, 0(t5)
                li   t6, 48
                blt  t5, t6, .L60_pv_done
                li   t6, 57
                bgt  t5, t6, .L60_pv_done
                li   t6, 10
                mul  a0, a0, t6
                addi t5, t5, -48
                add  a0, a0, t5
                addi t3, t3, 1
                j    .L60_pv_loop
            .L60_pv_done:
                beqz t4, .L60_pv_ret
                neg  a0, a0
            .L60_pv_ret:
                sext.w a0, a0
                ret
            .L60_pv_ret0:
                li   a0, 0
                ret
            .L60_pv0:
                li   a0, 0
                ret

            .section .rodata
            .L60_s1:
                .ascii "SELECT * FROM "
            .L60_lo:
                .ascii " LIMIT ? OFFSET ?"
            .L60_pre:
                .ascii "sqlite: "
            .L60_nc:
                .ascii "sqlite: no column "
            .L60_face:
                .ascii "orm.page: entity class not registered (ORM001)"
            .L60_magic:
                .quad 0x4B4F46425F425801
            .section .text
            """;
}
