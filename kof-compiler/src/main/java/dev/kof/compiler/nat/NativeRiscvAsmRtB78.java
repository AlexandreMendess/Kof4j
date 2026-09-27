package dev.kof.compiler.nat;

// DB-3/DB-1 cross, S5.5 fatia 5 (24/09, lane gaps-db): kof_orm_find sobre o
// wire MySQL no riscv64/aarch64 — F2d3b de RuntimeOrmMysqlFind x86-64.
//
// Le o resultset do COM_QUERY (mesmo reader de pacotes B68 que a B70) e
// constroi o record pelo resolver kof_orm_ctors, com as colunas casadas por
// NOME e os valores convertidos pelo typeCode do schema (int/long/string/
// bool/double/float; NULL -> 0/null/false). Miss -> null (a0=0). ERR do
// servidor -> throw "mysql: <msg>"; campo do schema sem coluna ->
// throw "mysql: no column <nome>" (R6 — nunca silencio).
//
// O key vira literal SQL por kof_orm_mysql_lit (B75: box §284/KofString/null,
// port do .Lorm_key_lit) e o `?` do "SELECT * FROM `t` WHERE `pk` = ?" e
// trocado por kof_db_mysql_replace_q (B71). O dispatch de tipo mora no
// kof_orm_find (B57), que faz tail-call para ca quando kof_db_type(id)==2.
//
// ABIs (riscv):
//   kof_orm_find_mysql(a0=id,a1=key,a2=table,a3=schema,a4=className)
//       -> a0 record* | 0
//   kof_orm_mysql_atoi(a0=ptr,a1=len) -> a0 (sinal opcional)
//   kof_orm_mysql_bool(a0=ptr,a1=len) -> a0 0/1 (paridade §397)
// Rótulos .L78_* locais. NUNCA usa s10 (x16 caller-saved no aarch64).
public final class NativeRiscvAsmRtB78 {

    private NativeRiscvAsmRtB78() {}

