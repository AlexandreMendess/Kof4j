package dev.kof.compiler.nat;

// DB-3/DB-1 cross, S5.5 fatia 5c (24/09, lane gaps-db): faces `where`/`where_op`
// sobre o wire MySQL no riscv64/aarch64 — F2d4b de RuntimeOrmMysqlWhere x86-64.
//
// UM corpo para as duas faces. O op chega como KofString* (ou 0 na face
// `where`) e passa por kof_orm_mysql_op (B80H, port de RuntimeOrmMysqlOp:
// `==`->`=`, `>` `<` `>=` `<=` `!=` `LIKE`, resto throw
// "ORM operator not allowed: <op>"), como no x86. SQL:
// SELECT * FROM `t` WHERE `f` <op> ? via COM_QUERY; o value vira literal por
// kof_orm_mysql_lit (B75, box §284/KofString/null) e o `?` e' trocado por
// kof_db_mysql_replace_q (B71). Mesmo walk de pacotes do all (B68/B63/B78/
// B78H), ctors uma vez, um record por linha em kof_list_new/kof_list_add,
// lista VAZIA (nunca null). ERR do servidor -> throw "mysql: <msg>"; campo
// sem coluna -> throw "mysql: no column <nome>"; conexao morta -> throw
// "mysql: connection lost" (R6).
//
// ABI: kof_orm_where_mysql(a0=id,a1=field,a2=value,a3=op KofString*|0,
//      a4=table,a5=schema,a6=className) -> a0 list*
// NUNCA usa s10 (x16 caller-saved no aarch64).
//
// Frame 272: slots 0 id|8 field|16 value|24 opStr|32 opBody|40 opLen|
// 48 table|56 schema|64 className|72 fd|80 ftab|88 nFields|96 sql|104 list|
// 112 dst|120 nCols|128 i|136 vtab|144 typeId|152 totalSize|160 vallen|
// 168 entry. s0 fd, s1 ftab, s2 nFields, s3 list, s4 dst, s5 colj, s6 valptr,
// s7 slot*, s8 cursor, s9 colcount/loop.
public final class NativeRiscvAsmRtB80 {

    private NativeRiscvAsmRtB80() {}

