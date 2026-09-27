package dev.kof.compiler.nat;

// DB-3/DB-1 cross, slice E-parte-4 (23/09, lane gaps-db): kof_orm_all no
// riscv64 — port de RuntimeOrm6 (F2c1) sobre o mesmo SQLite. SELECT * FROM "t"
// (sem bind); resolve kof_orm_ctors UMA vez antes do loop; por linha
// kof_alloc + kof_init_object + kof_orm_read_field (casando por NOME, §397
// incluído) e acumula em kof_list_new/kof_list_add — vazio devolve a LISTA
// VAZIA (nunca null), como o host. mysql (type 2) não é portado no cross:
// kof_orm_conn já recusa com "unknown db connection:" (R6/R7).
//
// Frame 192: slots 0 table|8 schema|16 className|24 buf|32 &stmt|40 vtab|
// 48 typeId|56 size|64 nCols|72 entry|80 conn; state callee-saved s0..s8
// (s0 stmt, s1 ftab, s2 nFields, s3 dst, s4 list, s5 i, s6 colj, s7 nameLen,
// s8 cursor). NUNCA usa s10 (x16 caller-saved no aarch64 — ver RtB55).
public final class NativeRiscvAsmRtB58 {

    private NativeRiscvAsmRtB58() {}

    static String RISCV_RUNTIME_ASM_B_58 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_all(id*, table*, schema*, className*) -> list*
            # ---------------------------------------------------------------
            .globl kof_orm_all
            .type kof_orm_all, @function
            kof_orm_all:
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
                sd   a1, 0(sp)                     # table
                sd   a2, 8(sp)                     # schema
                sd   a3, 16(sp)                    # className
                sd   a0, 88(sp)                    # id (temp p/ dispatch)
                call kof_db_type
                li   t0, 2
                beq  a0, t0, .L58_all_mysql
                ld   a0, 88(sp)
                call kof_orm_conn                  # a0 = id
                sd   a0, 80(sp)                    # conn
                ld   a0, 8(sp)
                call kof_orm_parse_schema          # a0=ftab a1=nbuf a2=nF a3=pkIdx
                mv   s1, a0                        # ftab
                mv   s2, a2                        # nFields
                # ---- resolve ctor UMA vez -------------------------------------
                ld   t0, 16(sp)
                addi a0, t0, 24                    # body do className (INLINE +24)
                lw   a1, 16(t0)                    # len
                call kof_orm_ctors
                beqz a0, .L58_noface
                sd   a0, 40(sp)                    # vtab
                sd   a1, 48(sp)                    # typeId
                sd   a2, 56(sp)                    # totalSize
                call kof_list_new
                mv   s4, a0                        # lista destino
                # ---- SQL: SELECT * FROM "t" -----------------------------------
                ld   t1, 0(sp)
                lw   t2, 16(t1)                    # tblLen
                addi a0, t2, 16
                call kof_alloc
                sd   a0, 24(sp)                    # base
                mv   s8, a0
                mv   a0, s8
                la   a1, .L58_s1
                li   a2, 14
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   a1, 0(sp)
                call kof_orm_sb_qstr               # "t"
                mv   s8, a0
                sb   zero, 0(s8)
                # ---- prepare (sem bind) ---------------------------------------
                sd   zero, 32(sp)
                ld   a0, 80(sp)
                ld   a1, 24(sp)
                li   a2, -1
                addi a3, sp, 32
                li   a4, 0
                call sqlite3_prepare_v2
                bnez a0, .L58_prep_fail
                ld   s0, 32(sp)                    # stmt
                beqz s0, .L58_prep_fail
                # ---- loop de linhas -------------------------------------------
            .L58_row:
                mv   a0, s0
                call sqlite3_step
                li   t0, 100
                bne  a0, t0, .L58_end
                ld   a0, 56(sp)
                call kof_alloc
                mv   s3, a0                        # dst (record novo por linha)
                mv   a0, s3
                ld   a1, 48(sp)
                ld   a2, 40(sp)
                call kof_init_object
                mv   a0, s0
                call sqlite3_column_count
                sw   a0, 64(sp)
                li   s5, 0                         # i
            .L58_fld:
                bge  s5, s2, .L58_rowdone
                slli t0, s5, 5
                add  t0, s1, t0
                sd   t0, 72(sp)                    # entry
                lw   s7, 8(t0)                     # nameLen
                li   s6, 0                         # colj
            .L58_col:
                lw   t0, 64(sp)
                bge  s6, t0, .L58_nomatch
                mv   a0, s0
                mv   a1, s6
                call sqlite3_column_name
                mv   t2, a0                        # colname (C string)
                ld   t1, 72(sp)
                ld   t1, 0(t1)                     # field name ptr
                li   t3, 0
            .L58_cmp:
                bge  t3, s7, .L58_cmpend
                add  t4, t2, t3
                lbu  t4, 0(t4)
                add  t5, t1, t3
                lbu  t5, 0(t5)
                bne  t4, t5, .L58_nextcol
                addi t3, t3, 1
                j    .L58_cmp
            .L58_cmpend:
                add  t4, t2, t3
                lbu  t4, 0(t4)
                bnez t4, .L58_nextcol
                j    .L58_read
            .L58_nextcol:
                addi s6, s6, 1
                j    .L58_col
            .L58_read:
                slli t0, s5, 3
                addi t0, t0, 16
                add  t0, s3, t0                    # slot do campo i
                mv   a0, s0
                mv   a1, s6
                mv   a2, t0
                ld   t1, 72(sp)
                lw   a3, 12(t1)                    # typeCode
                call kof_orm_read_field
                addi s5, s5, 1
                j    .L58_fld
            .L58_rowdone:
                mv   a0, s4
                mv   a1, s3
                call kof_list_add
                j    .L58_row
            .L58_end:
                mv   a0, s0
                call sqlite3_finalize
                mv   a0, s4
                j    .L58_ret
            # ---- fail: throw "sqlite: " + errmsg --------------------------
            .L58_prep_fail:
                ld   a0, 80(sp)
                call sqlite3_errmsg
                mv   s7, a0
                beqz s7, .L58_pre_only
                mv   a0, s7
                call kof_io_strlen
                mv   a1, a0
                mv   a0, s7
                call kof_string_from_literal
                mv   s7, a0
                la   a0, .L58_pre
                li   a1, 8
                call kof_string_from_literal
                mv   a1, s7
                call kof_string_concat
                call kof_throw_string
            .L58_pre_only:
                la   a0, .L58_pre
                li   a1, 8
                call kof_string_from_literal
                call kof_throw_string
            .L58_nomatch:
                # mensagem do x86: "sqlite: no column \"" + name + "\""
                ld   t0, 72(sp)
                lw   t1, 8(t0)                     # nameLen
                addi a0, t1, 2
                call kof_alloc
                mv   s7, a0
                li   t0, 34
                sb   t0, 0(s7)
                ld   t0, 72(sp)
                ld   a1, 0(t0)
                lw   a2, 8(t0)
                addi a0, s7, 1
                call kof_memcpy
                ld   t0, 72(sp)
                lw   t1, 8(t0)
                addi t1, t1, 1
                add  t1, s7, t1
                li   t0, 34
                sb   t0, 0(t1)
                ld   t0, 72(sp)
                lw   a1, 8(t0)
                addi a1, a1, 2
                mv   a0, s7
                call kof_io_make_string
                mv   s7, a0
                la   a0, .L58_nc
                li   a1, 18
                call kof_string_from_literal
                mv   a1, s7
                call kof_string_concat
                call kof_throw_string
            .L58_noface:
                la   a0, .L58_face
                li   a1, 45
                call kof_string_from_literal
                call kof_throw_string
            .L58_ret:
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
            # ---- dispatch mysql (type 2): tail-call kof_orm_all_mysql ----
            .L58_all_mysql:
                ld   a0, 88(sp)                    # id
                ld   a1, 0(sp)                     # table
                ld   a2, 8(sp)                     # schema
                ld   a3, 16(sp)                    # className
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
                j    kof_orm_all_mysql

            .section .rodata
            .L58_s1:
                .ascii "SELECT * FROM "
            .L58_pre:
                .ascii "sqlite: "
            .L58_nc:
                .ascii "sqlite: no column "
            .L58_face:
                .ascii "orm.all: entity class not registered (ORM001)"
            .section .text
            """;
}
