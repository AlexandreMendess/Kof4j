package dev.kof.compiler.nat;

// DB-3/DB-1 cross, slice E parte 2a (22/09, lane gaps-db): kof_orm_save no
// riscv64 — F2a de RuntimeOrm4 x86, sobre o kof.db SQLite de RtB46/RtB47.
// aarch64 herda via tradutor.
//
// Espelha as 3 saídas medidas do host: (1) pk nula/0 -> INSERT sem a coluna
// PK + generated key (sqlite3_last_insert_rowid) e devolve NOVA instância
// com a PK patchada (size = nFields*8+16 alinhado, vtable/typeId do header
// da FONTE, kof_alloc + kof_init_object + kof_memcpy); (2) pk != 0 ->
// UPDATE ... WHERE "pk" = ? (PK bindada por ÚLTIMO) e changes > 0 -> MESMO
// ponteiro; (3) UPDATE 0 linhas -> INSERT de TODAS as colunas (upsert) e
// MESMO ponteiro. Decisão da PK igual ao x86: int/long == 0, double/float
// TRUNCADO == 0 (fcvt.l.d/s rtz; NaN/±inf caem no sentinela e viram 0 como
// o cvttsd2si do x86), String null -> INSERT, bool NUNCA INSERT (host).
// Schema por kof_orm_parse_schema (global de RtB54); builder/bind/conexão
// pelos helpers globais de RtB55Helpers (gate 500). Falha de prepare/step
// -> throw "sqlite: " + errmsg (R6). Rótulos .L55_* locais (§445).
//
// ARMADILHA aarch64 (medida 23/09): o tradutor mapeia s10 -> x16, que é
// CALLER-SAVED na AAPCS64 — manter o índice de loop em s10 atraves de um
// `call` (sqlite3_bind_*/kof_memcpy) o corrompe e o binario aarch64 morre
// (rc=1, "true|1|1"); o riscv64 e imune (registradores proprios). Por isso
// `i` vive no slot de pilha 48(sp), nunca em s10. s0-s9 (x19-x28) sao
// callee-saved e podem atravessar chamadas; s10/S11 nao (S11=x29 ok).
public final class NativeRiscvAsmRtB55 {

    private NativeRiscvAsmRtB55() {}

    static String RISCV_RUNTIME_ASM_B_55 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_save(id*, obj*, table*, schema*) -> obj* (a0)
            # slots: 0 pkType | 8 pkVal | 16 insFlag | 24 paramIdx |
            #        32 &stmt | 40 changes | 48 loop i (nao usar s10 <=> x16)
            # ---------------------------------------------------------------
            .globl kof_orm_save
            .type kof_orm_save, @function
            kof_orm_save:
                addi sp, sp, -160
                sd   ra, 152(sp)
                sd   s0, 144(sp)
                sd   s1, 136(sp)
                sd   s2, 128(sp)
                sd   s3, 120(sp)
                sd   s4, 112(sp)
                sd   s5, 104(sp)
                sd   s6, 96(sp)
                sd   s7, 88(sp)
                sd   s8, 80(sp)
                sd   s9, 72(sp)
                sd   s10, 64(sp)
                sd   s11, 56(sp)
                mv   s0, a0                        # id (temporario)
                mv   s1, a1
                mv   s2, a2
                mv   s3, a3
                call kof_db_type                   # a0 = id ainda
                li   t0, 2
                beq  a0, t0, .L55_save_mysql
                mv   a0, s0
                call kof_orm_conn
                mv   s0, a0                        # conn
                mv   a0, s3
                call kof_orm_parse_schema          # a0=ftab a1=nbuf a2=nF a3=pkIdx
                mv   s4, a0                        # ftab
                mv   s5, a2                        # nFields
                mv   s6, a3                        # pkIndex
                slli t0, s6, 5
                add  t0, s4, t0
                lw   t1, 12(t0)
                sw   t1, 0(sp)                     # pkType
                # ---- decisao do path (pk do objeto = primeiro :generated) ----
                slli t3, s6, 3
                addi t3, t3, 16
                add  t3, s1, t3                    # &pk slot
                lw   t1, 0(sp)
                li   t2, 2
                beq  t1, t2, .L55_pk_str
                li   t2, 3
                beq  t1, t2, .L55_pk_bool
                li   t2, 5
                beq  t1, t2, .L55_pk_flt
                li   t2, 4
                beq  t1, t2, .L55_pk_dbl
                li   t2, 1
                beq  t1, t2, .L55_pk_lng
                lw   t2, 0(t3)
                sd   t2, 8(sp)
                j    .L55_pk_have
            .L55_pk_lng:
                ld   t2, 0(t3)
                sd   t2, 8(sp)
                j    .L55_pk_have
            .L55_pk_dbl:
                ld   t0, 0(t3)
                fmv.d.x fa0, t0
                fcvt.l.d t2, fa0, rtz
                j    .L55_pk_sat
            .L55_pk_flt:
                lw   t0, 0(t3)
                fmv.w.x fa0, t0
                fcvt.l.s t2, fa0, rtz
            .L55_pk_sat:
                li   t0, -1
                srli t0, t0, 1                     # INT64_MAX
                beq  t2, t0, .L55_pk_zero
                addi t0, t0, 1                     # INT64_MIN (cvttsd2si NaN)
                beq  t2, t0, .L55_pk_zero
                sd   t2, 8(sp)
                j    .L55_pk_have
            .L55_pk_zero:
                sd   zero, 8(sp)
                j    .L55_pk_have
            .L55_pk_str:
                ld   t2, 0(t3)
                sd   t2, 8(sp)
                beqz t2, .L55_path_insert
                j    .L55_path_update
            .L55_pk_bool:
                ld   t2, 0(t3)
                sd   t2, 8(sp)
                j    .L55_path_update
            .L55_pk_have:
                ld   t0, 8(sp)
                beqz t0, .L55_path_insert
                j    .L55_path_update