    static String RISCV_RUNTIME_ASM_B_80 = """
            .section .data
            .align 3
            .L80_colf:
                .zero 256
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_where_mysql(id*, field*, value*, op*|0, table*, schema*,
            #                     className*) -> list*
            # ---------------------------------------------------------------
            .globl kof_orm_where_mysql
            .type kof_orm_where_mysql, @function
            kof_orm_where_mysql:
                addi sp, sp, -272
                sd   ra, 264(sp)
                sd   s0, 256(sp)
                sd   s1, 248(sp)
                sd   s2, 240(sp)
                sd   s3, 232(sp)
                sd   s4, 224(sp)
                sd   s5, 216(sp)
                sd   s6, 208(sp)
                sd   s7, 200(sp)
                sd   s8, 192(sp)
                sd   s9, 184(sp)
                sd   a0, 0(sp)
                sd   a1, 8(sp)
                sd   a2, 16(sp)
                sd   a3, 24(sp)
                sd   a4, 48(sp)
                sd   a5, 56(sp)
                sd   a6, 64(sp)
                # ---- whitelist/normalizacao do op (B80H) ------------------
                ld   a0, 24(sp)
                call kof_orm_mysql_op
                sd   a0, 32(sp)
                sd   a1, 40(sp)
                # ---- resolve + schema + ctors -----------------------------
                ld   a0, 0(sp)
                call kof_db_resolve
                beqz a0, .L80_badconn
                mv   s0, a0                     # fd
                sd   a0, 72(sp)
                ld   a0, 56(sp)
                call kof_orm_parse_schema       # a0=ftab a2=nF
                mv   s1, a0                     # ftab
                mv   s2, a2                     # nFields
                sd   a0, 80(sp)
                sd   a2, 88(sp)
                # ---- resolve ctor UMA vez ---------------------------------
                ld   t0, 64(sp)
                addi a0, t0, 24
                lw   a1, 16(t0)
                call kof_orm_ctors
                beqz a0, .L80_noface
                sd   a0, 136(sp)
                sd   a1, 144(sp)
                sd   a2, 152(sp)
                call kof_list_new
                mv   s3, a0                     # lista destino
                sd   a0, 104(sp)
                # ---- SQL: SELECT * FROM `t` WHERE `f` <op> ? --------------
                ld   t0, 48(sp)
                lw   t1, 16(t0)
                ld   t0, 8(sp)
                lw   t2, 16(t0)
                add  t1, t1, t2
                ld   t2, 40(sp)
                add  t1, t1, t2
                addi a0, t1, 64
                call kof_alloc
                sd   a0, 96(sp)
                mv   s8, a0
                li   t0, 1
                sw   t0, 0(s8)
                sw   zero, 4(s8)
                sd   zero, 8(s8)
                sw   zero, 20(s8)
                addi s8, s8, 24
                mv   a0, s8
                la   a1, .L80_s1
                li   a2, 14
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   a1, 48(sp)
                call kof_orm_mysql_bt_str
                mv   s8, a0
                mv   a0, s8
                la   a1, .L80_s2
                li   a2, 7
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   a1, 8(sp)
                call kof_orm_mysql_bt_str
                mv   s8, a0
                mv   a0, s8
                la   a1, .L80_sp
                li   a2, 1
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   a1, 32(sp)
                ld   a2, 40(sp)
                call kof_orm_sb_append             # <op>
                mv   s8, a0
                mv   a0, s8
                la   a1, .L80_s3
                li   a2, 2
                call kof_orm_sb_append
                mv   s8, a0
                sb   zero, 0(s8)
                ld   t0, 96(sp)
                addi t1, t0, 24
                sub  t1, s8, t1
                sw   t1, 16(t0)
                # ---- literal do value + troca o `?` -----------------------
                ld   a0, 16(sp)
                call kof_orm_mysql_lit
                mv   a1, a0
                ld   a0, 96(sp)
                call kof_db_mysql_replace_q
                sd   a0, 96(sp)
                # ---- COM_QUERY (request no heap) --------------------------
                ld   t1, 96(sp)
                lw   s8, 16(t1)                 # sqllen
                addi a0, s8, 5
                call kof_alloc
                mv   s9, a0
                addi t0, s8, 1
                andi t1, t0, 0xff
                sb   t1, 0(s9)
                srli t1, t0, 8
                andi t1, t1, 0xff
                sb   t1, 1(s9)
                srli t1, t0, 16
                andi t1, t1, 0xff
                sb   t1, 2(s9)
                sb   zero, 3(s9)
                li   t1, 3
                sb   t1, 4(s9)
                addi a0, s9, 5
                ld   t1, 96(sp)
                addi a1, t1, 24
                mv   a2, s8
                call kof_memcpy
                mv   a0, s0
                mv   a1, s9
                addi a2, s8, 5
                call kof_plat_write
                mv   a0, s0
                call kof_db_mysql_reset
                call kof_db_mysql_next
                beqz a0, .L80_dead
                mv   s8, a0
                lbu  t0, 0(s8)
                li   t1, 0xFF
                beq  t0, t1, .L80_err
                beqz t0, .L80_empty            # OK sem resultset
                # ---- column count -----------------------------------------
                mv   a0, s8
                call kof_db_mysql_lenenc
                mv   s9, a0
                li   t0, 64
                ble  s9, t0, .L80_cc_ok
                li   s9, 64
            .L80_cc_ok:
                sd   s9, 120(sp)
                sd   zero, 128(sp)
            .L80_cols:
                ld   t0, 128(sp)
                ld   t1, 120(sp)
                bge  t0, t1, .L80_cols_done
                call kof_db_mysql_next
                beqz a0, .L80_dead
                mv   s8, a0
                mv   a0, s8
                call kof_db_mysql_lenenc
                add  s8, a1, a0
                mv   a0, s8
                call kof_db_mysql_lenenc
                add  s8, a1, a0
                mv   a0, s8
                call kof_db_mysql_lenenc
                add  s8, a1, a0
                mv   a0, s8
                call kof_db_mysql_lenenc
                add  s8, a1, a0
                mv   a0, s8
                call kof_db_mysql_lenenc
                mv   s6, a1                     # name ptr
                mv   s7, a0                     # name len
                la   t2, .L80_colf
                ld   t0, 128(sp)
                slli t0, t0, 2
                add  t2, t2, t0
                li   t0, -1
                sw   t0, 0(t2)
                li   s9, 0
            .L80_match:
                bge  s9, s2, .L80_nextcol
                slli t0, s9, 5
                add  t0, s1, t0
                lw   t1, 8(t0)
                bne  t1, s7, .L80_matchnext
                ld   t1, 0(t0)
                li   t2, 0
            .L80_mcmp:
                bge  t2, s7, .L80_matched
                add  t3, s6, t2
                lbu  t3, 0(t3)
                add  t4, t1, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .L80_matchnext
                addi t2, t2, 1
                j    .L80_mcmp
            .L80_matched:
                la   t2, .L80_colf
                ld   t0, 128(sp)
                slli t0, t0, 2
                add  t2, t2, t0
                sw   s9, 0(t2)
                j    .L80_nextcol
            .L80_matchnext:
                addi s9, s9, 1
                j    .L80_match
            .L80_nextcol:
                add  s8, s6, s7
                mv   a0, s8
                call kof_db_mysql_lenenc        # pula org_name
                add  s8, a1, a0
                ld   t0, 128(sp)
                addi t0, t0, 1
                sd   t0, 128(sp)
                j    .L80_cols
            .L80_cols_done:
                call kof_db_mysql_next
                beqz a0, .L80_dead
                lbu  t0, 0(a0)
                beqz t0, .L80_empty
                # ---- todo campo do schema tem coluna? ---------------------
                sd   zero, 128(sp)
            .L80_chkfld:
                ld   t0, 128(sp)
                bge  t0, s2, .L80_rows
                li   s9, 0
            .L80_chkcol:
                ld   t1, 120(sp)
                bge  s9, t1, .L80_noch
                la   t2, .L80_colf
                slli t3, s9, 2
                add  t2, t2, t3
                lw   t2, 0(t2)
                beq  t2, t0, .L80_chkok
                addi s9, s9, 1
                j    .L80_chkcol
            .L80_chkok:
                ld   t0, 128(sp)
                addi t0, t0, 1
                sd   t0, 128(sp)
                j    .L80_chkfld
            .L80_noch:
                la   a0, .L80_nc
                li   a1, 18
                call kof_string_from_literal
                mv   s9, a0
                ld   t0, 128(sp)
                slli t0, t0, 5
                add  t0, s1, t0
                ld   a0, 0(t0)
                lw   a1, 8(t0)
                call kof_io_make_string
                mv   a1, a0
                mv   a0, s9
                call kof_string_concat
                call kof_throw_string
            # ---- loop de linhas -------------------------------------------
            .L80_rows:
                call kof_db_mysql_next
                beqz a0, .L80_dead
                lbu  t0, 0(a0)
                li   t1, 0xFF
                beq  t0, t1, .L80_err
                li   t1, 0xFE
                beq  t0, t1, .L80_ok
                mv   s8, a0                     # cursor da linha
                ld   a0, 152(sp)
                call kof_alloc
                mv   s4, a0                     # dst
                mv   a0, s4
                ld   a1, 144(sp)
                ld   a2, 136(sp)
                call kof_init_object
                sd   zero, 128(sp)             # colj
            .L80_rcol:
                ld   t0, 128(sp)
                ld   t1, 120(sp)
                bge  t0, t1, .L80_rowdone
                lbu  t0, 0(s8)
                li   t1, 0xFB
                beq  t0, t1, .L80_rnull
                mv   a0, s8
                call kof_db_mysql_lenenc
                sd   a0, 160(sp)
                mv   s6, a1
                add  s8, a1, a0
                j    .L80_rconv
            .L80_rnull:
                addi s8, s8, 1
                li   t0, -1
                sd   t0, 160(sp)
            .L80_rconv:
                la   t2, .L80_colf
                ld   t0, 128(sp)
                slli t0, t0, 2
                add  t2, t2, t0
                lw   s9, 0(t2)
                bltz s9, .L80_rnext
                slli t0, s9, 5
                add  t0, s1, t0
                sd   t0, 168(sp)               # entry
                slli t0, s9, 3
                addi t0, t0, 16
                add  t0, s4, t0
                mv   s7, t0                     # slot*
                ld   t0, 160(sp)
                bltz t0, .L80_vnull
                ld   t0, 168(sp)
                lw   t1, 12(t0)                 # typeCode
                li   t2, 2
                beq  t1, t2, .L80_vstr
                li   t2, 3
                beq  t1, t2, .L80_vbool
                li   t2, 4
                beq  t1, t2, .L80_vdbl
                li   t2, 5
                beq  t1, t2, .L80_vflt
                li   t2, 1
                beq  t1, t2, .L80_vlng
                mv   a0, s6
                ld   a1, 160(sp)
                call kof_orm_mysql_atoi
                sd   a0, 0(s7)
                j    .L80_rnext
            .L80_vlng:
                mv   a0, s6
                ld   a1, 160(sp)
                call kof_orm_mysql_atoi
                sd   a0, 0(s7)
                j    .L80_rnext
            .L80_vstr:
                mv   a0, s6
                ld   a1, 160(sp)
                call kof_io_make_string
                sd   a0, 0(s7)
                j    .L80_rnext
            .L80_vbool:
                mv   a0, s6
                ld   a1, 160(sp)
                call kof_orm_mysql_bool
                sd   a0, 0(s7)
                j    .L80_rnext
            .L80_vdbl:
                mv   a0, s6
                ld   a1, 160(sp)
                call kof_io_make_string
                call kof_string_to_double
                sd   a0, 0(s7)
                j    .L80_rnext
            .L80_vflt:
                mv   a0, s6
                ld   a1, 160(sp)
                call kof_io_make_string
                call kof_string_to_float
                sw   a0, 0(s7)
                j    .L80_rnext
            .L80_vnull:
                sd   zero, 0(s7)
            .L80_rnext:
                ld   t0, 128(sp)
                addi t0, t0, 1
                sd   t0, 128(sp)
                j    .L80_rcol
            .L80_rowdone:
                mv   a0, s3
                mv   a1, s4
                call kof_list_add
                j    .L80_rows
            .L80_empty:
                mv   a0, s3
                j    .L80_ret
            .L80_ok:
                mv   a0, s3
                j    .L80_ret
            # ---- ERR do servidor -> throw "mysql: <msg>" -----------------
            .L80_err:
                addi a0, a0, 9
                addi a1, a1, -9
                bgtz a1, .L80_errclip
                li   a1, 0
            .L80_errclip:
                li   t0, 400
                ble  a1, t0, .L80_errok
                li   a1, 400
            .L80_errok:
                mv   s6, a0
                mv   s7, a1
                la   a0, .L80_my
                li   a1, 7
                call kof_string_from_literal
                mv   s8, a0
                mv   a0, s6
                mv   a1, s7
                call kof_io_make_string
                mv   a1, a0
                mv   a0, s8
                call kof_string_concat
                call kof_throw_string
            .L80_dead:
                la   a0, .L80_lost
                li   a1, 23
                call kof_string_from_literal
                call kof_throw_string
            .L80_badconn:
                la   a0, .L80_bc
                li   a1, 23
                call kof_string_from_literal
                ld   a1, 0(sp)
                call kof_string_concat
                call kof_throw_string
            .L80_noface:
                la   a0, .L80_face
                li   a1, 47
                call kof_string_from_literal
                call kof_throw_string
            .L80_ret:
                ld   ra, 264(sp)
                ld   s0, 256(sp)
                ld   s1, 248(sp)
                ld   s2, 240(sp)
                ld   s3, 232(sp)
                ld   s4, 224(sp)
                ld   s5, 216(sp)
                ld   s6, 208(sp)
                ld   s7, 200(sp)
                ld   s8, 192(sp)
                ld   s9, 184(sp)
                addi sp, sp, 272
                ret

            .section .rodata
            .L80_s1:
                .ascii "SELECT * FROM "
            .L80_s2:
                .ascii " WHERE "
            .L80_sp:
                .ascii " "
            .L80_s3:
                .ascii " ?"
            .L80_my:
                .ascii "mysql: "
            .L80_nc:
                .ascii "mysql: no column "
            .L80_lost:
                .ascii "mysql: connection lost"
            .L80_bc:
                .ascii "unknown db connection: "
            .L80_face:
                .ascii "orm.where: entity class not registered (ORM001)"
            .section .text
            """;
}
