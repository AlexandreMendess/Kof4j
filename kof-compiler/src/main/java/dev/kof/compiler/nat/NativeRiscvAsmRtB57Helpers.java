package dev.kof.compiler.nat;

// DB-3/DB-1 cross, slice E-parte-3 (23/09, lane gaps-db): helpers GLOBAIS das
// faces de LEITURA row-object do ORM no riscv64 (find/all) — bind do key
// (box §284 / KofString / null, o MESMO classificador do RuntimeOrm5) e leitura
// de UMA coluna p/ o slot do record por typeCode (paridade §397 com o host).
//
// ABI: kof_orm_bind_key(stmt@a0, key*@a1);
//      kof_orm_read_field(stmt@a0, colj@a1, slot*@a2, typeCode@a3).
// Classificador do bind (EXATO RuntimeOrm5): tag 0=int (sign-ext), 1/2=long,
// 4=double, 5=float; KofString->text; null->bind_null; qualquer outra coisa
// (tag 3 bool incluído, shape inválida) -> finalize + throw ORM001, como o x86.
// Nada de estado vivo em s10 (x16 caller-saved no aarch64 — ver RtB55).
public final class NativeRiscvAsmRtB57Helpers {

    private NativeRiscvAsmRtB57Helpers() {}

    static String RISCV_RUNTIME_ASM_B_57H = """
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_bind_key(a0=stmt, a1=key*) — bind no param 1
            #   box §284 (int/long/bool/double/float), KofString transient,
            #   null/tag-fora -> bind_null (como o x86).
            # ---------------------------------------------------------------
            .globl kof_orm_bind_key
            .type kof_orm_bind_key, @function
            kof_orm_bind_key:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0                        # stmt
                mv   s1, a1                        # key
                beqz s1, .L57h_bk_null
                la   t0, .L57h_magic
                ld   t0, 0(t0)
                ld   t1, 0(s1)
                bne  t0, t1, .L57h_bk_str
                lw   t1, 8(s1)                     # tag
                beqz t1, .L57h_bk_int
                li   t2, 1
                beq  t1, t2, .L57h_bk_quad
                li   t2, 2
                beq  t1, t2, .L57h_bk_quad
                li   t2, 4
                beq  t1, t2, .L57h_bk_dbl
                li   t2, 5
                beq  t1, t2, .L57h_bk_flt
                j    .L57h_bk_bad                  # tag fora -> throw ORM001 (Orm5)
            .L57h_bk_int:
                lw   a2, 16(s1)
                mv   a0, s0
                li   a1, 1
                call sqlite3_bind_int64
                j    .L57h_bk_ret
            .L57h_bk_quad:
                ld   a2, 16(s1)
                mv   a0, s0
                li   a1, 1
                call sqlite3_bind_int64
                j    .L57h_bk_ret
            .L57h_bk_dbl:
                ld   t0, 16(s1)
                fmv.d.x fa0, t0
                mv   a0, s0
                li   a1, 1
                call sqlite3_bind_double
                j    .L57h_bk_ret
            .L57h_bk_flt:
                lw   t0, 16(s1)
                fmv.w.x fa0, t0
                fcvt.d.s fa0, fa0
                mv   a0, s0
                li   a1, 1
                call sqlite3_bind_double
                j    .L57h_bk_ret
            .L57h_bk_str:
                lw   t0, 0(s1)                     # KofString (1,0,0)?
                li   t1, 1
                bne  t0, t1, .L57h_bk_bad
                lw   t0, 4(s1)
                bnez t0, .L57h_bk_bad
                ld   t0, 8(s1)
                bnez t0, .L57h_bk_bad
                addi a2, s1, 24
                lw   a3, 16(s1)
                li   a4, -1                        # SQLITE_TRANSIENT
                mv   a0, s0
                li   a1, 1
                call sqlite3_bind_text
                j    .L57h_bk_ret
            .L57h_bk_null:
                mv   a0, s0
                li   a1, 1
                call sqlite3_bind_null
                j    .L57h_bk_ret
            .L57h_bk_bad:
                mv   a0, s0
                call sqlite3_finalize
                la   a0, .L57h_badv
                li   a1, 56
                call kof_string_from_literal
                call kof_throw_string
            .L57h_bk_ret:
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                addi sp, sp, 32
                ret

            # ---------------------------------------------------------------
            # kof_orm_read_field(a0=stmt, a1=colj, a2=slot*, a3=typeCode)
            #   2 string (NULL->0, senão make_string) | 3 bool (§397) |
            #   4 double | 5 float (widen p/ REAL e estreita de volta) |
            #   1 long | 0/default int (sign-extended).
            # ---------------------------------------------------------------
            .globl kof_orm_read_field
            .type kof_orm_read_field, @function
            kof_orm_read_field:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0                        # stmt
                mv   s1, a1                        # colj
                mv   s2, a2                        # slot
                mv   s3, a3                        # typeCode
                li   t0, 2
                beq  s3, t0, .L57h_rf_str
                li   t0, 3
                beq  s3, t0, .L57h_rf_bool
                li   t0, 4
                beq  s3, t0, .L57h_rf_dbl
                li   t0, 5
                beq  s3, t0, .L57h_rf_flt
                li   t0, 1
                beq  s3, t0, .L57h_rf_lng
                # int (0/default): column_int + sign-extend
                mv   a0, s0
                mv   a1, s1
                call sqlite3_column_int
                sd   a0, 0(s2)
                j    .L57h_rf_ret
            .L57h_rf_lng:
                mv   a0, s0
                mv   a1, s1
                call sqlite3_column_int64
                sd   a0, 0(s2)
                j    .L57h_rf_ret
            .L57h_rf_dbl:
                mv   a0, s0
                mv   a1, s1
                call sqlite3_column_double
                fmv.x.d t0, fa0
                sd   t0, 0(s2)
                j    .L57h_rf_ret
            .L57h_rf_flt:
                mv   a0, s0
                mv   a1, s1
                call sqlite3_column_double
                fcvt.s.d fa0, fa0
                fmv.x.w t0, fa0
                sw   t0, 0(s2)
                j    .L57h_rf_ret
            .L57h_rf_str:
                mv   a0, s0
                mv   a1, s1
                call sqlite3_column_type
                li   t0, 5                         # SQLITE_NULL -> null
                beq  a0, t0, .L57h_rf_zero
                mv   a0, s0
                mv   a1, s1
                call sqlite3_column_text
                mv   s3, a0                        # ptr
                mv   a0, s3
                call kof_io_strlen
                mv   a1, a0
                mv   a0, s3
                call kof_io_make_string
                sd   a0, 0(s2)
                j    .L57h_rf_ret
            .L57h_rf_bool:
                # §397: NULL->false, INTEGER/FLOAT -> numero != 0, TEXT -> "true"
                mv   a0, s0
                mv   a1, s1
                call sqlite3_column_type
                li   t0, 5
                beq  a0, t0, .L57h_rf_zero
                li   t0, 1
                beq  a0, t0, .L57h_rf_bint
                li   t0, 2
                beq  a0, t0, .L57h_rf_bdbl
                mv   a0, s0
                mv   a1, s1
                call sqlite3_column_text
                beqz a0, .L57h_rf_zero
                lbu  t0, 0(a0)
                ori  t0, t0, 32
                li   t1, 116                       # 't'
                bne  t0, t1, .L57h_rf_zero
                lbu  t0, 1(a0)
                ori  t0, t0, 32
                li   t1, 114                       # 'r'
                bne  t0, t1, .L57h_rf_zero
                lbu  t0, 2(a0)
                ori  t0, t0, 32
                li   t1, 117                       # 'u'
                bne  t0, t1, .L57h_rf_zero
                lbu  t0, 3(a0)
                ori  t0, t0, 32
                li   t1, 101                       # 'e'
                bne  t0, t1, .L57h_rf_zero
                li   t0, 1
                sd   t0, 0(s2)
                j    .L57h_rf_ret
            .L57h_rf_bint:
                mv   a0, s0
                mv   a1, s1
                call sqlite3_column_int64
                snez a0, a0
                sd   a0, 0(s2)
                j    .L57h_rf_ret
            .L57h_rf_bdbl:
                mv   a0, s0
                mv   a1, s1
                call sqlite3_column_double
                fcvt.l.d a0, fa0, rtz              # truncate como intValue()
                snez a0, a0
                sd   a0, 0(s2)
                j    .L57h_rf_ret
            .L57h_rf_zero:
                sd   zero, 0(s2)
            .L57h_rf_ret:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                addi sp, sp, 48
                ret

            .section .rodata
            .L57h_magic:
                .quad 0x4B4F46425F425801
            .L57h_badv:
                .ascii "orm.find bind value: unsupported type on Native (ORM001)"
                .byte 0
            .section .text
            """;
}