    static String RISCV_RUNTIME_ASM_B_78 = """
            .section .data
            .align 3
            .L78_colf:
                .zero 256
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_find_mysql(id*, key*, table*, schema*, className*)
            #   -> record* | 0
            # slots: 0 id|8 key|16 table|24 schema|32 className|40 fd|48 ftab|
            # 56 nFields|64 pkIndex|72 pkEntry|80 sql|88 keyLit|96 dst|
            # 104 nCols|112 i|120 entry|128 tmp|136 vtab|144 typeId|
            # 152 totalSize|160 vallen(-1=NULL)|168 errptr|176 errlen
            # ---------------------------------------------------------------
            .globl kof_orm_find_mysql
            .type kof_orm_find_mysql, @function
            kof_orm_find_mysql:
                addi sp, sp, -288
                sd   ra, 280(sp)
                sd   s0, 272(sp)
                sd   s1, 264(sp)
                sd   s2, 256(sp)
                sd   s3, 248(sp)
                sd   s4, 240(sp)
                sd   s5, 232(sp)
                sd   s6, 224(sp)
                sd   s7, 216(sp)
                sd   s8, 208(sp)
                sd   s9, 200(sp)
                sd   a0, 0(sp)
                sd   a1, 8(sp)
                sd   a2, 16(sp)
                sd   a3, 24(sp)
                sd   a4, 32(sp)
                call kof_db_resolve
                beqz a0, .L78_badconn
                sd   a0, 40(sp)                # fd
                ld   a0, 24(sp)
                call kof_orm_parse_schema      # a0=ftab a2=nF a3=pkIdx
                sd   a0, 48(sp)
                sd   a2, 56(sp)
                sd   a3, 64(sp)
                slli t0, a3, 5
                add  t0, a0, t0
                sd   t0, 72(sp)                # pkEntry
                # ---- key -> literal (B75) ----------------------------------
                ld   a0, 8(sp)
                call kof_orm_mysql_lit
                sd   a0, 88(sp)                # keyLit
                # ---- SQL "SELECT * FROM `t` WHERE `pk` = ?" ---------------
                ld   t0, 16(sp)
                lw   t1, 16(t0)
                slli t1, t1, 1
                ld   t0, 72(sp)
                lw   t2, 8(t0)
                slli t2, t2, 1
                add  t1, t1, t2
                ld   t0, 88(sp)
                lw   t2, 16(t0)
                add  t1, t1, t2
                addi a0, t1, 80
                call kof_alloc
                sd   a0, 80(sp)
                mv   s8, a0
                li   t0, 1
                sw   t0, 0(s8)
                sw   zero, 4(s8)
                sd   zero, 8(s8)
                sw   zero, 20(s8)
                addi s8, s8, 24
                mv   a0, s8
                la   a1, .L78_s1
                li   a2, 14
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   a1, 16(sp)
                call kof_orm_mysql_bt_str
                mv   s8, a0
                mv   a0, s8
                la   a1, .L78_s2
                li   a2, 7
                call kof_orm_sb_append
                mv   s8, a0
                ld   t0, 72(sp)
                mv   a0, s8
                ld   a1, 0(t0)
                lw   a2, 8(t0)
                call kof_orm_mysql_bt_raw
                mv   s8, a0
                mv   a0, s8
                la   a1, .L78_s3
                li   a2, 4
                call kof_orm_sb_append
                mv   s8, a0
                sb   zero, 0(s8)
                ld   t0, 80(sp)
                addi t0, t0, 24
                sub  t0, s8, t0
                ld   t1, 80(sp)
                sw   t0, 16(t1)
                # ---- troca o `?` pelo literal ------------------------------
                ld   a0, 80(sp)
                ld   a1, 88(sp)
                call kof_db_mysql_replace_q
                sd   a0, 80(sp)
                # ---- COM_QUERY (request no heap) --------------------------
                ld   t1, 80(sp)
                lw   s8, 16(t1)                # sqllen
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
                ld   t1, 80(sp)
                addi a1, t1, 24
                mv   a2, s8
                call kof_memcpy
                ld   a0, 40(sp)
                mv   a1, s9
                addi a2, s8, 5
                call kof_plat_write
                ld   a0, 40(sp)
                call kof_db_mysql_reset
                call kof_db_mysql_next
                beqz a0, .L78_miss
                mv   s8, a0
                lbu  t0, 0(s8)
                li   t1, 0xFF
                beq  t0, t1, .L78_err
                beqz t0, .L78_miss
                # ---- column count -----------------------------------------
                mv   a0, s8
                call kof_db_mysql_lenenc
                mv   s9, a0
                li   t0, 64
                ble  s9, t0, .L78_cc_ok
                li   s9, 64
            .L78_cc_ok:
                sd   s9, 104(sp)
                # ---- column definitions: casa nome -> campo ----------------
                sd   zero, 112(sp)
            .L78_cols:
                ld   t0, 112(sp)
                ld   t1, 104(sp)
                bge  t0, t1, .L78_cols_done
                call kof_db_mysql_next
                beqz a0, .L78_miss
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
                la   t2, .L78_colf
                ld   t0, 112(sp)
                slli t0, t0, 2
                add  t2, t2, t0
                li   t0, -1
                sw   t0, 0(t2)                  # default: sem match
                li   s9, 0
            .L78_match:
                ld   t0, 56(sp)
                bge  s9, t0, .L78_nextcol
                slli t0, s9, 5
                ld   t1, 48(sp)
                add  t0, t1, t0                 # entry
                lw   t1, 8(t0)
                bne  t1, s7, .L78_matchnext
                ld   t1, 0(t0)                  # field name ptr
                li   t2, 0
            .L78_mcmp:
                bge  t2, s7, .L78_matched
                add  t3, s6, t2
                lbu  t3, 0(t3)
                add  t4, t1, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .L78_matchnext
                addi t2, t2, 1
                j    .L78_mcmp
            .L78_matched:
                la   t2, .L78_colf
                ld   t0, 112(sp)
                slli t0, t0, 2
                add  t2, t2, t0
                sw   s9, 0(t2)
                j    .L78_nextcol
            .L78_matchnext:
                addi s9, s9, 1
                j    .L78_match
            .L78_nextcol:
                add  s8, s6, s7                 # apos o nome
                mv   a0, s8
                call kof_db_mysql_lenenc        # pula org_name
                add  s8, a1, a0
                ld   t0, 112(sp)
                addi t0, t0, 1
                sd   t0, 112(sp)
                j    .L78_cols
            .L78_cols_done:
                # pacote apos as colunas: 0x00 (sem resultset) ou 0xFE (EOF)
                call kof_db_mysql_next
                beqz a0, .L78_miss
                lbu  t0, 0(a0)
                beqz t0, .L78_miss
                # ---- todo campo do schema tem coluna? ---------------------
                sd   zero, 112(sp)
            .L78_chkfld:
                ld   t0, 112(sp)
                ld   t1, 56(sp)
                bge  t0, t1, .L78_rows
                li   s9, 0
            .L78_chkcol:
                ld   t1, 104(sp)
                bge  s9, t1, .L78_noch
                la   t2, .L78_colf
                slli t3, s9, 2
                add  t2, t2, t3
                lw   t2, 0(t2)
                beq  t2, t0, .L78_chkok
                addi s9, s9, 1
                j    .L78_chkcol
            .L78_chkok:
                ld   t0, 112(sp)
                addi t0, t0, 1
                sd   t0, 112(sp)
                j    .L78_chkfld
            .L78_noch:
                la   a0, .L78_nc
                li   a1, 18
                call kof_string_from_literal
                mv   s9, a0
                ld   t0, 112(sp)
                slli t0, t0, 5
                ld   t1, 48(sp)
                add  t0, t1, t0
                ld   a0, 0(t0)
                lw   a1, 8(t0)
                call kof_io_make_string
                mv   a1, a0
                mv   a0, s9
                call kof_string_concat
                call kof_throw_string
            # ---- primeira linha ------------------------------------------
            .L78_rows:
                call kof_db_mysql_next
                beqz a0, .L78_miss
                lbu  t0, 0(a0)
                li   t1, 0xFF
                beq  t0, t1, .L78_err
                li   t1, 0xFE
                beq  t0, t1, .L78_miss
                sd   a0, 96(sp)                 # rowcur
                ld   t0, 32(sp)
                addi a0, t0, 24
                lw   a1, 16(t0)
                call kof_orm_ctors
                beqz a0, .L78_noface
                sd   a0, 136(sp)                # vtab
                sd   a1, 144(sp)                # typeId
                sd   a2, 152(sp)                # totalSize
                ld   a0, 152(sp)
                call kof_alloc
                mv   s4, a0                     # dst
                mv   a0, s4
                ld   a1, 144(sp)
                ld   a2, 136(sp)
                call kof_init_object
                sd   zero, 112(sp)              # colj
            .L78_rcol:
                ld   t0, 112(sp)
                ld   t1, 104(sp)
                bge  t0, t1, .L78_drain
                ld   s8, 96(sp)
                lbu  t0, 0(s8)
                li   t1, 0xFB
                beq  t0, t1, .L78_rnull
                mv   a0, s8
                call kof_db_mysql_lenenc
                sd   a0, 160(sp)
                mv   s6, a1
                add  s8, a1, a0
                sd   s8, 96(sp)
                j    .L78_rconv
            .L78_rnull:
                addi s8, s8, 1
                sd   s8, 96(sp)
                li   t0, -1
                sd   t0, 160(sp)
            .L78_rconv:
                la   t2, .L78_colf
                ld   t0, 112(sp)
                slli t0, t0, 2
                add  t2, t2, t0
                lw   s9, 0(t2)
                bltz s9, .L78_rnext
                slli t0, s9, 5
                ld   t1, 48(sp)
                add  t0, t1, t0
                sd   t0, 120(sp)                # entry
                slli t0, s9, 3
                addi t0, t0, 16
                add  t0, s4, t0
                mv   s7, t0                     # slot*
                ld   t0, 160(sp)
                bltz t0, .L78_vnull
                ld   t0, 120(sp)
                lw   t1, 12(t0)                 # typeCode
                li   t2, 2
                beq  t1, t2, .L78_vstr
                li   t2, 3
                beq  t1, t2, .L78_vbool
                li   t2, 4
                beq  t1, t2, .L78_vdbl
                li   t2, 5
                beq  t1, t2, .L78_vflt
                li   t2, 1
                beq  t1, t2, .L78_vlng
                mv   a0, s6
                ld   a1, 160(sp)
                call kof_orm_mysql_atoi
                sd   a0, 0(s7)
                j    .L78_rnext
            .L78_vlng:
                mv   a0, s6
                ld   a1, 160(sp)
                call kof_orm_mysql_atoi
                sd   a0, 0(s7)
                j    .L78_rnext
            .L78_vstr:
                mv   a0, s6
                ld   a1, 160(sp)
                call kof_io_make_string
                sd   a0, 0(s7)
                j    .L78_rnext
            .L78_vbool:
                mv   a0, s6
                ld   a1, 160(sp)
                call kof_orm_mysql_bool
                sd   a0, 0(s7)
                j    .L78_rnext
            .L78_vdbl:
                mv   a0, s6
                ld   a1, 160(sp)
                call kof_io_make_string
                call kof_string_to_double
                sd   a0, 0(s7)
                j    .L78_rnext
            .L78_vflt:
                mv   a0, s6
                ld   a1, 160(sp)
                call kof_io_make_string
                call kof_string_to_float
                sw   a0, 0(s7)
                j    .L78_rnext
            .L78_vnull:
                sd   zero, 0(s7)
            .L78_rnext:
                ld   t0, 112(sp)
                addi t0, t0, 1
                sd   t0, 112(sp)
                j    .L78_rcol
            .L78_drain:
                call kof_db_mysql_next
                beqz a0, .L78_ok
                lbu  t0, 0(a0)
                li   t1, 0xFE
                beq  t0, t1, .L78_ok
                beqz t0, .L78_ok
                j    .L78_drain
            .L78_ok:
                mv   a0, s4
                j    .L78_ret
            .L78_miss:
                li   a0, 0
                j    .L78_ret
            # ---- ERR do servidor -> throw "mysql: <msg>" -----------------
            .L78_err:
                sd   a0, 168(sp)
                sd   a1, 176(sp)
                ld   s8, 168(sp)
                ld   s9, 176(sp)
                addi s8, s8, 9
                addi s9, s9, -9
                bgtz s9, .L78_errclip
                li   s9, 0
            .L78_errclip:
                li   t0, 400
                ble  s9, t0, .L78_errok
                li   s9, 400
            .L78_errok:
                la   a0, .L78_my
                li   a1, 7
                call kof_string_from_literal
                mv   s7, a0
                mv   a0, s8
                mv   a1, s9
                call kof_io_make_string
                mv   a1, a0
                mv   a0, s7
                call kof_string_concat
                call kof_throw_string
            .L78_badconn:
                la   a0, .L78_bc
                li   a1, 23
                call kof_string_from_literal
                ld   a1, 0(sp)
                call kof_string_concat
                call kof_throw_string
            .L78_noface:
                la   a0, .L78_face
                li   a1, 46
                call kof_string_from_literal
                call kof_throw_string
            .L78_ret:
                ld   ra, 280(sp)
                ld   s0, 272(sp)
                ld   s1, 264(sp)
                ld   s2, 256(sp)
                ld   s3, 248(sp)
                ld   s4, 240(sp)
                ld   s5, 232(sp)
                ld   s6, 224(sp)
                ld   s7, 216(sp)
                ld   s8, 208(sp)
                ld   s9, 200(sp)
                addi sp, sp, 288
                ret

            .section .rodata
            .L78_s1:
                .ascii "SELECT * FROM "
            .L78_s2:
                .ascii " WHERE "
            .L78_s3:
                .ascii " = ?"
            .L78_my:
                .ascii "mysql: "
            .L78_nc:
                .ascii "mysql: no column "
            .L78_bc:
                .ascii "unknown db connection: "
            .L78_face:
                .ascii "orm.find: entity class not registered (ORM001)"
            .section .text
            """;
}
