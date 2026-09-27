package dev.kof.compiler.nat;

// DB-3/DB-1 cross, S5.5 fatia 4b (24/09, lane gaps-db): kof_orm_save sobre o
// wire MySQL no riscv64/aarch64 — F2d5 de RuntimeOrmMysqlSave x86-64.
//
// Espelha as 3 saidas medidas do host (JvmOrmRuntime.kof_orm_save, dialeto
// mysql): (1) pk nula/0 -> INSERT SEM a coluna PK + SELECT LAST_INSERT_ID()
// (kof_db_mysql_scalar_int/B74) e devolve NOVA instancia com a pk patchada
// (size = nFields*8+16 alinhado, typeId/vtable do header da FONTE, kof_alloc
// + kof_init_object + kof_memcpy); (2) pk != 0 -> UPDATE `t` SET `f` = <lit>
// WHERE `pk` = <lit>; afetadas > 0 -> MESMO ponteiro; (3) UPDATE 0 linhas ->
// INSERT de TODAS as colunas (upsert) e MESMO ponteiro. Decisao da PK igual
// ao x86: int/long == 0, double/float TRUNCADO == 0 (fcvt.l.d/s rtz, NaN/
// +-inf caem nos sentinelas INT64_MIN/MAX e viram 0 como o cvttsd2si do x86),
// String null -> INSERT, bool NUNCA INSERT (host). Schema por
// kof_orm_parse_schema (B54); literais por typeCode + backtick do dialeto
// (.L77 helpers); exec COM_QUERY com throw por kof_orm_mysql_exec (B76).
// Tipo fora do contrato -> ORM001 (R6). Rótulos .L77_* locais (§445).
public final class NativeRiscvAsmRtB77 {

    private NativeRiscvAsmRtB77() {}