            # ---- dispatch mysql (type 2): peca B77 ------------------------
            .L55_save_mysql:
                mv   a0, s0
                mv   a1, s1
                mv   a2, s2
                mv   a3, s3
                ld   ra, 152(sp)
                ld   s0, 144(sp)
                ld   s1, 136(sp)
                ld   s2, 128(sp)
                ld   s3, 120(sp)
                ld   s4, 112(sp)
                ld   s5, 104(sp)
                ld   s6, 96(sp)
                ld   s7, 88(sp)
                ld   s8, 80(sp)
                ld   s9, 72(sp)
                ld   s10, 64(sp)
                ld   s11, 56(sp)
                addi sp, sp, 160
                j    kof_orm_save_mysql

            # ---- path INSERT (nova instancia) / upsert (mesma) ------------
            .L55_path_insert:
                sd   zero, 16(sp)
                j    .L55_ins_go
            .L55_path_upsert:
                li   t0, -1
                mv   s6, t0                        # nao pular a pk
                li   t0, 1
                sd   t0, 16(sp)
            .L55_ins_go:
                lw   t0, 16(s2)
                lw   t1, 16(s3)
                slli t1, t1, 2
                add  t0, t0, t1
                addi t0, t0, 256
                mv   a0, t0
                call kof_alloc
                mv   s7, a0
                mv   s8, a0
                mv   a0, s8
                la   a1, .L55_i1
                li   a2, 12
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                mv   a1, s2
                call kof_orm_sb_qstr
                mv   s8, a0
                mv   a0, s8
                la   a1, .L55_i2
                li   a2, 2
                call kof_orm_sb_append
                mv   s8, a0
                li   s11, 0
                sd   zero, 48(sp)
            .L55_ins_col:
                ld   t0, 48(sp)
                bge  t0, s5, .L55_ins_col_end
                beq  t0, s6, .L55_ins_col_skip
                bnez s11, .L55_ins_col_c2
                li   s11, 1
                j    .L55_ins_col_q
            .L55_ins_col_c2:
                mv   a0, s8
                la   a1, .L55_cs
                li   a2, 2
                call kof_orm_sb_append
                mv   s8, a0
            .L55_ins_col_q:
                ld   t0, 48(sp)
                slli t0, t0, 5
                add  t0, s4, t0
                mv   a0, s8
                ld   a1, 0(t0)
                lw   a2, 8(t0)
                call kof_orm_sb_qraw
                mv   s8, a0
            .L55_ins_col_skip:
                ld   t0, 48(sp)
                addi t0, t0, 1
                sd   t0, 48(sp)
                j    .L55_ins_col
            .L55_ins_col_end:
                mv   a0, s8
                la   a1, .L55_i3
                li   a2, 10
                call kof_orm_sb_append
                mv   s8, a0
                li   s11, 0
                sd   zero, 48(sp)
            .L55_ins_mk:
                ld   t0, 48(sp)
                bge  t0, s5, .L55_ins_mk_end
                beq  t0, s6, .L55_ins_mk_skip
                bnez s11, .L55_ins_mk_c2
                li   s11, 1
                mv   a0, s8
                la   a1, .L55_q1
                li   a2, 1
                call kof_orm_sb_append
                mv   s8, a0
                j    .L55_ins_mk_skip
            .L55_ins_mk_c2:
                mv   a0, s8
                la   a1, .L55_cq
                li   a2, 3
                call kof_orm_sb_append
                mv   s8, a0
            .L55_ins_mk_skip:
                ld   t0, 48(sp)
                addi t0, t0, 1
                sd   t0, 48(sp)
                j    .L55_ins_mk
            .L55_ins_mk_end:
                mv   a0, s8
                li   a1, 41
                call kof_orm_sb_char
                mv   s8, a0
                sb   zero, 0(s8)
                mv   a0, s0
                mv   a1, s7
                li   a2, -1
                addi a3, sp, 32
                li   a4, 0
                call sqlite3_prepare_v2
                bnez a0, .L55_prep_fail
                ld   s9, 32(sp)
                beqz s9, .L55_prep_fail
                li   t0, 1
                sd   t0, 24(sp)
                sd   zero, 48(sp)
            .L55_ins_bind:
                ld   t0, 48(sp)
                bge  t0, s5, .L55_ins_bind_end
                beq  t0, s6, .L55_ins_bind_skip
                slli t0, t0, 5
                add  t0, s4, t0
                lw   a3, 12(t0)
                ld   t0, 48(sp)
                slli t0, t0, 3
                addi t0, t0, 16
                add  t0, s1, t0
                mv   a0, s9
                ld   a1, 24(sp)
                mv   a2, t0
                call kof_orm_bind_field
                ld   t0, 24(sp)
                addi t0, t0, 1
                sd   t0, 24(sp)
            .L55_ins_bind_skip:
                ld   t0, 48(sp)
                addi t0, t0, 1
                sd   t0, 48(sp)
                j    .L55_ins_bind
            .L55_ins_bind_end:
                mv   a0, s9
                call sqlite3_step
                li   t0, 101
                bne  a0, t0, .L55_sql_fail
                mv   a0, s9
                call sqlite3_finalize
                ld   t0, 16(sp)
                bnez t0, .L55_ret_obj
                mv   a0, s0
                call sqlite3_last_insert_rowid
                sd   a0, 40(sp)                    # rowid
                slli t0, s5, 3
                addi t0, t0, 16
                addi t0, t0, 15
                andi t0, t0, -16
                mv   a0, t0
                call kof_alloc
                mv   s11, a0                       # dst (NOVA instancia)
                mv   a0, s11
                lw   a1, 0(s1)
                ld   a2, 8(s1)
                call kof_init_object
                addi a0, s11, 16
                addi a1, s1, 16
                slli a2, s5, 3
                call kof_memcpy
                slli t0, s6, 3
                addi t0, t0, 16
                add  t0, s11, t0
                ld   t1, 40(sp)
                sd   t1, 0(t0)                     # patch da pk
                mv   a0, s11
                j    .L55_out

