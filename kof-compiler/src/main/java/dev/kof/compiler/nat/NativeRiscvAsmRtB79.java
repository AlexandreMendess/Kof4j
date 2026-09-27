package dev.kof.compiler.nat;

// DB-3/DB-1 cross, S5.5 fatia 5 (24/09, lane gaps-db): kof_orm_all sobre o
// wire MySQL no riscv64/aarch64 — F2d4a de RuntimeOrmMysqlAll x86-64.
//
// SELECT * FROM `t` (sem bind) via COM_QUERY; mesmo reader de pacotes (B68) e
// mesma casacao por NOME/typeCode do find (B78/B78H). UMA resolucao de
// kof_orm_ctors antes do loop; por linha kof_alloc + kof_init_object + slots
// convertidos (int/long atoi, bool §397, string, double/float; NULL ->
// 0/null/false) e acumula em kof_list_new/kof_list_add. Lista VAZIA (nunca
// null) quando nao ha linhas. ERR do servidor -> throw "mysql: <msg>";
// campo do schema sem coluna -> throw "mysql: no column <nome>" (R6).
//
// O dispatch de tipo mora no kof_orm_all (B58), que faz tail-call para ca
// quando kof_db_type(id)==2. NUNCA usa s10 (x16 caller-saved no aarch64).
//
// Frame 224: slots 0 id|8 table|16 schema|24 className|32 fd|40 ftab|
// 48 nFields|56 sql|64 list|72 dst|80 nCols|88 i|96 vtab|104 typeId|
// 112 totalSize|120 vallen(-1=NULL). s0..s9: s0 fd, s1 ftab, s2 nFields,
// s3 list, s4 dst, s5 colj, s6 valptr, s7 slot*, s8 cursor, s9 colcount.
public final class NativeRiscvAsmRtB79 {

    private NativeRiscvAsmRtB79() {}