    static String RISCV_RUNTIME_ASM_B_77 = """
            .section .text
            # `.L77 helper: kof_orm_mysql_bt_raw(a0=buf,a1=ptr,a2=len)->a0 --
            #  appenda `ptr` (backtick, dialeto do host; identidade do schema)
            .globl kof_orm_mysql_bt_raw
            .type kof_orm_mysql_bt_raw, @function
            kof_orm_mysql_bt_raw:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s1, a1
                mv   s2, a2
                li   a1, 96
                call kof_orm_sb_char
                mv   a1, s1
                mv   a2, s2
                call kof_orm_sb_append
                li   a1, 96
                call kof_orm_sb_char
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                addi sp, sp, 48
                ret

            # `kof_orm_mysql_bt_str(a0=buf,a1=KofString*)->a0: appenda `payload`
            .globl kof_orm_mysql_bt_str
            .type kof_orm_mysql_bt_str, @function
            kof_orm_mysql_bt_str:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s1, a1
                li   a1, 96
                call kof_orm_sb_char
                mv   s0, a0
                mv   a0, s0
                addi a1, s1, 24
                lw   a2, 16(s1)
                call kof_orm_sb_append
                li   a1, 96
                call kof_orm_sb_char
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                addi sp, sp, 32
                ret

            # `kof_orm_mysql_field_lit(a0=buf,a1=entry,a2=slot*)->a0:
            #  appenda o literal SQL do campo pelo typeCode do schema
            #  (port de RuntimeOrmMysqlFieldLit/.Lorm_sa_lit)
            .globl kof_orm_mysql_field_lit
            .type kof_orm_mysql_field_lit, @function
            kof_orm_mysql_field_lit:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                lw   t0, 12(s1)
                li   t1, 2
                beq  t0, t1, .L77fl_str
                li   t1, 3
                beq  t0, t1, .L77fl_bool
                li   t1, 4
                beq  t0, t1, .L77fl_dbl
                li   t1, 5
                beq  t0, t1, .L77fl_flt
                li   t1, 1
                beq  t0, t1, .L77fl_lng
                beqz t0, .L77fl_int
                la   a0, .L77_badv
                li   a1, 54
                call kof_string_from_literal
                call kof_throw_string
            .L77fl_int:
                lw   a0, 0(s2)
                call kof_long_to_string
                j    .L77fl_raw
            .L77fl_lng:
                ld   a0, 0(s2)
                call kof_long_to_string
                j    .L77fl_raw
            .L77fl_bool:
                ld   t0, 0(s2)
                snez a0, t0
                call kof_long_to_string
                j    .L77fl_raw
            .L77fl_dbl:
                ld   a0, 0(s2)
                call kof_double_to_string
                j    .L77fl_raw
            .L77fl_flt:
                lw   t0, 0(s2)
                fmv.w.x fa0, t0
                fcvt.d.s fa0, fa0
                fmv.x.d a0, fa0
                call kof_double_to_string
                j    .L77fl_raw
            .L77fl_str:
                ld   t0, 0(s2)
                beqz t0, .L77fl_null
                mv   a0, t0
                call kof_db_mysql_render
                j    .L77fl_raw
            .L77fl_null:
                mv   a0, s0
                la   a1, .L77_null
                li   a2, 4
                call kof_orm_sb_append
                j    .L77fl_out
            .L77fl_raw:
                mv   t1, a0
                mv   a0, s0
                addi a1, t1, 24
                lw   a2, 16(t1)
                call kof_orm_sb_append
            .L77fl_out:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                addi sp, sp, 48
                ret

            # ---------------------------------------------------------------
            # kof_orm_save_mysql(a0=id,a1=obj,a2=table,a3=schema) -> a0=obj*
            # slots: 0 skipPk | 8 retNew | 16 path (0 ins-gen/1 upd/2 upsert)
            #        24 loop i | 32 id | 40 lastid | 48 tmp
            # ---------------------------------------------------------------
            .globl kof_orm_save_mysql
            .type kof_orm_save_mysql, @function
            kof_orm_save_mysql:
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
                mv   s1, a1
                mv   s2, a2
                sd   a0, 32(sp)
                mv   a0, a3
                call kof_orm_parse_schema
                mv   s3, a0
                mv   s4, a2
                mv   s5, a3
                ld   a0, 32(sp)
                call kof_db_resolve
                mv   s0, a0
                slli t0, s5, 5
                add  t0, s3, t0
                lw   s6, 12(t0)
                slli t0, s5, 3
                addi t0, t0, 16
                add  t0, s1, t0
                li   t1, 2
                beq  s6, t1, .L77_pk_str
                li   t1, 3
                beq  s6, t1, .L77_update
                li   t1, 5
                beq  s6, t1, .L77_pk_flt
                li   t1, 4
                beq  s6, t1, .L77_pk_dbl
                li   t1, 1
                beq  s6, t1, .L77_pk_lng
                lw   t2, 0(t0)
                j    .L77_pk_have
            .L77_pk_lng:
                ld   t2, 0(t0)
                j    .L77_pk_have
            .L77_pk_dbl:
                ld   t0, 0(t0)
                fmv.d.x fa0, t0
                fcvt.l.d t2, fa0, rtz
                j    .L77_pk_sat
            .L77_pk_flt:
                lw   t0, 0(t0)
                fmv.w.x fa0, t0
                fcvt.l.s t2, fa0, rtz
            .L77_pk_sat:
                li   t0, -1
                srli t0, t0, 1
                beq  t2, t0, .L77_pk_zero
                addi t0, t0, 1
                beq  t2, t0, .L77_pk_zero
                j    .L77_pk_have
            .L77_pk_zero:
                li   t2, 0
            .L77_pk_have:
                beqz t2, .L77_insert
                j    .L77_update
            .L77_pk_str:
                ld   t2, 0(t0)
                beqz t2, .L77_insert
                j    .L77_update
            .L77_insert:
                li   t0, 1
                sd   t0, 0(sp)
                li   t0, 1
                sd   t0, 8(sp)
                li   t0, 0
                sd   t0, 16(sp)
                j    .L77_alloc
            .L77_update:
                sd   zero, 0(sp)
                sd   zero, 8(sp)
                li   t0, 1
                sd   t0, 16(sp)
                j    .L77_alloc
            .L77_upsert:
                sd   zero, 0(sp)
                sd   zero, 8(sp)
                li   t0, 2
                sd   t0, 16(sp)
                j    .L77_alloc

            # ---- estimativa do builder + alloc (KofString contigua) ------
            .L77_alloc:
                lw   t0, 16(s2)
                slli t0, t0, 1
                slli t1, s4, 8
                add  t0, t0, t1
                addi t0, t0, 1024
                li   t2, 0
            .L77_est:
                bge  t2, s4, .L77_est_done
                slli t3, t2, 5
                add  t3, s3, t3
                lw   t4, 12(t3)
                li   t5, 2
                bne  t4, t5, .L77_est_next
                slli t4, t2, 3
                addi t4, t4, 16
                add  t4, s1, t4
                ld   t4, 0(t4)
                beqz t4, .L77_est_next
                lw   t4, 16(t4)
                slli t4, t4, 1
                add  t0, t0, t4
            .L77_est_next:
                addi t2, t2, 1
                j    .L77_est
            .L77_est_done:
                addi a0, t0, 24
                call kof_alloc
                mv   s7, a0
                li   t0, 1
                sw   t0, 0(s7)
                sw   zero, 4(s7)
                sd   zero, 8(s7)
                sw   zero, 20(s7)
                addi s8, s7, 24
                ld   t0, 16(sp)
                beqz t0, .L77_build_insert
                li   t1, 1
                beq  t0, t1, .L77_build_update
                j    .L77_build_insert

            # ---- INSERT (skipPk=1: sem a PK) / upsert (skipPk=0) --------
            .L77_build_insert:
                mv   a0, s8
                la   a1, .L77_i1
                li   a2, 12
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                mv   a1, s2
                call kof_orm_mysql_bt_str
                mv   s8, a0
                mv   a0, s8
                la   a1, .L77_i2
                li   a2, 2
                call kof_orm_sb_append
                mv   s8, a0
                li   s11, 0
                sd   zero, 24(sp)
            .L77_ins_col:
                ld   t0, 24(sp)
                bge  t0, s4, .L77_ins_col_end
                ld   t1, 0(sp)
                beqz t1, .L77_ins_col_go
                beq  t0, s5, .L77_ins_col_next
            .L77_ins_col_go:
                bnez s11, .L77_ins_col_c2
                li   s11, 1
                j    .L77_ins_col_q
            .L77_ins_col_c2:
                mv   a0, s8
                la   a1, .L77_cs
                li   a2, 2
                call kof_orm_sb_append
                mv   s8, a0
            .L77_ins_col_q:
                ld   t0, 24(sp)
                slli t0, t0, 5
                add  t0, s3, t0
                mv   a0, s8
                ld   a1, 0(t0)
                lw   a2, 8(t0)
                call kof_orm_mysql_bt_raw
                mv   s8, a0
            .L77_ins_col_next:
                ld   t0, 24(sp)
                addi t0, t0, 1
                sd   t0, 24(sp)
                j    .L77_ins_col
            .L77_ins_col_end:
                mv   a0, s8
                la   a1, .L77_i3
                li   a2, 10
                call kof_orm_sb_append
                mv   s8, a0
                li   s11, 0
                sd   zero, 24(sp)
            .L77_ins_val:
                ld   t0, 24(sp)
                bge  t0, s4, .L77_ins_val_end
                ld   t1, 0(sp)
                beqz t1, .L77_ins_val_go
                beq  t0, s5, .L77_ins_val_next
            .L77_ins_val_go:
                bnez s11, .L77_ins_val_c2
                li   s11, 1
                j    .L77_ins_val_lit
            .L77_ins_val_c2:
                mv   a0, s8
                la   a1, .L77_cs
                li   a2, 2
                call kof_orm_sb_append
                mv   s8, a0
            .L77_ins_val_lit:
                ld   t0, 24(sp)
                slli t1, t0, 5
                add  t1, s3, t1
                slli t0, t0, 3
                addi t0, t0, 16
                add  t0, s1, t0
                mv   a0, s8
                mv   a2, t0
                mv   a1, t1
                call kof_orm_mysql_field_lit
                mv   s8, a0
            .L77_ins_val_next:
                ld   t0, 24(sp)
                addi t0, t0, 1
                sd   t0, 24(sp)
                j    .L77_ins_val
            .L77_ins_val_end:
                mv   a0, s8
                li   a1, 41
                call kof_orm_sb_char
                mv   s8, a0
                j    .L77_finish

            # ---- UPDATE `t` SET `f` = <lit>,... WHERE `pk` = <lit> -------
            .L77_build_update:
                mv   a0, s8
                la   a1, .L77_u1
                li   a2, 7
                call kof_orm_sb_append
                mv   s8, a0
                mv   a0, s8
                mv   a1, s2
                call kof_orm_mysql_bt_str
                mv   s8, a0
                mv   a0, s8
                la   a1, .L77_u2
                li   a2, 5
                call kof_orm_sb_append
                mv   s8, a0
                li   s11, 0
                sd   zero, 24(sp)
            .L77_upd_col:
                ld   t0, 24(sp)
                bge  t0, s4, .L77_upd_col_end
                beq  t0, s5, .L77_upd_col_next
                bnez s11, .L77_upd_col_c2
                li   s11, 1
                j    .L77_upd_col_q
            .L77_upd_col_c2:
                mv   a0, s8
                la   a1, .L77_cs
                li   a2, 2
                call kof_orm_sb_append
                mv   s8, a0
            .L77_upd_col_q:
                ld   t0, 24(sp)
                slli t1, t0, 5
                add  t1, s3, t1
                mv   a0, s8
                ld   a1, 0(t1)
                lw   a2, 8(t1)
                call kof_orm_mysql_bt_raw
                mv   s8, a0
                mv   a0, s8
                la   a1, .L77_eq
                li   a2, 3
                call kof_orm_sb_append
                mv   s8, a0
                ld   t0, 24(sp)
                slli t1, t0, 5
                add  t1, s3, t1
                slli t0, t0, 3
                addi t0, t0, 16
                add  t0, s1, t0
                mv   a0, s8
                mv   a2, t0
                mv   a1, t1
                call kof_orm_mysql_field_lit
                mv   s8, a0
            .L77_upd_col_next:
                ld   t0, 24(sp)
                addi t0, t0, 1
                sd   t0, 24(sp)
                j    .L77_upd_col
            .L77_upd_col_end:
                mv   a0, s8
                la   a1, .L77_where
                li   a2, 7
                call kof_orm_sb_append
                mv   s8, a0
                slli t1, s5, 5
                add  t1, s3, t1
                mv   a0, s8
                ld   a1, 0(t1)
                lw   a2, 8(t1)
                call kof_orm_mysql_bt_raw
                mv   s8, a0
                mv   a0, s8
                la   a1, .L77_eq
                li   a2, 3
                call kof_orm_sb_append
                mv   s8, a0
                slli t1, s5, 5
                add  t1, s3, t1
                slli t0, s5, 3
                addi t0, t0, 16
                add  t0, s1, t0
                mv   a0, s8
                mv   a2, t0
                mv   a1, t1
                call kof_orm_mysql_field_lit
                mv   s8, a0

            # ---- fecha o SQL + exec (COM QUERY, lanca no erro do servidor)
            .L77_finish:
                sb   zero, 0(s8)
                addi t0, s7, 24
                sub  t0, s8, t0
                sw   t0, 16(s7)
                mv   a0, s0
                mv   a1, s7
                call kof_orm_mysql_exec
                mv   s9, a0
                ld   t0, 16(sp)
                beqz t0, .L77_newinst
                li   t1, 1
                bne  t0, t1, .L77_ret_obj
                blez s9, .L77_upsert
                j    .L77_ret_obj

            # ---- pk gerada: LAST_INSERT_ID + NOVA instancia --------------
            .L77_newinst:
                la   a0, .L77_lastid
                li   a1, 23
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_db_mysql_scalar_int
                sd   a0, 40(sp)
                slli t0, s4, 3
                addi t0, t0, 16
                addi t0, t0, 15
                andi t0, t0, -16
                mv   a0, t0
                call kof_alloc
                mv   s11, a0
                mv   a0, s11
                lw   a1, 0(s1)
                ld   a2, 8(s1)
                call kof_init_object
                addi a0, s11, 16
                addi a1, s1, 16
                slli a2, s4, 3
                call kof_memcpy
                slli t0, s5, 3
                addi t0, t0, 16
                add  t0, s11, t0
                ld   t1, 40(sp)
                sd   t1, 0(t0)
                mv   a0, s11
                j    .L77_out
            .L77_ret_obj:
                mv   a0, s1
            .L77_out:
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
            .L77_i1:
                .ascii "INSERT INTO "
            .L77_i2:
                .ascii " ("
            .L77_i3:
                .ascii ") VALUES ("
            .L77_u1:
                .ascii "UPDATE "
            .L77_u2:
                .ascii " SET "
            .L77_where:
                .ascii " WHERE "
            .L77_eq:
                .ascii " = "
            .L77_cs:
                .ascii ", "
            .L77_null:
                .ascii "NULL"
            .L77_lastid:
                .ascii "SELECT LAST_INSERT_ID()"
            .L77_badv:
                .ascii "orm.saveAll: unsupported field type on Native (ORM001)"
            .section .text
            """;
}
