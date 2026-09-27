package dev.kof.compiler.nat;

// DB-3/DB-1 cross, S5.5 fatia 5d (24/09, lane gaps-db): face `page` sobre o
// wire MySQL no riscv64/aarch64 — F2d4c de RuntimeOrmMysqlPage x86-64.
//
// SELECT * FROM `t` LIMIT <lim> OFFSET <off> via COM_QUERY, com lim/off
// convertidos do box do host para int64 por kof_orm_mysql_pv (port de
// .Lorm8_pv) e entao kof_long_to_string (literais numericos, sem bind).
// Mesmo walk de pacotes/materializacao do all (B68/B63/B78/B78H), ctors uma
// vez, um record por linha em kof_list_new/kof_list_add, lista VAZIA (nunca
// null). ERR do servidor -> throw "mysql: <msg>"; campo sem coluna -> throw
// "mysql: no column <nome>"; conexao morta -> throw "mysql: connection lost"
// (R6).
//
// ABI: kof_orm_page_mysql(a0=id,a1=limbox,a2=offbox,a3=table,a4=schema,
//      a5=className) -> a0 list*
// NUNCA usa s10 (x16 caller-saved no aarch64).
//
// Frame 272: slots 0 id|8 limbox|16 offbox|24 limstr|32 offstr|40 table|
// 48 schema|56 className|64 fd|72 ftab|80 nFields|88 sql|96 list|104 dst|
// 112 nCols|120 i|128 vtab|136 typeId|144 totalSize|152 vallen|160 entry.
// s0 fd, s1 ftab, s2 nFields, s3 list, s4 dst, s5 colj, s6 valptr, s7 slot*,
// s8 cursor, s9 colcount/loop.
public final class NativeRiscvAsmRtB81 {

    private NativeRiscvAsmRtB81() {}

