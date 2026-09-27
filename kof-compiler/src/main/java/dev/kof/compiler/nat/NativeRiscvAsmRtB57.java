package dev.kof.compiler.nat;

// DB-3/DB-1 cross, slice E-parte-3 (23/09, lane gaps-db): kof_orm_find no
// riscv64 — port de RuntimeOrm5 (F2b) sobre o mesmo SQLite. SELECT * FROM "t"
// WHERE "pk" = ?; bind do key pelo classificador global kof_orm_bind_key;
// step==ROW -> resolve kof_orm_ctors(className) -> kof_alloc + kof_init_object
// e preenche os slots casando coluna por NOME (kof_orm_read_field), com throw
// "sqlite: no column <name>" (R6) e miss -> null. mysql (type 2) não é portado
// no cross: kof_orm_conn já recusa com "unknown db connection:" (R6/R7).
//
// Frame 192: slots 0 key|8 table|16 schema|24 className|32 buf|40 &stmt|
// 48 vtab|56 typeId|64 size|72 nCols|80 entry|88 conn; state callee-saved
// s0..s8. NUNCA usa s10 (x16 caller-saved no aarch64 — ver RtB55).
public final class NativeRiscvAsmRtB57 {

    private NativeRiscvAsmRtB57() {}

    static String RISCV_RUNTIME_ASM_B_57 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_find(id*, key*, table*, schema*, className*) -> record*|0
            # ---------------------------------------------------------------
            .globl kof_orm_find
            .type kof_orm_find, @function
            kof_orm_find:
                addi sp, sp, -192
                sd   ra, 184(sp)
                sd   s0, 176(sp)
                sd   s1, 168(sp)
                sd   s2, 160(sp)
                sd   s3, 152(sp)
                sd   s4, 144(sp)
                sd   s5, 136(sp)
                sd   s6, 128(sp)
                sd   s7, 120(sp)
                sd   s8, 112(sp)
                sd   a1, 0(sp)                     # key
                sd   a2, 8(sp)                     # table
                sd   a3, 16(sp)                    # schema
                sd   a4, 24(sp)                    # className
                sd   a0, 88(sp)                    # id (temporario)
                call kof_db_type
                li   t0, 2
                beq  a0, t0, .L57_find_mysql
                ld   a0, 88(sp)
                call kof_orm_conn                  # a0 = id
                sd   a0, 88(sp)                    # conn
                ld   a0, 16(sp)
                call kof_orm_parse_schema          # a0=ftab a1=nbuf a2=nF a3=pkIdx
                mv   s2, a0                        # ftab
                mv   s3, a2                        # nFields
                slli t0, a3, 5
                add  s4, s2, t0                    # entry da PK
                # ---- SQL: SELECT * FROM "t" WHERE "pk" = ? ------------------
                ld   t1, 8(sp)                     # table
                lw   t2, 16(t1)                    # tblLen
                lw   t3, 8(s4)                     # pkLen
                add  t2, t2, t3
                addi a0, t2, 41                    # 14+7+4 + folga
                call kof_alloc
                sd   a0, 32(sp)                    # base
                mv   s8, a0                        # cursor
                mv   a0, s8
                la   a1, .L57_s1
                li   a2, 14
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   a1, 8(sp)
                call kof_orm_sb_qstr               # "t"
                mv   s8, a0
                mv   a0, s8
                la   a1, .L57_s2
                li   a2, 7
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   a1, 0(s4)                     # pk name ptr
                lw   a2, 8(s4)                     # pk name len
                call kof_orm_sb_qraw               # "pk"
                mv   s8, a0
                mv   a0, s8
                la   a1, .L57_s3
                li   a2, 4
                call kof_orm_sb_append
                mv   s8, a0
                sb   zero, 0(s8)                   # NUL p/ o prepare
                # ---- prepare --------------------------------------------------
                sd   zero, 40(sp)
                ld   a0, 88(sp)
                ld   a1, 32(sp)
                li   a2, -1
                addi a3, sp, 40
                li   a4, 0
                call sqlite3_prepare_v2
                bnez a0, .L57_prep_fail
                ld   s0, 40(sp)                    # stmt (0 = falhou)
                beqz s0, .L57_prep_fail
                # ---- bind pk do key (kof_orm_bind_key) ------------------------
                mv   a0, s0
                ld   a1, 0(sp)
                call kof_orm_bind_key
                # ---- step: 100=ROW; senao miss -> null (host) -----------------
                mv   a0, s0
                call sqlite3_step
                li   t0, 100
                bne  a0, t0, .L57_miss
                # ---- resolve ctor (className -> vtab/typeId/totalSize) --------
                ld   t0, 24(sp)
                addi a0, t0, 24                    # body do className (INLINE +24)
                lw   a1, 16(t0)                    # len
                call kof_orm_ctors
                beqz a0, .L57_noface
                sd   a0, 48(sp)                    # vtab
                sd   a1, 56(sp)                    # typeId
                sd   a2, 64(sp)                    # totalSize
                ld   a0, 64(sp)
                call kof_alloc
                mv   s1, a0                        # dst
                mv   a0, s1
                ld   a1, 56(sp)
                ld   a2, 48(sp)
                call kof_init_object
                # ---- loop de campos: casar coluna por NOME, ler por typeCode -
                mv   a0, s0
                call sqlite3_column_count
                sw   a0, 72(sp)
                li   s5, 0                         # i
            .L57_fld:
                bge  s5, s3, .L57_done
                slli t0, s5, 5
                add  t0, s2, t0
                sd   t0, 80(sp)                    # entry
                lw   s7, 8(t0)                     # nameLen
                li   s6, 0                         # colj
            .L57_col:
                lw   t0, 72(sp)
                bge  s6, t0, .L57_nomatch
                mv   a0, s0
                mv   a1, s6
                call sqlite3_column_name
                mv   t2, a0                        # colname (C string)
                ld   t1, 80(sp)
                ld   t1, 0(t1)                     # field name ptr
                li   t3, 0
            .L57_cmp:
                bge  t3, s7, .L57_cmpend
                add  t4, t2, t3
                lbu  t4, 0(t4)
                add  t5, t1, t3
                lbu  t5, 0(t5)
                bne  t4, t5, .L57_nextcol
                addi t3, t3, 1
                j    .L57_cmp
            .L57_cmpend:
                add  t4, t2, t3
                lbu  t4, 0(t4)
                bnez t4, .L57_nextcol
                j    .L57_read
            .L57_nextcol:
                addi s6, s6, 1
                j    .L57_col
            .L57_read:
                slli t0, s5, 3
                addi t0, t0, 16
                add  t0, s1, t0                    # slot do campo i
                mv   a0, s0
                mv   a1, s6
                mv   a2, t0
                ld   t1, 80(sp)
                lw   a3, 12(t1)                    # typeCode
                call kof_orm_read_field
                addi s5, s5, 1
                j    .L57_fld
            .L57_done:
                mv   a0, s0
                call sqlite3_finalize
                mv   a0, s1
                j    .L57_ret
            .L57_miss:
                mv   a0, s0
                call sqlite3_finalize
                li   a0, 0
                j    .L57_ret
            # ---- fail: throw "sqlite: " + errmsg --------------------------
            .L57_prep_fail:
                ld   a0, 88(sp)
                call sqlite3_errmsg
                mv   s7, a0
                beqz s7, .L57_pre_only
                mv   a0, s7
                call kof_io_strlen
                mv   a1, a0
                mv   a0, s7
                call kof_string_from_literal
                mv   s7, a0
                la   a0, .L57_pre
                li   a1, 8
                call kof_string_from_literal
                mv   a1, s7
                call kof_string_concat             # "sqlite: " + errmsg
                call kof_throw_string
            .L57_pre_only:
                la   a0, .L57_pre
                li   a1, 8
                call kof_string_from_literal
                call kof_throw_string
            .L57_nomatch:
                # mensagem do x86: "sqlite: no column \"" + name + "\""
                # (o oráculo fecha o nome em aspas via .Lorm_qraw)
                ld   t0, 80(sp)
                lw   t1, 8(t0)                     # nameLen
                addi a0, t1, 2
                call kof_alloc                     # buf p/ "name"
                mv   s1, a0
                li   t0, 34
                sb   t0, 0(s1)
                ld   t0, 80(sp)
                ld   a1, 0(t0)                     # name ptr
                lw   a2, 8(t0)                     # nameLen
                addi a0, s1, 1
                call kof_memcpy
                ld   t0, 80(sp)
                lw   t1, 8(t0)
                addi t1, t1, 1
                add  t1, s1, t1
                li   t0, 34
                sb   t0, 0(t1)
                ld   t0, 80(sp)
                lw   a1, 8(t0)
                addi a1, a1, 2
                mv   a0, s1
                call kof_io_make_string
                mv   s1, a0
                la   a0, .L57_nc
                li   a1, 18
                call kof_string_from_literal
                mv   a1, s1
                call kof_string_concat             # "sqlite: no column " + "name"
                call kof_throw_string
            .L57_noface:
                la   a0, .L57_face
                li   a1, 46
                call kof_string_from_literal
                call kof_throw_string
            # ---- dispatch mysql (type 2): peca B78 ------------------------
            .L57_find_mysql:
                ld   a0, 88(sp)
                ld   a1, 0(sp)
                ld   a2, 8(sp)
                ld   a3, 16(sp)
                ld   a4, 24(sp)
                ld   ra, 184(sp)
                ld   s0, 176(sp)
                ld   s1, 168(sp)
                ld   s2, 160(sp)
                ld   s3, 152(sp)
                ld   s4, 144(sp)
                ld   s5, 136(sp)
                ld   s6, 128(sp)
                ld   s7, 120(sp)
                ld   s8, 112(sp)
                addi sp, sp, 192
                j    kof_orm_find_mysql
            .L57_ret:
                ld   ra, 184(sp)
                ld   s0, 176(sp)
                ld   s1, 168(sp)
                ld   s2, 160(sp)
                ld   s3, 152(sp)
                ld   s4, 144(sp)
                ld   s5, 136(sp)
                ld   s6, 128(sp)
                ld   s7, 120(sp)
                ld   s8, 112(sp)
                addi sp, sp, 192
                ret

            .section .rodata
            .L57_s1:
                .ascii "SELECT * FROM "
            .L57_s2:
                .ascii " WHERE "
            .L57_s3:
                .ascii " = ?"
            .L57_pre:
                .ascii "sqlite: "
            .L57_nc:
                .ascii "sqlite: no column "
            .L57_face:
                .ascii "orm.find: entity class not registered (ORM001)"
            .section .text
            """;
}
