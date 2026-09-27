package dev.kof.compiler.nat;

// DB-3/DB-1 cross, slice E-parte-5 (23/09, lane gaps-db): faces `where` no
// riscv64 — port de RuntimeOrm7 (F2c2). kof_orm_where (6 args, `=`) e
// kof_orm_where_op (7 args, op do usuario) compartilham UM corpo: whitelist do
// op IDENTICA ao host (medida: throw `ORM operator not allowed: <op>`; `==`
// normaliza p/ `=`), SQL `SELECT * FROM "t" WHERE "f" <op> ?` com bind do
// value pelo MESMO classificador do RuntimeOrm7 (box §284 / KofString / null;
// tag fora -> bind_null, ao contrario do find) e o loop de campos do
// RuntimeOrm6 (RtB58) acumulado em kof_list_new/kof_list_add; vazio = lista
// VAZIA (nunca null).
//
// No riscv64 o 7o arg (className) chega em a6 (RISC-V tem 8 regs de arg) —
// diferente do x86 (stack). Frame 240: slots 0 table|8 schema|16 className|
// 24 field|32 value|40 opBody|48 opLen|56 buf|64 &stmt|72 vtab|80 typeId|
// 88 size|96 list|104 nCols|112 entry|120 conn|128 id|136 opStr|144 A|152 B;
// state callee-saved s0..s8 em 224..160, ra em 232. NUNCA usa s10 (x16
// caller-saved no aarch64 — ver RtB55).
public final class NativeRiscvAsmRtB59 {

    private NativeRiscvAsmRtB59() {}