            # ---- path UPDATE ----------------------------------------------
            .L55_path_update:
                lw   t0, 16(s2)
                lw   t1, 16(s3)
                slli t1, t1, 2
                add  t0, t0, t1
                addi t0, t0, 256
                mv   a0, t0
                call kof_alloc
                mv   s7, a0
                mv   s8, a0
                mv   a0, s8
                la   a1, .L55_u1
                li   a2, 7
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                mv   a1, s2
                call kof_orm_sb_qstr
                mv   s8, a0
                mv   a0, s8
                la   a1, .L55_u2
                li   a2, 5
                call kof_orm_sb_append
                mv   s8, a0
                li   s11, 0
                sd   zero, 48(sp)
            .L55_upd_col:
                ld   t0, 48(sp)
                bge  t0, s5, .L55_upd_col_end
                beq  t0, s6, .L55_upd_col_skip
                bnez s11, .L55_upd_col_c2
                li   s11, 1
                j    .L55_upd_col_q
            .L55_upd_col_c2:
                mv   a0, s8
                la   a1, .L55_cs
                li   a2, 2
                call kof_orm_sb_append
                mv   s8, a0
            .L55_upd_col_q:
                ld   t0, 48(sp)
                slli t0, t0, 5
                add  t0, s4, t0
                mv   a0, s8
                ld   a1, 0(t0)
                lw   a2, 8(t0)
                call kof_orm_sb_qraw
                mv   s8, a0
                mv   a0, s8
                la   a1, .L55_eq
                li   a2, 4
                call kof_orm_sb_append
                mv   s8, a0
            .L55_upd_col_skip:
                ld   t0, 48(sp)
                addi t0, t0, 1
                sd   t0, 48(sp)
                j    .L55_upd_col
            .L55_upd_col_end:
                mv   a0, s8
                la   a1, .L55_u3
                li   a2, 7
                call kof_orm_sb_append
                mv   s8, a0
                slli t0, s6, 5
                add  t0, s4, t0
                mv   a0, s8
                ld   a1, 0(t0)
                lw   a2, 8(t0)
                call kof_orm_sb_qraw
                mv   s8, a0
                mv   a0, s8
                la   a1, .L55_eq
                li   a2, 4
                call kof_orm_sb_append
                mv   s8, a0
                sb   zero, 0(s8)
                mv   a0, s0
                mv   a1, s7
                li   a2, -1
                addi a3, sp, 32
                li   a4, 0
                call sqlite3_prepare_v2
                bnez a0, .L55_prep_fail
                ld   s9, 32(sp)
                beqz s9, .L55_prep_fail
                li   t0, 1
                sd   t0, 24(sp)
                sd   zero, 48(sp)
            .L55_upd_bind:
                ld   t0, 48(sp)
                bge  t0, s5, .L55_upd_bind_pk
                beq  t0, s6, .L55_upd_bind_skip
                slli t0, t0, 5
                add  t0, s4, t0
                lw   a3, 12(t0)
                ld   t0, 48(sp)
                slli t0, t0, 3
                addi t0, t0, 16
                add  t0, s1, t0
                mv   a0, s9
                ld   a1, 24(sp)
                mv   a2, t0
                call kof_orm_bind_field
                ld   t0, 24(sp)
                addi t0, t0, 1
                sd   t0, 24(sp)
            .L55_upd_bind_skip:
                ld   t0, 48(sp)
                addi t0, t0, 1
                sd   t0, 48(sp)
                j    .L55_upd_bind
            .L55_upd_bind_pk:
                slli t0, s6, 5
                add  t0, s4, t0
                lw   a3, 12(t0)
                slli t0, s6, 3
                addi t0, t0, 16
                add  t0, s1, t0
                mv   a0, s9
                ld   a1, 24(sp)
                mv   a2, t0
                call kof_orm_bind_field
                mv   a0, s9
                call sqlite3_step
                li   t0, 101
                bne  a0, t0, .L55_sql_fail
                mv   a0, s0
                call sqlite3_changes
                sd   a0, 40(sp)
                mv   a0, s9
                call sqlite3_finalize
                ld   t0, 40(sp)
                bgtz t0, .L55_ret_obj
                j    .L55_path_upsert