    static String RISCV_RUNTIME_ASM_B_79 = """
            .section .data
            .align 3
            .L79_colf:
                .zero 256
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_all_mysql(id*, table*, schema*, className*) -> list*
            # ---------------------------------------------------------------
            .globl kof_orm_all_mysql
            .type kof_orm_all_mysql, @function
            kof_orm_all_mysql:
                addi sp, sp, -224
                sd   ra, 216(sp)
                sd   s0, 208(sp)
                sd   s1, 200(sp)
                sd   s2, 192(sp)
                sd   s3, 184(sp)
                sd   s4, 176(sp)
                sd   s5, 168(sp)
                sd   s6, 160(sp)
                sd   s7, 152(sp)
                sd   s8, 144(sp)
                sd   s9, 136(sp)
                sd   a0, 0(sp)
                sd   a1, 8(sp)
                sd   a2, 16(sp)
                sd   a3, 24(sp)
                call kof_db_resolve
                beqz a0, .L79_badconn
                mv   s0, a0                     # fd
                sd   a0, 32(sp)
                ld   a0, 16(sp)
                call kof_orm_parse_schema       # a0=ftab a2=nF
                mv   s1, a0                     # ftab
                mv   s2, a2                     # nFields
                sd   a0, 40(sp)
                sd   a2, 48(sp)
                # ---- resolve ctor UMA vez ---------------------------------
                ld   t0, 24(sp)
                addi a0, t0, 24
                lw   a1, 16(t0)
                call kof_orm_ctors
                beqz a0, .L79_noface
                sd   a0, 96(sp)
                sd   a1, 104(sp)
                sd   a2, 112(sp)
                call kof_list_new
                mv   s3, a0                     # lista destino
                sd   a0, 64(sp)
                # ---- SQL: SELECT * FROM `t` -------------------------------
                ld   t0, 8(sp)
                lw   t1, 16(t0)
                slli t1, t1, 2
                addi a0, t1, 64
                call kof_alloc
                sd   a0, 56(sp)
                mv   s8, a0
                li   t0, 1
                sw   t0, 0(s8)
                sw   zero, 4(s8)
                sd   zero, 8(s8)
                sw   zero, 20(s8)
                addi s8, s8, 24
                mv   a0, s8
                la   a1, .L79_s1
                li   a2, 14
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   a1, 8(sp)
                call kof_orm_mysql_bt_str
                mv   s8, a0
                sb   zero, 0(s8)
                ld   t0, 56(sp)
                addi t1, t0, 24
                sub  t1, s8, t1
                sw   t1, 16(t0)
                # ---- COM_QUERY (request no heap) --------------------------
                ld   t1, 56(sp)
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
                ld   t1, 56(sp)
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
                beqz a0, .L79_dead
                mv   s8, a0
                lbu  t0, 0(s8)
                li   t1, 0xFF
                beq  t0, t1, .L79_err
                beqz t0, .L79_empty            # OK sem resultset
                # ---- column count -----------------------------------------
                mv   a0, s8
                call kof_db_mysql_lenenc
                mv   s9, a0
                li   t0, 64
                ble  s9, t0, .L79_cc_ok
                li   s9, 64
            .L79_cc_ok:
                sd   s9, 80(sp)
                sd   zero, 88(sp)
            .L79_cols:
                ld   t0, 88(sp)
                ld   t1, 80(sp)
                bge  t0, t1, .L79_cols_done
                call kof_db_mysql_next
                beqz a0, .L79_dead
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
                la   t2, .L79_colf
                ld   t0, 88(sp)
                slli t0, t0, 2
                add  t2, t2, t0
                li   t0, -1
                sw   t0, 0(t2)
                li   s9, 0
            .L79_match:
                bge  s9, s2, .L79_nextcol
                slli t0, s9, 5
                add  t0, s1, t0
                lw   t1, 8(t0)
                bne  t1, s7, .L79_matchnext
                ld   t1, 0(t0)
                li   t2, 0
            .L79_mcmp:
                bge  t2, s7, .L79_matched
                add  t3, s6, t2
                lbu  t3, 0(t3)
                add  t4, t1, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .L79_matchnext
                addi t2, t2, 1
                j    .L79_mcmp
            .L79_matched:
                la   t2, .L79_colf
                ld   t0, 88(sp)
                slli t0, t0, 2
                add  t2, t2, t0
                sw   s9, 0(t2)
                j    .L79_nextcol
            .L79_matchnext:
                addi s9, s9, 1
                j    .L79_match
            .L79_nextcol:
                add  s8, s6, s7
                mv   a0, s8
                call kof_db_mysql_lenenc        # pula org_name
                add  s8, a1, a0
                ld   t0, 88(sp)
                addi t0, t0, 1
                sd   t0, 88(sp)
                j    .L79_cols
            .L79_cols_done:
                call kof_db_mysql_next
                beqz a0, .L79_dead
                lbu  t0, 0(a0)
                beqz t0, .L79_empty
                # ---- todo campo do schema tem coluna? ---------------------
                sd   zero, 88(sp)
            .L79_chkfld:
                ld   t0, 88(sp)
                bge  t0, s2, .L79_rows
                li   s9, 0
            .L79_chkcol:
                ld   t1, 80(sp)
                bge  s9, t1, .L79_noch
                la   t2, .L79_colf
                slli t3, s9, 2
                add  t2, t2, t3
                lw   t2, 0(t2)
                beq  t2, t0, .L79_chkok
                addi s9, s9, 1
                j    .L79_chkcol
            .L79_chkok:
                ld   t0, 88(sp)
                addi t0, t0, 1
                sd   t0, 88(sp)
                j    .L79_chkfld
            .L79_noch:
                la   a0, .L79_nc
                li   a1, 18
                call kof_string_from_literal
                mv   s9, a0
                ld   t0, 88(sp)
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
            .L79_rows:
                call kof_db_mysql_next
                beqz a0, .L79_dead
                lbu  t0, 0(a0)
                li   t1, 0xFF
                beq  t0, t1, .L79_err
                li   t1, 0xFE
                beq  t0, t1, .L79_ok
                mv   s8, a0                     # cursor da linha
                ld   a0, 112(sp)
                call kof_alloc
                mv   s4, a0                     # dst
                mv   a0, s4
                ld   a1, 104(sp)
                ld   a2, 96(sp)
                call kof_init_object
                sd   zero, 88(sp)              # colj
            .L79_rcol:
                ld   t0, 88(sp)
                ld   t1, 80(sp)
                bge  t0, t1, .L79_rowdone
                lbu  t0, 0(s8)
                li   t1, 0xFB
                beq  t0, t1, .L79_rnull
                mv   a0, s8
                call kof_db_mysql_lenenc
                sd   a0, 120(sp)
                mv   s6, a1
                add  s8, a1, a0
                j    .L79_rconv
            .L79_rnull:
                addi s8, s8, 1
                li   t0, -1
                sd   t0, 120(sp)
            .L79_rconv:
                la   t2, .L79_colf
                ld   t0, 88(sp)
                slli t0, t0, 2
                add  t2, t2, t0
                lw   s9, 0(t2)
                bltz s9, .L79_rnext
                slli t0, s9, 5
                add  t0, s1, t0
                sd   t0, 96(sp)                # entry
                slli t0, s9, 3
                addi t0, t0, 16
                add  t0, s4, t0
                mv   s7, t0                     # slot*
                ld   t0, 120(sp)
                bltz t0, .L79_vnull
                ld   t0, 96(sp)
                lw   t1, 12(t0)                 # typeCode
                li   t2, 2
                beq  t1, t2, .L79_vstr
                li   t2, 3
                beq  t1, t2, .L79_vbool
                li   t2, 4
                beq  t1, t2, .L79_vdbl
                li   t2, 5
                beq  t1, t2, .L79_vflt
                li   t2, 1
                beq  t1, t2, .L79_vlng
                mv   a0, s6
                ld   a1, 120(sp)
                call kof_orm_mysql_atoi
                sd   a0, 0(s7)
                j    .L79_rnext
            .L79_vlng:
                mv   a0, s6
                ld   a1, 120(sp)
                call kof_orm_mysql_atoi
                sd   a0, 0(s7)
                j    .L79_rnext
            .L79_vstr:
                mv   a0, s6
                ld   a1, 120(sp)
                call kof_io_make_string
                sd   a0, 0(s7)
                j    .L79_rnext
            .L79_vbool:
                mv   a0, s6
                ld   a1, 120(sp)
                call kof_orm_mysql_bool
                sd   a0, 0(s7)
                j    .L79_rnext
            .L79_vdbl:
                mv   a0, s6
                ld   a1, 120(sp)
                call kof_io_make_string
                call kof_string_to_double
                sd   a0, 0(s7)
                j    .L79_rnext
            .L79_vflt:
                mv   a0, s6
                ld   a1, 120(sp)
                call kof_io_make_string
                call kof_string_to_float
                sw   a0, 0(s7)
                j    .L79_rnext
            .L79_vnull:
                sd   zero, 0(s7)
            .L79_rnext:
                ld   t0, 88(sp)
                addi t0, t0, 1
                sd   t0, 88(sp)
                j    .L79_rcol
            .L79_rowdone:
                mv   a0, s3
                mv   a1, s4
                call kof_list_add
                j    .L79_rows
            .L79_empty:
                mv   a0, s3
                j    .L79_ret
            .L79_ok:
                mv   a0, s3
                j    .L79_ret
            # ---- ERR do servidor -> throw "mysql: <msg>" -----------------
            .L79_err:
                addi a0, a0, 9
                addi a1, a1, -9
                bgtz a1, .L79_errclip
                li   a1, 0
            .L79_errclip:
                li   t0, 400
                ble  a1, t0, .L79_errok
                li   a1, 400
            .L79_errok:
                mv   s6, a0
                mv   s7, a1
                la   a0, .L79_my
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
            .L79_dead:
                la   a0, .L79_lost
                li   a1, 23
                call kof_string_from_literal
                call kof_throw_string
            .L79_badconn:
                la   a0, .L79_bc
                li   a1, 23
                call kof_string_from_literal
                ld   a1, 0(sp)
                call kof_string_concat
                call kof_throw_string
            .L79_noface:
                la   a0, .L79_face
                li   a1, 45
                call kof_string_from_literal
                call kof_throw_string
            .L79_ret:
                ld   ra, 216(sp)
                ld   s0, 208(sp)
                ld   s1, 200(sp)
                ld   s2, 192(sp)
                ld   s3, 184(sp)
                ld   s4, 176(sp)
                ld   s5, 168(sp)
                ld   s6, 160(sp)
                ld   s7, 152(sp)
                ld   s8, 144(sp)
                ld   s9, 136(sp)
                addi sp, sp, 224
                ret

            .section .rodata
            .L79_s1:
                .ascii "SELECT * FROM "
            .L79_my:
                .ascii "mysql: "
            .L79_nc:
                .ascii "mysql: no column "
            .L79_lost:
                .ascii "mysql: connection lost"
            .L79_bc:
                .ascii "unknown db connection: "
            .L79_face:
                .ascii "orm.all: entity class not registered (ORM001)"
            .section .text
            """;
}