    static String RISCV_RUNTIME_ASM_B_59 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_where(id,field,value,table,schema,className) -> list*
            # ---------------------------------------------------------------
            .globl kof_orm_where
            .type kof_orm_where, @function
            kof_orm_where:
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
                sd   a1, 24(sp)                    # field
                sd   a2, 32(sp)                    # value
                sd   a3, 0(sp)                     # table
                sd   a4, 8(sp)                     # schema
                sd   a5, 16(sp)                    # className
                sd   zero, 40(sp)                  # opBody (default -> "=")
                li   t0, 1
                sd   t0, 48(sp)                    # opLen
                sd   zero, 136(sp)                 # opStr (sem KofString)
                j    .L59_body
            # ---------------------------------------------------------------
            # kof_orm_where_op(id,field,op,value,table,schema,className)
            # ---------------------------------------------------------------
            .globl kof_orm_where_op
            .type kof_orm_where_op, @function
            kof_orm_where_op:
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
                sd   a1, 24(sp)                    # field
                sd   a3, 32(sp)                    # value
                sd   a4, 0(sp)                     # table
                sd   a5, 8(sp)                     # schema
                sd   a6, 16(sp)                    # className
                sd   a2, 136(sp)                   # op KofString*
                addi t0, a2, 24
                sd   t0, 40(sp)                    # opBody
                lw   t0, 16(a2)
                sd   t0, 48(sp)                    # opLen
            .L59_body:
                ld   a0, 128(sp)
                call kof_db_type
                li   t0, 2
                beq  a0, t0, .L59_where_mysql
                ld   a0, 128(sp)
                call kof_orm_conn
                sd   a0, 120(sp)                   # conn
                ld   a0, 8(sp)
                call kof_orm_parse_schema          # a0=ftab a1=nbuf a2=nF a3=pkIdx
                mv   s1, a0                        # ftab
                mv   s2, a2                        # nFields
                ld   t0, 16(sp)
                addi a0, t0, 24                    # body do className
                lw   a1, 16(t0)
                call kof_orm_ctors
                beqz a0, .L59_noface
                sd   a0, 72(sp)                    # vtab
                sd   a1, 80(sp)                    # typeId
                sd   a2, 88(sp)                    # totalSize
                call kof_list_new
                mv   s4, a0                        # lista destino
                # ---- whitelist do op (host: antes do SQL; "==" -> "=") ----
                ld   t1, 136(sp)                   # opStr (0 = where/default)
                beqz t1, .L59_opdef
                ld   t6, 40(sp)                    # opBody (header+24, ja' calculado)
                ld   t2, 48(sp)                    # opLen (header+16)
                li   t3, 1
                beq  t2, t3, .L59_oplen1
                li   t3, 2
                beq  t2, t3, .L59_oplen2
                li   t3, 4
                beq  t2, t3, .L59_oplen4
                j    .L59_opbad
            .L59_oplen1:
                lbu  t3, 0(t6)
                li   t4, 62                        # '>'
                beq  t3, t4, .L59_opok
                li   t4, 60                        # '<'
                beq  t3, t4, .L59_opok
                j    .L59_opbad
            .L59_oplen2:
                lbu  t3, 0(t6)
                lbu  t4, 1(t6)
                li   t5, 61                        # '='
                beq  t3, t5, .L59_opeq             # ==
                li   t5, 62
                bne  t3, t5, .L59_op2b
                li   t5, 61
                beq  t4, t5, .L59_opok             # >=
                j    .L59_opbad
            .L59_op2b:
                li   t5, 60
                bne  t3, t5, .L59_op2c
                li   t5, 61
                beq  t4, t5, .L59_opok             # <=
                j    .L59_opbad
            .L59_op2c:
                li   t5, 33                        # '!'
                bne  t3, t5, .L59_opbad
                li   t5, 61
                beq  t4, t5, .L59_opok             # !=
                j    .L59_opbad
            .L59_oplen4:
                lbu  t3, 0(t6)
                li   t4, 76                        # 'L'
                bne  t3, t4, .L59_opbad
                lbu  t3, 1(t6)
                li   t4, 73                        # 'I'
                bne  t3, t4, .L59_opbad
                lbu  t3, 2(t6)
                li   t4, 75                        # 'K'
                bne  t3, t4, .L59_opbad
                lbu  t3, 3(t6)
                li   t4, 69                        # 'E'
                bne  t3, t4, .L59_opbad
                j    .L59_opok
            .L59_opeq:
            .L59_opdef:
                la   t1, .L59_eqop
                sd   t1, 40(sp)
                li   t1, 1
                sd   t1, 48(sp)
            .L59_opok:
                # ---- SQL: SELECT * FROM "t" WHERE "f" <op> ? --------------
                ld   t1, 0(sp)
                lw   t2, 16(t1)                    # tblLen
                ld   t1, 24(sp)
                lw   t3, 16(t1)                    # fieldLen
                add  t2, t2, t3
                ld   t3, 48(sp)                    # opLen
                add  t2, t2, t3
                addi a0, t2, 45
                call kof_alloc
                sd   a0, 56(sp)                    # buf base
                mv   s8, a0
                mv   a0, s8
                la   a1, .L59_s1
                li   a2, 14
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   a1, 0(sp)
                call kof_orm_sb_qstr               # "t"
                mv   s8, a0
                mv   a0, s8
                la   a1, .L59_q
                li   a2, 7
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   a1, 24(sp)
                call kof_orm_sb_qstr               # "f"
                mv   s8, a0
                mv   a0, s8
                la   a1, .L59_sp
                li   a2, 1
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                ld   a1, 40(sp)
                ld   a2, 48(sp)
                call kof_orm_sb_append             # <op>
                mv   s8, a0
                mv   a0, s8
                la   a1, .L59_qm
                li   a2, 2
                call kof_orm_sb_append
                mv   s8, a0
                sb   zero, 0(s8)
                # ---- prepare + bind value (param 1, classificador Orm7) ---
                sd   zero, 64(sp)
                ld   a0, 120(sp)
                ld   a1, 56(sp)
                li   a2, -1
                addi a3, sp, 64
                li   a4, 0
                call sqlite3_prepare_v2
                bnez a0, .L59_prep_fail
                ld   s0, 64(sp)
                beqz s0, .L59_prep_fail
                ld   t1, 32(sp)                    # value
                beqz t1, .L59_bind_null
                la   t2, .L59_magic
                ld   t2, 0(t2)
                ld   t3, 0(t1)
                bne  t2, t3, .L59_bind_str
                lw   t3, 8(t1)                     # tag
                beqz t3, .L59_bind_int
                li   t4, 1
                beq  t3, t4, .L59_bind_quad
                li   t4, 2
                beq  t3, t4, .L59_bind_quad
                li   t4, 4
                beq  t3, t4, .L59_bind_dbl
                li   t4, 5
                beq  t3, t4, .L59_bind_flt
                j    .L59_bind_null                # Orm7: tag fora -> bind_null
            .L59_bind_int:
                lw   a2, 16(t1)
                mv   a0, s0
                li   a1, 1
                call sqlite3_bind_int64
                j    .L59_stepped
            .L59_bind_quad:
                ld   a2, 16(t1)
                mv   a0, s0
                li   a1, 1
                call sqlite3_bind_int64
                j    .L59_stepped
            .L59_bind_dbl:
                ld   t3, 16(t1)
                fmv.d.x fa0, t3
                mv   a0, s0
                li   a1, 1
                call sqlite3_bind_double
                j    .L59_stepped
            .L59_bind_flt:
                lw   t3, 16(t1)
                fmv.w.x fa0, t3
                fcvt.d.s fa0, fa0
                mv   a0, s0
                li   a1, 1
                call sqlite3_bind_double
                j    .L59_stepped
            .L59_bind_str:
                lw   t3, 0(t1)                     # KofString (1,0,0)?
                li   t4, 1
                bne  t3, t4, .L59_bind_null
                lw   t3, 4(t1)
                bnez t3, .L59_bind_null
                ld   t3, 8(t1)
                bnez t3, .L59_bind_null
                addi a2, t1, 24
                lw   a3, 16(t1)
                li   a4, -1
                mv   a0, s0
                li   a1, 1
                call sqlite3_bind_text
                j    .L59_stepped
            .L59_bind_null:
                mv   a0, s0
                li   a1, 1
                call sqlite3_bind_null
            .L59_stepped:
                # ---- loop de linhas ---------------------------------------
            .L59_row:
                mv   a0, s0
                call sqlite3_step
                li   t0, 100
                bne  a0, t0, .L59_end
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
            .L59_fld:
                bge  s5, s2, .L59_rowdone
                slli t0, s5, 5
                add  t0, s1, t0
                sd   t0, 112(sp)                   # entry
                lw   s7, 8(t0)                     # nameLen
                li   s6, 0
            .L59_col:
                lw   t0, 104(sp)
                bge  s6, t0, .L59_nomatch
                mv   a0, s0
                mv   a1, s6
                call sqlite3_column_name
                mv   t2, a0
                ld   t1, 112(sp)
                ld   t1, 0(t1)
                li   t3, 0
            .L59_cmp:
                bge  t3, s7, .L59_cmpend
                add  t4, t2, t3
                lbu  t4, 0(t4)
                add  t5, t1, t3
                lbu  t5, 0(t5)
                bne  t4, t5, .L59_nextcol
                addi t3, t3, 1
                j    .L59_cmp
            .L59_cmpend:
                add  t4, t2, t3
                lbu  t4, 0(t4)
                bnez t4, .L59_nextcol
                j    .L59_read
            .L59_nextcol:
                addi s6, s6, 1
                j    .L59_col
            .L59_read:
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
                j    .L59_fld
            .L59_rowdone:
                mv   a0, s4
                mv   a1, s3
                call kof_list_add
                j    .L59_row
            .L59_end:
                mv   a0, s0
                call sqlite3_finalize
                mv   a0, s4
                j    .L59_ret
            # ---- fail: throw "sqlite: " + errmsg --------------------------
            .L59_prep_fail:
                ld   a0, 120(sp)
                call sqlite3_errmsg
                mv   s7, a0
                beqz s7, .L59_pre_only
                mv   a0, s7
                call kof_io_strlen
                mv   a1, a0
                mv   a0, s7
                call kof_string_from_literal
                mv   s7, a0
                la   a0, .L59_pre
                li   a1, 8
                call kof_string_from_literal
                mv   a1, s7
                call kof_string_concat
                call kof_throw_string
            .L59_pre_only:
                la   a0, .L59_pre
                li   a1, 8
                call kof_string_from_literal
                call kof_throw_string
            .L59_nomatch:
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
                la   a0, .L59_nc
                li   a1, 18
                call kof_string_from_literal
                mv   a1, s7
                call kof_string_concat
                call kof_throw_string
            .L59_noface:
                la   a0, .L59_face
                li   a1, 47
                call kof_string_from_literal
                call kof_throw_string
            .L59_opbad:
                # GC-safe: A e B em slots do frame (varrimento conservativo
                #  cobre rsp..kof_main_stack_bottom durante o alloc do concat).
                ld   t1, 136(sp)                   # B = op KofString*
                sd   t1, 152(sp)
                la   a0, .L59_bad
                li   a1, 26
                call kof_string_from_literal        # A
                sd   a0, 144(sp)
                ld   a1, 152(sp)
                call kof_string_concat
                call kof_throw_string
            .L59_ret:
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
            # ---- dispatch mysql (type 2): tail-call kof_orm_where_mysql ----
            .L59_where_mysql:
                ld   a0, 128(sp)                   # id
                ld   a1, 24(sp)                    # field
                ld   a2, 32(sp)                    # value
                ld   a3, 136(sp)                   # op KofString* (0 = where)
                ld   a4, 0(sp)                     # table
                ld   a5, 8(sp)                     # schema
                ld   a6, 16(sp)                    # className
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
                j    kof_orm_where_mysql

            .section .rodata
            .L59_s1:
                .ascii "SELECT * FROM "
            .L59_q:
                .ascii " WHERE "
            .L59_sp:
                .ascii " "
            .L59_qm:
                .ascii " ?"
            .L59_eqop:
                .ascii "="
                .byte 0
            .L59_bad:
                .ascii "ORM operator not allowed: "
            .L59_pre:
                .ascii "sqlite: "
            .L59_nc:
                .ascii "sqlite: no column "
            .L59_face:
                .ascii "orm.where: entity class not registered (ORM001)"
            .L59_magic:
                .quad 0x4B4F46425F425801
            .section .text
            """;
}
