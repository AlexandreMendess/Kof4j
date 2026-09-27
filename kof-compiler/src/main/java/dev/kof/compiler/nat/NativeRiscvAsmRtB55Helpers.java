package dev.kof.compiler.nat;

// DB-3/DB-1 cross (22/09, lane gaps-db): helpers GLOBAIS das faces
// row-object do ORM no riscv64 — extraídos da fatia E-parte-2a no gate 500
// (RtB55 ia a 606): conexão, builder de SQL e bind de campo. Nomes globais
// (kof_orm_*) com ABI explícita — sem contrato de registrador escondido,
// reusáveis pelas próximas fatias (find/all/where/page) e seguros na poda
// (BFS por símbolo global, como kof_orm_parse_schema de RtB54).
//
// ABIs: kof_orm_conn(id*) -> conn (lança "unknown db connection: <id>");
// kof_orm_sb_append(buf,ptr,len) -> buf+len; kof_orm_sb_char(buf,byte) ->
// buf+1; kof_orm_sb_qraw(buf,ptr,len) -> buf' (appenda "ptr");
// kof_orm_sb_qstr(buf,KofString*) -> buf' (appenda "payload");
// kof_orm_bind_field(stmt,idx,slot*,typeCode) (port do RuntimeOrmBind).
public final class NativeRiscvAsmRtB55Helpers {

    private NativeRiscvAsmRtB55Helpers() {}

    static String RISCV_RUNTIME_ASM_B_55H = """
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_conn(a0=id*) -> a0=handle sqlite | lança (port .Lorm_conn)
            # ---------------------------------------------------------------
            .globl kof_orm_conn
            .type kof_orm_conn, @function
            kof_orm_conn:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                beqz a0, .L55h_conn_bad
                call kof_db_type
                li   t0, 1
                bne  a0, t0, .L55h_conn_bad
                mv   a0, s0
                call kof_db_resolve
                beqz a0, .L55h_conn_bad
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret
            .L55h_conn_bad:
                beqz s0, .L55h_conn_bad_null
                la   a0, .L55h_bc_pre
                li   a1, 23
                call kof_string_from_literal
                mv   a1, s0
                call kof_string_concat
                j    .L55h_conn_throw
            .L55h_conn_bad_null:
                la   a0, .L55h_bc_pre
                li   a1, 23
                call kof_string_from_literal
            .L55h_conn_throw:
                call kof_throw_string
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret

            # kof_orm_sb_append(a0=buf, a1=ptr, a2=len) -> a0=buf+len
            .globl kof_orm_sb_append
            .type kof_orm_sb_append, @function
            kof_orm_sb_append:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a2
                mv   a0, s0
                call kof_memcpy
                add  a0, s0, s1
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                addi sp, sp, 32
                ret

            # kof_orm_sb_char(a0=buf, a1=byte) -> a0=buf+1
            .globl kof_orm_sb_char
            .type kof_orm_sb_char, @function
            kof_orm_sb_char:
                sb   a1, 0(a0)
                addi a0, a0, 1
                ret

            # kof_orm_sb_qraw(a0=buf, a1=ptr, a2=len) -> a0 (appenda "ptr")
            .globl kof_orm_sb_qraw
            .type kof_orm_sb_qraw, @function
            kof_orm_sb_qraw:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s1, a1
                mv   s2, a2
                li   a1, 34
                call kof_orm_sb_char
                mv   a0, a0
                mv   a1, s1
                mv   a2, s2
                call kof_orm_sb_append
                li   a1, 34
                call kof_orm_sb_char
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                addi sp, sp, 48
                ret

            # kof_orm_sb_qstr(a0=buf, a1=KofString*) -> a0 (appenda "payload")
            .globl kof_orm_sb_qstr
            .type kof_orm_sb_qstr, @function
            kof_orm_sb_qstr:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s1, a1
                li   a1, 34
                call kof_orm_sb_char
                mv   s0, a0
                mv   a0, s0
                addi a1, s1, 24
                lw   a2, 16(s1)
                call kof_orm_sb_append
                li   a1, 34
                call kof_orm_sb_char
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                addi sp, sp, 32
                ret

            # kof_orm_bind_field(a0=stmt, a1=idx, a2=slot*, a3=typeCode)
            #  (port RuntimeOrmBind: int sign-extended como o Integer JDBC,
            #   float widened, KofString transient, null -> bind_null)
            .globl kof_orm_bind_field
            .type kof_orm_bind_field, @function
            kof_orm_bind_field:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                li   t0, 2
                beq  a3, t0, .L55h_bfld_str
                li   t0, 4
                beq  a3, t0, .L55h_bfld_dbl
                li   t0, 5
                beq  a3, t0, .L55h_bfld_flt
                li   t0, 1
                beq  a3, t0, .L55h_bfld_quad
                li   t0, 3
                beq  a3, t0, .L55h_bfld_quad
                lw   a2, 0(s2)
                j    .L55h_bfld_int
            .L55h_bfld_quad:
                ld   a2, 0(s2)
            .L55h_bfld_int:
                mv   a0, s0
                mv   a1, s1
                call sqlite3_bind_int64
                j    .L55h_bfld_ret
            .L55h_bfld_dbl:
                ld   t0, 0(s2)
                fmv.d.x fa0, t0
                j    .L55h_bfld_d
            .L55h_bfld_flt:
                lw   t0, 0(s2)
                fmv.w.x fa0, t0
                fcvt.d.s fa0, fa0
            .L55h_bfld_d:
                mv   a0, s0
                mv   a1, s1
                call sqlite3_bind_double
                j    .L55h_bfld_ret
            .L55h_bfld_str:
                ld   t2, 0(s2)
                beqz t2, .L55h_bfld_nul
                addi a2, t2, 24
                lw   a3, 16(t2)
                li   a4, -1
                mv   a0, s0
                mv   a1, s1
                call sqlite3_bind_text
                j    .L55h_bfld_ret
            .L55h_bfld_nul:
                mv   a0, s0
                mv   a1, s1
                call sqlite3_bind_null
            .L55h_bfld_ret:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                addi sp, sp, 48
                ret

            .section .rodata
            .L55h_bc_pre:
                .ascii "unknown db connection: "
            .section .text
            """;
}