    static String RISCV_RUNTIME_ASM_B_81 = """
            .section .data
            .align 3
            .L81_colf:
                .zero 256
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_page_mysql(id*, limbox*, offbox*, table*, schema*,
            #                    className*) -> list*
            # ---------------------------------------------------------------
            .globl kof_orm_page_mysql
            .type kof_orm_page_mysql, @function
            kof_orm_page_mysql:
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
                sd   a3, 40(sp)
                sd   a4, 48(sp)
                sd   a5, 56(sp)
                # ---- lim/off box -> int64 -> literal numerico -------------
                ld   a0, 8(sp)
                call kof_orm_mysql_pv
                call kof_long_to_string
                sd   a0, 24(sp)
                ld   a0, 16(sp)
                call kof_orm_mysql_pv
                call kof_long_to_string
                sd   a0, 32(sp)
                # ---- resolve + schema + ctors -----------------------------
                ld   a0, 0(sp)
                call kof_db_resolve
                beqz a0, .L81_badconn
                mv   s0, a0                     # fd
                sd   a0, 64(sp)
                ld   a0, 48(sp)
                call kof_orm_parse_schema       # a0=ftab a2=nF
                mv   s1, a0                     # ftab
                mv   s2, a2                     # nFields
                sd   a0, 72(sp)
                sd   a2, 80(sp)
                ld   t0, 56(sp)
                addi a0, t0, 24
                lw   a1, 16(t0)
                call kof_orm_ctors
                beqz a0, .L81_noface
                sd   a0, 128(sp)
                sd   a1, 136(sp)
                sd   a2, 144(sp)
                call kof_list_new
                mv   s3, a0                     # lista destino
                sd   a0, 96(sp)
                # ---- SQL: SELECT * FROM `t` LIMIT <lim> OFFSET <off> ------
                ld   t0, 40(sp)
                lw   t1, 16(t0)
                ld   t0, 24(sp)
                lw   t2, 16(t0)
                add  t1, t1, t2
                ld   t0, 32(sp)
                lw   t2, 16(t0)
                add  t1, t1, t2
                addi a0, t1, 64
                call kof_alloc
                sd   a0, 88(sp)
                mv   s8, a0
                li   t0, 1
                sw   t0, 0(s8)
                sw   zero, 4(s8)
                sd   zero, 8(s8)
                sw   zero, 20(s8)
                addi s8, s8, 24
                mv   a0, s8
                la   a1, .L81_s1
                li   a2, 14
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   a1, 40(sp)
                call kof_orm_mysql_bt_str
                mv   s8, a0
                mv   a0, s8
                la   a1, .L81_lim
                li   a2, 7
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   t0, 24(sp)
                addi a1, t0, 24
                lw   a2, 16(t0)
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                la   a1, .L81_off
                li   a2, 8
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   t0, 32(sp)
                addi a1, t0, 24
                lw   a2, 16(t0)
                call kof_orm_sb_append
                mv   s8, a0
                sb   zero, 0(s8)
                ld   t0, 88(sp)
                addi t1, t0, 24
                sub  t1, s8, t1
                sw   t1, 16(t0)
                # ---- COM_QUERY (request no heap) --------------------------
                ld   t1, 88(sp)
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
                ld   t1, 88(sp)
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
                beqz a0, .L81_dead
                mv   s8, a0
                lbu  t0, 0(s8)
                li   t1, 0xFF
                beq  t0, t1, .L81_err
                beqz t0, .L81_empty            # OK sem resultset
                # ---- column count -----------------------------------------
                mv   a0, s8
                call kof_db_mysql_lenenc
                mv   s9, a0
                li   t0, 64
                ble  s9, t0, .L81_cc_ok
                li   s9, 64
            .L81_cc_ok:
                sd   s9, 112(sp)
                sd   zero, 120(sp)
            .L81_cols:
                ld   t0, 120(sp)
                ld   t1, 112(sp)
                bge  t0, t1, .L81_cols_done
                call kof_db_mysql_next
                beqz a0, .L81_dead
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
                la   t2, .L81_colf
                ld   t0, 120(sp)
                slli t0, t0, 2
                add  t2, t2, t0
                li   t0, -1
                sw   t0, 0(t2)
                li   s9, 0
            .L81_match:
                bge  s9, s2, .L81_nextcol
                slli t0, s9, 5
                add  t0, s1, t0
                lw   t1, 8(t0)
                bne  t1, s7, .L81_matchnext
                ld   t1, 0(t0)
                li   t2, 0
            .L81_mcmp:
                bge  t2, s7, .L81_matched
                add  t3, s6, t2
                lbu  t3, 0(t3)
                add  t4, t1, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .L81_matchnext
                addi t2, t2, 1
                j    .L81_mcmp
            .L81_matched:
                la   t2, .L81_colf
                ld   t0, 120(sp)
                slli t0, t0, 2
                add  t2, t2, t0
                sw   s9, 0(t2)
                j    .L81_nextcol
            .L81_matchnext:
                addi s9, s9, 1
                j    .L81_match
            .L81_nextcol:
                add  s8, s6, s7
                mv   a0, s8
                call kof_db_mysql_lenenc        # pula org_name
                add  s8, a1, a0
                ld   t0, 120(sp)
                addi t0, t0, 1
                sd   t0, 120(sp)
                j    .L81_cols
            .L81_cols_done:
                call kof_db_mysql_next
                beqz a0, .L81_dead
                lbu  t0, 0(a0)
                beqz t0, .L81_empty
                # ---- todo campo do schema tem coluna? ---------------------
                sd   zero, 120(sp)
            .L81_chkfld:
                ld   t0, 120(sp)
                bge  t0, s2, .L81_rows
                li   s9, 0
            .L81_chkcol:
                ld   t1, 112(sp)
                bge  s9, t1, .L81_noch
                la   t2, .L81_colf
                slli t3, s9, 2
                add  t2, t2, t3
                lw   t2, 0(t2)
                beq  t2, t0, .L81_chkok
                addi s9, s9, 1
                j    .L81_chkcol
            .L81_chkok:
                ld   t0, 120(sp)
                addi t0, t0, 1
                sd   t0, 120(sp)
                j    .L81_chkfld
            .L81_noch:
                la   a0, .L81_nc
                li   a1, 18
                call kof_string_from_literal
                mv   s9, a0
                ld   t0, 120(sp)
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
            .L81_rows:
                call kof_db_mysql_next
                beqz a0, .L81_dead
                lbu  t0, 0(a0)
                li   t1, 0xFF
                beq  t0, t1, .L81_err
                li   t1, 0xFE
                beq  t0, t1, .L81_ok
                mv   s8, a0                     # cursor da linha
                ld   a0, 144(sp)
                call kof_alloc
                mv   s4, a0                     # dst
                mv   a0, s4
                ld   a1, 136(sp)
                ld   a2, 128(sp)
                call kof_init_object
                sd   zero, 120(sp)             # colj
            .L81_rcol:
                ld   t0, 120(sp)
                ld   t1, 112(sp)
                bge  t0, t1, .L81_rowdone
                lbu  t0, 0(s8)
                li   t1, 0xFB
                beq  t0, t1, .L81_rnull
                mv   a0, s8
                call kof_db_mysql_lenenc
                sd   a0, 152(sp)
                mv   s6, a1
                add  s8, a1, a0
                j    .L81_rconv
            .L81_rnull:
                addi s8, s8, 1
                li   t0, -1
                sd   t0, 152(sp)
            .L81_rconv:
                la   t2, .L81_colf
                ld   t0, 120(sp)
                slli t0, t0, 2
                add  t2, t2, t0
                lw   s9, 0(t2)
                bltz s9, .L81_rnext
                slli t0, s9, 5
                add  t0, s1, t0
                sd   t0, 160(sp)               # entry
                slli t0, s9, 3
                addi t0, t0, 16
                add  t0, s4, t0
                mv   s7, t0                     # slot*
                ld   t0, 152(sp)
                bltz t0, .L81_vnull
                ld   t0, 160(sp)
                lw   t1, 12(t0)                 # typeCode
                li   t2, 2
                beq  t1, t2, .L81_vstr
                li   t2, 3
                beq  t1, t2, .L81_vbool
                li   t2, 4
                beq  t1, t2, .L81_vdbl
                li   t2, 5
                beq  t1, t2, .L81_vflt
                li   t2, 1
                beq  t1, t2, .L81_vlng
                mv   a0, s6
                ld   a1, 152(sp)
                call kof_orm_mysql_atoi
                sd   a0, 0(s7)
                j    .L81_rnext
            .L81_vlng:
                mv   a0, s6
                ld   a1, 152(sp)
                call kof_orm_mysql_atoi
                sd   a0, 0(s7)
                j    .L81_rnext
            .L81_vstr:
                mv   a0, s6
                ld   a1, 152(sp)
                call kof_io_make_string
                sd   a0, 0(s7)
                j    .L81_rnext
            .L81_vbool:
                mv   a0, s6
                ld   a1, 152(sp)
                call kof_orm_mysql_bool
                sd   a0, 0(s7)
                j    .L81_rnext
            .L81_vdbl:
                mv   a0, s6
                ld   a1, 152(sp)
                call kof_io_make_string
                call kof_string_to_double
                sd   a0, 0(s7)
                j    .L81_rnext
            .L81_vflt:
                mv   a0, s6
                ld   a1, 152(sp)
                call kof_io_make_string
                call kof_string_to_float
                sw   a0, 0(s7)
                j    .L81_rnext
            .L81_vnull:
                sd   zero, 0(s7)
            .L81_rnext:
                ld   t0, 120(sp)
                addi t0, t0, 1
                sd   t0, 120(sp)
                j    .L81_rcol
            .L81_rowdone:
                mv   a0, s3
                mv   a1, s4
                call kof_list_add
                j    .L81_rows
            .L81_empty:
                mv   a0, s3
                j    .L81_ret
            .L81_ok:
                mv   a0, s3
                j    .L81_ret
            # ---- ERR do servidor -> throw "mysql: <msg>" -----------------
            .L81_err:
                addi a0, a0, 9
                addi a1, a1, -9
                bgtz a1, .L81_errclip
                li   a1, 0
            .L81_errclip:
                li   t0, 400
                ble  a1, t0, .L81_errok
                li   a1, 400
            .L81_errok:
                mv   s6, a0
                mv   s7, a1
                la   a0, .L81_my
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
            .L81_dead:
                la   a0, .L81_lost
                li   a1, 23
                call kof_string_from_literal
                call kof_throw_string
            .L81_badconn:
                la   a0, .L81_bc
                li   a1, 23
                call kof_string_from_literal
                ld   a1, 0(sp)
                call kof_string_concat
                call kof_throw_string
            .L81_noface:
                la   a0, .L81_face
                li   a1, 46
                call kof_string_from_literal
                call kof_throw_string
            .L81_ret:
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
            .L81_s1:
                .ascii "SELECT * FROM "
            .L81_lim:
                .ascii " LIMIT "
            .L81_off:
                .ascii " OFFSET "
            .L81_my:
                .ascii "mysql: "
            .L81_nc:
                .ascii "mysql: no column "
            .L81_lost:
                .ascii "mysql: connection lost"
            .L81_bc:
                .ascii "unknown db connection: "
            .L81_face:
                .ascii "orm.page: entity class not registered (ORM001)"
            .section .text
            """;
}