            .L55_ret_obj:
                mv   a0, s1
                j    .L55_out

            # ---- fail: throw "sqlite: " + errmsg --------------------------
            .L55_sql_fail:
                mv   a0, s9
                call sqlite3_finalize
            .L55_prep_fail:
                mv   a0, s0
                call sqlite3_errmsg
                mv   s11, a0
                beqz s11, .L55_pre_only
                li   t1, 0
                mv   t0, s11
            .L55_slen:
                lbu  t2, 0(t0)
                beqz t2, .L55_slen_done
                addi t0, t0, 1
                addi t1, t1, 1
                j    .L55_slen
            .L55_slen_done:
                mv   a0, s11
                mv   a1, t1
                call kof_string_from_literal
                mv   s11, a0
                la   a0, .L55_pre_c
                li   a1, 8
                call kof_string_from_literal
                mv   a1, s11
                call kof_string_concat
                call kof_throw_string
            .L55_pre_only:
                la   a0, .L55_pre_c
                li   a1, 8
                call kof_string_from_literal
                call kof_throw_string

            .L55_out:
                ld   ra, 152(sp)
                ld   s0, 144(sp)
                ld   s1, 136(sp)
                ld   s2, 128(sp)
                ld   s3, 120(sp)
                ld   s4, 112(sp)
                ld   s5, 104(sp)
                ld   s6, 96(sp)
                ld   s7, 88(sp)
                ld   s8, 80(sp)
                ld   s9, 72(sp)
                ld   s10, 64(sp)
                ld   s11, 56(sp)
                addi sp, sp, 160
                ret

            .section .rodata
            .L55_i1:
                .ascii "INSERT INTO "
            .L55_i2:
                .ascii " ("
            .L55_i3:
                .ascii ") VALUES ("
            .L55_u1:
                .ascii "UPDATE "
            .L55_u2:
                .ascii " SET "
            .L55_u3:
                .ascii " WHERE "
            .L55_eq:
                .ascii " = ?"
            .L55_cs:
                .ascii ", "
            .L55_cq:
                .ascii ", ?"
            .L55_q1:
                .ascii "?"
            .L55_pre_c:
                .ascii "sqlite: "
            .section .text
            """;
}
